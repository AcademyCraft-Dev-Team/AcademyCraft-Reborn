package org.academy.internal.common.ability.darkmatter.skills.lv2;

import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterShaping;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DarkmatterPhaseTuning extends Skill {
    static final float PHASE_PER_TICK = 0.01f;

    public DarkmatterPhaseTuning() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL2)
                .energyCost(10_000)
                .cpCost(0)
                .dependsOn(Skills.DARKMATTER_SHAPING)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL2))
                .devCondition(new DevCondition.DependencyCondition(
                        "Dark Matter Shaping", "academy:darkmatter_shaping"))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        register(Client.KEY_ALPHA, Client.LEGACY_KEY_ALPHA_PRESS, Client.LEGACY_KEY_ALPHA_RELEASE,
                Direction.ALPHA, 0);
        register(Client.KEY_BETA, Client.LEGACY_KEY_BETA_PRESS, Client.LEGACY_KEY_BETA_RELEASE,
                Direction.BETA, InputConstants.MOD_ALT);
        AcademyCraftClient.Config.INSTANCE.save();
    }

    private static void register(
            String keyName,
            String legacyPress,
            String legacyRelease,
            Direction direction,
            int modifiers
    ) {
        var binding = Client.CONFIG.getMaintainedKeyBinding(
                keyName,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_J,
                        InputSystem.ANY_ACTION, modifiers),
                legacyPress,
                legacyRelease
        );
        InputSystem.addMaintainedKeyBinding(
                keyName,
                binding,
                _ -> Client.control(direction, true),
                _ -> Client.control(direction, false),
                _ -> Client.heartbeat(direction),
                () -> AbilitySystemClient.canUseSkill(Skills.DARKMATTER_PHASE_TUNING.get())
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public enum Direction {
        ALPHA(1.0f),
        BETA(-1.0f);

        private final float sign;

        Direction(float sign) {
            this.sign = sign;
        }
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_PHASE_TUNING.get(),
                        List.of(DarkmatterShaping.Client.SKILL_INFO),
                        R.textures.darkmatter_shaping_icon,
                        98,
                        40
                )
        );
        public static final String KEY_ALPHA = SkillNames.DARKMATTER_PHASE_TUNING + "_alpha";
        public static final String KEY_BETA = SkillNames.DARKMATTER_PHASE_TUNING + "_beta";
        private static final String LEGACY_KEY_ALPHA_PRESS = SkillNames.DARKMATTER_PHASE_TUNING + "_alpha_press";
        private static final String LEGACY_KEY_ALPHA_RELEASE = SkillNames.DARKMATTER_PHASE_TUNING + "_alpha_release";
        private static final String LEGACY_KEY_BETA_PRESS = SkillNames.DARKMATTER_PHASE_TUNING + "_beta_press";
        private static final String LEGACY_KEY_BETA_RELEASE = SkillNames.DARKMATTER_PHASE_TUNING + "_beta_release";
        public static Config CONFIG = new Config();
        private static Direction activeDirection;

        private Client() {
        }

        private static void control(Direction direction, boolean pressed) {
            if (pressed) {
                if (activeDirection != null || ClientUtil.hasScreen()
                        || !AbilitySystemClient.canUseSkill(Skills.DARKMATTER_PHASE_TUNING.get())) return;
                activeDirection = direction;
                MisakaNetworkClient.send(new ControlPacket(direction, true));
            } else if (activeDirection == direction) {
                activeDirection = null;
                MisakaNetworkClient.send(new ControlPacket(direction, false));
            }
        }

        private static void heartbeat(Direction direction) {
            if (activeDirection == direction) {
                MisakaNetworkClient.send(new ControlPacket(direction, true));
            }
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
        private static final Map<UUID, TuningState> TUNING = new ConcurrentHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void control(ControlPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (packet.pressed()) {
                beginTuning(player, packet.direction());
            } else {
                endTuning(player, packet.direction());
            }
        }

        /** Starts or refreshes the server lease used by maintained-key packets and GameTests. */
        public static boolean beginTuning(ServerPlayer player, Direction direction) {
            if (!Skills.DARKMATTER_PHASE_TUNING.get().isEnabled(player)) return false;
            TUNING.compute(player.getUUID(), (_, existing) -> {
                var now = player.level().getGameTime();
                if (existing != null && existing.direction == direction
                        && existing.level == player.level()) {
                    existing.leaseExpiresAt = now + TuningState.LEASE_TICKS;
                    return existing;
                }
                return new TuningState(direction, player.level(), now,
                        now + TuningState.LEASE_TICKS);
            });
            return true;
        }

        /** Ends the lease only when the released direction matches the active direction. */
        public static void endTuning(ServerPlayer player, Direction direction) {
            TUNING.computeIfPresent(player.getUUID(), (_, state) ->
                    state.direction == direction ? null : state);
        }

        public static boolean isTuning(ServerPlayer player) {
            return TUNING.containsKey(player.getUUID());
        }

        public static void tick(ServerPlayer player) {
            var state = TUNING.get(player.getUUID());
            var skill = Skills.DARKMATTER_PHASE_TUNING.get();
            if (state == null) return;
            if (!player.isAlive() || player.hasDisconnected() || state.level != player.level()
                    || !skill.isEnabled(player)) {
                TUNING.remove(player.getUUID());
                return;
            }
            var now = player.level().getGameTime();
            if (now > state.leaseExpiresAt) {
                TUNING.remove(player.getUUID(), state);
                return;
            }
            var elapsedTicks = now - state.lastProcessedAt;
            if (elapsedTicks <= 0) return;
            state.lastProcessedAt = now;
            var resource = AbilitySystemServer.getSystem(player).getDarkmatterResourceManager();
            var delta = phasePointDelta(
                    resource.getPhaseSnapshot(player).totalPoints(),
                    skill.getEffectiveProficiencyMilestone(player), elapsedTicks);
            var changed = resource.tuneAlphaPoints(player, state.direction.sign * delta);
            if (changed) skill.reportActivity(player, true);
        }

        static float phaseStep(int milestone) {
            return PHASE_PER_TICK * (1.0f + Math.clamp(milestone, 0, 3) * 0.25f);
        }

        static float phasePointStep(int totalPoints, int milestone) {
            return Math.max(0, totalPoints) / 200.0f
                    * (1.0f + Math.clamp(milestone, 0, 3) * 0.25f);
        }

        static float phasePointDelta(int totalPoints, int milestone, long elapsedTicks) {
            if (elapsedTicks <= 0) return 0.0f;
            return phasePointStep(totalPoints, milestone) * elapsedTicks;
        }

        static void clear(ServerPlayer player) {
            TUNING.remove(player.getUUID());
        }

        private static final class TuningState {
            private static final long LEASE_TICKS = 60;
            private final Direction direction;
            private final Object level;
            private long lastProcessedAt;
            private long leaseExpiresAt;

            private TuningState(Direction direction, Object level, long lastProcessedAt,
                                long leaseExpiresAt) {
                this.direction = direction;
                this.level = level;
                this.lastProcessedAt = lastProcessedAt;
                this.leaseExpiresAt = leaseExpiresAt;
            }
        }
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onTick(PlayerTickEvent.Post event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.tick(player);
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clear(player);
        }

        @SubscribeEvent
        public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) Server.clear(player);
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ControlPacket extends Packet<ServerGamePacketListenerImpl, ControlPacket> {
        public static final StreamCodec<ByteBuf, ControlPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.map(
                        value -> Direction.values()[Math.clamp(value, 0, Direction.values().length - 1)],
                        Enum::ordinal
                ), ControlPacket::direction,
                ByteBufCodecs.BOOL, ControlPacket::pressed,
                ControlPacket::new
        );
        private final Direction direction;
        private final boolean pressed;

        public ControlPacket(Direction direction, boolean pressed) {
            this.direction = direction;
            this.pressed = pressed;
        }

        public Direction direction() {
            return direction;
        }

        public boolean pressed() {
            return pressed;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ControlPacket> getPacketType() {
            return PacketTypes.DARKMATTER_PHASE_TUNING_CONTROL.get();
        }
    }
}
