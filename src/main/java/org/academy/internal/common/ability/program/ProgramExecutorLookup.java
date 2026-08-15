package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ProgramExecutorLookup {
    @Nullable ProgramNodeExecutor<?> find(Identifier nodeType);

    static ProgramExecutorLookup firstOf(ProgramExecutorLookup... lookups) {
        var chain = java.util.List.of(lookups);
        if (chain.isEmpty()) throw new IllegalArgumentException("Program executor lookup chain is empty");
        return id -> {
            for (var lookup : chain) {
                var result = lookup.find(id);
                if (result != null) return result;
            }
            return null;
        };
    }
}
