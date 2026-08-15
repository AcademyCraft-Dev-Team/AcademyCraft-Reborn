package org.academy.internal.common.ability.program;

import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.ProgramNodeType;
import org.academy.api.common.registries.Registries;

import java.util.Comparator;
import java.util.HashMap;

/**
 * Built-in node registrations for the shared ability-program registry.
 */
public final class AbilityProgramNodeTypes {
    public static final DeferredRegister<ProgramNodeType<?>> REGISTER =
            DeferredRegister.create(Registries.Keys.PROGRAM_NODE_TYPES, AcademyCraft.MOD_ID);

    static {
        var builtIns = new HashMap<>(CommonProgramNodeCatalog.INSTANCE.types());
        AbilityProgramDefinitions.all().forEach(definition -> {
            definition.categoryNodeTypes().forEach((id, type) -> {
                if (builtIns.putIfAbsent(id, type) != null) {
                    throw new IllegalStateException("Duplicate built-in program node " + id);
                }
            });
        });
        builtIns.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> REGISTER.register(entry.getKey().getPath(), entry::getValue));
    }

    private AbilityProgramNodeTypes() {
    }
}
