package org.academy.internal.common.structure.upgrade.control;

import org.academy.api.common.structure.upgrade.StructureOperation;

/** Transient observations are retried against one original deadline, never waived. */
public final class CapturePreparationPolicy {
    public static final int TIMEOUT_TICKS = 100;
    private CapturePreparationPolicy() {}

    public static boolean retryable(StructureOperation.Status status, String detail) {
        return status == StructureOperation.Status.TERRAIN_PENDING
                || status == StructureOperation.Status.UNSUPPORTED
                && "capture_source_scheduled_tick_pending".equals(detail);
    }

    public static boolean expired(long startedAt, long now) {
        return now < startedAt || now - startedAt >= TIMEOUT_TICKS;
    }

    public static boolean mayAttempt(long lastAttempt, long now) { return lastAttempt != now; }
}
