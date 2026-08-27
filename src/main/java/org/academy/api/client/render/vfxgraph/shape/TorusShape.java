package org.academy.api.client.render.vfxgraph.shape;

import java.util.Random;

/**
 * 圆环（torus）表面采样（XZ 平面，主半径 + 次半径）。
 */
public final class TorusShape implements EmitterShape {
    private final float cx;
    private final float cy;
    private final float cz;
    private final float majorRadius;
    private final float minorRadius;

    public TorusShape(float cx, float cy, float cz, float majorRadius, float minorRadius) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.majorRadius = majorRadius;
        this.minorRadius = minorRadius;
    }

    @Override
    public void sample(Random random, float[] out) {
        var u = random.nextFloat() * 2f * (float) Math.PI;
        var v = random.nextFloat() * 2f * (float) Math.PI;
        var r = majorRadius + minorRadius * (float) Math.cos(v);
        out[0] = cx + r * (float) Math.cos(u);
        out[1] = cy + minorRadius * (float) Math.sin(v);
        out[2] = cz + r * (float) Math.sin(u);
    }
}
