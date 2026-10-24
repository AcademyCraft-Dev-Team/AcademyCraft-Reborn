package org.academy.internal.common.sublevel;

import java.util.Objects;

/** Chunk geometry and capacity of one shared plot grid. */
public record PlotLayout(int logSize, int originChunkX, int originChunkZ, int maxPlots) {
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
        return Math.addExact(originChunkX, Math.multiplyExact(plot.x(), sideChunks()));
    }

    public int chunkMinZ(PlotPos plot) {
        Objects.requireNonNull(plot, "plot");
        return Math.addExact(originChunkZ, Math.multiplyExact(plot.z(), sideChunks()));
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        int side = sideChunks();
        return chunkX >= originChunkX && chunkZ >= originChunkZ
                && chunkX < originChunkX + side * gridSide()
                && chunkZ < originChunkZ + side * gridSide();
    }
}
