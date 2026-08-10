package org.academy.internal.common.ability.darkmatter.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.client.resources.R;
import org.academy.api.client.util.ClientUtil;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class DarkmatterRepair extends Skill {
    static final float BASE_HEAL = 4.0f;

    public DarkmatterRepair() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL4)
                .energyCost(60_000)
                .passive()
                .initiallyDisabled()
                .cpCost(10)
                .iterationTicks(40)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.DARKMATTER_SHAPING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL4))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Shaping", "academy:darkmatter_shaping"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(
                Client.KEY_NAME_TOGGLE,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_U,
                        InputConstants.RELEASE, InputConstants.MOD_ALT)
        ), context -> Client.toggle());
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_REPAIR.get(),
                        List.of(DarkmatterShaping.Client.SKILL_INFO),
                        R.textures.darkmatter_repair_icon,
                        150,
                        40
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.DARKMATTER_REPAIR + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (ClientUtil.hasScreen()
                    || !AbilitySystemClient.beginToggleRequest(Skills.DARKMATTER_REPAIR.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public Config getDefault() {
                    return new Config();
                }

                @Override
                public Class<Config> getTypeClass() {
                    return Config.class;
                }
            }
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            Skills.DARKMATTER_REPAIR.get().toggle(packet.getPacketListener().getPlayer());
        }

        static float healAmount(float abilityPower) {
            return BASE_HEAL * abilityPower;
        }

        private static void tick(ServerPlayer player) {
            var skill = Skills.DARKMATTER_REPAIR.get();
            if (!skill.isEnabled(player) || !player.isAlive()
                    || player.getHealth() >= player.getMaxHealth()) return;

            var system = AbilitySystemServer.getSystem(player);
            skill.executeContinuous(player, (context, actualCost) -> {
                player.heal(healAmount(system.getPlayerAbilityPowerMultiplier(player.getUUID())));
            }, true);
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class TogglePacket extends Packet<ServerGamePacketListenerImpl, TogglePacket> {
        public static final TogglePacket INSTANCE = new TogglePacket();
        public static final StreamCodec<ByteBuf, TogglePacket> CODEC = StreamCodec.unit(INSTANCE);

        private TogglePacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, TogglePacket> getPacketType() {
            return PacketTypes.DARKMATTER_REPAIR_TOGGLE.get();
        }
    }
}
