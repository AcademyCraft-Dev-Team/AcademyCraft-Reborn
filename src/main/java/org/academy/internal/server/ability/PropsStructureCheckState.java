package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;

/** Per-player cadence for optional structure discovery; no chunk tickets or world references. */
final class PropsStructureCheckState {
    static final int MOVING_INTERVAL = 20;
    static final int STATIONARY_INTERVAL = 200;
    private Identifier dimension;
    private long position;
    private long lastCheckTick;

    boolean shouldCheck(long tick, Identifier currentDimension, long currentPosition, int phase) {
        if (Math.floorMod(tick, MOVING_INTERVAL) != Math.floorMod(phase, MOVING_INTERVAL)) return false;
        if (currentDimension.equals(dimension) && position == currentPosition
                && tick >= lastCheckTick && tick - lastCheckTick < STATIONARY_INTERVAL) return false;
        dimension = currentDimension;
        position = currentPosition;
        lastCheckTick = tick;
        return true;
    }
}
