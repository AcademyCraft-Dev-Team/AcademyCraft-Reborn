package org.academy.api.client.render.vfxgraph.shape;

import java.util.Random;

/** 点发射：恒定原点。 */
public final class PointShape implements EmitterShape {
    private final float x;
    private final float y;
    private final float z;

    public PointShape(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void sample(Random random, float[] out) {
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }
}
