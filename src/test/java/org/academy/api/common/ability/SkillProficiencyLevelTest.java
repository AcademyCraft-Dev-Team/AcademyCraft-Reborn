package org.academy.api.common.ability;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillProficiencyLevelTest {
    @Test
    void defaultThreeLevelSkillUsesFourEqualIntervals() {
        var skill = new TestSkill(new TestCategory(), 3);

        assertEquals(0, skill.getLevelForProficiency(0.0f));
        assertEquals(0, skill.getLevelForProficiency(749.999f));
        assertEquals(1, skill.getLevelForProficiency(750.0f));
        assertEquals(2, skill.getLevelForProficiency(1500.0f));
        assertEquals(3, skill.getLevelForProficiency(2250.0f));
        assertEquals(3, skill.getLevelForProficiency(3000.0f));
    }

    @Test
    void zeroLevelSkillAlwaysHasZeroEffectLevel() {
        var skill = new TestSkill(new TestCategory(), 0);

        assertEquals(0, skill.getLevelForProficiency(0.0f));
        assertEquals(0, skill.getLevelForProficiency(3000.0f));
    }

    @Test
    void otherMaximumLevelsUseMaximumPlusOneIntervals() {
        var skill = new TestSkill(new TestCategory(), 1);

        assertEquals(0, skill.getLevelForProficiency(1499.999f));
        assertEquals(1, skill.getLevelForProficiency(1500.0f));
    }

    private static final class TestSkill extends Skill {
        private TestSkill(AbilityCategory category, int maxLevel) {
            super(Builder.of(category).maxSkillLevel(maxLevel));
        }
    }

    private static final class TestCategory extends AbilityCategory {
        private TestCategory() {
            super(1.0f);
        }

        @Override
        public Identifier getDeveloperIcon() {
            return AcademyCraft.academy("test/proficiency");
        }

        @Override
        public String getDisplayName() {
            return "Test";
        }
    }
}
