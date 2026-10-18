package org.academy.api.client.render.vfxgraph.instance;

import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

public record InstanceGeometryFrame(@Nullable ByteBuffer vertices, int vertexCount,
                                    @Nullable ByteBuffer instances, int instanceCount) {
    public InstanceGeometryFrame {
        if (vertexCount < 0 || instanceCount < 0) {
            throw new IllegalArgumentException("Negative instance geometry counts");
        }
        if (vertexCount > 0 && vertices == null) {
            throw new IllegalArgumentException("Instance geometry vertices required");
        }
        if (instanceCount > 0 && instances == null) {
            throw new IllegalArgumentException("Instance geometry instances required");
        }
    }

    public boolean isEmpty() {
        return vertexCount == 0 || instanceCount == 0;
    }
}
