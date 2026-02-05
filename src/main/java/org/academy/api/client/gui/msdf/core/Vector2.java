package org.academy.api.client.gui.msdf.core;

import java.util.Objects;

public class Vector2 {
    public double x, y;

    public Vector2() {
        x = 0;
        y = 0;
    }

    public Vector2(double val) {
        x = val;
        y = val;
    }

    public Vector2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public static double dotProduct(Vector2 a, Vector2 b) {
        return a.x * b.x + a.y * b.y;
    }

    public static double crossProduct(Vector2 a, Vector2 b) {
        return a.x * b.y - a.y * b.x;
    }

    public static Vector2 add(Vector2 a, Vector2 b) {
        return new Vector2(a.x + b.x, a.y + b.y);
    }

    public static Vector2 subtract(Vector2 a, Vector2 b) {
        return new Vector2(a.x - b.x, a.y - b.y);
    }

    public static Vector2 multiply(Vector2 a, Vector2 b) {
        return new Vector2(a.x * b.x, a.y * b.y);
    }

    public static Vector2 divide(Vector2 a, Vector2 b) {
        return new Vector2(a.x / b.x, a.y / b.y);
    }

    public static Vector2 multiply(double a, Vector2 b) {
        return new Vector2(a * b.x, a * b.y);
    }

    public static Vector2 divide(double a, Vector2 b) {
        return new Vector2(a / b.x, a / b.y);
    }

    public static Vector2 multiply(Vector2 a, double b) {
        return new Vector2(a.x * b, a.y * b);
    }

    public static Vector2 divide(Vector2 a, double b) {
        return new Vector2(a.x / b, a.y / b);
    }

    public void reset() {
        x = 0;
        y = 0;
    }

    public void set(double newX, double newY) {
        x = newX;
        y = newY;
    }

    public double squaredLength() {
        return x * x + y * y;
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public Vector2 normalize() {
        return normalize(true);
    }

    public Vector2 normalize(boolean allowZero) {
        var len = length();
        if (len != 0) {
            return new Vector2(x / len, y / len);
        }
        return new Vector2(0, allowZero ? 0 : 1);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public Vector2 getOrthogonal(boolean polarity) {
        return polarity ? new Vector2(-y, x) : new Vector2(y, -x);
    }

    public Vector2 getOrthonormal(boolean polarity) {
        return getOrthonormal(polarity, true);
    }

    public Vector2 getOrthonormal(boolean polarity, boolean allowZero) {
        var len = length();
        if (len != 0) {
            return polarity ? new Vector2(-y / len, x / len) : new Vector2(y / len, -x / len);
        }
        return polarity ? new Vector2(0, allowZero ? 0 : 1) : new Vector2(0, allowZero ? 0 : -1);
    }

    public boolean isZero() {
        return x == 0 && y == 0;
    }

    public Vector2 add(Vector2 other) {
        return new Vector2(x + other.x, y + other.y);
    }

    public Vector2 subtract(Vector2 other) {
        return new Vector2(x - other.x, y - other.y);
    }

    public Vector2 multiply(Vector2 other) {
        return new Vector2(x * other.x, y * other.y);
    }

    public Vector2 divide(Vector2 other) {
        return new Vector2(x / other.x, y / other.y);
    }

    public Vector2 multiply(double value) {
        return new Vector2(x * value, y * value);
    }

    public Vector2 divide(double value) {
        return new Vector2(x / value, y / value);
    }

    public Vector2 negate() {
        return new Vector2(-x, -y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        var vector2 = (Vector2) obj;
        return Double.compare(vector2.x, x) == 0 && Double.compare(vector2.y, y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}