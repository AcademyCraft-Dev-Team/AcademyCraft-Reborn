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
