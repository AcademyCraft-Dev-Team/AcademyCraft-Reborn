package org.academy.api.server.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.network.PacketTypes;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.*;

/**
 * Server-owned numeric tuning for every registered skill: damage, range, cost, iteration and stacks,
 * plus advanced gates such as block destruction.
 *
 * <p>Skills with no explicit entry in {@code academy-server.json} keep their builder defaults, so the
 * five canonical numbers are configurable for all skills without a hand-written entry. The server
 * implementation supplies the resolved values through a registered {@link Provider}, which keeps
 * these methods usable by non-player actors, programs and precision operations rather than being
 * tied to a {@link ServerPlayer}.</p>
 *
 * <p>The client receives a resolved snapshot on login so cost quotes, stack caps and advanced-toggle
 * availability match what the server enforces. The server is always authoritative.</p>
 */
public final class SkillTuning {
    /**
     * Multipliers are clamped to a sane band so a typo cannot zero out or explode gameplay.
     */
    private static final float MAX_MULTIPLIER = 4.0f;
    private static final int MAX_ITERATION_TICKS = 20 * 60;
    private static final int MAX_STACKS = 512;

    /**
     * Sentinel for a configurable field that keeps the skill's own value.
     */
    public static final int USE_DEFAULT = -1;

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

    /**
     * Implementation-provided source of server-side tuning; registered once during setup.
     */
    public interface Provider {
        Optional<Entry> entry(MinecraftServer server, String skillId);

        float globalDamageMultiplier(MinecraftServer server);

        float maxHealthDamageMultiplier(MinecraftServer server, String skillId);

        boolean allowsMaxHealthDamage(MinecraftServer server, String skillId);

        boolean allowDisarmPlayers(MinecraftServer server, String skillId);
    }

    private static volatile @Nullable Provider provider;

    private SkillTuning() {
    }

    public static void registerProvider(Provider provider) {
        SkillTuning.provider = Objects.requireNonNull(provider);
    }

    /**
     * Skill paths that deal percentage max-health damage, for config discoverability.
     */
    public static Set<String> maxHealthDamageSkillIds() {
        return MAX_HEALTH_DAMAGE_SKILLS;
    }

