package org.academy.internal.common.structure.upgrade.control;

/** Counts completed, continuously admitted solver ticks, independently of receipt polling. */
final class ExtractionHoverClock {
    private final int maximumTicks;
    private int elapsedTicks;
    private int observedTick;
    private boolean observed;
    private boolean stableInTick;

    ExtractionHoverClock(int maximumTicks) {
        if (maximumTicks < 0) throw new IllegalArgumentException("Negative hover duration");
        this.maximumTicks = maximumTicks;
    }

    /** Every substep must succeed; a later observation cannot undo a pause in the same tick. */
    int observe(int serverTick, boolean stable) {
        if (!observed || observedTick != serverTick) {
            // Commit only the preceding complete solver tick. A clock jump is not simulated
            // hover time; integer subtraction also handles the server tick counter wrapping.
            if (observed && serverTick - observedTick == 1 && stableInTick && elapsedTicks < maximumTicks) {
                elapsedTicks++;
            }
            observed = true;
            observedTick = serverTick;
            stableInTick = stable;
        } else {
            stableInTick &= stable;
        }
        return elapsedTicks;
    }

    int elapsedTicks() { return elapsedTicks; }
}
