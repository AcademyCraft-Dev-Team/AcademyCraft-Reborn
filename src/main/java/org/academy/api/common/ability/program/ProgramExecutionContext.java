package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Read-only information exposed to an externally registered program-node executor.
 *
 * <p>The VM deliberately does not expose its variable table, continuation state, attachment,
 * action transaction, or executor-local storage through this boundary.</p>
 */
public interface ProgramExecutionContext {
    long gameTime();

    int nodeId();

    Identifier nodeType();

    /**
     * Returns the runtime's restricted, read-only target resolver when one is available.
     *
     * <p>This is the stable route for external query nodes to identify their caster and convert
     * typed world references. The VM attachment, action transaction, and mutable executor state
     * remain inaccessible.</p>
     */
    default Optional<ProgramTargetResolver> targetResolver() {
        return Optional.empty();
    }
}
