package org.academy.internal.common.ability.darkmatter.skills.lv1;

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
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
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

public final class DarkmatterGeneration extends Skill {
    static final float SECONDS_TO_FILL_BASE_CAPACITY = 10.0f;
    static final float PHASE_RATE_PER_POWER = 0.15f;

    public DarkmatterGeneration() {
        super(Builder
                .of(AbilityCategories.DARKMATTER.get())
                .level(AbilityLevel.LEVEL1)
                .energyCost(5_000)
                .cpCost(0)
                .iterationTicks(20)
                .maxStacks(NO_STACK_LIMIT)
                .devCondition(new DevCondition.LevelCondition(AbilityLevel.LEVEL1))
        );
    }

    @Override
    public void initClient() {
        var key = getKey();
        AcademyCraftConfig.registerTypeHandler(key, Client.Config.Action.INSTANCE);
        Client.CONFIG = AcademyCraftClient.Config.INSTANCE.getConfig(key);
        register(
                Client.KEY_CREATE,
                Client.LEGACY_KEY_CREATE_PRESS,
                Client.LEGACY_KEY_CREATE_RELEASE,
                Mode.CREATE,
                0
        );
        register(
                Client.KEY_ERASE,
                Client.LEGACY_KEY_ERASE_PRESS,
                Client.LEGACY_KEY_ERASE_RELEASE,
                Mode.ERASE,
                InputConstants.MOD_ALT
        );
        AcademyCraftClient.Config.INSTANCE.save();
    }

