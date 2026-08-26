package org.academy.api.common.ability;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void cpConsumingSkillsClampIterationToOneSecond() {
        assertEquals(20, new CostedSkill(
                new TestCategory("cast_iteration"), 10, 0, 80).getIterationTicks(0));
        assertEquals(20, new CostedSkill(
                new TestCategory("maintenance_iteration"), 0, 10, 40).getIterationTicks(0));
        assertEquals(40, new CostedSkill(
                new TestCategory("free_iteration"), 0, 0, 40).getIterationTicks(0));
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

    private static final class CostedSkill extends Skill {
        private CostedSkill(AbilityCategory category, int cpCost, int maintenanceCost,
                            int iterationTicks) {
            super(Builder.of(category)
                    .cpCost(cpCost)
                    .maintenanceCost(maintenanceCost)
                    .iterationTicks(iterationTicks));
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
