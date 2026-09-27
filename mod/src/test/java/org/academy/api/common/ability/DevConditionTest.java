package org.academy.api.common.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevConditionTest {
    @Test
    void dependencyConditionAcceptsALearnedDependency() {
        assertTrue(DevCondition.DependencyCondition.isSatisfied(
                "academy:darkmatter_shaping",
                "academy:darkmatter_shaping"::equals
        ));
    }

    @Test
    void dependencyConditionRejectsAnUnlearnedDependency() {
        assertFalse(DevCondition.DependencyCondition.isSatisfied(
                "academy:darkmatter_shaping",
                _ -> false
        ));
    }

    @Test
    void legacyConditionWithoutAnIdRemainsNonBlocking() {
        assertTrue(DevCondition.DependencyCondition.isSatisfied("", _ -> false));
    }
}
