package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.academy.api.common.registries.Registries;

/**
 * Production lookup backed by the synchronized NeoForge program-node registry.
 */
public final class RegistryProgramNodeLookup implements ProgramNodeLookup {
    public static final RegistryProgramNodeLookup INSTANCE = new RegistryProgramNodeLookup();

    private RegistryProgramNodeLookup() {
    }

    @Override
    public ProgramNodeType<?> find(Identifier id) {
        return Registries.PROGRAM_NODE_TYPES.get(id)
                .map(reference -> reference.value())
                .orElse(null);
    }
}
