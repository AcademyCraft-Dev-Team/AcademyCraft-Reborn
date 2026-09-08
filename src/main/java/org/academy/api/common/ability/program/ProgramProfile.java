package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.academy.api.common.registries.Registries;
import java.util.Objects;

/** Declares a category's generic program runtime. Uses the existing Level 5 unlock policy. */
public record ProgramProfile(ResourceKey<ProgramNodeType<?>> entryNode, ProgramLimits limits,
                             ProgramSpatialLimits spatialLimits) {
    public ProgramProfile {
        Objects.requireNonNull(entryNode);
        Objects.requireNonNull(limits);
        Objects.requireNonNull(spatialLimits);
    }
    public static ProgramProfile standard(ResourceKey<ProgramNodeType<?>> entryNode) {
        return new ProgramProfile(entryNode, ProgramLimits.DEFAULT, ProgramSpatialLimits.DEFAULT);
    }
    public static ProgramProfile standard(Identifier entryNode) {
        return standard(ResourceKey.create(Registries.Keys.PROGRAM_NODE_TYPES, entryNode));
    }
}
