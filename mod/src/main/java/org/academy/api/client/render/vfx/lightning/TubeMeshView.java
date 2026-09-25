package org.academy.api.client.render.vfx.lightning;

import java.nio.ByteBuffer;

/**
 * 网格的只读快照视图；渲染线程只通过它访问已发布的数据。
 */
public interface TubeMeshView {
    boolean isEmpty();

    int vertexCount();

    int indexCount();

    float[] positions();

    float[] uvs();

    int[] indices();

    /**
     * 几何版本号；渲染器据此跳过无变化的顶点/索引上传。
     */
    long version();

    void packIndices(ByteBuffer buffer);
}
