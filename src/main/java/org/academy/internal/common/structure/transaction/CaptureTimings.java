package org.academy.internal.common.structure.transaction;

import org.academy.AcademyCraft;

/**
 * Opt-in capture stage timings. Disabled by default; when enabled with
 * {@code -Dacademy.captureTimings=true} it emits one log line per measured stage. The lines are
 * diagnostic evidence for PIPE-01/03 and do not alter capture behavior.
 */
public final class CaptureTimings {
    private static final boolean ENABLED = Boolean.getBoolean("academy.captureTimings");

    private CaptureTimings() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static long begin() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    public static void record(String owner, String stage, long started) {
        if (!ENABLED || started == 0L) return;
        double millis = (System.nanoTime() - started) / 1_000_000.0;
        AcademyCraft.LOGGER.info("CAPTURE_STAGE owner={} stage={} ms={}", owner, stage, String.format(java.util.Locale.ROOT, "%.3f", millis));
    }
}
