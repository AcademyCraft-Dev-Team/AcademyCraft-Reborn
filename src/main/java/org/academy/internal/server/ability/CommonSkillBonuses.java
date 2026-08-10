package org.academy.internal.server.ability;

import org.academy.AcademyCraft;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.skilldata.SkillData;

import java.util.Map;
import net.minecraft.util.Mth;

/**
 * Derived effects supplied by learned common-course skills.
 */
final class CommonSkillBonuses {
    static final Bonuses NONE = new Bonuses(0.0f, 0, 1.0f, 10.0f, false, 0.0, 0.0, 0.0);

    private static final String BRAIN_DEVELOPMENT = id(SkillNames.LEVEL0_PASSIVE_LV1);
    private static final String MULTI_BRAIN = id(SkillNames.LEVEL0_PASSIVE_LV2);
    private static final String PARALLEL_THOUGHT = id(SkillNames.LEVEL0_PASSIVE_LV3);
    private static final String CONSCIOUSNESS_ANALYSIS = id(SkillNames.LEVEL0_PASSIVE_LV4);
    private static final String ABSOLUTE_SELF_CONTROL = id(SkillNames.LEVEL0_PASSIVE_LV5);
    private static final String ENDURANCE_TRAINING = id(SkillNames.ENDURANCE_TRAINING);
    private static final String PHYSICAL_TRAINING = id(SkillNames.PHYSICAL_TRAINING);

    private CommonSkillBonuses() {
    }

    static Bonuses calculate(Map<String, SkillData> learnedSkills, int abilityLevel,
                             boolean commonSkillsAvailable) {
        if (!commonSkillsAvailable || learnedSkills == null || learnedSkills.isEmpty()) return NONE;

        var brainTier = tier(learnedSkills.get(BRAIN_DEVELOPMENT));
        var stackTier = tier(learnedSkills.get(MULTI_BRAIN));
        var iterationTier = tier(learnedSkills.get(PARALLEL_THOUGHT));
        var spTier = tier(learnedSkills.get(CONSCIOUSNESS_ANALYSIS));
        var enduranceTier = tier(learnedSkills.get(ENDURANCE_TRAINING));
        var physicalTier = tier(learnedSkills.get(PHYSICAL_TRAINING));

        return new Bonuses(
                brainTier * Mth.clamp(abilityLevel, 0, 5) * 5.0f,
                stackTier,
                1.0f + iterationTier * 0.05f,
                10.0f * (1.0f + spTier * 0.10f),
                learnedSkills.containsKey(ABSOLUTE_SELF_CONTROL),
                enduranceTier * 50.0,
                physicalTier * 25.0,
                physicalTier * 25.0
        );
    }

    static int tier(SkillData data) {
        return data == null ? 0 : proficiencyTier(data.getProficiency());
    }

    static int proficiencyTier(float proficiency) {
        return SkillData.getProficiencyTier(proficiency);
    }

    static int reachedProficiencyThresholds(float proficiency) {
        return SkillData.getReachedProficiencyThresholds(proficiency);
    }

    private static String id(String path) {
        return AcademyCraft.academy(path).toString();
    }

    record Bonuses(
            float maxCp,
            int stackBonus,
            float iterationMultiplier,
            float recoveredCpPerSp,
            boolean overloadImmune,
            double enduranceBonus,
            double muscleBonus,
            double dexterityBonus
    ) {
    }
}
