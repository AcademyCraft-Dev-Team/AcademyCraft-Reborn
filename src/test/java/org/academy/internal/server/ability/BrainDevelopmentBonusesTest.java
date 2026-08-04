package org.academy.internal.server.ability;

import org.academy.AcademyCraft;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.server.config.AbilityConfig;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrainDevelopmentBonusesTest {
    @Test
    void accumulatesReferenceBonusesForTheCompleteBranch() {
        var learned = Set.of(
                id(SkillNames.LEVEL0_PASSIVE_LV1),
                id(SkillNames.LEVEL0_PASSIVE_LV2),
                id(SkillNames.LEVEL0_PASSIVE_LV3),
                id(SkillNames.LEVEL0_PASSIVE_LV4),
                id(SkillNames.LEVEL0_PASSIVE_LV5)
        );

        var bonuses = BrainDevelopmentBonuses.calculate(
                learned,
                new AbilityConfig.BrainDevelopmentSettings(),
                true
        );

        assertEquals(620.0f, bonuses.maxCp());
        assertEquals(31.0f, bonuses.recovery());
        assertEquals(0.50f, bonuses.efficiency(), 0.0001f);
    }

    @Test
    void disablesBonusesForLevel0StyleCategories() {
        var bonuses = BrainDevelopmentBonuses.calculate(
                Set.of(id(SkillNames.LEVEL0_PASSIVE_LV5)),
                new AbilityConfig.BrainDevelopmentSettings(),
                false
        );

        assertEquals(BrainDevelopmentBonuses.NONE, bonuses);
    }

    private static String id(String path) {
        return AcademyCraft.academy(path).toString();
    }
}
