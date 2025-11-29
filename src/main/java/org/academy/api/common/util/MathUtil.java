package org.academy.api.common.util;

import it.unimi.dsi.fastutil.ints.IntComparator;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

import java.lang.Math;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

public class MathUtil {
    public static final RandomSource RANDOM_SOURCE = RandomSource.create();
    public static final Random RANDOM = new Random();
    public static final float PI = (float) Math.PI;
    public static final float TWO_PI = 2.0f * PI;
    public static final double EPSILON = 1e-6;

    public static Vec3 intersectRayCapsule(Vec3 origin, Vec3 direction, Vec3 capsuleCenter, float width, float height) {
        float radius = width / 2.0F;
        float halfEffectiveHeight = height / 2.0F - radius;

        if (halfEffectiveHeight <= 0) {
            return intersectRaySphere(origin, direction, capsuleCenter, radius);
        }

        Vector3f originF = new Vector3f((float) origin.x, (float) origin.y, (float) origin.z);
        Vector3f dirF = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        Vector3f centerF = new Vector3f((float) capsuleCenter.x, (float) capsuleCenter.y, (float) capsuleCenter.z);

        Vector3f topCenter = new Vector3f(centerF).add(0, halfEffectiveHeight, 0);
        Vector3f bottomCenter = new Vector3f(centerF).sub(0, halfEffectiveHeight, 0);

        Vector2f resultSphere = new Vector2f();
        float minT = Float.MAX_VALUE;

        if (Intersectionf.intersectRaySphere(originF, dirF, topCenter, radius * radius, resultSphere)) {
            float t = resultSphere.x > 0 ? resultSphere.x : resultSphere.y;
            if (t > 0 && (originF.y + dirF.y * t) >= topCenter.y) {
                minT = t;
            }
        }

        if (Intersectionf.intersectRaySphere(originF, dirF, bottomCenter, radius * radius, resultSphere)) {
            float t = resultSphere.x > 0 ? resultSphere.x : resultSphere.y;
            if (t > 0 && t < minT && (originF.y + dirF.y * t) <= bottomCenter.y) {
                minT = t;
            }
        }

        float dx = originF.x - centerF.x;
        float dz = originF.z - centerF.z;

        float a = dirF.x * dirF.x + dirF.z * dirF.z;
        float b = 2 * (dx * dirF.x + dz * dirF.z);
        float c = dx * dx + dz * dz - radius * radius;

        if (Math.abs(a) > 1e-6) {
            float delta = b * b - 4 * a * c;
            if (delta >= 0) {
                float sqrtDelta = (float) Math.sqrt(delta);
                float t1 = (-b - sqrtDelta) / (2 * a);
                float t2 = (-b + sqrtDelta) / (2 * a);

                if (t1 > 0 && t1 < minT) {
                    float yHit = originF.y + dirF.y * t1;
                    if (yHit >= bottomCenter.y && yHit <= topCenter.y) {
                        minT = t1;
                    }
                }
                if (t2 > 0 && t2 < minT) {
                    float yHit = originF.y + dirF.y * t2;
                    if (yHit >= bottomCenter.y && yHit <= topCenter.y) {
                        minT = t2;
                    }
                }
            }
        }

        if (minT < Float.MAX_VALUE) {
            return origin.add(direction.scale(minT));
        }

        return capsuleCenter;
    }

    private static Vec3 intersectRaySphere(Vec3 origin, Vec3 direction, Vec3 center, float radius) {
        Vector3f originF = new Vector3f((float) origin.x, (float) origin.y, (float) origin.z);
        Vector3f dirF = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        Vector3f centerF = new Vector3f((float) center.x, (float) center.y, (float) center.z);
        Vector2f result = new Vector2f();

        if (Intersectionf.intersectRaySphere(originF, dirF, centerF, radius * radius, result)) {
            float t = result.x > 0 ? result.x : result.y;
            if (t > 0) {
                return origin.add(direction.scale(t));
            }
        }
        return center;
    }

    public static double calculateVerticalFov(double horizontalFovDegrees, double aspectRatio) {
        return 2 * Math.atan(Math.tan(Math.toRadians(horizontalFovDegrees) / 2) / aspectRatio);
    }

