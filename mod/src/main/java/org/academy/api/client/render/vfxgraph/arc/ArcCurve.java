package org.academy.api.client.render.vfxgraph.arc;

import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public final class ArcCurve {
    private static final int INITIAL = 16;

    private float[] px = new float[INITIAL];
    private float[] py = new float[INITIAL];
    private float[] pz = new float[INITIAL];
    private float[] width = new float[INITIAL];
    private float[] gen = new float[INITIAL];
    private int[] seg = new int[INITIAL];
    private float[] pa = new float[INITIAL];
    private int size;

    private float[] bx = new float[INITIAL];
    private float[] by = new float[INITIAL];
    private float[] bz = new float[INITIAL];

    private float r, g, b, a;
    private float age;
    private float lifetime;
    private long seed;
    private long replacementGroup;
    private int maxTubeSegments;
    private boolean endCap;
    private String layer = "";

    private float @Nullable [] surface;

    private boolean fresh;

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

    public void clearPoints() {
        size = 0;
    }

    public void resetSimState() {
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
        this.replacementGroup = 0L;
        this.maxTubeSegments = 0;
        this.endCap = false;
        this.layer = "";
        this.archRandom = 1f;
        this.archHeight = 1f;
        this.archHalf = 0.5f;
        this.archSpanScale = 1f;
        this.archCurve = 0.78f;
        this.archWidth = 0.01f;
        this.archSegments = 12;
    }

    public int maxTubeSegments() {
        return maxTubeSegments;
    }

    public boolean endCap() {
        return endCap;
    }

    public String layer() {
        return layer;
    }

    public void setLayer(String name) {
        layer = Objects.requireNonNull(name);
    }

    public void setEndCap(boolean endCap) {
        this.endCap = endCap;
    }

    public void setMaxTubeSegments(int segments) {
        maxTubeSegments = segments <= 0 ? 0 : Math.clamp(segments, 3, 16);
    }

    long replacementGroup() {
        return replacementGroup;
    }

    void setReplacementGroup(long replacementGroup) {
        this.replacementGroup = replacementGroup;
    }

    public void addPoint(float x, float y, float z, float w, float generation) {
        addPoint(x, y, z, w, generation, 0);
    }

    public void addPoint(float x, float y, float z, float w, float generation, int segment) {
        if (size == px.length) {
            var cap = px.length * 2;
            px = Arrays.copyOf(px, cap);
            py = Arrays.copyOf(py, cap);
            pz = Arrays.copyOf(pz, cap);
            width = Arrays.copyOf(width, cap);
            gen = Arrays.copyOf(gen, cap);
            seg = Arrays.copyOf(seg, cap);
            bx = Arrays.copyOf(bx, cap);
            by = Arrays.copyOf(by, cap);
            bz = Arrays.copyOf(bz, cap);
            pa = Arrays.copyOf(pa, cap);
        }
        px[size] = x;
        py[size] = y;
        pz[size] = z;
        bx[size] = x;
        by[size] = y;
        bz[size] = z;
        width[size] = w;
        gen[size] = generation;
        seg[size] = segment;
        pa[size] = 1f;
        size++;
    }

    public int size() {
        return size;
    }

    public float x(int i) {
        return px[i];
    }

    public float y(int i) {
        return py[i];
    }

    public float z(int i) {
        return pz[i];
    }

    public float width(int i) {
        return width[i];
    }

    public float generation(int i) {
        return gen[i];
    }

    public int segment(int i) {
        return seg[i];
    }

    public void setPoint(int i, float x, float y, float z) {
        px[i] = x;
        py[i] = y;
        pz[i] = z;
    }

    public float baseX(int i) {
        return bx[i];
    }

    public float baseY(int i) {
        return by[i];
    }

    public float baseZ(int i) {
        return bz[i];
    }

    public float pa(int i) {
        return pa[i];
    }

    public void setPa(int i, float pa) {
        this.pa[i] = pa;
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

    public float r() {
        return r;
    }

    public float g() {
        return g;
    }

    public float b() {
        return b;
    }

    public float a() {
        return a;
    }

    public void setColor(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public float age() {
        return age;
    }

    public void setAge(float age) {
        this.age = age;
    }

    public float lifetime() {
        return lifetime;
    }

    public void setLifetime(float lifetime) {
        this.lifetime = lifetime;
    }

    public boolean isAlive() {
        return age < lifetime;
    }

    public long seed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
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

    public boolean fresh() {
        return fresh;
    }

    public void setFresh(boolean fresh) {
        this.fresh = fresh;
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
