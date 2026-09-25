package org.academy.api.client.render.vfxgraph.shape;

import java.util.Random;

/**
 * 平面圆盘采样（XZ 平面，y 固定，半径内均匀分布）。火焰/篝火基底发射器。
 */
public final class DiscShape implements EmitterShape {
    private final float cx;
    private final float cy;
    private final float cz;
    private final float radius;

    public DiscShape(float cx, float cy, float cz, float radius) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
    }

    @Override
    public void sample(Random random, float[] out) {
        var angle = random.nextFloat() * 2f * (float) Math.PI;
        var r = (float) Math.sqrt(random.nextFloat()) * radius;
        out[0] = cx + (float) Math.cos(angle) * r;
        out[1] = cy;
        out[2] = cz + (float) Math.sin(angle) * r;
    }
}
