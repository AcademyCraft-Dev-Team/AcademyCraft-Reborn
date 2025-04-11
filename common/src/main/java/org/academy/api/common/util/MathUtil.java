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

    public static boolean rayIntersectPanelFastAngles(
            double rayOx, double rayOy, double rayOz,        // 射线起点
            double rayPitchDeg, double rayYawDeg,            // 射线俯仰和偏航角
            double panelCx, double panelCy, double panelCz,  // 面板中心
            double panelPitchDeg, double panelYawDeg,        // 面板旋转（法线方向）
            double[] outIntersection                         // 输出交点
    ) {
        double rayPitchRad = Math.toRadians(rayPitchDeg);
        double rayYawRad = Math.toRadians(rayYawDeg);

        double rayDx = Math.cos(rayPitchRad) * Math.sin(rayYawRad);
        double rayDy = -Math.sin(rayPitchRad);
        double rayDz = Math.cos(rayPitchRad) * Math.cos(rayYawRad);

        double panelPitchRad = Math.toRadians(panelPitchDeg);
        double panelYawRad = Math.toRadians(panelYawDeg);

        double nx = Math.sin(panelYawRad);
        double ny = -Math.sin(panelPitchRad) * Math.cos(panelYawRad);
        double nz = Math.cos(panelPitchRad) * Math.cos(panelYawRad);

        double dx = panelCx - rayOx;
        double dy = panelCy - rayOy;
        double dz = panelCz - rayOz;

        double denom = nx * rayDx + ny * rayDy + nz * rayDz;
        if (Math.abs(denom) < 1e-6) return false; // 平行

        double t = (nx * dx + ny * dy + nz * dz) / denom;
        if (t < 0) return false; // 背后

        outIntersection[0] = rayOx + t * rayDx;
        outIntersection[1] = rayOy + t * rayDy;
        outIntersection[2] = rayOz + t * rayDz;

        return true;
    }

    public static class WeightedRandom<T> {
        private final NavigableMap<Double, T> map = new TreeMap<>();
        private final Random random = new Random();
        private double totalWeight = 0.0;

        public void addItem(T item, double probability) {
            if (probability <= 0) return;
            totalWeight += probability;
            map.put(totalWeight, item);
        }

        public T getRandomItem() {
            if (totalWeight <= 0) return null;
            double r = random.nextDouble() * totalWeight;
            NavigableMap.Entry<Double, T> entry = map.ceilingEntry(r);
            return entry != null ? entry.getValue() : map.firstEntry().getValue();
        }
    }
}