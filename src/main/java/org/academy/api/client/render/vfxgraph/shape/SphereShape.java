package org.academy.api.client.render.vfxgraph.shape;

import java.util.Random;

/** 球面内均匀采样。 */
public final class SphereShape implements EmitterShape {
    private final float cx;
    private final float cy;
    private final float cz;
    private final float radius;

    public SphereShape(float cx, float cy, float cz, float radius) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
    }

    @Override
    public void sample(Random random, float[] out) {
        float u = random.nextFloat();
        float v = random.nextFloat();
        float w = random.nextFloat();
        out[0] = cx + radius * (u * 2f - 1f);
        out[1] = cy + radius * (v * 2f - 1f);
        out[2] = cz + radius * (w * 2f - 1f);
    }
}
