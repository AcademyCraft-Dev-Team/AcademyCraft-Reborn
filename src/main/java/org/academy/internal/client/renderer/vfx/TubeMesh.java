package org.academy.internal.client.renderer.vfx;

import org.academy.api.client.render.vfx.lightning.TubeMeshView;

public interface TubeMesh {
    int VERTEX_STRIDE_BYTES = (3 + 2) * 4;

    /**
     * 当前已发布（对渲染线程可见）的网格快照。
     */
    TubeMeshView mesh();
}
