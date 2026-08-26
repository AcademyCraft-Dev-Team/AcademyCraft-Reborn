package org.academy.api.client.render.vfxgraph.arc;

import java.util.Random;

/**
 * 电弧模拟器（M22-Rev2）：复刻 Blender Simulation Zone。
 *
 * <p>每帧 tick 驱动：噪声动画 + 表面约束 + age 递增 + 过期删除。
 * Blender 对应：Simulation Input → Set Position(Noise) → Sample Nearest Surface
 * → Store Named Attribute(age) → Simulation Output。</p>
 */
public final class ArcSimulator {
    private final ArcBuffer buffer;
    private final SurfaceConstraint constraint;
    private float time;

    public ArcSimulator(ArcBuffer buffer, SurfaceDistributor distributor) {
        this.buffer = buffer;
        this.constraint = new SurfaceConstraint(distributor);
    }

    /**
     * 每帧推进模拟。
     *
     * @param dt            帧间隔（秒）
     * @param driftSpeed    噪声动画速度
     * @param noiseStrength 噪声振幅
     * @param noiseScale    噪声频率（默认 2.0）
     * @param noiseSeed     噪声种子
     */
    public void tick(float dt, float driftSpeed, float noiseStrength,
                     float noiseScale, long noiseSeed) {
        time += dt;

        // 1. 噪声位移（Blender: Noise Texture + Set Position Offset）
        for (int i = 0; i < buffer.count(); i++) {
            NoiseAnimator.animate(buffer.arc(i), time, driftSpeed, noiseStrength, noiseScale, noiseSeed);
        }

        // 2. 表面约束（Blender: Sample Nearest Surface → Set Position）
        for (int i = 0; i < buffer.count(); i++) {
            constraint.constrain(buffer.arc(i));
        }

        // 3. 老化 + 过期删除（Blender: Store Named Attribute(age) + Delete Geometry）
        buffer.advance(dt, new Random(noiseSeed));
    }

    /**
     * 获取当前模拟时间。
     */
    public float time() {
        return time;
    }

    /**
     * 获取弧线缓冲区。
     */
    public ArcBuffer buffer() {
        return buffer;
    }
}
