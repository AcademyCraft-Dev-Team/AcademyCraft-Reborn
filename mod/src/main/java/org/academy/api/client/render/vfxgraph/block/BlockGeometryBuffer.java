package org.academy.api.client.render.vfxgraph.block;

import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

public final class BlockGeometryBuffer {
    private @Nullable ByteBuffer @Nullable [] layerVertices;
    private int @Nullable [] layerVertexCounts;

    public void setFrame(@Nullable BlockGeometryFrame frame) {
        if (frame == null) {
            layerVertices = null;
            layerVertexCounts = null;
            return;
        }
        layerVertices = frame.layerVertices();
        layerVertexCounts = frame.layerVertexCounts();
    }

    public @Nullable ByteBuffer @Nullable [] layerVertices() {
        return layerVertices;
    }

    public int @Nullable [] layerVertexCounts() {
        return layerVertexCounts;
    }

    public boolean isEmpty() {
        return layerVertexCounts == null || vertexCount() == 0;
    }

    public int vertexCount() {
        var counts = layerVertexCounts;
        if (counts == null) return 0;
        var count = 0;
        for (var layerCount : counts) {
            count += layerCount;
        }
        return count;
    }
}
