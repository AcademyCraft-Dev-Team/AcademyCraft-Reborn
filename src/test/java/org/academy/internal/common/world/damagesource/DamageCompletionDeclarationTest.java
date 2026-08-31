package org.academy.internal.common.world.damagesource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void acceptsSeparateInflictedAndHealthDamage() {
        assertTrue(DamageCompletionDeclaration.isValid(8.0f, 6.0f, 4.0f));
        assertTrue(DamageCompletionDeclaration.isValid(8.0f, 6.0f, 0.0f));
        assertFalse(DamageCompletionDeclaration.isValid(8.0f, 6.0f, 7.0f));
        assertFalse(DamageCompletionDeclaration.isValid(8.0f, 6.0f, Float.NaN));
    }

    @Test
    void declaresAcceptedDamageWhenFloatHealthCannotRepresentTheSubtraction() {
        var before = 0x1.0p63f;
        var acceptedDamage = 8.0f;
        var expected = Math.max(0.0f, before - acceptedDamage);

        assertEquals(before, expected);
        assertEquals(acceptedDamage,
                DamageCompletionDeclaration.resolveHealthDamageForDeclaration(
                        before, expected, before, acceptedDamage
                ));
    }

    @Test
    void declarationResolutionDoesNotInventDamageForRejectedWrites() {
        assertEquals(5.0f,
                DamageCompletionDeclaration.resolveHealthDamageForDeclaration(
                        20.0f, 15.0f, 15.0f, 5.0f
                ));
        assertEquals(0.0f,
                DamageCompletionDeclaration.resolveHealthDamageForDeclaration(
                        20.0f, 15.0f, 20.0f, 5.0f
                ));
    }
}