    public static class RayUtil {
        public static boolean intersectRayTransformedAABB(
                Vector3fc rayOriginWorld,
                Vector3fc rayEndWorld,
                AABB aabbLocal,
                Matrix4fc worldTransform,
                Vector3f intersectionPointWorld
        ) {
            var invTransform = new Matrix4f();
            worldTransform.invert(invTransform);

            var rayOriginLocal = new Vector3f();
            invTransform.transformPosition(rayOriginWorld, rayOriginLocal);

            var rayEndLocal = new Vector3f();
            invTransform.transformPosition(rayEndWorld, rayEndLocal);

            var rayDirLocal = new Vector3f();
            rayEndLocal.sub(rayOriginLocal, rayDirLocal);

            var lengthSq = rayDirLocal.lengthSquared();

            var localAabbMinJoml = new Vector3f((float) aabbLocal.minX, (float) aabbLocal.minY, (float) aabbLocal.minZ);
            var localAabbMaxJoml = new Vector3f((float) aabbLocal.maxX, (float) aabbLocal.maxY, (float) aabbLocal.maxZ);

            if (lengthSq < 1.0E-12f) {
                if (rayOriginLocal.x >= localAabbMinJoml.x() && rayOriginLocal.x <= localAabbMaxJoml.x() &&
                        rayOriginLocal.y >= localAabbMinJoml.y() && rayOriginLocal.y <= localAabbMaxJoml.y() &&
                        rayOriginLocal.z >= localAabbMinJoml.z() && rayOriginLocal.z <= localAabbMaxJoml.z()) {
                    intersectionPointWorld.set(rayOriginWorld);
                    return true;
                }
                return false;
            }

            var rayLengthLocal = (float) Math.sqrt(lengthSq);
            rayDirLocal.div(rayLengthLocal);

            var nearFar = new Vector2f();
            var intersects = Intersectionf.intersectRayAab(
                    rayOriginLocal,
                    rayDirLocal,
                    localAabbMinJoml,
                    localAabbMaxJoml,
                    nearFar
            );

            if (!intersects) {
                return false;
            }

            var tNear = nearFar.x;
            var tFar = nearFar.y;

            var effectiveNear = Math.max(0.0f, tNear);
            var effectiveFar = Math.min(rayLengthLocal, tFar);

            if (effectiveNear > effectiveFar) {
                return false;
            }

            var intersectionPointLocal = new Vector3f();
            rayDirLocal.mul(effectiveNear, intersectionPointLocal);
            intersectionPointLocal.add(rayOriginLocal);

            worldTransform.transformPosition(intersectionPointLocal, intersectionPointWorld);

            return true;
        }
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
            var r = RANDOM.nextDouble() * totalWeight;
            var entry = map.ceilingEntry(r);
            return entry != null ? entry.getValue() : map.firstEntry().getValue();
        }
    }

    public enum Axis2D {
        HORIZONTAL,
        VERTICAL;

        public Axis2D orthogonal() {
            return switch (this) {
                case HORIZONTAL -> VERTICAL;
                case VERTICAL -> HORIZONTAL;
            };
        }

        public Direction2D getPositive() {
            return switch (this) {
                case HORIZONTAL -> Direction2D.RIGHT;
                case VERTICAL -> Direction2D.DOWN;
            };
        }

        public Direction2D getNegative() {
            return switch (this) {
                case HORIZONTAL -> Direction2D.LEFT;
                case VERTICAL -> Direction2D.UP;
            };
        }

        public Direction2D getDirection(boolean isPositive) {
            return isPositive ? getPositive() : getNegative();
        }
    }

    public enum Direction2D {
        UP,
        DOWN,
        LEFT,
        RIGHT;

        private final IntComparator coordinateValueComparator = (first, second) -> first == second
                ? 0
                : (isBefore(first, second) ? -1 : 1);

        public Axis2D getAxis() {
            return switch (this) {
                case UP, DOWN -> Axis2D.VERTICAL;
                case LEFT, RIGHT -> Axis2D.HORIZONTAL;
            };
        }

        public Direction2D getOpposite() {
            return switch (this) {
                case UP -> DOWN;
                case DOWN -> UP;
                case LEFT -> RIGHT;
                case RIGHT -> LEFT;
            };
        }

        public boolean isPositive() {
            return switch (this) {
                case UP, LEFT -> false;
                case DOWN, RIGHT -> true;
            };
        }

        public boolean isAfter(int first, int second) {
            return isPositive() ? first > second : second > first;
        }

        public boolean isBefore(int first, int second) {
            return isPositive() ? first < second : second < first;
        }

        public IntComparator coordinateValueComparator() {
            return coordinateValueComparator;
        }
    }
}