package org.academy.api.client.render.vfxgraph.sim;

import java.util.Arrays;

/**
 * SoA（结构体数组）粒子缓冲。并行 float 数组，swap-remove 删除，容量翻倍增长。
 *
 * <p>字段：position(x,y,z)、velocity(x,y,z)、color(r,g,b)、alpha/startAlpha、
 * size/startSize、age、lifetime、rotation、mass、seed（每粒子稳定随机种子）、
 * layer（0=fire additive，1=smoke alpha，供渲染按层拆分），以及每粒子环形 trail 历史
 * （TRAIL_LENGTH 个最近位置，M13-07）。</p>
 */
public final class ParticleBuffer {
    private static final int INITIAL_CAPACITY = 64;

    /**
     * 每粒子 trail 历史长度。
     */
    public static final int TRAIL_LENGTH = 8;

    /**
     * 递增种子计数器：swap-remove 与扩容均不改变 seed（着色器需要每粒子稳定的随机特征）。
     */
    private long nextSeed = 1L;

    private float[] px;
    private float[] seed;
    private byte[] layer;
    private float[] py;
    private float[] pz;
    private float[] vx;
    private float[] vy;
    private float[] vz;
    private float[] cr;
    private float[] cg;
    private float[] cb;
    private float[] alpha;
    private float[] startAlpha;
    private float[] size;
    private float[] startSize;
    private float[] age;
    private float[] lifetime;
    private float[] rotation;
    private float[] mass;
    private float[] trailX;
    private float[] trailY;
    private float[] trailZ;
    private int[] trailSize;
    private int count;

    public ParticleBuffer() {
        this(INITIAL_CAPACITY);
    }

    public ParticleBuffer(int initialCapacity) {
        px = new float[initialCapacity];
        seed = new float[initialCapacity];
        layer = new byte[initialCapacity];
        py = new float[initialCapacity];
        pz = new float[initialCapacity];
        vx = new float[initialCapacity];
        vy = new float[initialCapacity];
        vz = new float[initialCapacity];
        cr = new float[initialCapacity];
        cg = new float[initialCapacity];
        cb = new float[initialCapacity];
        alpha = new float[initialCapacity];
        startAlpha = new float[initialCapacity];
        size = new float[initialCapacity];
        startSize = new float[initialCapacity];
        age = new float[initialCapacity];
        lifetime = new float[initialCapacity];
        rotation = new float[initialCapacity];
        mass = new float[initialCapacity];
        trailX = new float[initialCapacity * TRAIL_LENGTH];
        trailY = new float[initialCapacity * TRAIL_LENGTH];
        trailZ = new float[initialCapacity * TRAIL_LENGTH];
        trailSize = new int[initialCapacity];
    }

    public int count() {
        return count;
    }

    public int capacity() {
        return px.length;
    }

    /**
     * 分配一个新粒子（必要时扩容），返回其索引。
     */
    public int spawn() {
        if (count == px.length) {
            grow();
        }
        // Bug 修复：重置关键字段，防止 swap-remove 槽位复用时的残留数据泄漏
        int i = count;
        lifetime[i] = 0f;
        age[i] = 0f;
        seed[i] = (float) (nextSeed++);
        layer[i] = 0;
        size[i] = 0f;
        startSize[i] = 0f;
        alpha[i] = 0f;
        startAlpha[i] = 0f;
        rotation[i] = 0f;
        mass[i] = 0f;
        trailSize[i] = 0;
        return count++;
    }

    /**
     * swap-remove 删除索引 i 的粒子（末位粒子移入该槽）。
     */
    public void kill(int i) {
        int last = --count;
        if (i != last) {
            px[i] = px[last];
            seed[i] = seed[last];
            layer[i] = layer[last];
            py[i] = py[last];
            pz[i] = pz[last];
            vx[i] = vx[last];
            vy[i] = vy[last];
            vz[i] = vz[last];
            cr[i] = cr[last];
            cg[i] = cg[last];
            cb[i] = cb[last];
            alpha[i] = alpha[last];
            startAlpha[i] = startAlpha[last];
            size[i] = size[last];
            startSize[i] = startSize[last];
            age[i] = age[last];
            lifetime[i] = lifetime[last];
            rotation[i] = rotation[last];
            mass[i] = mass[last];
            System.arraycopy(trailX, last * TRAIL_LENGTH, trailX, i * TRAIL_LENGTH, TRAIL_LENGTH);
            System.arraycopy(trailY, last * TRAIL_LENGTH, trailY, i * TRAIL_LENGTH, TRAIL_LENGTH);
            System.arraycopy(trailZ, last * TRAIL_LENGTH, trailZ, i * TRAIL_LENGTH, TRAIL_LENGTH);
            trailSize[i] = trailSize[last];
        }
    }

