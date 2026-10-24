package org.academy.api.client.render.vfxgraph.render;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * 世界变换（M15-03）：把发射器局部坐标映射到世界坐标。
 *
 * <p>{@code world = position + rotation * (scale * local)}。纯数学，供渲染器在写实例/轨迹顶点时
 * 逐粒子应用，亦供运行时效果实例持有。恒等变换由 [GraphEffect] 编辑器路径保持零改动。</p>
 *
 * <p>{@code ignoreSceneDepth}：跳过 soft-particle 场景深度采样（用远平面兜底）。用于尾焰撞上
 * waterlogged 水面之类会把 depthDiff 打成负值、整柱 alpha 灭掉的场合。</p>
 */
public record WorldTransform(Vector3f position, Quaternionf rotation, float scale, boolean ignoreSceneDepth) {
    private static final WorldTransform IDENTITY = new WorldTransform(new Vector3f(), new Quaternionf(), 1f, false);

    public WorldTransform {
        position = new Vector3f(position);
        rotation = new Quaternionf(rotation);
    }

    public WorldTransform(Vector3f position, Quaternionf rotation, float scale) {
        this(position, rotation, scale, false);
    }

    public static WorldTransform identity() {
        return IDENTITY;
    }

    public boolean isIdentity() {
        return scale == 1f && position.lengthSquared() == 0f && rotation.equals(IDENTITY.rotation, 0f);
    }

    /**
     * 应用变换到局部坐标，写入 out[0..2]（world 坐标）。
     */
    public void apply(float x, float y, float z, float[] out) {
        var sx = x * scale;
        var sy = y * scale;
        var sz = z * scale;
        var qx = rotation.x;
        var qy = rotation.y;
        var qz = rotation.z;
        var qw = rotation.w;
        var tx = 2f * (qy * sz - qz * sy);
        var ty = 2f * (qz * sx - qx * sz);
        var tz = 2f * (qx * sy - qy * sx);
        out[0] = sx + qw * tx + (qy * tz - qz * ty) + position.x;
        out[1] = sy + qw * ty + (qz * tx - qx * tz) + position.y;
        out[2] = sz + qw * tz + (qx * ty - qy * tx) + position.z;
    }

    /**
     * 变换方向向量（旋转 + 缩放，无平移），写入 out[0..2]（world 方向）。
     */
    public void applyDirection(float x, float y, float z, float[] out) {
        var sx = x * scale;
        var sy = y * scale;
        var sz = z * scale;
        var qx = rotation.x;
        var qy = rotation.y;
        var qz = rotation.z;
        var qw = rotation.w;
        var tx = 2f * (qy * sz - qz * sy);
        var ty = 2f * (qz * sx - qx * sz);
        var tz = 2f * (qx * sy - qy * sx);
        out[0] = sx + qw * tx + (qy * tz - qz * ty);
        out[1] = sy + qw * ty + (qz * tx - qx * tz);
        out[2] = sz + qw * tz + (qx * ty - qy * tx);
    }

    /** Maps a local allowed half-space to normalized camera-relative world coordinates. */
    public void applyClipPlane(float x, float y, float z, float offset, Vector3fc camera, float[] out) {
        float localLength = (float) Math.sqrt(x * x + y * y + z * z);
        if (!(localLength > 0.000001f) || Math.abs(scale) < 0.000001f) {
            out[0] = 0; out[1] = 0; out[2] = 0; out[3] = 1;
            return;
        }
        applyDirection(x, y, z, out);
        float worldLength = (float) Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
        out[0] /= worldLength; out[1] /= worldLength; out[2] /= worldLength;
        out[3] = offset * Math.abs(scale) / localLength
                + out[0] * (camera.x() - position.x) + out[1] * (camera.y() - position.y)
                + out[2] * (camera.z() - position.z);
    }
}
