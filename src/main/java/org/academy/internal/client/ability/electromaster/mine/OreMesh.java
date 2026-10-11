package org.academy.internal.client.ability.electromaster.mine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** POSITION_COLOR_TEX vertices. Construction touches only immutable geometry snapshots. */
public record OreMesh(byte[] vertices, int vertexCount, int surfaceVertexCount) {
    private static final int[][] DIRECTIONS = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
    private static final int[][] FACES = {
            {0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4},
            {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}
    };

    public static OreMesh build(OreGeometry[] ores) {
        int boxes = 0;
        for (var ore : ores) if (ore != null) boxes += ore.boxes().size();
        var buffer = ByteBuffer.allocate(boxes * 6 * 4 * 24).order(ByteOrder.nativeOrder());
        int surfaceVertices = 0;
        // Keep shared faces in a separate suffix, drawn only for cells intersecting the range boundary.
        // Without those caps, clipping one of two adjacent ores would leave the other with a hole.
        for (int sharedPass = 0; sharedPass < 2; sharedPass++) {
            for (int i = 0; i < ores.length; i++) {
                var ore = ores[i];
                if (ore == null) continue;
                int x = i & 7, y = i >> 3 & 7, z = i >> 6;
                for (var box : ore.boxes()) for (int face = 0; face < 6; face++) {
                    var direction = DIRECTIONS[face];
                    int nx = x + direction[0], ny = y + direction[1], nz = z + direction[2];
                    int sharedWith = -1;
                    if (ore.fullCube() && nx >= 0 && nx < 8 && ny >= 0 && ny < 8 && nz >= 0 && nz < 8) {
                        var neighbour = ores[OreScanCache.index(nx, ny, nz)];
                        if (neighbour != null && neighbour.fullCube() && neighbour.color() == ore.color())
                            sharedWith = OreScanCache.index(nx, ny, nz);
                    }
                    if (sharedPass == 0 && sharedWith >= 0 || sharedPass == 1 && (sharedWith < 0 || sharedWith < i)) continue;
                    for (int corner : FACES[face]) {
                        buffer.putFloat(x + ((corner & 1) == 0 ? box.minX() : box.maxX()));
                        buffer.putFloat(y + ((corner & 2) == 0 ? box.minY() : box.maxY()));
                        buffer.putFloat(z + ((corner & 4) == 0 ? box.minZ() : box.maxZ()));
                        buffer.put((byte) (ore.color() >> 16)).put((byte) (ore.color() >> 8)).put((byte) ore.color()).put((byte) 255);
                        // The block's local index survives interpolation, allowing exact whole-block range clipping.
                        buffer.putFloat(i).putFloat(sharedWith + 1);
                    }
                }
            }
            if (sharedPass == 0) surfaceVertices = buffer.position() / 24;
        }
        return new OreMesh(Arrays.copyOf(buffer.array(), buffer.position()), buffer.position() / 24, surfaceVertices);
    }
}
