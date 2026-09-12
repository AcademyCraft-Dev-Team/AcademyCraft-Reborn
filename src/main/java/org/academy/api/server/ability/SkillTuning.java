package org.academy.api.server.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.config.AbilityConfig;
import org.jetbrains.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Server-owned numeric tuning for every registered skill: damage, range, cost, iteration and stacks,
 * plus advanced gates such as block destruction.
 *
 * <p>Skills with no explicit entry in {@code academy-server.json} keep their builder defaults, so the
 * five canonical numbers are configurable for all skills without a hand-written entry. The core
 * resolution methods take an {@link AbilityConfig} directly, which keeps them usable by non-player
 * actors, programs and precision operations rather than being tied to a {@link ServerPlayer}.</p>
 *
 * <p>The client receives a resolved snapshot on login so cost quotes, stack caps and advanced-toggle
 * availability match what the server enforces. The server is always authoritative.</p>
 */
public final class SkillTuning {
    /** Multipliers are clamped to a sane band so a typo cannot zero out or explode gameplay. */
    private static final float MAX_MULTIPLIER = 4.0f;
    private static final int MAX_ITERATION_TICKS = 20 * 60;
    private static final int MAX_STACKS = 512;

    /**
     * Skill paths that combine a percentage max-health term into their damage. Kept here so the
     * defaults can surface the dedicated multiplier and its on/off gate for those skills.
     */
    private static final Set<String> MAX_HEALTH_DAMAGE_SKILLS = Set.of(
            "bloodflow_reverse",
            "black_wing", "white_wing", "platinum_wing",
            "ball_lightning", "thunderclap", "lightning_storm",
            "mind_destruction",
            "single_high_speed_electron_beam", "scatter_bomb", "particle_wave_cannon",
            "auto_cruise_beam_cannon", "disintegrate",
            "vacuum_domain",
            "self_teleport"
    );

    private static volatile Map<String, Entry> clientSnapshot = Map.of();
    private static volatile float clientGlobalDamageMultiplier = 1.0f;

    private SkillTuning() {
    }

    /** Skill paths that deal percentage max-health damage, for config discoverability. */
    public static Set<String> maxHealthDamageSkillIds() {
        return MAX_HEALTH_DAMAGE_SKILLS;
    }

    /** Resolved tuning values for one skill. */
    public record Entry(
            boolean enabled,
            float damageMultiplier,
            float rangeMultiplier,
            float costMultiplier,
            int iterationTicks,
            int maxStacks,
            boolean blockDestruction
    ) {
        public static final Entry DEFAULT = new Entry(true, 1.0f, 1.0f, 1.0f,
                AbilityConfig.SkillSettings.USE_DEFAULT,
                AbilityConfig.SkillSettings.USE_DEFAULT, true);
    }

    // ------------------------------------------------------------------
    // Config-core resolution (usable without a player)
    // ------------------------------------------------------------------

    /** Damage multiplier for one skill, already combined with the global ability damage scalar. */
    public static float damageMultiplier(@Nullable AbilityConfig config, Skill skill) {
        return damageMultiplier(config, path(skill));
    }

    public static float damageMultiplier(@Nullable AbilityConfig config, @Nullable String skillId) {
        var local = config == null ? 1.0f
                : clampMultiplier(settings(config, skillId).map(s -> s.damageMultiplier).orElse(1.0f));
        return local * globalDamageMultiplier(config);
    }

    public static float rangeMultiplier(@Nullable AbilityConfig config, Skill skill) {
        return rangeMultiplier(config, path(skill));
    }

    public static float rangeMultiplier(@Nullable AbilityConfig config, @Nullable String skillId) {
        return config == null ? 1.0f
                : clampMultiplier(settings(config, skillId).map(s -> s.rangeMultiplier).orElse(1.0f));
    }

    public static float costMultiplier(@Nullable AbilityConfig config, Skill skill) {
        return costMultiplier(config, path(skill));
    }

    public static float costMultiplier(@Nullable AbilityConfig config, @Nullable String skillId) {
        return config == null ? 1.0f
                : clampMultiplier(settings(config, skillId).map(s -> s.costMultiplier).orElse(1.0f));
    }

    /** Configured iteration ticks, or the skill's own value when the config keeps the default. */
    public static int iterationTicks(@Nullable AbilityConfig config, Skill skill, int fallback) {
        return iterationTicks(config, path(skill), fallback);
    }

