package org.academy.api.common.util;

import net.minecraft.world.phys.AABB;
import org.joml.*;

import java.lang.Math;
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

    public static class RayUtil {
        public static boolean intersectRayTransformedAABB(
                Vector3fc rayOriginWorld,
                Vector3fc rayEndWorld,
                AABB aabbLocal,
                Matrix4fc worldTransform,
                Vector3f intersectionPointWorld
        ) {
            Matrix4f invTransform = new Matrix4f();
            worldTransform.invert(invTransform);

            Vector3f rayOriginLocal = new Vector3f();
            invTransform.transformPosition(rayOriginWorld, rayOriginLocal);

            Vector3f rayEndLocal = new Vector3f();
            invTransform.transformPosition(rayEndWorld, rayEndLocal);

            Vector3f rayDirLocal = new Vector3f();
            rayEndLocal.sub(rayOriginLocal, rayDirLocal);

            float lengthSq = rayDirLocal.lengthSquared();

            Vector3f localAabbMinJoml = new Vector3f((float)aabbLocal.minX, (float)aabbLocal.minY, (float)aabbLocal.minZ);
            Vector3f localAabbMaxJoml = new Vector3f((float)aabbLocal.maxX, (float)aabbLocal.maxY, (float)aabbLocal.maxZ);

            if (lengthSq < 1.0E-12f) {
                if (rayOriginLocal.x >= localAabbMinJoml.x() && rayOriginLocal.x <= localAabbMaxJoml.x() &&
                        rayOriginLocal.y >= localAabbMinJoml.y() && rayOriginLocal.y <= localAabbMaxJoml.y() &&
                        rayOriginLocal.z >= localAabbMinJoml.z() && rayOriginLocal.z <= localAabbMaxJoml.z()) {
                    intersectionPointWorld.set(rayOriginWorld);
                    return true;
                }
                return false;
            }

            float rayLengthLocal = (float) Math.sqrt(lengthSq);
            rayDirLocal.div(rayLengthLocal);

            Vector2f nearFar = new Vector2f();
            boolean intersects = Intersectionf.intersectRayAab(
                    rayOriginLocal,
                    rayDirLocal,
                    localAabbMinJoml,
                    localAabbMaxJoml,
                    nearFar
            );

            if (!intersects) {
                return false;
            }

            float tNear = nearFar.x;
            float tFar = nearFar.y;

            float effectiveNear = Math.max(0.0f, tNear);
            float effectiveFar = Math.min(rayLengthLocal, tFar);

            if (effectiveNear > effectiveFar) {
                return false;
            }

            Vector3f intersectionPointLocal = new Vector3f();
            rayDirLocal.mul(effectiveNear, intersectionPointLocal);
            intersectionPointLocal.add(rayOriginLocal);

            worldTransform.transformPosition(intersectionPointLocal, intersectionPointWorld);

            return true;
        }
    }

    public static float magicAnimationFactor(float animationDuration, float partialTick) {
        return 1 - (float) Math.exp(-Math.log(20) * partialTick / 20 / animationDuration);
    }

    public static float animationFactor(float animationDuration, float deltaTime) {
        return deltaTime / animationDuration;
    }

    public static class WeightedRandom<T> {
        private final NavigableMap<Double, T> map = new TreeMap<>();
        private double totalWeight = 0.0;

        public void addItem(T item, double probability) {
            if (probability <= 0) return;
            totalWeight += probability;
            map.put(totalWeight, item);
        }

        public T getRandomItem() {
            if (totalWeight <= 0) return null;
            double r = RANDOM.nextDouble() * totalWeight;
            NavigableMap.Entry<Double, T> entry = map.ceilingEntry(r);
            return entry != null ? entry.getValue() : map.firstEntry().getValue();
        }
    }
}