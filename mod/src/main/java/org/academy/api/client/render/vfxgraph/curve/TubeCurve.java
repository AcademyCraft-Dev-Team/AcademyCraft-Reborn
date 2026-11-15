package org.academy.api.client.render.vfxgraph.curve;

import java.util.Arrays;
import java.util.Objects;

public class TubeCurve {
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
    private boolean fresh;

    public TubeCurve() {
    }

    public void clearPoints() {
        size = 0;
    }

    public void resetSimState() {
        this.replacementGroup = 0L;
        this.maxTubeSegments = 0;
        this.endCap = false;
        this.layer = "";
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

    public boolean fresh() {
        return fresh;
    }

    public void setFresh(boolean fresh) {
        this.fresh = fresh;
    }
}
