package org.academy.api.common.ability.program;

import org.academy.api.common.structure.upgrade.StructureProvenance;

import java.util.Objects;
import java.util.Optional;

/** Known provenance observation. An empty metadata value means confirmed unmarked, not a read failure. */
public record ProgramStructureSource(Optional<StructureProvenance.Metadata> metadata, boolean ownedByCaster) {
    public ProgramStructureSource {
        Objects.requireNonNull(metadata, "metadata");
        if (ownedByCaster && metadata.flatMap(StructureProvenance.Metadata::ownerId).isEmpty()) {
            throw new IllegalArgumentException("An unowned structure cannot belong to the caster");
        }
    }
}
