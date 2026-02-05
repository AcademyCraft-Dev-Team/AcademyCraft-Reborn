package org.academy.api.client.gui.msdf.core;

public class SignedDistance {
    public double distance;
    public double dot;

    public SignedDistance() {
        distance = -Double.MAX_VALUE;
        dot = 0;
    }

    public SignedDistance(double distance, double dot) {
        this.distance = distance;
        this.dot = dot;
    }

    public static boolean lessThan(SignedDistance a, SignedDistance b) {
        return Math.abs(a.distance) < Math.abs(b.distance) ||
                (Math.abs(a.distance) == Math.abs(b.distance) && a.dot < b.dot);
    }

    public static boolean greaterThan(SignedDistance a, SignedDistance b) {
        return Math.abs(a.distance) > Math.abs(b.distance) ||
                (Math.abs(a.distance) == Math.abs(b.distance) && a.dot > b.dot);
    }

    public static boolean lessThanOrEqual(SignedDistance a, SignedDistance b) {
        return Math.abs(a.distance) < Math.abs(b.distance) ||
                (Math.abs(a.distance) == Math.abs(b.distance) && a.dot <= b.dot);
    }

    public static boolean greaterThanOrEqual(SignedDistance a, SignedDistance b) {
        return Math.abs(a.distance) > Math.abs(b.distance) ||
                (Math.abs(a.distance) == Math.abs(b.distance) && a.dot >= b.dot);
    }
}