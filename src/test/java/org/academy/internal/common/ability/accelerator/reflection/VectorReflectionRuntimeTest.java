package org.academy.internal.common.ability.accelerator.reflection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorReflectionRuntimeTest {
    @Test
    void canceledKillDoesNotRebuildHealthyObservers() {
        assertFalse(VectorReflectionRuntime.shouldRebuildObservers(
                false, true, false, true
        ));
    }

    @Test
    void rebuildsOnlyForActualRegistrationDamage() {
        assertTrue(VectorReflectionRuntime.shouldRebuildObservers(
                true, true, false, true
        ));
        assertTrue(VectorReflectionRuntime.shouldRebuildObservers(
                false, false, false, true
        ));
        assertTrue(VectorReflectionRuntime.shouldRebuildObservers(
                false, true, false, false
        ));
    }

    @Test
    void doesNotOverwriteConflictingUuidRegistration() {
        assertFalse(VectorReflectionRuntime.shouldRebuildObservers(
                false, false, true, false
        ));
    }
}
