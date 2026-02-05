package org.academy.api.client.gui.msdf.core;

public class Arithmetic {
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

    public static double clamp(double n) {
        return n >= 0 && n <= 1 ? n : (n > 0 ? 1 : 0);
    }

    public static float clamp(float n) {
        return n >= 0 && n <= 1 ? n : (n > 0 ? 1 : 0);
    }

    public static double clamp(double n, double b) {
        return n >= 0 && n <= b ? n : (n > 0 ? b : 0);
    }

    public static float clamp(float n, float b) {
        return n >= 0 && n <= b ? n : (n > 0 ? b : 0);
    }

    public static double clamp(double n, double a, double b) {
        return n >= a && n <= b ? n : (n < a ? a : b);
    }

    public static float clamp(float n, float a, float b) {
        return n >= a && n <= b ? n : (n < a ? a : b);
    }

    public static int sign(double n) {
        return (0 < n ? 1 : 0) - (n < 0 ? 1 : 0);
    }

    public static int sign(float n) {
        return (0 < n ? 1 : 0) - (n < 0 ? 1 : 0);
    }

    public static int nonZeroSign(double n) {
        return 2 * (n > 0 ? 1 : 0) - 1;
    }
}