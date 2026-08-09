package org.academy.internal.common.ability.aeromanip.skills;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
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
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.ability.SkillFlightController;
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

public final class Flight extends Skill {
    private static final Identifier FLIGHT_SOURCE =
            AcademyCraft.academy(SkillNames.FLIGHT);
    private static final float ACCELERATION_CP_COST = 8.0f;
    private static final int ACCELERATION_CP_INTERVAL_TICKS = 20;
    private static final double NORMAL_FLIGHT_SPEED_CAP = 0.7;
    private static final double ACCELERATED_FLIGHT_SPEED_CAP = 1.1;

    public Flight() {
        super(Builder
                .of(AbilityCategories.AEROMANIP.get())
                .level(AbilityLevel.LEVEL5)
                .energyCost(100_000)
                .passive()
                .initiallyDisabled()
                .maintenanceCost(60)
                .iterationTicks(40)
                .maxStacks(NO_STACK_LIMIT)
                .dependsOn(Skills.WIND_CORRIDOR)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL5))
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
                                InputConstants.KEY_F,
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
                AbilityCategories.AEROMANIP.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.FLIGHT.get(),
                        List.of(WindCorridor.Client.SKILL_INFO),
                        R.textures.flight_icon,
                        150,
                        104
                )
        );
        public static final String KEY_NAME_TOGGLE = SkillNames.FLIGHT + "_toggle";
        public static Config CONFIG = new Config();

        private Client() {
        }

        private static void toggle() {
            if (!AbilitySystemClient.canToggleSkill(Skills.FLIGHT.get())) return;
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
        private static final Map<ServerPlayer, Integer> NEXT_ACCELERATION_COST_TICK = new WeakHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void handle(TogglePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.FLIGHT.get();
            skill.toggle(player);
            sync(player);
        }

        private static void sync(ServerPlayer player) {
            SkillFlightController.setSource(
                    player,
                    FLIGHT_SOURCE,
                    Skills.FLIGHT.get().isEnabled(player) && player.isAlive()
            );
        }

        /** Immediately refreshes the creative-flight lease after server-side automation toggles it. */
        public static void refreshFlightPermission(ServerPlayer player) {
            if (player != null) sync(player);
        }

        public static boolean usesFlightAccelerationCost(ServerPlayer player) {
            return player.isAlive() && !player.hasDisconnected()
                    && Skills.FLIGHT.get().isEnabled(player)
                    && player.getAbilities().flying;
        }

        private static boolean chargeAccelerationCost(
                ServerPlayer player,
                AbilitySystemServer system,
                Skill skill
        ) {
            var nextCostTick = NEXT_ACCELERATION_COST_TICK.getOrDefault(
                    player,
                    player.tickCount
            );
            if (player.tickCount < nextCostTick) return true;
            var paid = system.tryTimedOccupation(
                    player.getUUID(),
                    ACCELERATION_CP_COST
                            * AeromanipConfig.cpMultiplier(player, SkillNames.FLIGHT),
                    skill,
                    ACCELERATION_CP_INTERVAL_TICKS
            );
            if (paid) {
                NEXT_ACCELERATION_COST_TICK.put(
                        player,
                        player.tickCount + ACCELERATION_CP_INTERVAL_TICKS
                );
            }
            return paid;
        }
    }

    static boolean consumesAccelerationCp(
            boolean flightSkillEnabled,
            boolean creativeFlightActive,
            boolean sprinting,
            boolean airflowJetActive,
            double speed
    ) {
        return flightSkillEnabled && creativeFlightActive
                && (sprinting || airflowJetActive
                || Double.isFinite(speed) && speed > NORMAL_FLIGHT_SPEED_CAP);
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            var skill = Skills.FLIGHT.get();
            var enabled = skill.isEnabled(player) && player.isAlive() && !player.hasDisconnected();
            if (enabled) {
                var system = AbilitySystemServer.getSystem(player);
                var creativeFlightActive = player.getAbilities().flying;
                if (creativeFlightActive) {
                    enabled = system.ensurePermanentOccupation(
                            player.getUUID(),
                            skill.getMaintenanceCost(skill.getLevel(player))
                                    * AeromanipConfig.cpMultiplier(player, SkillNames.FLIGHT),
                            skill
                    );
                } else {
                    system.releaseMaintenanceOccupation(player.getUUID(), skill.getKeyString());
                }
                if (!enabled && skill.isEnabled(player)) {
                    system.toggleSkill(player.getUUID(), skill.getKeyString());
                }
                if (enabled) {
                    var velocity = player.getDeltaMovement();
                    var speed = velocity.length();
                    if (!Double.isFinite(speed) || !Double.isFinite(velocity.x)
                            || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) {
                        player.setDeltaMovement(0, 0, 0);
                        Server.NEXT_ACCELERATION_COST_TICK.remove(player);
                    } else {
                        var accelerationActive = consumesAccelerationCp(
                                enabled,
                                creativeFlightActive,
                                player.isSprinting(),
                                AirflowJet.Server.isActive(player),
                                speed
                        );
                        if (accelerationActive) {
                            enabled = Server.chargeAccelerationCost(player, system, skill);
                            if (!enabled && skill.isEnabled(player)) {
                                system.toggleSkill(player.getUUID(), skill.getKeyString());
                            }
                        } else {
                            Server.NEXT_ACCELERATION_COST_TICK.remove(player);
                        }

                        var cap = enabled && accelerationActive
                                ? ACCELERATED_FLIGHT_SPEED_CAP
                                : NORMAL_FLIGHT_SPEED_CAP;
                        if (speed > cap) {
                            var desired = velocity.scale(cap / speed);
                            AeromanipTargeting.addClampedVelocity(player, desired.subtract(velocity));
                        }
                    }
                }
            } else {
                Server.NEXT_ACCELERATION_COST_TICK.remove(player);
            }
            SkillFlightController.setSource(player, FLIGHT_SOURCE, enabled);
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
            return PacketTypes.FLIGHT_TOGGLE.get();
        }
    }
}
