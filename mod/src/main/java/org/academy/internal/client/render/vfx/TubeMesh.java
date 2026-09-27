package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.lightning.TubeMeshView;

public interface TubeMesh {
    int VERTEX_STRIDE_BYTES = (3 + 2) * 4;

    TubeMeshView mesh();
}