    public static int iterationTicks(@Nullable AbilityConfig config, @Nullable String skillId, int fallback) {
        var configured = settings(config, skillId)
                .map(s -> s.iterationTicks)
                .orElse(AbilityConfig.SkillSettings.USE_DEFAULT);
        return configured == AbilityConfig.SkillSettings.USE_DEFAULT
                ? fallback : Math.clamp(configured, 0, MAX_ITERATION_TICKS);
    }

    /** Configured stack cap, or the skill's own value when the config keeps the default. */
    public static int maxStacks(@Nullable AbilityConfig config, Skill skill, int fallback) {
        return maxStacks(config, path(skill), fallback);
    }

    public static int maxStacks(@Nullable AbilityConfig config, @Nullable String skillId, int fallback) {
        var configured = settings(config, skillId)
                .map(s -> s.maxStacks)
                .orElse(AbilityConfig.SkillSettings.USE_DEFAULT);
        if (configured == AbilityConfig.SkillSettings.USE_DEFAULT || configured < 0) return fallback;
        return Math.min(configured, MAX_STACKS);
    }

    /** Server-side per-skill switch; disabled skills never execute. */
    public static boolean isSkillEnabled(@Nullable AbilityConfig config, Skill skill) {
        return isSkillEnabled(config, path(skill));
    }

    public static boolean isSkillEnabled(@Nullable AbilityConfig config, @Nullable String skillId) {
        return settings(config, skillId).map(s -> s.enabled).orElse(true);
    }

    /**
     * Server-owned advanced gate for skill block destruction. When false the client cannot enable it,
     * regardless of personal settings.
     */
    public static boolean allowBlockDestruction(@Nullable AbilityConfig config, Skill skill) {
        return allowBlockDestruction(config, path(skill));
    }

    public static boolean allowBlockDestruction(@Nullable AbilityConfig config, @Nullable String skillId) {
        return settings(config, skillId).map(s -> s.advanced.blockDestruction).orElse(true);
    }

    /**
     * Server-only policy for skills that can take items from a player target (e.g. Disarm). Defaults
     * to {@code false}; player targets are protected unless an administrator opts in.
     */
    public static boolean allowDisarmPlayers(@Nullable AbilityConfig config, Skill skill) {
        return allowDisarmPlayers(config, path(skill));
    }

    public static boolean allowDisarmPlayers(@Nullable AbilityConfig config, @Nullable String skillId) {
        return settings(config, skillId).map(s -> s.advanced.disarmPlayers).orElse(false);
    }

    public static boolean allowDisarmPlayers(@Nullable ServerPlayer owner, Skill skill) {
        return allowDisarmPlayers(config(owner == null ? null : owner.level()), skill);
    }

    public static boolean allowDisarmPlayers(@Nullable Level level, Skill skill) {
        return allowDisarmPlayers(config(level), skill);
    }

    /** Global ability damage scalar for every skill. */
    public static float globalDamageMultiplier(@Nullable AbilityConfig config) {
        return config == null ? 1.0f : clampMultiplier(config.damageMultiplier);
    }

    /**
     * Multiplier for a skill's percentage max-health damage term. Deliberately independent of
     * {@link #damageMultiplier} and the global scalar: percentage damage is not affected by ordinary
     * damage multipliers and must be tuned on its own.
     */
    public static float maxHealthDamageMultiplier(@Nullable AbilityConfig config, Skill skill) {
        return maxHealthDamageMultiplier(config, path(skill));
    }

    public static float maxHealthDamageMultiplier(@Nullable AbilityConfig config, @Nullable String skillId) {
        return config == null ? 1.0f
                : clampMultiplier(settings(config, skillId)
                .map(s -> s.maxHealthDamageMultiplier).orElse(1.0f));
    }

    /**
     * Server-owned gate for a skill's percentage max-health damage. When false the max-health term is
     * dropped entirely; the ordinary damage term is unaffected.
     */
    public static boolean allowsMaxHealthDamage(@Nullable AbilityConfig config, Skill skill) {
        return allowsMaxHealthDamage(config, path(skill));
    }

    public static boolean allowsMaxHealthDamage(@Nullable AbilityConfig config, @Nullable String skillId) {
        return settings(config, skillId).map(s -> s.advanced.maxHealthDamage).orElse(true);
    }

