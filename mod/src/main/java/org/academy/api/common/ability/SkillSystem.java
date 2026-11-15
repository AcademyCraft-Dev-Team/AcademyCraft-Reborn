package org.academy.api.common.ability;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.ability.data.SkillData;
import org.academy.api.common.ability.program.ProgramSharedVariables;
import org.academy.api.common.data.AbilityData;
import org.academy.api.server.ability.ServerContext;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SkillSystem {
    int getPlayerLevel(UUID uuid);

    boolean isSkillLearned(UUID uuid, String skillKey);

    Optional<SkillData> skillData(UUID uuid, String skillKey);

    void markPlayerDirty(UUID uuid);

    void reportSkillActivity(UUID uuid, Skill skill, SkillActivity activity);

    boolean addPlayerSkillProficiency(UUID uuid, Skill skill, ProficiencyEvent event);

    boolean castCpIfPossible(ServerPlayer player, Skill skill,
                            Skill.CostCalculator calculator, Skill.SkillAction action);

    boolean castCpIfPossible(ServerPlayer player, Skill skill,
                            Skill.CostCalculator calculator, Skill.SkillAction action,
                            String stackGroup, int stackLimit);

    boolean castContinuousCpIfPossible(ServerPlayer player, Skill skill,
                                      Skill.CostCalculator calculator, Skill.SkillAction action,
                                      boolean effective);

    boolean castCpAndMpIfPossible(ServerPlayer player, Skill skill,
                                 Skill.CostCalculator cpCalculator, Skill.CostCalculator mpCalculator,
                                 Skill.SkillAction action);

    boolean castCpAndMpIfPossible(ServerPlayer player, Skill skill,
                                 Skill.CostCalculator cpCalculator, Skill.CostCalculator mpCalculator,
                                 Skill.SkillAction action, String stackGroup, int stackLimit);

    boolean castContinuousCpAndMpIfPossible(ServerPlayer player, Skill skill,
                                           Skill.CostCalculator cpCalculator,
                                           Skill.CostCalculator mpCalculator,
                                           Skill.SkillAction action, boolean effective);

    AbilityData.Status getPlayerStatus(UUID uuid);

    void toggleSkill(UUID uuid, String skillId);

    AbilityCategory getPlayerAbilityCategory(UUID uuid);

    float getPlayerAvailableCP(UUID uuid);

    float getPlayerMaxCP(UUID uuid);

    float getPlayerCalculationIntensity(UUID uuid);

    boolean tryPermanentOccupation(UUID uuid, float amount, Skill skill);

    void releaseMaintenanceOccupation(UUID uuid, String skillId);

    int getPlayerSkillLevel(UUID uuid, String skillKey);

    boolean proficiencyEnabled(ServerPlayer player);

    Map<String, ProgramSharedVariables.SavedValue> getProgramSharedVariables(UUID uuid, String category);

    void setProgramSharedVariable(UUID uuid, String category, String name,
                                 ProgramSharedVariables.SavedValue value);

    void removeProgramSharedVariable(UUID uuid, String category, String name);

    void clearProgramSharedVariables(UUID uuid, String category);

    void unregisterContext(ServerContext serverContext);

    float getPlayerAbilityPowerMultiplier(UUID uuid);

    float getPlayerDamageMultiplier(UUID uuid);
}
