package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;

import java.util.*;

/** Bounded six-face topology; an empty result asks the caller to preserve the original body. */
public final class SubLevelConnectedComponents {
    public static final int MAX_CELLS = 4096;
    public static final int MAX_COMPONENTS = 64;
    private static final Comparator<BlockPos> ORDER = Comparator.<BlockPos>comparingInt(position -> position.getX())
            .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);
    private static final int[][] NEIGHBORS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private SubLevelConnectedComponents() {}

    public static List<List<BlockPos>> find(List<BlockPos> cells) {
        Objects.requireNonNull(cells, "cells");
        if (cells.isEmpty() || cells.size() > MAX_CELLS) return List.of();
        var remaining = new TreeSet<>(ORDER);
        for (var cell : cells) remaining.add(Objects.requireNonNull(cell, "cell").immutable());
        var components = new ArrayList<List<BlockPos>>();
        var queue = new ArrayDeque<BlockPos>();
        while (!remaining.isEmpty()) {
            if (components.size() == MAX_COMPONENTS) return List.of();
            queue.add(remaining.pollFirst());
            var component = new ArrayList<BlockPos>();
            while (!queue.isEmpty()) {
                var position = queue.removeFirst();
                component.add(position);
                for (var offset : NEIGHBORS) {
                    long x = (long) position.getX() + offset[0];
                    long y = (long) position.getY() + offset[1];
                    long z = (long) position.getZ() + offset[2];
                    if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE
                            || y > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) continue;
                    var neighbor = new BlockPos((int) x, (int) y, (int) z);
                    if (remaining.remove(neighbor)) queue.addLast(neighbor);
                }
            }
            component.sort(ORDER);
            components.add(List.copyOf(component));
        }
        components.sort(Comparator.<List<BlockPos>>comparingInt(List::size).reversed()
                .thenComparing(group -> group.getFirst(), ORDER));
        return List.copyOf(components);
    }
}
