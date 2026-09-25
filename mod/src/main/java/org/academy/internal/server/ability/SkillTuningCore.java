package org.academy.internal.server.ability;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.SkillTuning;
import org.academy.internal.server.config.AbilityConfig;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side source of {@link SkillTuning} values and the login snapshot publisher.
 *
 * <p>Owns every {@link AbilityConfig} lookup so the public facade stays free of config types.</p>
 */
public final class SkillTuningCore implements SkillTuning.Provider {
    private static final float MAX_MULTIPLIER = 4.0f;
    private static final int MAX_ITERATION_TICKS = 20 * 60;

    @Override
    public Optional<SkillTuning.Entry> entry(MinecraftServer server, String skillId) {
        return entry(config(server), skillId);
    }

    @Override
    public float globalDamageMultiplier(MinecraftServer server) {
        var config = config(server);
        return config == null ? 1.0f : config.damageMultiplier;
    }

    @Override
    public float maxHealthDamageMultiplier(MinecraftServer server, String skillId) {
        var settings = settings(config(server), skillId);
        return settings == null ? 1.0f : settings.maxHealthDamageMultiplier;
    }

    @Override
    public boolean allowsMaxHealthDamage(MinecraftServer server, String skillId) {
        var settings = settings(config(server), skillId);
        return settings == null || settings.advanced.maxHealthDamage;
    }

    @Override
    public boolean allowDisarmPlayers(MinecraftServer server, String skillId) {
        var settings = settings(config(server), skillId);
        return settings != null && settings.advanced.disarmPlayers;
    }

    // ------------------------------------------------------------------
    // Config-scoped resolution for internal call sites that already hold a config
    // ------------------------------------------------------------------

    public static float rangeMultiplier(@Nullable AbilityConfig config, @Nullable String skillId) {
        return clampMultiplier(entry(config, skillId).map(SkillTuning.Entry::rangeMultiplier).orElse(1.0f));
    }

    public static int maxStacks(@Nullable AbilityConfig config, Skill skill, int fallback) {
        return maxStacks(config, path(skill), fallback);
    }

    public static int maxStacks(@Nullable AbilityConfig config, @Nullable String skillId, int fallback) {
        var configured = entry(config, skillId).map(SkillTuning.Entry::maxStacks)
                .orElse(SkillTuning.USE_DEFAULT);
        if (configured == SkillTuning.USE_DEFAULT || configured < 0) return fallback;
        return Math.min(configured, 512);
    }

    public static boolean allowBlockDestruction(@Nullable AbilityConfig config, Skill skill) {
        return entry(config, path(skill)).map(SkillTuning.Entry::blockDestruction).orElse(true);
    }

    public static float globalDamageMultiplier(@Nullable AbilityConfig config) {
        return config == null ? 1.0f : clampMultiplier(config.damageMultiplier);
    }

    public static float damageMultiplier(@Nullable AbilityConfig config, @Nullable String skillId) {
        var local = clampMultiplier(entry(config, skillId).map(SkillTuning.Entry::damageMultiplier).orElse(1.0f));
        return local * globalDamageMultiplier(config);
    }

    public static float costMultiplier(@Nullable AbilityConfig config, @Nullable String skillId) {
        return clampMultiplier(entry(config, skillId).map(SkillTuning.Entry::costMultiplier).orElse(1.0f));
    }

    public static int iterationTicks(@Nullable AbilityConfig config, @Nullable String skillId, int fallback) {
        var configured = entry(config, skillId).map(SkillTuning.Entry::iterationTicks)
                .orElse(SkillTuning.USE_DEFAULT);
        return configured == SkillTuning.USE_DEFAULT
                ? fallback : Math.clamp(configured, 0, MAX_ITERATION_TICKS);
    }

    public static boolean isSkillEnabled(@Nullable AbilityConfig config, @Nullable String skillId) {
        return entry(config, skillId).map(SkillTuning.Entry::enabled).orElse(true);
    }

    public static boolean allowBlockDestruction(@Nullable AbilityConfig config, @Nullable String skillId) {
        return entry(config, skillId).map(SkillTuning.Entry::blockDestruction).orElse(true);
    }

    public static boolean allowDisarmPlayers(@Nullable AbilityConfig config, @Nullable String skillId) {
        var settings = settings(config, skillId);
        return settings != null && settings.advanced.disarmPlayers;
    }

    public static float maxHealthDamageMultiplier(@Nullable AbilityConfig config, @Nullable String skillId) {
        var settings = settings(config, skillId);
        return clampMultiplier(settings == null ? 1.0f : settings.maxHealthDamageMultiplier);
    }

    public static boolean allowsMaxHealthDamage(@Nullable AbilityConfig config, @Nullable String skillId) {
        var settings = settings(config, skillId);
        return settings == null || settings.advanced.maxHealthDamage;
    }

    public static float scaleMaxHealthDamage(@Nullable AbilityConfig config, @Nullable String skillId,
                                             float maxHealthDamage) {
        if (!Float.isFinite(maxHealthDamage) || maxHealthDamage <= 0.0f) return 0.0f;
        if (!allowsMaxHealthDamage(config, skillId)) return 0.0f;
        var multiplier = maxHealthDamageMultiplier(config, skillId);
        return Float.isFinite(multiplier) ? Math.max(0.0f, maxHealthDamage * multiplier) : maxHealthDamage;
    }

    // ------------------------------------------------------------------
    // Login snapshot
    // ------------------------------------------------------------------

    public static SkillTuning.SyncPacket snapshot(@Nullable AbilityConfig config) {
        if (config == null) return new SkillTuning.SyncPacket(Map.of(), 1.0f);
        var resolved = new HashMap<String, SkillTuning.Entry>();
        for (var group : config.skills.values()) {
            if (group == null) continue;
            for (var entry : group.entrySet()) {
                var settings = entry.getValue();
                if (settings == null) continue;
                resolved.put(entry.getKey(), new SkillTuning.Entry(
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
        return new SkillTuning.SyncPacket(Map.copyOf(resolved), clampMultiplier(config.damageMultiplier));
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                MisakaNetworkServer.send(player, snapshot(config(player.level().getServer())));
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Optional<SkillTuning.Entry> entry(@Nullable AbilityConfig config, @Nullable String skillId) {
        var settings = settings(config, skillId);
        if (settings == null) return Optional.empty();
        return Optional.of(new SkillTuning.Entry(
                settings.enabled,
                clampMultiplier(settings.damageMultiplier),
                clampMultiplier(settings.rangeMultiplier),
                clampMultiplier(settings.costMultiplier),
                settings.iterationTicks,
                settings.maxStacks,
                settings.advanced.blockDestruction
        ));
    }

    private static AbilityConfig.@Nullable SkillSettings settings(
            @Nullable AbilityConfig config, @Nullable String skillId) {
        if (config == null || skillId == null) return null;
        return config.skillSettings(skillId);
    }

    private static @Nullable AbilityConfig config(@Nullable MinecraftServer server) {
        if (server == null) return null;
        var academyServer = server.getAcademyCraftServer();
        return academyServer == null ? null : academyServer.getAbilityConfig();
    }

    private static @Nullable String path(@Nullable Skill skill) {
        return skill == null ? null : skill.getKey().getPath();
    }

    private static float clampMultiplier(float value) {
        return Float.isFinite(value) ? Math.clamp(value, 0.0f, MAX_MULTIPLIER) : 1.0f;
    }
}
