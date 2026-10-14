package org.academy.internal.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Signals that a node executor performed its action and the action did not succeed.
 *
 * <p>Unlike a generic runtime exception, the diagnostic and the machine-readable detail are
 * known here, so the VM reports both on the failing action node instead of leaving downstream
 * nodes to fail with a missing value.</p>
 */
public final class ProgramActionFailure extends RuntimeException {
    private final ProgramVmDiagnostic diagnostic;
    private final String detail;

    public ProgramActionFailure(ProgramVmDiagnostic diagnostic, @Nullable String detail) {
        super(detail, null, false, false);
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        this.detail = detail == null ? "" : detail;
    }

    public ProgramVmDiagnostic diagnostic() {
        return diagnostic;
    }

    public String detail() {
        return detail;
    }
}
