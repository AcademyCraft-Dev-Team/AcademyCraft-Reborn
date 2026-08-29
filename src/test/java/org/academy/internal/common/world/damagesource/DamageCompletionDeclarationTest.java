package org.academy.internal.common.world.damagesource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageCompletionDeclarationTest {
    @Test
    void acceptsOnlyCompletedPositiveFiniteDamage() {
        assertTrue(DamageCompletionDeclaration.isValid(8.0f, 5.0f));
        assertFalse(DamageCompletionDeclaration.isValid(0.0f, 5.0f));
        assertFalse(DamageCompletionDeclaration.isValid(8.0f, 0.0f));
        assertFalse(DamageCompletionDeclaration.isValid(Float.NaN, 5.0f));
        assertFalse(DamageCompletionDeclaration.isValid(8.0f, Float.POSITIVE_INFINITY));
    }
}
