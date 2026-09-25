package org.academy.api.server.ability;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.ProficiencyEvent;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.ability.SkillActivity;
import org.academy.api.common.ability.data.SkillData;
import org.academy.api.common.data.AbilityData;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public access to the per-server ability system. The implementation registers one backend per
 * running server; callers resolve it from the acting entity.
 */
public final class AbilityServerAccess {
    /**
     * Implementation-provided backend; registered once per running server.
     */
    public interface Backend {
        AbilityCategory getPlayerAbilityCategory(UUID uuid);

        AbilityData.Status getPlayerStatus(UUID uuid);

        int getPlayerLevel(UUID uuid);

        int getPlayerSkillLevel(UUID uuid, String skillKey);

        float getPlayerAvailableCP(UUID uuid);

        float getPlayerMaxCP(UUID uuid);

        float getPlayerCalculationIntensity(UUID uuid);

        float getPlayerDamageMultiplier(UUID uuid);

        float getPlayerAbilityPowerMultiplier(UUID uuid);

        boolean proficiencyEnabled(ServerPlayer player);

        boolean isSkillLearned(UUID uuid, String skillKey);

        Optional<SkillData> skillData(UUID uuid, String skillKey);

        void markPlayerDirty(UUID uuid);

        void addPlayerSkill(ServerPlayer player, String skillKey);

        float quoteTimedOccupation(ServerPlayer player, float amount, Skill skill);

        void toggleSkill(UUID uuid, String skillId);

        boolean tryPermanentOccupation(UUID uuid, float amount, Skill skill);

        void releaseMaintenanceOccupation(UUID uuid, String skillId);

        boolean addPlayerSkillProficiency(UUID uuid, Skill skill, ProficiencyEvent event);

        void reportSkillActivity(UUID uuid, Skill skill, SkillActivity activity);

        boolean castCpIfPossible(ServerPlayer player, Skill skill,
                                 Skill.CostCalculator calculator, Skill.SkillAction action);

        boolean castCpIfPossible(ServerPlayer player, Skill skill,
                                 Skill.CostCalculator calculator, Skill.SkillAction action,
                                 String stackGroup, int stackLimit);

        boolean castCpAndMpIfPossible(ServerPlayer player, Skill skill,
                                      Skill.CostCalculator cpCalculator, Skill.CostCalculator mpCalculator,
                                      Skill.SkillAction action);

        boolean castCpAndMpIfPossible(ServerPlayer player, Skill skill,
                                      Skill.CostCalculator cpCalculator, Skill.CostCalculator mpCalculator,
                                      Skill.SkillAction action, String stackGroup, int stackLimit);

        boolean castContinuousCpIfPossible(ServerPlayer player, Skill skill,
                                           Skill.CostCalculator calculator, Skill.SkillAction action,
                                           boolean effective);

        boolean castContinuousCpAndMpIfPossible(ServerPlayer player, Skill skill,
                                                Skill.CostCalculator cpCalculator, Skill.CostCalculator mpCalculator,
                                                Skill.SkillAction action, boolean effective);

        void unregisterContext(ServerContext serverContext);
    }

    private static final Map<MinecraftServer, Backend> BACKENDS = new ConcurrentHashMap<>();

    private AbilityServerAccess() {
    }

    public static void register(MinecraftServer server, Backend backend) {
        BACKENDS.put(Objects.requireNonNull(server), Objects.requireNonNull(backend));
    }

    public static void unregister(MinecraftServer server) {
        BACKENDS.remove(server);
    }

    public static @Nullable Backend find(MinecraftServer server) {
        return BACKENDS.get(server);
    }

    public static Backend of(MinecraftServer server) {
        var backend = BACKENDS.get(server);
        if (backend == null) throw new IllegalStateException("Ability server access is not registered");
        return backend;
    }

    public static Backend of(Entity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) return of(serverLevel.getServer());
        throw new IllegalStateException("Entity is not in a ServerLevel");
    }
}