    private void grow() {
        int newCap = px.length * 2;
        px = Arrays.copyOf(px, newCap);
        seed = Arrays.copyOf(seed, newCap);
        layer = Arrays.copyOf(layer, newCap);
        py = Arrays.copyOf(py, newCap);
        pz = Arrays.copyOf(pz, newCap);
        vx = Arrays.copyOf(vx, newCap);
        vy = Arrays.copyOf(vy, newCap);
        vz = Arrays.copyOf(vz, newCap);
        cr = Arrays.copyOf(cr, newCap);
        cg = Arrays.copyOf(cg, newCap);
        cb = Arrays.copyOf(cb, newCap);
        alpha = Arrays.copyOf(alpha, newCap);
        startAlpha = Arrays.copyOf(startAlpha, newCap);
        size = Arrays.copyOf(size, newCap);
        startSize = Arrays.copyOf(startSize, newCap);
        age = Arrays.copyOf(age, newCap);
        lifetime = Arrays.copyOf(lifetime, newCap);
        rotation = Arrays.copyOf(rotation, newCap);
        mass = Arrays.copyOf(mass, newCap);
        trailX = Arrays.copyOf(trailX, newCap * TRAIL_LENGTH);
        trailY = Arrays.copyOf(trailY, newCap * TRAIL_LENGTH);
        trailZ = Arrays.copyOf(trailZ, newCap * TRAIL_LENGTH);
        trailSize = Arrays.copyOf(trailSize, newCap);
    }

    // ---- position ----

    public float positionX(int i) {
        return px[i];
    }

    public float positionY(int i) {
        return py[i];
    }

    public float positionZ(int i) {
        return pz[i];
    }

    public void setPosition(int i, float x, float y, float z) {
        px[i] = x;
        py[i] = y;
        pz[i] = z;
    }

    // ---- seed（每粒子稳定随机种子，M21 火焰） ----

    public float seed(int i) {
        return seed[i];
    }

    // ---- layer（0=fire additive，1=smoke alpha，M21 引擎式分层） ----

    public byte layer(int i) {
        return layer[i];
    }

    public void setLayer(int i, byte layer) {
        this.layer[i] = layer;
    }

    /**
     * 粒子层编码（单一映射源，spawn/over-life 过滤/渲染层过滤共用）：{@code "smoke"} → 1，其余 → 0。
     */
    public static byte layerByte(String layer) {
        return "smoke".equals(layer) ? (byte) 1 : (byte) 0;
    }

    /**
     * 层过滤编码：{@code ""}（或 null）→ -1（全部），否则按 {@link #layerByte}。
     */
    public static byte layerFilter(String layer) {
        return layer == null || layer.isEmpty() ? -1 : layerByte(layer);
    }

    // ---- velocity ----

    public float velocityX(int i) {
        return vx[i];
    }

    public float velocityY(int i) {
        return vy[i];
    }

    public float velocityZ(int i) {
        return vz[i];
    }

    public void setVelocity(int i, float x, float y, float z) {
        vx[i] = x;
        vy[i] = y;
        vz[i] = z;
    }

    // ---- color ----

    public float colorR(int i) {
        return cr[i];
    }

    public float colorG(int i) {
        return cg[i];
    }

    public float colorB(int i) {
        return cb[i];
    }

    public float alpha(int i) {
        return alpha[i];
    }

    public float startAlpha(int i) {
        return startAlpha[i];
    }

    public void setColor(int i, float r, float g, float b, float a) {
        cr[i] = r;
        cg[i] = g;
        cb[i] = b;
        alpha[i] = a;
        startAlpha[i] = a;
    }

    /**
     * 仅写 RGB 通道（不动 alpha/startAlpha）：供 life_color 逐帧改色而不破坏起始 alpha 语义。
     */
    public void setColorRgb(int i, float r, float g, float b) {
        cr[i] = r;
        cg[i] = g;
        cb[i] = b;
    }

    public void setAlpha(int i, float a) {
        alpha[i] = a;
    }

    // ---- size ----

    public float size(int i) {
        return size[i];
    }

    public float startSize(int i) {
        return startSize[i];
    }

    public void setSize(int i, float s) {
        size[i] = s;
        startSize[i] = s;
    }

    public void setSizeScaled(int i, float s) {
        size[i] = s;
    }

    // ---- life ----

    public float age(int i) {
        return age[i];
    }

    public void setAge(int i, float a) {
        age[i] = a;
    }

    public float lifetime(int i) {
        return lifetime[i];
    }

    public void setLifetime(int i, float l) {
        lifetime[i] = l;
    }

    // ---- rotation（M13 orient）----

    public float rotation(int i) {
        return rotation[i];
    }

    public void setRotation(int i, float r) {
        rotation[i] = r;
    }

    // ---- mass（M13 drag）----

    public float mass(int i) {
        return mass[i];
    }

    public void setMass(int i, float m) {
        mass[i] = m;
    }

    // ---- trail 历史（M13-07 line/ribbon）----

    public int trailSize(int i) {
        return trailSize[i];
    }

    public float trailX(int i, int k) {
        return trailX[i * TRAIL_LENGTH + k];
    }

    public float trailY(int i, int k) {
        return trailY[i * TRAIL_LENGTH + k];
    }

    public float trailZ(int i, int k) {
        return trailZ[i * TRAIL_LENGTH + k];
    }

    /**
     * 把当前位置压入 trail（新样本在最前，k=0 最新）。
     */
    public void pushTrail(int i, float x, float y, float z) {
        int size = Math.min(trailSize[i], TRAIL_LENGTH - 1);
        int base = i * TRAIL_LENGTH;
        System.arraycopy(trailX, base, trailX, base + 1, size);
        System.arraycopy(trailY, base, trailY, base + 1, size);
        System.arraycopy(trailZ, base, trailZ, base + 1, size);
        trailX[base] = x;
        trailY[base] = y;
        trailZ[base] = z;
        trailSize[i] = size + 1;
    }
}
