package org.academy.internal.common.ability.program;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Per-invocation server execution frame shared by node executors.
 *
 * <p>Action nodes stage compensatable world mutations into the transaction instead of applying
 * them immediately. The environment remains runtime-specific, allowing each ability category to
 * expose only the services its nodes are permitted to use.</p>
 */
public final class ProgramExecutionFrame {
    private final ProgramActionTransaction transaction;
    private final @Nullable Object environment;

    public ProgramExecutionFrame(
            ProgramActionTransaction transaction,
            @Nullable Object environment
    ) {
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.environment = environment;
    }

    public ProgramActionTransaction transaction() {
        return transaction;
    }

    public void stage(
            ProgramVmContext context,
            ProgramActionTransaction.ProgramAction action
    ) {
        transaction.stage(context.nodeId(), action);
    }

    public <T> Optional<T> environment(Class<T> type) {
        return type.isInstance(environment)
                ? Optional.of(type.cast(environment))
                : Optional.empty();
    }
}
