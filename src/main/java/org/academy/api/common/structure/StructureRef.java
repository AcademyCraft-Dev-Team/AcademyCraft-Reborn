package org.academy.api.common.structure;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** A backend-independent identity; a reference does not keep its structure loaded. */
public record StructureRef(UUID id, Identifier dimension, Identifier backend, long generation) {
    public static final Identifier LEGACY_ENTITY_BACKEND =
            Identifier.fromNamespaceAndPath("academy", "legacy_entity");

    public StructureRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(backend, "backend");
        if (generation < 0) throw new IllegalArgumentException("generation cannot be negative");
    }
}
