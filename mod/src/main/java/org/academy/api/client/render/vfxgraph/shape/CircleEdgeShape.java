package org.academy.api.client.render.vfxgraph.shape;

import java.util.Random;

/**
 * 圆环线（circle edge）采样（XZ 平面，半径圆环）。
 */
public final class CircleEdgeShape implements EmitterShape {
    private final float cx;
    private final float cy;
    private final float cz;
    private final float radius;

    public CircleEdgeShape(float cx, float cy, float cz, float radius) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
    }

    @Override
    public void sample(Random random, float[] out) {
        var theta = random.nextFloat() * 2f * (float) Math.PI;
        out[0] = cx + (float) Math.cos(theta) * radius;
        out[1] = cy;
        out[2] = cz + (float) Math.sin(theta) * radius;
    }
}