    /**
     * Resolved tuning values for one skill.
     */
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
                USE_DEFAULT, USE_DEFAULT, true);
    }

    // ------------------------------------------------------------------
    // Core resolution (server-scoped, usable without a player)
    // ------------------------------------------------------------------

    private static @Nullable MinecraftServer serverOf(@Nullable Level level) {
        return level instanceof ServerLevel serverLevel ? serverLevel.getServer() : null;
    }

    private static Optional<Entry> entry(@Nullable MinecraftServer server, @Nullable String skillId) {
        var current = provider;
        if (server == null || skillId == null || current == null) return Optional.empty();
        return current.entry(server, skillId);
    }

    private static float globalDamageMultiplier(@Nullable MinecraftServer server) {
        var current = provider;
        if (server == null || current == null) return 1.0f;
        return clampMultiplier(current.globalDamageMultiplier(server));
    }

    private static float damageMultiplier(@Nullable MinecraftServer server, @Nullable String skillId) {
        if (server == null || skillId == null) return 1.0f;
        var local = clampMultiplier(entry(server, skillId).map(Entry::damageMultiplier).orElse(1.0f));
        return local * globalDamageMultiplier(server);
    }

    private static float rangeMultiplier(@Nullable MinecraftServer server, @Nullable String skillId) {
        if (server == null || skillId == null) return 1.0f;
        return clampMultiplier(entry(server, skillId).map(Entry::rangeMultiplier).orElse(1.0f));
    }

    private static float costMultiplier(@Nullable MinecraftServer server, @Nullable String skillId) {
        if (server == null || skillId == null) return 1.0f;
        return clampMultiplier(entry(server, skillId).map(Entry::costMultiplier).orElse(1.0f));
    }

    /**
     * Configured iteration ticks, or the skill's own value when the config keeps the default.
     */
    private static int iterationTicks(@Nullable MinecraftServer server, @Nullable String skillId, int fallback) {
        var configured = entry(server, skillId).map(Entry::iterationTicks).orElse(USE_DEFAULT);
        return configured == USE_DEFAULT
                ? fallback : Math.clamp(configured, 0, MAX_ITERATION_TICKS);
    }

    /**
     * Configured stack cap, or the skill's own value when the config keeps the default.
     */
    private static int maxStacks(@Nullable MinecraftServer server, @Nullable String skillId, int fallback) {
        var configured = entry(server, skillId).map(Entry::maxStacks).orElse(USE_DEFAULT);
        if (configured == USE_DEFAULT || configured < 0) return fallback;
        return Math.min(configured, MAX_STACKS);
    }

    /**
     * Server-side per-skill switch; disabled skills never execute.
     */
    private static boolean isSkillEnabled(@Nullable MinecraftServer server, @Nullable String skillId) {
        return entry(server, skillId).map(Entry::enabled).orElse(true);
    }

    /**
     * Server-owned advanced gate for skill block destruction. When false the client cannot enable it,
     * regardless of personal settings.
     */
    private static boolean allowBlockDestruction(@Nullable MinecraftServer server, @Nullable String skillId) {
        return entry(server, skillId).map(Entry::blockDestruction).orElse(true);
    }

    /**
     * Server-only policy for skills that can take items from a player target (e.g. Disarm). Defaults
     * to {@code false}; player targets are protected unless an administrator opts in.
     */
    private static boolean allowDisarmPlayers(@Nullable MinecraftServer server, @Nullable String skillId) {
        var current = provider;
        if (server == null || skillId == null || current == null) return false;
        return current.allowDisarmPlayers(server, skillId);
    }

    private static float maxHealthDamageMultiplier(@Nullable MinecraftServer server, @Nullable String skillId) {
        var current = provider;
        if (server == null || skillId == null || current == null) return 1.0f;
        return clampMultiplier(current.maxHealthDamageMultiplier(server, skillId));
    }

    /**
     * Server-owned gate for a skill's percentage max-health damage. When false the max-health term is
     * dropped entirely; the ordinary damage term is unaffected.
     */
    private static boolean allowsMaxHealthDamage(@Nullable MinecraftServer server, @Nullable String skillId) {
        var current = provider;
        if (server == null || skillId == null || current == null) return true;
        return current.allowsMaxHealthDamage(server, skillId);
    }

    /**
     * Scales a percentage max-health damage value by its dedicated multiplier, and zeroes it when the
     * per-skill gate is off. Ordinary {@code damageMultiplier} never touches this value.
     */
    private static float scaleMaxHealthDamage(@Nullable MinecraftServer server, @Nullable String skillId,
                                              float maxHealthDamage) {
        if (!Float.isFinite(maxHealthDamage) || maxHealthDamage <= 0.0f) return 0.0f;
        if (!allowsMaxHealthDamage(server, skillId)) return 0.0f;
        var multiplier = maxHealthDamageMultiplier(server, skillId);
        return Float.isFinite(multiplier)
                ? Math.max(0.0f, maxHealthDamage * multiplier)
                : maxHealthDamage;
    }

    // ------------------------------------------------------------------
    // Level / player resolution for gameplay call sites
    // ------------------------------------------------------------------

    public static float maxHealthDamageMultiplier(@Nullable Level level, Skill skill) {
        return maxHealthDamageMultiplier(serverOf(level), path(skill));
    }

    public static float maxHealthDamageMultiplier(@Nullable ServerPlayer owner, Skill skill) {
        return maxHealthDamageMultiplier(owner == null ? null : owner.level(), skill);
    }

    public static boolean allowsMaxHealthDamage(@Nullable Level level, Skill skill) {
        return allowsMaxHealthDamage(serverOf(level), path(skill));
    }

    public static boolean allowsMaxHealthDamage(@Nullable ServerPlayer owner, Skill skill) {
        return allowsMaxHealthDamage(owner == null ? null : owner.level(), skill);
    }

    public static float scaleMaxHealthDamage(@Nullable Level level, Skill skill, float maxHealthDamage) {
        return scaleMaxHealthDamage(serverOf(level), path(skill), maxHealthDamage);
    }

    public static float scaleMaxHealthDamage(@Nullable ServerPlayer owner, Skill skill, float maxHealthDamage) {
        return scaleMaxHealthDamage(owner == null ? null : owner.level(), skill, maxHealthDamage);
    }

    public static float damageMultiplier(@Nullable Level level, Skill skill) {
        return damageMultiplier(serverOf(level), path(skill));
    }

    public static float damageMultiplier(@Nullable ServerPlayer owner, Skill skill) {
        return damageMultiplier(owner == null ? null : owner.level(), skill);
    }

    public static float rangeMultiplier(@Nullable Level level, Skill skill) {
        return rangeMultiplier(serverOf(level), path(skill));
    }

    public static float rangeMultiplier(@Nullable ServerPlayer owner, Skill skill) {
        return rangeMultiplier(owner == null ? null : owner.level(), skill);
    }

    public static float costMultiplier(@Nullable ServerPlayer owner, Skill skill) {
        return costMultiplier(serverOf(owner == null ? null : owner.level()), path(skill));
    }

    /**
     * Cost multiplier resolved from the server for the UUID-based CP charge paths. Neutral (1.0)
     * whenever the server or its config is unavailable, e.g. during shutdown.
     */
    public static float costMultiplier(@Nullable MinecraftServer server, Skill skill) {
        return costMultiplier(server, path(skill));
    }

    public static int iterationTicks(@Nullable ServerPlayer owner, Skill skill, int fallback) {
        return iterationTicks(serverOf(owner == null ? null : owner.level()), path(skill), fallback);
    }

    public static int maxStacks(@Nullable ServerPlayer owner, Skill skill, int fallback) {
        return maxStacks(serverOf(owner == null ? null : owner.level()), path(skill), fallback);
    }

    public static boolean isSkillEnabled(@Nullable ServerPlayer owner, Skill skill) {
        return isSkillEnabled(serverOf(owner == null ? null : owner.level()), path(skill));
    }

    public static boolean allowBlockDestruction(@Nullable ServerPlayer owner, Skill skill) {
        return allowBlockDestruction(serverOf(owner == null ? null : owner.level()), path(skill));
    }

    public static boolean allowDisarmPlayers(@Nullable ServerPlayer owner, Skill skill) {
        return allowDisarmPlayers(serverOf(owner == null ? null : owner.level()), path(skill));
    }

    public static boolean allowDisarmPlayers(@Nullable Level level, Skill skill) {
        return allowDisarmPlayers(serverOf(level), path(skill));
    }

    /**
     * Global ability damage scalar for every skill.
     */
    public static float globalDamageMultiplier(@Nullable Level level) {
        return globalDamageMultiplier(serverOf(level));
    }

    /**
     * Applies the owning skill's damage multiplier (and the global scalar) to one skill damage hit.
     * Non-skill damage and unknown skills pass through unchanged, which keeps reflected and
     * non-player attacks on the same authoritative numbers.
     */
    public static float scaleSkillDamage(@Nullable DamageSource source,
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
    public static float scaleSkillDamage(@Nullable DamageSource source,
                                         float damage,
                                         float maxHealthPart) {
        if (!(source instanceof SkillDamageSource skillSource)
                || skillSource.getSkill() == null || !Float.isFinite(damage)) {
            return damage;
        }
        var skill = skillSource.getSkill();
        var attacker = skillSource.getEntity();
        var level = attacker != null ? attacker.level()
                : skillSource.getDirectEntity() == null ? null : skillSource.getDirectEntity().level();
        var server = serverOf(level);

        var percentagePart = clampedMaxHealthPart(damage, maxHealthPart);
        return applyDamageParts(
                damage,
                percentagePart,
                damageMultiplier(server, path(skill)),
                scaleMaxHealthDamage(server, path(skill), percentagePart)
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
        return value == USE_DEFAULT
                ? fallback : Math.clamp(value, 0, MAX_ITERATION_TICKS);
    }

    public static int clientMaxStacks(Skill skill, int fallback) {
        var entry = clientEntry(skill);
        if (entry.maxStacks() == USE_DEFAULT || entry.maxStacks() < 0) {
            return fallback;
        }
        return Math.min(entry.maxStacks(), MAX_STACKS);
    }

    public static boolean isSkillEnabledOnClient(Skill skill) {
        return clientEntry(skill).enabled();
    }

    /**
     * Whether the server lets this skill destroy blocks; false hides/disables the personal toggle.
     */
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
