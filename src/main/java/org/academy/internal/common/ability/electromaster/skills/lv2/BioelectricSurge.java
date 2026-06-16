package org.academy.internal.common.ability.electromaster.skills.lv2;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.AcademyCraftClient;
import org.academy.AcademyCraftConfig;
import org.academy.api.client.config.KeyBindingConfig;
import org.academy.api.client.input.InputSystem;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.gson.TypeHandler;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.BioelectricSurgeData;
import org.lwjgl.glfw.GLFW;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.LinkedHashSet;
import java.util.Set;

public class BioelectricSurge extends Skill {
    private static final int MAX_DEBUFF_TICKS = 6000;
    private static final int MIN_DEBUFF_TICKS = 100;

    public BioelectricSurge() {
        super(Builder
                .of(AbilityCategories.ELECTROMASTER.get())
                .level(AbilityLevel.LEVEL2)
                .passive()
                .maintenanceCost(30)
                .withCustomData(BioelectricSurgeData.ID, BioelectricSurgeData.class, player -> new BioelectricSurgeData())
                .dependsOn(Skills.ARC_GENERATE)
        );
    }

    @Override
    public float getMaintenanceCost(int skillLevel) {
        if (skillLevel >= 3) return 15;
        return super.getMaintenanceCost(skillLevel);
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);

        InputSystem.addKeyBinding(Client.KEY_NAME_TOGGLE, Client.CONFIG.getKeyBinding(Client.KEY_NAME_TOGGLE,
                new InputSystem.InputPair(
                        InputSystem.InputType.KEYBOARD,
                        new InputSystem.KeyInfo(
                                new LinkedHashSet<>(Set.of(GLFW.GLFW_KEY_J)),
                                GLFW.GLFW_RELEASE,
                                new LinkedHashSet<>()
                        )
                )
        ), Client::onToggle);
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final String KEY_NAME_TOGGLE = SkillNames.BIOELECTRIC_SURGE + "_toggle";
        public static Config CONFIG = new Config();

        public static void onToggle() {
            MisakaNetworkClient.sendPacket(TogglePacket.INSTANCE);
        }

        public static class Config extends KeyBindingConfig {
            public static final class Action implements TypeHandler<Config> {
                public static final TypeHandler<Config> INSTANCE = new Action();

                private Action() {
                }

                @Override
                public BioelectricSurge.Client.Config getDefault() {
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
        @SubscribePacket
        public static void handleToggle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.BIOELECTRIC_SURGE.get();
            var wasEnabled = skill.isEnabled(player);

            skill.toggle(player);

            if (wasEnabled) {
                skill.<BioelectricSurgeData>getRuntimeData(player).ifPresent(data -> {
                    var activeTicks = data.getAccumulatedActiveTicks();
                    var debuffDuration = Math.clamp(activeTicks, MIN_DEBUFF_TICKS, MAX_DEBUFF_TICKS);

                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, debuffDuration, 0, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, debuffDuration, 0, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, debuffDuration, 0, false, false));
                    player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, debuffDuration, 0, false, false));

                    data.resetActiveTicks();
                });
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private static final int BUFF_DURATION = 100;

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var skill = Skills.BIOELECTRIC_SURGE.get();
            if (!skill.isEnabled(player)) return;

            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BUFF_DURATION, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BUFF_DURATION, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, BUFF_DURATION, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, BUFF_DURATION, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, BUFF_DURATION, 0, false, false));

            skill.<BioelectricSurgeData>getRuntimeData(player).ifPresent(BioelectricSurgeData::incrementActiveTick);
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
            return PacketTypes.BIOELECTRIC_SURGE_TOGGLE.get();
        }
    }
}
