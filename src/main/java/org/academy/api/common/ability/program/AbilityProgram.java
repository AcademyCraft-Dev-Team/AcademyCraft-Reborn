package org.academy.api.common.ability.program;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/**
 * One player-authored skill program owned by an ability category.
 */
public record AbilityProgram(
        int schemaVersion,
        UUID id,
        String name,
        Identifier category,
        ProgramGraph graph,
        ProgramEditorLayout editorLayout
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public AbilityProgram {
        if (schemaVersion <= 0) throw new IllegalArgumentException("Invalid ability program schema");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(category, "category");
        graph = graph == null ? ProgramGraph.EMPTY : graph;
        editorLayout = editorLayout == null ? ProgramEditorLayout.EMPTY : editorLayout;
        if (name.isBlank()) throw new IllegalArgumentException("Ability program name cannot be blank");
        if (name.length() > 64) throw new IllegalArgumentException("Ability program name is too long");
    }
}
