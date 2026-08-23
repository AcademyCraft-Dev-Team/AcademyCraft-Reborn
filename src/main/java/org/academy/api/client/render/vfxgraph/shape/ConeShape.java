package org.academy.api.client.render.vfxgraph.shape;

import java.util.Random;

/** 圆锥内均匀采样（底面半径随高度线性缩放，顶点朝上）。 */
public final class ConeShape implements EmitterShape {
    private final float cx;
    private final float cy;
    private final float cz;
    private final float radius;
    private final float height;

    public ConeShape(float cx, float cy, float cz, float radius, float height) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
        this.height = height;
    }

    @Override
    public void sample(Random random, float[] out) {
        float t = random.nextFloat();
        float r = radius * (1f - t);
        float angle = random.nextFloat() * (float) (2.0 * Math.PI);
        float rr = (float) Math.sqrt(random.nextFloat()) * r;
        out[0] = cx + (float) Math.cos(angle) * rr;
        out[1] = cy + t * height;
        out[2] = cz + (float) Math.sin(angle) * rr;
    }
}
