package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Set;

/**
 * Server-authoritative category and entitlement snapshot used during compilation.
 */
public record ProgramCompileContext(
        Identifier category,
        Set<Identifier> capabilities,
        ProgramLimits limits
) {
    public ProgramCompileContext {
        Objects.requireNonNull(category, "category");
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        limits = limits == null ? ProgramLimits.DEFAULT : limits;
    }
}
