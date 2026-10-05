package org.academy.api.client.render.vfxgraph.block;

import java.nio.ByteBuffer;

public record BlockGeometryFrame(ByteBuffer[] layerVertices, int[] layerVertexCounts) {
    public BlockGeometryFrame {
        if (layerVertices.length != layerVertexCounts.length) {
            throw new IllegalArgumentException("Block geometry layer mismatch");
        }
        for (var i = 0; i < layerVertexCounts.length; i++) {
            if (layerVertexCounts[i] < 0) {
                throw new IllegalArgumentException("Negative block geometry vertex count");
            }
            if (layerVertexCounts[i] > 0 && layerVertices[i] == null) {
                throw new IllegalArgumentException("Block geometry layer vertices required");
            }
        }
    }

    public boolean isEmpty() {
        return vertexCount() == 0;
    }

    public int vertexCount() {
        var count = 0;
        for (var layerCount : layerVertexCounts) {
            count += layerCount;
        }
        return count;
    }
}
