package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxRenderData;

import java.nio.ByteBuffer;

public record ColorMeshData(ByteBuffer vertices, int vertexCount) implements VfxRenderData {
    static final int VERTEX_STRIDE = 7 * Float.BYTES;
}
