package org.academy.api.client.render.vfxgraph.runtime;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 效果预算（M15-06）：粒子上限 + 距离/视锥剔除。纯函数，可单测。
 *
 * <p>视锥剔除（Bug 修复）：视图矩阵为「纯旋转 + 相机平移」，合成完整 view·projection 后
 * 交给 JOML {@link FrustumIntersection}（正确处理矩阵约定），世界坐标效果位置直接测球。
 * 之前手工 Gribb–Hartmann 平面提取在非原点相机下坐标系错误（世界坐标代入纯旋转视图平面）。</p>
 */
public final class EffectBudget {
    private int maxParticlesPerEffect = 10000;
    private float maxRenderDistance = 96f;
    private boolean frustumCullingEnabled = true;
    private float effectRadius = 8f;
    private final FrustumIntersection frustum = new FrustumIntersection();

    public int maxParticlesPerEffect() {
        return maxParticlesPerEffect;
    }

    public void setMaxParticlesPerEffect(int maxParticlesPerEffect) {
        this.maxParticlesPerEffect = Math.max(1, maxParticlesPerEffect);
    }

    public float maxRenderDistance() {
        return maxRenderDistance;
    }

    public void setMaxRenderDistance(float maxRenderDistance) {
        this.maxRenderDistance = maxRenderDistance;
    }

    public boolean frustumCullingEnabled() {
        return frustumCullingEnabled;
    }

    public void setFrustumCullingEnabled(boolean frustumCullingEnabled) {
        this.frustumCullingEnabled = frustumCullingEnabled;
    }

    public float effectRadius() {
        return effectRadius;
    }

    public void setEffectRadius(float effectRadius) {
        this.effectRadius = effectRadius;
    }

    /**
     * 粒子数达到上限时不再 tick 产粒（保留已有粒子渲染）。
     */
    public boolean canSpawnMore(int particleCount) {
        return particleCount < maxParticlesPerEffect;
    }

    /**
     * 发射器原点与相机距离超上限则跳过渲染。
     */
    public boolean shouldRender(Vector3f cameraPos, Vector3f effectPos) {
        var dx = effectPos.x - cameraPos.x;
        var dy = effectPos.y - cameraPos.y;
        var dz = effectPos.z - cameraPos.z;
        var dist2 = dx * dx + dy * dy + dz * dz;
        return dist2 <= maxRenderDistance * maxRenderDistance;
    }

    /**
     * 包围球（世界坐标 effectPos、半径 effectRadius）是否进入视锥。
     *
     * @param projection   投影矩阵
     * @param viewRotation 纯旋转视图矩阵（无平移）
     * @param cameraPos    相机世界位置（合成视图平移）
     */
    public boolean sphereInFrustum(Matrix4f projection, Matrix4f viewRotation, Vector3f cameraPos, Vector3f effectPos) {
        if (!frustumCullingEnabled) {
            return true;
        }
        // view = R·T(-cam)：纯旋转 + 相机平移 → 完整视图矩阵；vp = projection·view。
        // 必须用 translate（后乘平移矩阵，平移列 = R·(-camPos)）；setTranslation 直接覆盖平移列（漏掉 R 旋转），
        // 相机远离原点时视锥坐标系错误。
        var view = new Matrix4f(viewRotation).translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        var viewProjection = new Matrix4f(projection).mul(view);
        frustum.set(viewProjection);
        return frustum.testSphere(effectPos.x, effectPos.y, effectPos.z, effectRadius);
    }
}
