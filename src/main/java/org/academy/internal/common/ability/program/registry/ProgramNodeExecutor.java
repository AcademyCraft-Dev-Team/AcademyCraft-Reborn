package org.academy.internal.common.ability.program.registry;

import org.academy.internal.common.ability.program.ProgramVmContext;


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