    /**
     * Scales a percentage max-health damage value by its dedicated multiplier, and zeroes it when the
     * per-skill gate is off. Ordinary {@code damageMultiplier} never touches this value.
     */
    public static float scaleMaxHealthDamage(@Nullable AbilityConfig config, Skill skill, float maxHealthDamage) {
        return scaleMaxHealthDamage(config, path(skill), maxHealthDamage);
    }

    public static float scaleMaxHealthDamage(@Nullable AbilityConfig config, @Nullable String skillId,
                                             float maxHealthDamage) {
        if (!Float.isFinite(maxHealthDamage) || maxHealthDamage <= 0.0f) return 0.0f;
        if (!allowsMaxHealthDamage(config, skillId)) return 0.0f;
        var multiplier = maxHealthDamageMultiplier(config, skillId);
        return Float.isFinite(multiplier)
                ? Math.max(0.0f, maxHealthDamage * multiplier)
                : maxHealthDamage;
    }

    // ------------------------------------------------------------------
    // Level / player resolution for gameplay call sites
    // ------------------------------------------------------------------

    public static float maxHealthDamageMultiplier(@Nullable Level level, Skill skill) {
        return maxHealthDamageMultiplier(config(level), skill);
    }

    public static float maxHealthDamageMultiplier(@Nullable ServerPlayer owner, Skill skill) {
        return maxHealthDamageMultiplier(owner == null ? null : owner.level(), skill);
    }

    public static boolean allowsMaxHealthDamage(@Nullable Level level, Skill skill) {
        return allowsMaxHealthDamage(config(level), skill);
    }

    public static boolean allowsMaxHealthDamage(@Nullable ServerPlayer owner, Skill skill) {
        return allowsMaxHealthDamage(owner == null ? null : owner.level(), skill);
    }

    public static float scaleMaxHealthDamage(@Nullable Level level, Skill skill, float maxHealthDamage) {
        return scaleMaxHealthDamage(config(level), skill, maxHealthDamage);
    }

    public static float scaleMaxHealthDamage(@Nullable ServerPlayer owner, Skill skill, float maxHealthDamage) {
        return scaleMaxHealthDamage(owner == null ? null : owner.level(), skill, maxHealthDamage);
    }


    public static float damageMultiplier(@Nullable Level level, Skill skill) {
        return damageMultiplier(config(level), skill);
    }

    public static float damageMultiplier(@Nullable ServerPlayer owner, Skill skill) {
        return damageMultiplier(owner == null ? null : owner.level(), skill);
    }

    public static float rangeMultiplier(@Nullable Level level, Skill skill) {
        return rangeMultiplier(config(level), skill);
    }

    public static float rangeMultiplier(@Nullable ServerPlayer owner, Skill skill) {
        return rangeMultiplier(owner == null ? null : owner.level(), skill);
    }

    public static float costMultiplier(@Nullable ServerPlayer owner, Skill skill) {
        return costMultiplier(config(owner == null ? null : owner.level()), skill);
    }

    /**
     * Cost multiplier resolved from the server for the UUID-based CP charge paths. Neutral (1.0)
     * whenever the server or its config is unavailable, e.g. during shutdown.
     */
    public static float costMultiplier(@Nullable MinecraftServer server, Skill skill) {
        return costMultiplier(config(server), skill);
    }

    public static int iterationTicks(@Nullable ServerPlayer owner, Skill skill, int fallback) {
        return iterationTicks(config(owner == null ? null : owner.level()), skill, fallback);
    }

    public static int maxStacks(@Nullable ServerPlayer owner, Skill skill, int fallback) {
        return maxStacks(config(owner == null ? null : owner.level()), skill, fallback);
    }

    public static boolean isSkillEnabled(@Nullable ServerPlayer owner, Skill skill) {
        return isSkillEnabled(config(owner == null ? null : owner.level()), skill);
    }

    public static boolean allowBlockDestruction(@Nullable ServerPlayer owner, Skill skill) {
        return allowBlockDestruction(config(owner == null ? null : owner.level()), skill);
    }

    public static float globalDamageMultiplier(@Nullable Level level) {
        return globalDamageMultiplier(config(level));
    }

    /**
     * Applies the owning skill's damage multiplier (and the global scalar) to one skill damage hit.
     * Non-skill damage and unknown skills pass through unchanged, which keeps reflected and
     * non-player attacks on the same authoritative numbers.
     */
    public static float scaleSkillDamage(@Nullable net.minecraft.world.damagesource.DamageSource source,
                                         float damage) {
        return scaleSkillDamage(source, damage, 0.0f);
    }

