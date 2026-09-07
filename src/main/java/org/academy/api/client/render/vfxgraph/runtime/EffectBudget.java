package org.academy.api.client.render.vfxgraph.runtime;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Particle limits and conservative camera-relative visibility without per-effect matrix allocation. */
public final class EffectBudget {
    private int maxParticlesPerEffect = 10000;
    private float maxRenderDistance = 96f;
    private boolean frustumCullingEnabled = true;
    private float effectRadius = 8f;
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f projectionCache = new Matrix4f();
    private final Matrix4f viewCache = new Matrix4f();
    private final Matrix4f combined = new Matrix4f();
    private boolean prepared;

    public int maxParticlesPerEffect() { return maxParticlesPerEffect; }
    public void setMaxParticlesPerEffect(int value) { maxParticlesPerEffect = Math.max(1, value); }
    public float maxRenderDistance() { return maxRenderDistance; }
    public void setMaxRenderDistance(float value) { maxRenderDistance = Math.max(0f, value); }
    public boolean frustumCullingEnabled() { return frustumCullingEnabled; }
    public void setFrustumCullingEnabled(boolean value) { frustumCullingEnabled = value; }
    public float effectRadius() { return effectRadius; }
    public void setEffectRadius(float value) { effectRadius = Math.max(0f, value); }
    public boolean canSpawnMore(int count) { return count < maxParticlesPerEffect; }

    public boolean shouldRender(Vector3f camera, Vector3f center) {
        return shouldRender(camera, center, 0f, maxRenderDistance);
    }

    /** Distance is measured to the volume, not just its emitter/center. */
    public boolean shouldRender(Vector3f camera, Vector3f center, float radius, float distance) {
        double dx = (double) center.x - camera.x, dy = (double) center.y - camera.y, dz = (double) center.z - camera.z;
        double range = Math.max(0, distance) + Math.max(0, radius);
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    public boolean sphereInFrustum(Matrix4f projection, Matrix4f view, Vector3f camera, Vector3f center) {
        return sphereInFrustum(projection, view, camera, center, effectRadius);
    }

    public boolean sphereInFrustum(Matrix4f projection, Matrix4f view, Vector3f camera, Vector3f center, float radius) {
        if (!frustumCullingEnabled) return true;
        if (!prepared || !projectionCache.equals(projection) || !viewCache.equals(view)) {
            projectionCache.set(projection);
            viewCache.set(view);
            frustum.set(combined.set(projection).mul(view));
            prepared = true;
        }
        return frustum.testSphere(center.x - camera.x, center.y - camera.y, center.z - camera.z, radius);
    }
}
