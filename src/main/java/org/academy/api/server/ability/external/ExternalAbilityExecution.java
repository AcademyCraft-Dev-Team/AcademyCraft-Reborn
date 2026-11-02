package org.academy.api.server.ability.external;

import org.academy.api.server.ability.AbilityExecutionHandle;

/** A terminal failure remains inspectable even after tick returns false. */
public interface ExternalAbilityExecution extends AbilityExecutionHandle {
    enum State { RUNNING, COMPLETED, CANCELLED, FAILED }
    State state();
    String detail();
}
