package org.academy.api.client.render.vfxgraph.shape;

import java.util.Random;

/**
 * 发射器形状（契约）。把随机采样写入 {@code out[0..2]}（世界坐标）。
 */
public interface EmitterShape {
    void sample(Random random, float[] out);
}
