package org.academy.api.common.ability;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillInitialStateTest {
    @Test
    void skillsAreEnabledByDefault() {
        var skill = new TestSkill(new TestCategory("enabled"), false);

        assertTrue(skill.createData().isEnabled());
    }

    @Test
    void toggleSkillsCanStartDisabled() {
        var skill = new TestSkill(new TestCategory("disabled"), true);

        assertFalse(skill.createData().isEnabled());
    }

    private static final class TestSkill extends Skill {
        private TestSkill(AbilityCategory category, boolean initiallyDisabled) {
            super(createBuilder(category, initiallyDisabled));
        }

        private static Builder createBuilder(AbilityCategory category, boolean initiallyDisabled) {
            var builder = Builder.of(category);
            return initiallyDisabled ? builder.initiallyDisabled() : builder;
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
}
