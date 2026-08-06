package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
import org.academy.api.server.ability.ServerContext;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipFieldSyncPacket;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class AirflowJet extends Skill {
    private static final double SPEED_MULTIPLIER = 1.5;
    private static final double LAUNCH_SPEED = 1.4;
    private static final int CP_INTERVAL_TICKS = 10;

    public AirflowJet() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(10)
                .iterationTicks(10)
                .maxStacks(NO_STACK_LIMIT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    @Override
    public void initClient() {
        AeromanipFieldSyncPacket.initClient();
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        var defaultBinding = InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                InputConstants.KEY_R,
                InputSystem.ANY_ACTION,
                0
        );
        var configuredBinding = Client.CONFIG.getKeyBinding(Client.KEY_NAME_CAST, defaultBinding);
        if (configuredBinding.action() != InputSystem.ANY_ACTION
                || configuredBinding.type() == InputSystem.InputType.KEYBOARD
                && configuredBinding.keys().equals(java.util.Set.of(InputConstants.KEY_R))
                && configuredBinding.modifiers() == InputSystem.ANY_MODIFIER) {
            configuredBinding = new InputSystem.KeyCombination(
                    configuredBinding.type(),
                    configuredBinding.keys(),
                    InputSystem.ANY_ACTION,
                    configuredBinding.type() == InputSystem.InputType.KEYBOARD
                            && configuredBinding.keys().equals(java.util.Set.of(InputConstants.KEY_R))
                            && configuredBinding.modifiers() == InputSystem.ANY_MODIFIER
                            ? 0
                            : configuredBinding.modifiers(),
                    configuredBinding.availableWhenScreen(),
                    configuredBinding.unbound()
            );
            Client.CONFIG.setKeyBinding(Client.KEY_NAME_CAST, configuredBinding);
            AcademyCraftClient.Config.INSTANCE.save();
        }
        InputSystem.addKeyBinding(
                Client.KEY_NAME_CAST,
                configuredBinding,
                Client::handleInput
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.AIRFLOW_JET.get(),
                        List.of(),
                        R.textures.airflow_jet_icon,
                        20,
                        40
                )
        );
        public static final String KEY_NAME_CAST = SkillNames.AIRFLOW_JET + "_cast";
        public static Config CONFIG = new Config();

        private Client() {
        }

        public static void handleInput(InputSystem.BindingContext context) {
            if (context.action() == InputConstants.PRESS) {
                if (!AbilitySystemClient.canUseSkill(Skills.AIRFLOW_JET.get())) return;
                MisakaNetworkClient.send(StartPacket.INSTANCE);
            } else if (context.action() == InputConstants.RELEASE) {
                MisakaNetworkClient.send(StopPacket.INSTANCE);
            }
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
        private static final Map<ServerPlayer, Context> ACTIVE = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handleStart(StartPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.AIRFLOW_JET.get();
            if (ACTIVE.containsKey(player) || !skill.isEnabled(player)) return;
            var context = new Context(player);
            ACTIVE.put(player, context);
            AbilitySystemServer.registerContext(context);
        }

        @SubscribePacket
        public static void handleStop(StopPacket packet) {
            var context = ACTIVE.get(packet.getPacketListener().getPlayer());
            if (context != null) context.end();
        }

        private static final class Context extends ServerContext {
            private final ServerLevel initialLevel;
            private int ticks;
            private boolean ended;

            private Context(ServerPlayer player) {
                super(player);
                initialLevel = player.level();
            }

            private void end() {
                if (ended) return;
                ended = true;
                unregister();
            }

            @SubscribeEvent
            public void onTick(ServerTickEvent.Pre event) {
                var skill = Skills.AIRFLOW_JET.get();
                if (ended || player.hasDisconnected() || !player.isAlive()
                        || player.level() != initialLevel || !skill.isEnabled(player)) {
                    end();
                    return;
                }

                if (ticks % CP_INTERVAL_TICKS == 0
                        && !AbilitySystemServer.getSystem(player).tryTimedOccupation(
                        player.getUUID(),
                        skill.getCpCost(skill.getLevel(player))
                                * AeromanipConfig.cpMultiplier(player, SkillNames.AIRFLOW_JET),
                        skill,
                        CP_INTERVAL_TICKS)) {
                    end();
                    return;
                }

                if (player.isShiftKeyDown()) {
                    AeromanipTargeting.scaleVelocity(player, 0.15);
                } else {
                    var direction = player.getLookAngle().add(0.0, 0.12, 0.0);
                    var skillLevel = Math.max(0, Math.min(2, skill.getLevel(player)));
                    var response = 0.38 + skillLevel * 0.1;
                    AeromanipTargeting.steerVelocity(
                            player,
                            direction,
                            response,
                            (LAUNCH_SPEED + skillLevel * 0.1) * SPEED_MULTIPLIER
                    );
                }
                player.resetFallDistance();
                spawnEffects();
                ticks++;
            }

            private void spawnEffects() {
                if ((ticks & 1) == 0) {
                    initialLevel.sendParticles(
                            ParticleTypes.CLOUD,
                            player.getX(),
                            player.getY() + 0.8,
                            player.getZ(),
                            4,
                            0.25,
                            0.25,
                            0.25,
                            0.015
                    );
                }
                if (ticks % 20 == 0) {
                    initialLevel.playSound(null, player.blockPosition(),
                            org.academy.internal.common.sounds.SoundEvents.AIRFLOW_JET.get(),
                            SoundSource.PLAYERS, 0.55f, 1.0f);
                }
            }

            @Override
            protected void onUnregistered() {
                ACTIVE.remove(player, this);
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StartPacket extends Packet<ServerGamePacketListenerImpl, StartPacket> {
        public static final StartPacket INSTANCE = new StartPacket();
        public static final StreamCodec<ByteBuf, StartPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StartPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StartPacket> getPacketType() {
            return PacketTypes.AIRFLOW_JET_START.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class StopPacket extends Packet<ServerGamePacketListenerImpl, StopPacket> {
        public static final StopPacket INSTANCE = new StopPacket();
        public static final StreamCodec<ByteBuf, StopPacket> CODEC = StreamCodec.unit(INSTANCE);

        private StopPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, StopPacket> getPacketType() {
            return PacketTypes.AIRFLOW_JET_STOP.get();
        }
    }
}
