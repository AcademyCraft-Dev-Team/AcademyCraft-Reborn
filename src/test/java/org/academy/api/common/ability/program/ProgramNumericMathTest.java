package org.academy.api.common.ability.program;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

class ProgramNumericMathTest {
    @Test
    void divisionTruncatesTowardZeroAndRejectsUndefinedResults() {
        assertEquals(-2, ProgramNumericMath.integerDivide(-7, 3));
        assertEquals(-2.0, ProgramNumericMath.integerDivide(7.9, -3.0));
        assertThrows(ArithmeticException.class, () -> ProgramNumericMath.integerDivide(1.0, 0.0));
        assertThrows(ArithmeticException.class, () -> ProgramNumericMath.integerDivide(Integer.MIN_VALUE, -1));
    }

    @Test
    void powersStayExactAndHaveABoundedCost() {
        assertEquals(BigInteger.valueOf(1024), ProgramNumericMath.power(BigInteger.TWO, BigInteger.TEN));
        assertEquals(BigInteger.valueOf(-27), ProgramNumericMath.power(BigInteger.valueOf(-3), BigInteger.valueOf(3)));
        assertThrows(ArithmeticException.class, () -> ProgramNumericMath.power(BigInteger.TWO, BigInteger.valueOf(-1)));
        assertThrows(ArithmeticException.class, () -> ProgramNumericMath.power(BigInteger.TWO, BigInteger.valueOf(1000000)));
    }

    @Test
    void numericConversionsTruncateAndCheckNarrowing() {
        assertEquals(-3, ProgramNumericMath.convert(-3.9, ProgramValueTypes.INTEGER));
        assertEquals(BigInteger.valueOf(3), ProgramNumericMath.convert(3.9, ProgramValueTypes.BIG_INTEGER));
        assertEquals(7.0, ProgramNumericMath.convert(BigInteger.valueOf(7), ProgramValueTypes.FLOAT));
        assertThrows(ArithmeticException.class, () -> ProgramNumericMath.convert(BigInteger.ONE.shiftLeft(32), ProgramValueTypes.INTEGER));
        assertThrows(ArithmeticException.class, () -> ProgramNumericMath.convert(Double.NaN, ProgramValueTypes.INTEGER));
    }
}
