package org.academy.api.common.ability.program;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Checked numeric operations shared by all ability-program runtimes. */
public final class ProgramNumericMath {
    private static final int MAX_POWER_BITS = 16_384;

    private ProgramNumericMath() {
    }

    public static double truncate(double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Non-finite number");
        return value < 0.0 ? Math.ceil(value) : Math.floor(value);
    }

    public static int integerDivide(int left, int right) {
        if (left == Integer.MIN_VALUE && right == -1) throw new ArithmeticException("Integer overflow");
        return left / right;
    }

    public static double integerDivide(double left, double right) {
        if (right == 0.0) throw new ArithmeticException("Division by zero");
        return truncate(left / right);
    }

    public static BigInteger power(BigInteger base, BigInteger exponent) {
        if (exponent.signum() < 0) throw new ArithmeticException("Integer exponent must be non-negative");
        if (exponent.signum() == 0) return BigInteger.ONE;
        if (base.signum() == 0) return BigInteger.ZERO;
        if (base.abs().equals(BigInteger.ONE)) return base.signum() < 0 && exponent.testBit(0)
                ? BigInteger.ONE.negate() : BigInteger.ONE;
        if (exponent.bitLength() > 31 || (long) base.abs().bitLength() * exponent.intValue() > MAX_POWER_BITS) {
            throw new ArithmeticException("Power result exceeds program numeric limit");
        }
        return base.pow(exponent.intValueExact());
    }

    /** Float to integer conversion truncates toward zero; narrowing overflow is rejected. */
    public static Object convert(Number value, ProgramValueType destination) {
        if (destination.equals(ProgramValueTypes.FLOAT)) {
            double result = value.doubleValue();
            if (!Double.isFinite(result)) throw new ArithmeticException("Float conversion overflow");
            return result;
        }
        BigInteger integer = value instanceof BigInteger big ? big
                : value instanceof Integer ? BigInteger.valueOf(value.longValue())
                : BigDecimal.valueOf(truncate(value.doubleValue())).toBigInteger();
        if (destination.equals(ProgramValueTypes.INTEGER)) return integer.intValueExact();
        if (destination.equals(ProgramValueTypes.BIG_INTEGER)) return integer;
        throw new IllegalArgumentException("Destination must be numeric");
    }
}
