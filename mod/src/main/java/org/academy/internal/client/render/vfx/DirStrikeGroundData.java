package org.academy.internal.client.render.vfx;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.academy.api.client.render.vfx.VfxRenderData;

import java.nio.ByteBuffer;

public record DirStrikeGroundData(
        ByteBuffer vertices,
        int vertexCount,
        ChunkSectionLayer layer
) implements VfxRenderData {
    static final int VERTEX_STRIDE = (3 + 2 + 4) * Float.BYTES + 2 * Short.BYTES + 4 * Byte.BYTES;

    static short packedBlockCoordinate(int packedLight) {
        return (short) (packedLight & 0xFFFF);
    }

    static short packedSkyCoordinate(int packedLight) {
        return (short) (packedLight >>> 16 & 0xFFFF);
    }

    static byte packNormal(float component) {
        return (byte) Math.round(Math.clamp(component, -1.0f, 1.0f) * 127.0f);
    }

    static float unpackNormal(byte component) {
        return Math.max(-1.0f, component / 127.0f);
    }
}
