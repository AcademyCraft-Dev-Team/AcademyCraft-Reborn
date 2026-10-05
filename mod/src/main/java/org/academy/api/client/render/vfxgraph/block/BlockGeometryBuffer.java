package org.academy.api.client.render.vfxgraph.block;

import java.nio.ByteBuffer;
import org.jspecify.annotations.Nullable;

public final class BlockGeometryBuffer {
    private @Nullable ByteBuffer[] layerVertices;
    private @Nullable int[] layerVertexCounts;

    public void setFrame(@Nullable BlockGeometryFrame frame) {
        if (frame == null) {
            layerVertices = null;
            layerVertexCounts = null;
            return;
        }
        layerVertices = frame.layerVertices();
        layerVertexCounts = frame.layerVertexCounts();
    }

    public @Nullable ByteBuffer[] layerVertices() {
        return layerVertices;
    }

    public @Nullable int[] layerVertexCounts() {
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
