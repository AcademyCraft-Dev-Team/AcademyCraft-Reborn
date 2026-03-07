package org.academy.api.client.gui.msdf.core;

@SuppressWarnings("MathClampMigration")
public final class Arithmetic {
    public static double median(double a, double b, double c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }

    public static float median(float a, float b, float c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }

    public static double mix(double a, double b, double weight) {
        return Math.fma(weight, b - a, a);
    }

    public static float mix(float a, float b, float weight) {
        return Math.fma(weight, b - a, a);
    }

    public static double clamp(double n, double max) {
        return Math.min(max, Math.max(n, 0.0));
    }

    public static int sign(double n) {
        return (0 < n ? 1 : 0) - (n < 0 ? 1 : 0);
    }

    public static int nonZeroSign(double n) {
        return 2 * (n > 0 ? 1 : 0) - 1;
    }
}