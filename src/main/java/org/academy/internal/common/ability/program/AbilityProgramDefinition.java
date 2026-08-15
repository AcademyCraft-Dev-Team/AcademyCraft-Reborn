package org.academy.internal.common.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramDiagnostic;
import org.academy.api.common.ability.program.ProgramDiagnosticCode;
import org.academy.api.common.ability.program.ProgramCompileContext;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.api.common.ability.program.ProgramLimits;
import org.academy.api.common.ability.program.ProgramNodeRole;
import org.academy.api.common.ability.program.ProgramNodeType;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Complete compile, execution, and editor assembly for one programmable ability category.
 */
public final class AbilityProgramDefinition {
    private final Identifier category;
    private final Map<Identifier, ProgramNodeType<?>> categoryNodeTypes;
    private final ProgramNodeLookup nodeLookup;
    private final ProgramExecutorLookup executors;
    private final ProgramEditorNodeCatalog editorCatalog;
    private final ProgramLimits limits;

    public AbilityProgramDefinition(
            Identifier category,
            Map<Identifier, ProgramNodeType<?>> categoryNodeTypes,
            ProgramExecutorLookup categoryExecutors,
            ProgramEditorNodeCatalog editorCatalog,
            ProgramLimits limits
    ) {
        this.category = Objects.requireNonNull(category, "category");
        this.categoryNodeTypes = Map.copyOf(categoryNodeTypes);
        this.editorCatalog = Objects.requireNonNull(editorCatalog, "editorCatalog");
        this.limits = Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(categoryExecutors, "categoryExecutors");
        if (this.categoryNodeTypes.isEmpty()) {
            throw new IllegalArgumentException("Ability program definition needs category nodes");
        }
        if (!editorCatalog.category().equals(category)) {
            throw new IllegalArgumentException("Editor catalog category does not match definition");
        }
        for (var entry : this.categoryNodeTypes.entrySet()) {
            var id = Objects.requireNonNull(entry.getKey(), "category node id");
            var type = Objects.requireNonNull(entry.getValue(), "category node type");
            if (CommonProgramNodeCatalog.INSTANCE.find(id) != null) {
                throw new IllegalArgumentException("Category node shadows common node " + id);
            }
            if (!type.scope().allowsCategory(category)) {
                throw new IllegalArgumentException("Category node rejects its definition " + id);
            }
            if (editorCatalog.find(id) != type) {
                throw new IllegalArgumentException("Editor catalog is missing category node " + id);
            }
            if (type.role() != ProgramNodeRole.ENTRY && categoryExecutors.find(id) == null) {
                throw new IllegalArgumentException("Category node has no executor " + id);
            }
        }
        CommonProgramNodeCatalog.INSTANCE.types().forEach((id, type) -> {
            if (editorCatalog.find(id) != type) {
                throw new IllegalArgumentException("Editor catalog is missing common node " + id);
            }
        });
        nodeLookup = ProgramNodeLookup.firstOf(this.categoryNodeTypes::get,
                CommonProgramNodeCatalog.INSTANCE);
        executors = ProgramExecutorLookup.firstOf(categoryExecutors, CommonProgramExecutors.INSTANCE);
    }

    public Identifier category() {
        return category;
    }

    public Map<Identifier, ProgramNodeType<?>> categoryNodeTypes() {
        return categoryNodeTypes;
    }

    public ProgramNodeLookup nodeLookup() {
        return nodeLookup;
    }

    public ProgramExecutorLookup executors() {
        return executors;
    }

    public ProgramEditorNodeCatalog editorCatalog() {
        return editorCatalog;
    }

    public ProgramLimits limits() {
        return limits;
    }

    public ProgramCompileResult compile(AbilityProgram program, Set<Identifier> capabilities) {
        Objects.requireNonNull(program, "program");
        if (program.schemaVersion() != AbilityProgram.CURRENT_SCHEMA_VERSION) {
            return failure(ProgramDiagnosticCode.INVALID_CONFIGURATION);
        }
        if (!program.category().equals(category)) {
            return failure(ProgramDiagnosticCode.CATEGORY_MISMATCH);
        }
        return compile(program.graph(), capabilities);
    }

    public ProgramCompileResult compile(ProgramGraph graph, Set<Identifier> capabilities) {
        return ProgramCompiler.compile(
                Objects.requireNonNull(graph, "graph"),
                new ProgramCompileContext(category, capabilities, limits),
                nodeLookup
        );
    }

    private static ProgramCompileResult failure(ProgramDiagnosticCode code) {
        return new ProgramCompileResult(null, java.util.List.of(ProgramDiagnostic.graph(code)));
    }
}
