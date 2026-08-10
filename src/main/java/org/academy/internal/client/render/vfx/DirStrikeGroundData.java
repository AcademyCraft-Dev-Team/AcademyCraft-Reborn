package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxRenderData;

import java.nio.ByteBuffer;

public record DirStrikeGroundData(ByteBuffer vertices, int vertexCount) implements VfxRenderData {
    static final int VERTEX_STRIDE = (3 + 2 + 4) * Float.BYTES;
}
