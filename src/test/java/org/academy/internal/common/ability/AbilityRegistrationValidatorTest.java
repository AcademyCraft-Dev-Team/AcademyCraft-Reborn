package org.academy.internal.common.ability;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbilityRegistrationValidatorTest {
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
}
