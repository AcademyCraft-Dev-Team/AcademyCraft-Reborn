package org.academy.api.common.ability.program;

import com.mojang.serialization.Codec;

/**
 * Public extension point for a configured node in an ability program.
 *
 * @param <C> immutable configuration decoded from the graph
 */
public interface ProgramNodeType<C> {
    Codec<C> configurationCodec();

    int schemaVersion();

    ProgramNodeSchema schema(C configuration);

    ProgramNodeRole role();

    ProgramNodePurity purity();

    ProgramNodeScope scope();
}
