package org.academy.api.client.render.vfxgraph.arc;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class ArcEmission {
    private final ArcBuffer buffer;
    private final long group;
    private final int maxPaths;
    private final int maxPoints;
    private final String layer;
    private int emitted;

    public ArcEmission(ArcBuffer buffer, long group, int maxPaths, int maxPoints, String layer) {
        this.buffer = buffer;
        this.group = group;
        this.maxPaths = Math.max(0, maxPaths);
        this.maxPoints = Math.max(2, maxPoints);
        this.layer = Objects.requireNonNull(layer);
    }

    public @Nullable ArcCurve add() {
        if (emitted >= maxPaths) return null;
        emitted++;
        var arc = buffer.add(group);
        arc.setLayer(layer);
        return arc;
    }

    public int maxPaths() {
        return maxPaths;
    }

    public int maxPoints() {
        return maxPoints;
    }

    public int emitted() {
        return emitted;
    }
}
