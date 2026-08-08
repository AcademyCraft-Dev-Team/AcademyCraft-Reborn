package org.academy.api.common.ability;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningHelperTest {
    @Test
    void categoryScopedSkillIsAvailableOnlyToItsOwnCategory() {
        var owner = new TestCategory("owner");
        var other = new TestCategory("other");
        var skill = new TestSkill(owner);

        assertTrue(LearningHelper.isSkillAvailableForCategory(owner, skill));
        assertFalse(LearningHelper.isSkillAvailableForCategory(other, skill));
        assertFalse(LearningHelper.isSkillAvailableForCategory(null, skill));
    }

    @Test
    void commonSkillIsAvailableOnlyToCategoriesThatSupportCommonSkills() {
        var anchor = new TestCategory("anchor");
        var developed = new TestCategory("developed");
        var level0 = new NoCommonSkillCategory();
        var skill = new CommonTestSkill(anchor);

        assertTrue(LearningHelper.isSkillAvailableForCategory(anchor, skill));
        assertTrue(LearningHelper.isSkillAvailableForCategory(developed, skill));
        assertFalse(LearningHelper.isSkillAvailableForCategory(level0, skill));
        assertFalse(LearningHelper.isSkillAvailableForCategory(null, skill));
    }

    @Test
    void abilityExperienceRequirementKeepsTheOneThousandBaseAndLevelFourFactor() {
        var category = new TestCategory("requirements");
        new LeveledTestSkill(category, AbilityLevel.LEVEL4);

        assertEquals(1333.0f, LearningHelper.getAbilityExpRequirement(category, 4));
        assertEquals(666.0f, LearningHelper.getAbilityExpRequirement(category, 3));
    }

    private static final class TestSkill extends Skill {
        private TestSkill(AbilityCategory category) {
            super(Builder.of(category));
        }
    }

    private static final class CommonTestSkill extends Skill {
        private CommonTestSkill(AbilityCategory category) {
            super(Builder.of(category).common());
        }
    }

    private static final class LeveledTestSkill extends Skill {
        private LeveledTestSkill(AbilityCategory category, AbilityLevel level) {
            super(Builder.of(category).level(level));
        }
    }

    private static final class TestCategory extends AbilityCategory {
        private final Identifier icon;

        private TestCategory(String name) {
            super(1.0f);
            icon = AcademyCraft.academy("test/" + name);
        }

        @Override
        public Identifier getDeveloperIcon() {
            return icon;
        }

        @Override
        public String getDisplayName() {
            return "Test";
        }
    }

    private static final class NoCommonSkillCategory extends AbilityCategory {
        private NoCommonSkillCategory() {
            super(1.0f);
        }

        @Override
        public boolean supportsCommonSkills() {
            return false;
        }

        @Override
        public Identifier getDeveloperIcon() {
            return AcademyCraft.academy("test/no_common");
        }

        @Override
        public String getDisplayName() {
            return "No common skills";
        }
    }
}
