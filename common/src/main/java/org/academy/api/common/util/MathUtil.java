package org.academy.api.common.util;

import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

public class MathUtil {
    public static final Random RANDOM = new Random();
    public static final float PI = (float) Math.PI;
    public static final float TWO_PI = 2.0f * PI;

    public static float lerpStartEndFactor(float a, float b, float t) {
        return a + t * (b - a);
    }

    public static float lerpFactorStartEnd(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double lerpFactorStartEnd(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(val, max));
    }

    public static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(val, max));
    }

    public static float smoothStep(float x) {
        x = clamp(x, 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    public static double smoothStep(double x) {
        x = clamp(x, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    public static class WeightedRandom {
        private final NavigableMap<Double, String> map = new TreeMap<>();
        private final Random random = new Random();
        private double totalWeight = 0.0;

        public void addItem(String item, double probability) {
            if (probability <= 0) return;
            totalWeight += probability;
            map.put(totalWeight, item);
        }

        public String getRandomItem() {
            if (totalWeight <= 0) return null;
            double r = random.nextDouble() * totalWeight;
            NavigableMap.Entry<Double, String> entry = map.ceilingEntry(r);
            return entry != null ? entry.getValue() : map.firstEntry().getValue();
        }
    }
}