    private static void register(
            String keyName,
            String legacyPress,
            String legacyRelease,
            Mode mode,
            int modifiers
    ) {
        var binding = Client.CONFIG.getMaintainedKeyBinding(
                keyName,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_G,
                        InputSystem.ANY_ACTION, modifiers),
                legacyPress,
                legacyRelease
        );
        InputSystem.addMaintainedKeyBinding(
                keyName,
                binding,
                _ -> Client.control(mode, true),
                _ -> Client.control(mode, false),
                _ -> Client.heartbeat(mode),
                () -> AbilitySystemClient.canUseSkill(Skills.DARKMATTER_GENERATION.get())
        );
    }

    @Override
    public void initServer(MinecraftServerContext context) {
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    static float unitsForDuration(float baseCapacity, long heldTicks, boolean sixWings) {
        return unitsForDuration(baseCapacity, heldTicks, 0.0f,
                sixWings ? 1.0f : 0.0f, 0);
    }

    static float unitsForDuration(
            float baseCapacity,
            long heldTicks,
            boolean sixWings,
            int proficiencyMilestone
    ) {
        return unitsForDuration(baseCapacity, heldTicks, 0.0f,
                sixWings ? 1.0f : 0.0f, proficiencyMilestone, 1.0f);
    }

    static float unitsForDuration(float baseCapacity, long heldTicks, float phasePower,
                                  float gammaPower, int proficiencyMilestone,
                                  float gammaMagnitudeMultiplier) {
        if (!Float.isFinite(baseCapacity) || baseCapacity <= 0.0f || heldTicks <= 0) return 0.0f;
        var milestone = Math.clamp(proficiencyMilestone, 0, 3);
        var safePhase = Float.isFinite(phasePower) ? Math.max(0.0f, phasePower) : 0.0f;
        var safeGamma = Float.isFinite(gammaPower) ? Math.max(0.0f, gammaPower) : 0.0f;
        var gammaCoefficient = (milestone >= 3 ? 0.15f : 0.10f)
                * Math.max(1.0f, gammaMagnitudeMultiplier);
        var multiplier = (1.0f + PHASE_RATE_PER_POWER * safePhase)
                * (1.0f + gammaCoefficient * safeGamma);
        if (milestone >= 1) multiplier *= 1.15f;
        return baseCapacity / SECONDS_TO_FILL_BASE_CAPACITY
                * heldTicks / 20.0f
                * multiplier;
    }

    static float unitsForDuration(
            float baseCapacity,
            long heldTicks,
            float phasePower,
            float gammaPower,
            int proficiencyMilestone
    ) {
        return unitsForDuration(baseCapacity, heldTicks, phasePower, gammaPower,
                proficiencyMilestone, 1.0f);
    }

    static float cpPerCreatedMatter(float gammaPower, int proficiencyMilestone) {
        return cpPerCreatedMatter(gammaPower, proficiencyMilestone, 1.0f);
    }

    static float cpPerCreatedMatter(float gammaPower, int proficiencyMilestone,
                                    float gammaMagnitudeMultiplier) {
        var milestone = Math.clamp(proficiencyMilestone, 0, 3);
        var base = milestone >= 2 ? 1.8f : 2.0f;
        var gammaCoefficient = (milestone >= 3 ? 0.15f : 0.10f)
                * Math.max(1.0f, gammaMagnitudeMultiplier);
        var gamma = Float.isFinite(gammaPower) ? Math.max(0.0f, gammaPower) : 0.0f;
        return Math.max(1.0f, base - gammaCoefficient * gamma);
    }

    public enum Mode {
        CREATE,
        ERASE
    }

    public static final class Client {
        public static final AbilitySystemClient.SkillInfo SKILL_INFO = AbilitySystemClient.addSkillInfo(
                AbilityCategories.DARKMATTER.get(),
                new AbilitySystemClient.SkillInfo(
                        Skills.DARKMATTER_GENERATION.get(),
                        List.of(),
                        R.textures.darkmatter_shaping_icon,
                        20,
                        72
                )
        );
        public static final String KEY_CREATE = SkillNames.DARKMATTER_GENERATION + "_create";
        public static final String KEY_ERASE = SkillNames.DARKMATTER_GENERATION + "_erase";
        private static final String LEGACY_KEY_CREATE_PRESS = SkillNames.DARKMATTER_GENERATION + "_create_press";
        private static final String LEGACY_KEY_CREATE_RELEASE = SkillNames.DARKMATTER_GENERATION + "_create_release";
        private static final String LEGACY_KEY_ERASE_PRESS = SkillNames.DARKMATTER_GENERATION + "_erase_press";
        private static final String LEGACY_KEY_ERASE_RELEASE = SkillNames.DARKMATTER_GENERATION + "_erase_release";
        public static Config CONFIG = new Config();
        private static Mode activeMode;

        private Client() {
        }

        private static void control(Mode mode, boolean pressed) {
            if (pressed) {
                if (activeMode != null || ClientUtil.hasScreen()
                        || !AbilitySystemClient.canUseSkill(Skills.DARKMATTER_GENERATION.get())) return;
                activeMode = mode;
                MisakaNetworkClient.send(new ControlPacket(mode, true));
            } else if (activeMode == mode) {
                activeMode = null;
                MisakaNetworkClient.send(new ControlPacket(mode, false));
            }
        }

        private static void heartbeat(Mode mode) {
            if (activeMode == mode) MisakaNetworkClient.send(new ControlPacket(mode, true));
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
        private static final Map<UUID, HoldState> HOLDING = new ConcurrentHashMap<>();

        private Server() {
        }

        @SubscribePacket
        public static void control(ControlPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var skill = Skills.DARKMATTER_GENERATION.get();
            if (packet.pressed()) {
                if (!skill.isEnabled(player)) return;
                HOLDING.compute(player.getUUID(), (_, existing) -> {
                    var now = player.level().getGameTime();
                    if (existing != null && existing.mode == packet.mode()
                            && existing.level == player.level()) {
                        existing.leaseExpiresAt = now + HoldState.LEASE_TICKS;
                        return existing;
                    }
                    return new HoldState(packet.mode(), now, player.level());
                });
                return;
            }

            var state = HOLDING.remove(player.getUUID());
            if (state != null && state.mode != packet.mode()) {
                HOLDING.putIfAbsent(player.getUUID(), state);
            }
        }

        static void tick(ServerPlayer player) {
            var state = HOLDING.get(player.getUUID());
            if (state == null) return;
            var skill = Skills.DARKMATTER_GENERATION.get();
            if (!player.isAlive() || player.hasDisconnected() || state.level != player.level()
                    || !skill.isEnabled(player)) {
                HOLDING.remove(player.getUUID());
                return;
            }

            var now = player.level().getGameTime();
            if (now > state.leaseExpiresAt) {
                HOLDING.remove(player.getUUID(), state);
                return;
            }
            var elapsedTicks = now - state.lastProcessedAt;
            if (elapsedTicks <= 0) return;
            state.lastProcessedAt = now;
            var resource = AbilitySystemServer.getSystem(player).getDarkmatterResourceManager();
            var snapshot = resource.getPhaseSnapshot(player);
            var milestone = skill.getEffectiveProficiencyMilestone(player);
            var phasePower = state.mode == Mode.CREATE
                    ? snapshot.alphaPower() : snapshot.betaPower();
            var gammaMagnitude = DarkmatterSixWings.Server.gammaMagnitudeMultiplier(player);
            var units = unitsForDuration(resource.getBaseCapacity(player), elapsedTicks,
                    phasePower, snapshot.activeGammaPower(), milestone, gammaMagnitude);
            var changed = state.mode == Mode.CREATE
                    ? resource.create(player, units,
                    cpPerCreatedMatter(snapshot.activeGammaPower(), milestone, gammaMagnitude))
                    : resource.erase(player, units, milestone >= 2
                    ? Math.max(1, Math.round(skill.getIterationTicks(player) * 0.8f))
                    : skill.getIterationTicks(player));
            if (changed) {
                if (!state.triggered) {
                    skill.reportTrigger(player);
                    state.triggered = true;
                }
                skill.reportActivity(player, true);
            }
        }

        static void clear(ServerPlayer player) {
            HOLDING.remove(player.getUUID());
        }

        private static final class HoldState {
            private static final long LEASE_TICKS = 60;
            private final Mode mode;
            private final Object level;
            private long lastProcessedAt;
            private long leaseExpiresAt;
            private boolean triggered;

            private HoldState(Mode mode, long lastProcessedAt, Object level) {
                this.mode = mode;
                this.lastProcessedAt = lastProcessedAt;
                this.level = level;
                leaseExpiresAt = lastProcessedAt + LEASE_TICKS;
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
                        value -> Mode.values()[Math.clamp(value, 0, Mode.values().length - 1)],
                        Enum::ordinal
                ), ControlPacket::mode,
                ByteBufCodecs.BOOL, ControlPacket::pressed,
                ControlPacket::new
        );
        private final Mode mode;
        private final boolean pressed;

        public ControlPacket(Mode mode, boolean pressed) {
            this.mode = mode;
            this.pressed = pressed;
        }

        public Mode mode() {
            return mode;
        }

        public boolean pressed() {
            return pressed;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ControlPacket> getPacketType() {
            return PacketTypes.DARKMATTER_GENERATION_CONTROL.get();
        }
    }
}
