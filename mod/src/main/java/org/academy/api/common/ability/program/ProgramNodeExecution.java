package org.academy.api.common.ability.program;

/**
 * Safe execution boundary for a registered extension node.
 *
 * @param <C> immutable node configuration decoded by the node type
 */
@FunctionalInterface
public interface ProgramNodeExecution<C> {
    ProgramNodeStep execute(
            ProgramExecutionContext context,
            C configuration,
            ProgramInputView inputs
    );
}