    /**
     * Applies the owning skill's damage multiplier to the ordinary part of a hit and the dedicated
     * max-health multiplier (or the on/off gate) to {@code maxHealthPart}. The percentage term is
     * deliberately never touched by {@link #damageMultiplier} or the global scalar.
     *
     * @param maxHealthPart how much of {@code damage} is the skill's percentage max-health term
     */
    public static float scaleSkillDamage(@Nullable net.minecraft.world.damagesource.DamageSource source,
                                         float damage,
                                         float maxHealthPart) {
        if (!(source instanceof org.academy.api.common.damage.SkillDamageSource skillSource)
                || skillSource.getSkill() == null || !Float.isFinite(damage)) {
            return damage;
        }
        var skill = skillSource.getSkill();
        var attacker = skillSource.getEntity();
        var level = attacker != null ? attacker.level()
                : skillSource.getDirectEntity() == null ? null : skillSource.getDirectEntity().level();
        var config = config(level);

        var percentagePart = clampedMaxHealthPart(damage, maxHealthPart);
        return applyDamageParts(
                damage,
                percentagePart,
                damageMultiplier(config, skill),
                scaleMaxHealthDamage(config, skill, percentagePart)
        );
    }

    /**
     * Pure split of a hit into its ordinary and percentage parts. Exposed for tests and for callers
     * that already resolved the two multipliers. {@code maxHealthPart} is clamped to {@code damage}.
     */
    static float applyDamageParts(float damage, float maxHealthPart,
                                  float ordinaryMultiplier, float scaledPercentagePart) {
        if (!Float.isFinite(damage)) return damage;
        var percentagePart = clampedMaxHealthPart(damage, maxHealthPart);
        var ordinaryPart = Math.max(0.0f, damage - percentagePart);
        var multiplier = Float.isFinite(ordinaryMultiplier) ? Math.max(0.0f, ordinaryMultiplier) : 1.0f;
        var percentage = Float.isFinite(scaledPercentagePart) ? Math.max(0.0f, scaledPercentagePart) : 0.0f;
        return Math.max(0.0f, ordinaryPart * multiplier + percentage);
    }

    private static float clampedMaxHealthPart(float damage, float maxHealthPart) {
        return Float.isFinite(maxHealthPart) && maxHealthPart > 0.0f
                ? Math.min(maxHealthPart, Math.max(0.0f, damage))
                : 0.0f;
    }

    private static @Nullable AbilityConfig config(@Nullable Level level) {
        return config(level instanceof ServerLevel serverLevel ? serverLevel.getServer() : (MinecraftServer) null);
    }

    private static @Nullable AbilityConfig config(@Nullable MinecraftServer server) {
        if (server == null) return null;
        var academyServer = server.getAcademyCraftServer();
        return academyServer == null ? null : academyServer.getAbilityConfig();
    }

    private static java.util.Optional<AbilityConfig.SkillSettings> settings(
            @Nullable AbilityConfig config, @Nullable Skill skill) {
        return settings(config, path(skill));
    }

    private static java.util.Optional<AbilityConfig.SkillSettings> settings(
            @Nullable AbilityConfig config, @Nullable String skillId) {
        if (config == null || skillId == null) return java.util.Optional.empty();
        return java.util.Optional.ofNullable(config.skillSettings(skillId));
    }

    private static @Nullable String path(@Nullable Skill skill) {
        return skill == null ? null : skill.getKey().getPath();
    }

    private static float clampMultiplier(float value) {
        return Float.isFinite(value) ? Math.clamp(value, 0.0f, MAX_MULTIPLIER) : 1.0f;
    }

    // ------------------------------------------------------------------
    // Client snapshot
    // ------------------------------------------------------------------

    public static float clientDamageMultiplier(Skill skill) {
        var entry = clientEntry(skill);
        return clampMultiplier(entry.damageMultiplier() * clientGlobalDamageMultiplier);
    }

    public static float clientRangeMultiplier(Skill skill) {
        return clampMultiplier(clientEntry(skill).rangeMultiplier());
    }

    public static float clientCostMultiplier(Skill skill) {
        return clampMultiplier(clientEntry(skill).costMultiplier());
    }

    public static int clientIterationTicks(Skill skill, int fallback) {
        var value = clientEntry(skill).iterationTicks();
        return value == AbilityConfig.SkillSettings.USE_DEFAULT
                ? fallback : Math.clamp(value, 0, MAX_ITERATION_TICKS);
    }

