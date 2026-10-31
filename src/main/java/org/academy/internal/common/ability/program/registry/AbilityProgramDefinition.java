package org.academy.internal.common.ability.program.registry;

import org.academy.internal.common.ability.program.CommonProgramExecutors;
import org.academy.internal.common.ability.program.ProgramNodeExtensionIndex;
import org.academy.internal.common.ability.program.migrate.ProgramGraphMigrations;


import org.academy.internal.common.ability.program.compile.ProgramCompileResult;
import org.academy.internal.common.ability.program.compile.ProgramCompiler;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;


import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.profiler.AcademyProfiler;

import java.util.List;
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
    private final ProgramSpatialLimits spatialLimits;
    private final ProgramCompilationCache compilationCache = new ProgramCompilationCache();

    public AbilityProgramDefinition(
            Identifier category,
            Map<Identifier, ProgramNodeType<?>> categoryNodeTypes,
            ProgramExecutorLookup categoryExecutors,
            ProgramEditorNodeCatalog editorCatalog,
            ProgramLimits limits
    ) {
        this(
                category,
                categoryNodeTypes,
                categoryExecutors,
                editorCatalog,
                limits,
                AbilityProgramSpatialRanges.forCategory(category)
        );
    }

    public AbilityProgramDefinition(
            Identifier category,
            Map<Identifier, ProgramNodeType<?>> categoryNodeTypes,
            ProgramExecutorLookup categoryExecutors,
            ProgramEditorNodeCatalog editorCatalog,
            ProgramLimits limits,
            ProgramSpatialLimits spatialLimits
    ) {
        this.category = Objects.requireNonNull(category, "category");
        this.categoryNodeTypes = Map.copyOf(categoryNodeTypes);
        this.editorCatalog = Objects.requireNonNull(editorCatalog, "editorCatalog");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.spatialLimits = Objects.requireNonNull(spatialLimits, "spatialLimits");
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
        nodeLookup = ProgramNodeLookup.firstOf(
                this.categoryNodeTypes::get,
                CommonProgramNodeCatalog.INSTANCE,
                id -> {
                    var registration = ProgramNodeExtensionIndex.snapshot(category).find(id);
                    return registration == null ? null : registration.extension();
                }
        );
        executors = ProgramExecutorLookup.firstOf(
                categoryExecutors,
                CommonProgramExecutors.INSTANCE,
                id -> {
                    var registration = ProgramNodeExtensionIndex.snapshot(category).find(id);
                    return registration == null ? null : registration.executor();
                }
        );
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

    public ProgramSpatialLimits spatialLimits() {
        return spatialLimits;
    }

    /**
     * Deterministic compatibility fingerprint of all validated extension nodes for this category.
     */
    public String extensionFingerprint() {
        return ProgramNodeExtensionIndex.snapshot(category).fingerprint();
    }

    public ProgramCompileResult compile(AbilityProgram program, Set<Identifier> capabilities) {
        AcademyProfiler.push("academy.program.compile.total");
        AcademyProfiler.incrementCounter("program.compile.definition_requests");
        try {
            return recordCompileResult(compileProgram(program, capabilities));
        } finally {
            AcademyProfiler.pop();
        }
    }

    private ProgramCompileResult compileProgram(AbilityProgram program, Set<Identifier> capabilities) {
        Objects.requireNonNull(program, "program");
        AbilityProgram migrated;
        AcademyProfiler.push("academy.program.compile.migrate");
        try {
            AcademyProfiler.incrementCounter("program.compile.migration_requests");
            migrated = ProgramGraphMigrations.migrate(program, nodeLookup).program();
        } finally {
            AcademyProfiler.pop();
        }
        if (migrated.schemaVersion() != AbilityProgram.CURRENT_SCHEMA_VERSION) {
            return failure(ProgramDiagnosticCode.INVALID_CONFIGURATION);
        }
        if (!migrated.category().equals(category)) {
            return failure(ProgramDiagnosticCode.CATEGORY_MISMATCH);
        }
        return compileGraph(migrated.graph(), capabilities);
    }

    public ProgramCompileResult compile(ProgramGraph graph, Set<Identifier> capabilities) {
        AcademyProfiler.push("academy.program.compile.total");
        AcademyProfiler.incrementCounter("program.compile.definition_requests");
        try {
            return recordCompileResult(compileGraph(graph, capabilities));
        } finally {
            AcademyProfiler.pop();
        }
    }

    private ProgramCompileResult compileGraph(ProgramGraph graph, Set<Identifier> capabilities) {
        Objects.requireNonNull(graph, "graph");
        var context = new ProgramCompileContext(category, capabilities, limits);
        // Custom definitions and extension configurations do not inherit built-in cache assumptions.
        if (AbilityProgramDefinitions.find(category) != this) {
            AcademyProfiler.incrementCounter("program.compile.cache_bypass");
            return ProgramCompiler.compile(graph, context, nodeLookup);
        }
        return compilationCache.compile(graph, context, categoryNodeTypes,
                () -> ProgramCompiler.compile(graph, context, nodeLookup));
    }

    void clearCompilationCache() { compilationCache.clear(); }

    ProgramCompilationCache.Stats compilationCacheStats() { return compilationCache.stats(); }

    private static ProgramCompileResult recordCompileResult(ProgramCompileResult result) {
        AcademyProfiler.incrementCounter(result.valid()
                ? "program.compile.definition_success" : "program.compile.definition_rejected");
        return result;
    }

    private static ProgramCompileResult failure(ProgramDiagnosticCode code) {
        return new ProgramCompileResult(null, List.of(ProgramDiagnostic.graph(code)));
    }
}
