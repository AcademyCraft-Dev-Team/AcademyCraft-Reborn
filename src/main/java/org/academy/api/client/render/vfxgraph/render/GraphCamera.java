package org.academy.api.client.render.vfxgraph.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 自持渲染相机：位置、纯旋转视图矩阵、投影矩阵（与现有 VFX 相机无关）。
 */
public record GraphCamera(Vector3f position, Matrix4f viewRotation, Matrix4f projection) {
    public GraphCamera(Vector3f position, Matrix4f viewRotation, Matrix4f projection) {
        this.position = new Vector3f(position);
        this.viewRotation = new Matrix4f(viewRotation);
        this.projection = new Matrix4f(projection);
    }

    /**
     * 纯旋转（无平移）视图矩阵，配合实例坐标已减去相机位置的约定。
     */
    @Override
    public Matrix4f viewRotation() {
        return viewRotation;
    }

    public Matrix4f projection() {
        return projection;
    }

    /**
     * 保留当前 FOV、宽高比、近裁剪面与正/反向 Z 约定，只在需要时扩大可见远平面。
     */
    public GraphCamera withMinimumFarPlane(float minimumFarPlane) {
        if (!(minimumFarPlane > 0f) || !Float.isFinite(minimumFarPlane)) {
            return this;
        }
        float firstPlane = projection.perspectiveNear();
        float secondPlane = projection.perspectiveFar();
        if (!(firstPlane > 0f) || !(secondPlane > 0f)
                || !Float.isFinite(firstPlane) || !Float.isFinite(secondPlane)) {
            return this;
        }

        boolean reversedZ = firstPlane > secondPlane;
        float currentFarPlane = reversedZ ? firstPlane : secondPlane;
        if (currentFarPlane >= minimumFarPlane) {
            return this;
        }

        float nearPlane = reversedZ ? secondPlane : firstPlane;
        float fov = projection.perspectiveFov();
        float aspect = Math.abs(projection.m11() / projection.m00());
        if (!(fov > 0f) || !(aspect > 0f) || !Float.isFinite(fov) || !Float.isFinite(aspect)) {
            return this;
        }
        var extendedProjection = reversedZ
                ? new Matrix4f().setPerspective(fov, aspect, minimumFarPlane, nearPlane)
                : new Matrix4f().setPerspective(fov, aspect, nearPlane, minimumFarPlane);
        return new GraphCamera(position, viewRotation, extendedProjection);
    }

    public static GraphCamera perspective(Vector3f position, float fovYRadians, float aspect, float zNear, float zFar) {
        var projection = new Matrix4f().setPerspective(fovYRadians, aspect, zNear, zFar);
        return new GraphCamera(position, new Matrix4f(), projection);
    }

    /**
     * 由游戏相机状态构建（M15-01）：位置 + 纯旋转视图矩阵 + 投影矩阵。
     *
     * @param position     相机世界位置
     * @param viewRotation 纯旋转（无平移）视图矩阵
     * @param projection   投影矩阵
     */
    public static GraphCamera fromGameCamera(Vector3f position, Matrix4f viewRotation, Matrix4f projection) {
        return new GraphCamera(position, new Matrix4f(viewRotation), new Matrix4f(projection));
    }
}