    public static int clientMaxStacks(Skill skill, int fallback) {
        var entry = clientEntry(skill);
        if (entry.maxStacks() == AbilityConfig.SkillSettings.USE_DEFAULT || entry.maxStacks() < 0) {
            return fallback;
        }
        return Math.min(entry.maxStacks(), MAX_STACKS);
    }

    public static boolean isSkillEnabledOnClient(Skill skill) {
        return clientEntry(skill).enabled();
    }

    /** Whether the server lets this skill destroy blocks; false hides/disables the personal toggle. */
    public static boolean clientBlockDestructionAllowed(Skill skill) {
        return clientEntry(skill).blockDestruction();
    }

    private static Entry clientEntry(Skill skill) {
        if (skill == null) return Entry.DEFAULT;
        return clientSnapshot.getOrDefault(skill.getKeyString(), Entry.DEFAULT);
    }

    public static void initClient() {
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                MisakaNetworkServer.send(player, snapshot(config(player.level())));
            }
        }
    }

    private static SyncPacket snapshot(@Nullable AbilityConfig config) {
        if (config == null) return new SyncPacket(Map.of(), 1.0f);
        var resolved = new HashMap<String, Entry>();
        for (var group : config.skills.values()) {
            if (group == null) continue;
            for (var entry : group.entrySet()) {
                var settings = entry.getValue();
                if (settings == null) continue;
                resolved.put(entry.getKey(), new Entry(
                        settings.enabled,
                        clampMultiplier(settings.damageMultiplier),
                        clampMultiplier(settings.rangeMultiplier),
                        clampMultiplier(settings.costMultiplier),
                        settings.iterationTicks,
                        settings.maxStacks,
                        settings.advanced.blockDestruction
                ));
            }
        }
        return new SyncPacket(Map.copyOf(resolved), clampMultiplier(config.damageMultiplier));
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void receive(SyncPacket packet) {
            clientSnapshot = packet.entries;
            clientGlobalDamageMultiplier = packet.globalDamageMultiplier;
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SyncPacket extends Packet<ClientPacketListener, SyncPacket> {
        public static final StreamCodec<ByteBuf, SyncPacket> CODEC = StreamCodec.of(
                SyncPacket::write,
                SyncPacket::read
        );
        private final Map<String, Entry> entries;
        private final float globalDamageMultiplier;

        public SyncPacket(Map<String, Entry> entries, float globalDamageMultiplier) {
            this.entries = entries == null ? Map.of() : Map.copyOf(entries);
            this.globalDamageMultiplier = globalDamageMultiplier;
        }

        @Override
        public PacketType<ClientPacketListener, SyncPacket> getPacketType() {
            return PacketTypes.SKILL_TUNING_SYNC.get();
        }

        private static void write(ByteBuf buf, SyncPacket packet) {
            var keys = new ArrayList<>(packet.entries.keySet());
            ByteBufCodecs.VAR_INT.encode(buf, keys.size());
            for (var key : keys) {
                var entry = packet.entries.get(key);
                ByteBufCodecs.STRING_UTF8.encode(buf, key);
                ByteBufCodecs.BOOL.encode(buf, entry.enabled());
                ByteBufCodecs.FLOAT.encode(buf, entry.damageMultiplier());
                ByteBufCodecs.FLOAT.encode(buf, entry.rangeMultiplier());
                ByteBufCodecs.FLOAT.encode(buf, entry.costMultiplier());
                ByteBufCodecs.VAR_INT.encode(buf, entry.iterationTicks());
                ByteBufCodecs.VAR_INT.encode(buf, entry.maxStacks());
                ByteBufCodecs.BOOL.encode(buf, entry.blockDestruction());
            }
            ByteBufCodecs.FLOAT.encode(buf, packet.globalDamageMultiplier);
        }

        private static SyncPacket read(ByteBuf buf) {
            var size = ByteBufCodecs.VAR_INT.decode(buf);
            var entries = new HashMap<String, Entry>(Math.max(16, size));
            for (var i = 0; i < size; i++) {
                var key = ByteBufCodecs.STRING_UTF8.decode(buf);
                entries.put(key, new Entry(
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.FLOAT.decode(buf),
                        ByteBufCodecs.FLOAT.decode(buf),
                        ByteBufCodecs.FLOAT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)
                ));
            }
            return new SyncPacket(entries, ByteBufCodecs.FLOAT.decode(buf));
        }
    }
}
