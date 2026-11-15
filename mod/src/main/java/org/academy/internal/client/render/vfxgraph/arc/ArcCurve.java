package org.academy.internal.client.render.vfxgraph.arc;

import org.academy.api.client.render.vfxgraph.curve.SurfaceDistributor;
import org.academy.api.client.render.vfxgraph.curve.TubeCurve;
import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class ArcCurve extends TubeCurve {
    private float @Nullable [] surface;

    private float noiseStrength = Float.NaN;
    private float driftSpeed = Float.NaN;

    private float wanderX, wanderY, wanderZ;

    private float archX, archY, archZ;
    private float archNx, archNy, archNz;
    private float archDx, archDy, archDz;
    private float archRandom = 1f;
    private float archHeight = 1f;
    private float archHalf = 0.5f;
    private float archSpanScale = 1f;
    private float archCurve = 0.78f;
    private float archWidth = 0.01f;
    private int archSegments = 12;
    private boolean hasArch;
    private boolean pinStart;
    private boolean flatRadius;
    private float @Nullable [] sparkVelocity;

    private @Nullable SurfaceSampler anchorSampler;
    private int anchorIndex;
    private float anchorU;
    private float anchorV;
    private float anchorWanderU;
    private float anchorWanderV;

    public ArcCurve() {
    }

    @Override
    public void resetSimState() {
        super.resetSimState();
        this.wanderX = 0f;
        this.wanderY = 0f;
        this.wanderZ = 0f;
        this.pinStart = false;
        this.flatRadius = false;
        this.hasArch = false;
        this.sparkVelocity = null;
        this.surface = null;
        this.anchorSampler = null;
        this.anchorIndex = 0;
        this.anchorU = 0f;
        this.anchorV = 0f;
        this.anchorWanderU = 0f;
        this.anchorWanderV = 0f;
        this.noiseStrength = Float.NaN;
        this.driftSpeed = Float.NaN;
        this.archRandom = 1f;
        this.archHeight = 1f;
        this.archHalf = 0.5f;
        this.archSpanScale = 1f;
        this.archCurve = 0.78f;
        this.archWidth = 0.01f;
        this.archSegments = 12;
    }

    public float noiseStrength() {
        return noiseStrength;
    }

    public boolean hasNoiseStrength() {
        return !Float.isNaN(noiseStrength);
    }

    public void setNoiseStrength(float noiseStrength) {
        this.noiseStrength = noiseStrength;
    }

    public float driftSpeed() {
        return driftSpeed;
    }

    public boolean hasDriftSpeed() {
        return !Float.isNaN(driftSpeed);
    }

    public void setDriftSpeed(float driftSpeed) {
        this.driftSpeed = driftSpeed;
    }

    public float wanderX() {
        return wanderX;
    }

    public float wanderY() {
        return wanderY;
    }

    public float wanderZ() {
        return wanderZ;
    }

    public void accumulateWander(float dx, float dy, float dz) {
        this.wanderX += dx;
        this.wanderY += dy;
        this.wanderZ += dz;
    }

    public void setSurface(float @Nullable [] triangles) {
        this.surface = triangles;
    }

    public float @Nullable [] surface() {
        return surface;
    }

    public boolean hasSurface() {
        return surface != null && surface.length > 0;
    }

    public void setAnchor(SurfaceSampler sampler, int index, float u, float v) {
        this.anchorSampler = Objects.requireNonNull(sampler);
        this.anchorIndex = index;
        this.anchorU = u;
        this.anchorV = v;
        this.anchorWanderU = 0f;
        this.anchorWanderV = 0f;
    }

    public boolean hasAnchor() {
        return anchorSampler != null;
    }

    public @Nullable SurfaceSampler anchorSampler() {
        return anchorSampler;
    }

    public int anchorIndex() {
        return anchorIndex;
    }

    public float anchorU() {
        return anchorU;
    }

    public float anchorV() {
        return anchorV;
    }

    public void accumulateAnchorWander(float du, float dv) {
        this.anchorWanderU += du;
        this.anchorWanderV += dv;
    }

    public float anchorWanderU() {
        return anchorWanderU;
    }

    public float anchorWanderV() {
        return anchorWanderV;
    }

    public void reanchorSurface(float x, float y, float z, float nx, float ny, float nz) {
        this.archX = x;
        this.archY = y;
        this.archZ = z;
        this.archNx = nx;
        this.archNy = ny;
        this.archNz = nz;
        var dot = archDx * nx + archDy * ny + archDz * nz;
        var dx = archDx - nx * dot;
        var dy = archDy - ny * dot;
        var dz = archDz - nz * dot;
        var length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6f) {
            var perpendicular = SurfaceDistributor.perpendicular(nx, ny, nz);
            dx = perpendicular[0];
            dy = perpendicular[1];
            dz = perpendicular[2];
        } else {
            dx /= length;
            dy /= length;
            dz /= length;
        }
        this.archDx = dx;
        this.archDy = dy;
        this.archDz = dz;
        this.hasArch = true;
    }

    public void setArchBase(float x, float y, float z,
                            float nx, float ny, float nz,
                            float dx, float dy, float dz,
                            float randomScale, float height) {
        this.archX = x;
        this.archY = y;
        this.archZ = z;
        this.archNx = nx;
        this.archNy = ny;
        this.archNz = nz;
        this.archDx = dx;
        this.archDy = dy;
        this.archDz = dz;
        this.archRandom = randomScale;
        this.archHeight = Math.max(1e-3f, height);
        this.archHalf = this.archHeight * 0.5f;
        this.hasArch = true;
    }

    public void setArchResample(float curve, float width, int segments) {
        this.archCurve = curve;
        this.archWidth = width;
        this.archSegments = Math.max(3, segments);
    }

    public float archSpanScale() {
        return archSpanScale;
    }

    public void setArchSpanScale(float scale) {
        this.archSpanScale = scale;
    }

    public boolean pinStart() {
        return pinStart;
    }

    public void setPinStart(boolean pinStart) {
        this.pinStart = pinStart;
    }

    public boolean flatRadius() {
        return flatRadius;
    }

    public void setFlatRadius(boolean flatRadius) {
        this.flatRadius = flatRadius;
    }

    public float @Nullable [] sparkVelocity() {
        return sparkVelocity;
    }

    public void setSparkVelocity(float vx, float vy, float vz) {
        this.sparkVelocity = new float[]{vx, vy, vz};
    }

    public boolean hasArchBase() {
        return hasArch;
    }

    public void clearArchBase() {
        this.hasArch = false;
    }

    public float archX() {
        return archX;
    }

    public float archY() {
        return archY;
    }

    public float archZ() {
        return archZ;
    }

    public float archNx() {
        return archNx;
    }

    public float archNy() {
        return archNy;
    }

    public float archNz() {
        return archNz;
    }

    public float archDx() {
        return archDx;
    }

    public float archDy() {
        return archDy;
    }

    public float archDz() {
        return archDz;
    }

    public float archRandom() {
        return archRandom;
    }

    public float archHeight() {
        return archHeight;
    }

    public float archHalf() {
        return archHalf;
    }

    public float archCurve() {
        return archCurve;
    }

    public float archWidth() {
        return archWidth;
    }

    public int archSegments() {
        return archSegments;
    }
}
