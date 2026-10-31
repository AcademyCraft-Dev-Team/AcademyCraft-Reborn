package org.academy.internal.client.ability.program;

import org.academy.api.common.ability.program.ProgramRunStatus;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Own-view filter: an older run, revision, dimension or editor cannot overwrite a newer view. */
public final class ProgramRunStatusSelection {
    private final UUID token;
    private final ProgramRunStatus.Target target;
    private @Nullable ProgramRunStatus current;

    public ProgramRunStatusSelection(UUID token, ProgramRunStatus.Target target) {
        this.token = Objects.requireNonNull(token); this.target = Objects.requireNonNull(target);
    }
    public UUID token() { return token; }
    public ProgramRunStatus.Target target() { return target; }
    public @Nullable ProgramRunStatus current() { return current; }

    public boolean accept(ProgramRunStatus incoming) {
        if (!token.equals(incoming.token()) || !target.equals(incoming.target())) return false;
        if (current != null && (incoming.runSequence() < current.runSequence()
                || incoming.updateSequence() <= current.updateSequence())) return false;
        current = incoming;
        return true;
    }
}
