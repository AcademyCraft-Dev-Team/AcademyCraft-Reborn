package org.academy.internal.server.ability;

import org.academy.AcademyCraft;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.server.config.AbilityConfig;

import java.util.Set;

/**
 * Calculates derived bonuses from the learned common brain-development branch.
 */
final class BrainDevelopmentBonuses {
    static final Bonuses NONE = new Bonuses(0, 0, 0);
    private static final String LEVEL_1 = AcademyCraft.academy(SkillNames.LEVEL0_PASSIVE_LV1).toString();
    private static final String LEVEL_2 = AcademyCraft.academy(SkillNames.LEVEL0_PASSIVE_LV2).toString();
    private static final String LEVEL_3 = AcademyCraft.academy(SkillNames.LEVEL0_PASSIVE_LV3).toString();
    private static final String LEVEL_4 = AcademyCraft.academy(SkillNames.LEVEL0_PASSIVE_LV4).toString();
    private static final String LEVEL_5 = AcademyCraft.academy(SkillNames.LEVEL0_PASSIVE_LV5).toString();

    private BrainDevelopmentBonuses() {
    }

    static Bonuses calculate(
            Set<String> learnedSkills,
            AbilityConfig.BrainDevelopmentSettings settings,
            boolean commonSkillsAvailable
    ) {
        if (!commonSkillsAvailable) return NONE;

        float maxCp = 0;
        float recovery = 0;
        float efficiency = 0;
        if (learnedSkills.contains(LEVEL_1)) {
            maxCp += safe(settings.level1MaxCpBonus);
            recovery += safe(settings.level1RecoveryBonus);
        }
        if (learnedSkills.contains(LEVEL_2)) {
            efficiency += safe(settings.level2EfficiencyBonus);
        }
        if (learnedSkills.contains(LEVEL_3)) {
            maxCp += safe(settings.level3MaxCpBonus);
            recovery += safe(settings.level3RecoveryBonus);
        }
        if (learnedSkills.contains(LEVEL_4)) {
            efficiency += safe(settings.level4EfficiencyBonus);
        }
        if (learnedSkills.contains(LEVEL_5)) {
            maxCp += safe(settings.level5MaxCpBonus);
            recovery += safe(settings.level5RecoveryBonus);
            efficiency += safe(settings.level5EfficiencyBonus);
        }
        return new Bonuses(maxCp, recovery, efficiency);
    }

    private static float safe(float value) {
        return Float.isFinite(value) && value > 0 ? value : 0;
    }

    record Bonuses(float maxCp, float recovery, float efficiency) {
    }
}
