package org.academy.internal.common.ability.electromaster.skills.lv3;

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
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.DevCondition;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.ability.electromaster.skills.lv3.CurrentRecharge;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.util.EnergyChargeHelper;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;

public final class CurrentSymbiosis extends Skill {
    private static final int CHARGE_INTERVAL_TICKS = 10;

    public CurrentSymbiosis() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL3)
                .energyCost(30_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(30)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.CURRENT_RECHARGE)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL3))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        InputSystem.addKeyBinding(
                Client.KEY_NAME_TOGGLE,
                Client.CONFIG.getKeyBinding(
                        Client.KEY_NAME_TOGGLE,
                        InputSystem.combo(
                                InputSystem.InputType.KEYBOARD,
                                InputConstants.KEY_H,
                                InputConstants.RELEASE,
                                InputConstants.MOD_ALT
                        )
                ),
                _ -> Client.toggle()
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.ELECTROMASTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.CURRENT_SYMBIOSIS.get(),
                        List.of(CurrentRecharge.Client.SKILL_INFO),
                        R.textures.current_symbiosis_icon,
                        144,
                        46
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.CURRENT_SYMBIOSIS + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (!AbilitySystemClient.beginToggleRequest(Skills.CURRENT_SYMBIOSIS.get())) return;
            MisakaNetworkClient.send(TogglePacket.INSTANCE);
        }

        public static final class Config extends KeyBindingConfig {
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
            Skills.CURRENT_SYMBIOSIS.get().toggle(packet.getPacketListener().getPlayer());
        }

        public static float adjustNextCastCost(ServerPlayer player, Skill castSkill, float amount) {
            if (player == null || castSkill == null || castSkill.getCategory() != AbilityCategories.ELECTROMASTER.get()) {
                return amount;
            }
            var sourceSkill = Skills.CURRENT_SYMBIOSIS.get();
            var now = player.level().getGameTime();
            if (TimedSkillEffectRuntime.consume(player.getUUID(), player.getUUID(), sourceSkill,
                    "overcharge", now).isEmpty()) return amount;
            TimedSkillEffectRuntime.put(player, player.getUUID(), sourceSkill,
                    "overcharge_cooldown", 100, 1.0f);
            return amount * 0.8f;
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            var skill = Skills.CURRENT_SYMBIOSIS.get();
            if (!skill.isEnabled(player)) return;

            var system = AbilitySystemServer.getSystem(player);
            var uuid = player.getUUID();
            if (!system.ensurePermanentOccupation(
                    uuid,
                    skill.getMaintenanceCost(player),
                    skill
            )) {
                system.toggleSkill(uuid, skill.getKeyString());
                return;
            }
            if (player.level().getGameTime() % CHARGE_INTERVAL_TICKS == 0) {
                EnergyChargeHelper.chargeEquipment(player);
                var milestone = skill.getEffectiveProficiencyMilestone(player);
                if (milestone >= 2) EnergyChargeHelper.chargeHotbar(player);
                if (milestone >= 3 && EnergyChargeHelper.hasFullyChargedEquipment(player, true)) {
                    var now = player.level().getGameTime();
                    if (TimedSkillEffectRuntime.get(player.getUUID(), player.getUUID(), skill,
                            "overcharge", now).isEmpty()
                            && TimedSkillEffectRuntime.get(player.getUUID(), player.getUUID(), skill,
                            "overcharge_cooldown", now).isEmpty()) {
                        TimedSkillEffectRuntime.put(player, player.getUUID(), skill,
                                "overcharge", 72_000, 1.0f);
                    }
                }
            }
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
            return PacketTypes.CURRENT_SYMBIOSIS_TOGGLE.get();
        }
    }
}
