package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.jspecify.annotations.Nullable;

import java.util.List;

@FunctionalInterface
public interface ProgramNodeLookup {
    @Nullable ProgramNodeType<?> find(Identifier id);

    static ProgramNodeLookup firstOf(ProgramNodeLookup... lookups) {
        var chain = List.of(lookups);
        if (chain.isEmpty()) throw new IllegalArgumentException("Program node lookup chain is empty");
        return id -> {
            for (var lookup : chain) {
                var result = lookup.find(id);
                if (result != null) return result;
            }
            return null;
        };
    }
}
