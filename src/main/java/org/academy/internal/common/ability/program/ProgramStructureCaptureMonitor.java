package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramStructureOperationResult;
import org.academy.api.common.structure.StructureCaptureResult;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Retains a program's transaction identity independently of a skill's temporary session. */
public final class ProgramStructureCaptureMonitor {
    private @Nullable UUID transactionId;

    public void track(Optional<UUID> transactionId) {
        this.transactionId = Objects.requireNonNull(transactionId, "transactionId").orElse(null);
    }

    /** Terminal failures remain readable even after the skill has removed its capture session. */
    public Optional<ProgramStructureOperationResult> pollFailure(
            Function<UUID, Optional<StructureCaptureResult>> poll
    ) {
        if (transactionId == null) return Optional.empty();
        var result = poll.apply(transactionId).orElse(null);
        if (result != null && result.pending()) return Optional.empty();
        transactionId = null;
        if (result == null) {
            return Optional.of(ProgramStructureOperationResult.unavailable("capture_transaction_unavailable"));
        }
        if (result.succeeded()) return Optional.empty();
        return Optional.of(ProgramStructureOperationResult.rejected(result.status() + ":" + result.detail()));
    }
}
