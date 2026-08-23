package org.academy.api.client.render.vfxgraph.shape;

import java.util.Random;

/** 圆柱侧面采样（沿 Y 轴，半径 + 高度）。 */
public final class CylinderShape implements EmitterShape {
    private final float cx;
    private final float cy;
    private final float cz;
    private final float radius;
    private final float height;

    public CylinderShape(float cx, float cy, float cz, float radius, float height) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
        this.height = height;
    }

    @Override
    public void sample(Random random, float[] out) {
        float theta = random.nextFloat() * 2f * (float) Math.PI;
        out[0] = cx + (float) Math.cos(theta) * radius;
        out[1] = cy + random.nextFloat() * height;
        out[2] = cz + (float) Math.sin(theta) * radius;
    }
}
