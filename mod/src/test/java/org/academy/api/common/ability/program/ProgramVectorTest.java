package org.academy.api.common.ability.program;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgramVectorTest {
    @Test
    void preservesMagnitudeAndSupportsVectorAlgebra() {
        var value = new ProgramVector(3.0, 4.0, 0.0);

        assertEquals(5.0, value.length());
        assertEquals(new ProgramVector(4.0, 5.0, 1.0),
                value.add(new ProgramVector(1.0, 1.0, 1.0)));
        assertEquals(new ProgramVector(2.0, 3.0, -1.0),
                value.subtract(new ProgramVector(1.0, 1.0, 1.0)));
        assertEquals(new ProgramVector(6.0, 8.0, 0.0), value.scale(2.0));
        assertEquals(3.0, value.dot(new ProgramVector(1.0, 0.0, 0.0)));
        assertEquals(new ProgramVector(0.0, 0.0, -4.0),
                value.cross(new ProgramVector(1.0, 0.0, 0.0)));
        assertEquals(new ProgramDirection(3.0, 4.0, 0.0), value.direction());
    }

    @Test
    void permitsZeroButRejectsInvalidComponentsAndZeroNormalization() {
        var zero = new ProgramVector(0.0, 0.0, 0.0);

        assertEquals(0.0, zero.length());
        assertThrows(IllegalStateException.class, zero::direction);
        assertThrows(IllegalArgumentException.class,
                () -> new ProgramVector(Double.NaN, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> zero.scale(Double.POSITIVE_INFINITY));
    }
}
