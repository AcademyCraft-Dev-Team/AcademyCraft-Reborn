package org.academy.internal.common.ability.development;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.Skill;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbilityRegistrationValidatorTest {
    @Test
    void hiddenDependenciesAreAllowedOutsideTheLearningPath() {
        var category = new TestCategory("hidden_dependency");
        var hiddenDependency = new TestSkill(category, true);
        var hiddenDependent = new TestSkill(category, true);

        assertTrue(AbilityRegistrationValidator.acceptsCategoryDependency(hiddenDependent, hiddenDependency));
    }

    @Test
    void visibleSkillsStillRequireAnAvailableDependency() {
        var category = new TestCategory("visible_dependency");
        var hiddenDependency = new TestSkill(category, true);
        var visibleSkill = new TestSkill(category, false);

        assertTrue(AbilityRegistrationValidator.acceptsCategoryDependency(visibleSkill, visibleSkill));
        assertFalse(AbilityRegistrationValidator.acceptsCategoryDependency(visibleSkill, hiddenDependency));
    }

    @Test
    void acceptsAnAcyclicDependencyGraph() {
        var root = new Node("root");
        var middle = new Node("middle");
        var leaf = new Node("leaf");
        middle.dependencies.add(root);
        leaf.dependencies.add(middle);

        assertDoesNotThrow(() -> AbilityRegistrationValidator.validateAcyclic(
                List.of(root, middle, leaf),
                node -> node.dependencies,
                node -> node.name
        ));
    }

    @Test
    void rejectsADependencyCycleWithAReadablePath() {
        var first = new Node("first");
        var second = new Node("second");
        var third = new Node("third");
        first.dependencies.add(second);
        second.dependencies.add(third);
        third.dependencies.add(first);

        var error = assertThrows(IllegalStateException.class, () ->
                AbilityRegistrationValidator.validateAcyclic(
                        List.of(first, second, third),
                        node -> node.dependencies,
                        node -> node.name
                ));

        assertTrue(error.getMessage().contains("first"));
        assertTrue(error.getMessage().contains("second"));
        assertTrue(error.getMessage().contains("third"));
    }

    private static final class Node {
        private final String name;
        private final List<Node> dependencies = new ArrayList<>();

        private Node(String name) {
            this.name = name;
        }
    }

    private static final class TestSkill extends Skill {
        private TestSkill(AbilityCategory category, boolean hidden) {
            super(hidden ? Builder.of(category).hidden() : Builder.of(category));
        }
    }

    private static final class TestCategory extends AbilityCategory {
        private TestCategory(String name) {
            super(1.0f);
        }

        @Override
        public Identifier getDeveloperIcon() {
            return AcademyCraft.academy("test/validator");
        }

        @Override
        public String getDisplayName() {
            return "Validator test";
        }
    }
}
