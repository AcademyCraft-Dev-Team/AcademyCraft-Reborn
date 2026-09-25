package org.academy.internal.common.ability.program;

import org.academy.api.common.ability.program.ProgramInputView;
import org.academy.api.common.ability.program.ProgramNodeStep;

@FunctionalInterface
public interface ProgramNodeExecutor<C> {
    ProgramNodeStep execute(
            ProgramVmContext context,
            C configuration,
            ProgramInputView inputs
    );
}
