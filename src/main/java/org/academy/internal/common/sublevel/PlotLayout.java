package org.academy.internal.common.sublevel;

import java.util.Objects;

/** Chunk geometry and capacity of one shared plot grid. */
public record PlotLayout(int logSize, int originChunkX, int originChunkZ, int maxPlots) {
    public static final int SPACIOUS_MARKER = 65_536;
    public static final int SPACIOUS_SIDE_CHUNKS = 64;
    public static final int SPACIOUS_RADIUS = SPACIOUS_SIDE_CHUNKS * 8;
    public static final int MAX_LOG_SIZE = 10;
    public static final int MAX_PLOTS = 65_536;

    public PlotLayout {
        if (logSize < 0 || logSize > MAX_LOG_SIZE) {
            throw new IllegalArgumentException("Plot log size must be in [0, 10]");
        }
        if (maxPlots < 1 || maxPlots > MAX_PLOTS) {
            throw new IllegalArgumentException("Plot capacity must be in [1, " + MAX_PLOTS + "]");
        }
    }

    public int sideChunks() {
        return 1 << logSize;
    }

    public int gridSide() {
        return Math.max(1, (int) Math.ceil(Math.sqrt(maxPlots)));
    }

    public int chunkMinX(PlotPos plot) {
        Objects.requireNonNull(plot, "plot");
        if (spacious(plot)) return spaciousOrigin(originChunkX, plot.x() - SPACIOUS_MARKER);
        return Math.addExact(originChunkX, Math.multiplyExact(plot.x(), sideChunks()));
    }

    public int chunkMinZ(PlotPos plot) {
        Objects.requireNonNull(plot, "plot");
        if (spacious(plot)) return spaciousOrigin(originChunkZ, plot.z());
        return Math.addExact(originChunkZ, Math.multiplyExact(plot.z(), sideChunks()));
    }

    public static boolean spacious(PlotPos plot) { return plot.x() >= SPACIOUS_MARKER; }

    private int spaciousOrigin(int origin, int index) {
        if (index < 0 || index >= gridSide()) throw new IllegalArgumentException("Invalid spacious plot coordinate");
        return Math.addExact(origin + gridSide() * sideChunks() + 32,
                index * SPACIOUS_SIDE_CHUNKS + SPACIOUS_SIDE_CHUNKS / 2);
    }

    public boolean containsLocal(PlotPos plot, int x, int z) {
        return spacious(plot) ? x >= -SPACIOUS_RADIUS && z >= -SPACIOUS_RADIUS
                && x < SPACIOUS_RADIUS && z < SPACIOUS_RADIUS
                : x >= 0 && z >= 0 && x < sideChunks() * 16 && z < sideChunks() * 16;
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        int side = sideChunks();
        return chunkX >= originChunkX && chunkZ >= originChunkZ
                && chunkX < originChunkX + side * gridSide()
                && chunkZ < originChunkZ + side * gridSide();
    }
}
