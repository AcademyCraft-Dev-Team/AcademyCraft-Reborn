package org.academy.api.client.render.vfxgraph.shape;

import java.util.Random;

/**
 * 盒内均匀采样（半边长 hx/hy/hz）。
 */
public final class BoxShape implements EmitterShape {
    private final float cx;
    private final float cy;
    private final float cz;
    private final float hx;
    private final float hy;
    private final float hz;

    public BoxShape(float cx, float cy, float cz, float hx, float hy, float hz) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.hx = hx;
        this.hy = hy;
        this.hz = hz;
    }

    @Override
    public void sample(Random random, float[] out) {
        out[0] = cx + hx * (random.nextFloat() * 2f - 1f);
        out[1] = cy + hy * (random.nextFloat() * 2f - 1f);
        out[2] = cz + hz * (random.nextFloat() * 2f - 1f);
    }
}
