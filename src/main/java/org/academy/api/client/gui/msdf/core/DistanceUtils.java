package org.academy.api.client.gui.msdf.core;

public class DistanceUtils {
    public static double resolveDistance(double distance) {
        return distance;
    }

    public static double resolveDistance(MultiDistance distance) {
        return Arithmetic.median(distance.r, distance.g, distance.b);
    }

    public static double resolveDistance(MultiAndTrueDistance distance) {
        return Arithmetic.median(distance.r, distance.g, distance.b);
    }
}