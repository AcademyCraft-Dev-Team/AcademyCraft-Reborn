package org.academy.internal.common.ability.program;

@FunctionalInterface
public interface ProgramNodeExecutor<C> {
    ProgramNodeStep execute(
            ProgramVmContext context,
            C configuration,
            ProgramInputView inputs
    );
}
