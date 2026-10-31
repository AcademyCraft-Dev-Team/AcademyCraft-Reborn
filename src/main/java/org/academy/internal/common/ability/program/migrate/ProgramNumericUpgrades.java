package org.academy.internal.common.ability.program.migrate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.program.CommonProgramExecutors;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeCatalog;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Optional, read-only upgrade preview for 29 audited legacy numeric operations.
 *
 * <p>This is deliberately separate from automatic graph migrations. It never saves, executes,
 * changes a registration, or drops an edge. A caller must show the changes and verify a fresh
 * preview against the current draft before applying it as one reversible edit. A plan is not an
 * execution entitlement or a substitute for normal server compilation.</p>
 */
public final class ProgramNumericUpgrades {
    private static final Map<Identifier, Mapping> MAPPINGS = mappings();

    private ProgramNumericUpgrades() {
    }

    public enum Reason {
        INTEGER_DIVISION_SEMANTICS,
        NONEMPTY_CONFIGURATION,
        UNSUPPORTED_SCHEMA_VERSION,
        EXTENSION_OR_CUSTOM_IMPLEMENTATION,
        SCHEMA_MISMATCH,
        RUNTIME_CONTRACT_MISMATCH,
        INVALID_CONFIGURATION
    }

    public record Change(int nodeId, Identifier sourceType, Identifier targetType, JsonElement configuration) {
        public Change {
            Objects.requireNonNull(sourceType, "sourceType");
            Objects.requireNonNull(targetType, "targetType");
            configuration = freezeJson(Objects.requireNonNull(configuration, "configuration"));
        }

        @Override
        public JsonElement configuration() {
            return configuration.deepCopy();
        }
    }

    public record Retained(int nodeId, Identifier type, Reason reason) {
        public Retained {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public record Plan(AbilityProgram source, AbilityProgram candidate, List<Change> changes,
                       List<Retained> retained, List<ProgramDiagnostic> diagnostics) {
        public Plan {
            source = snapshot(Objects.requireNonNull(source, "source"));
            candidate = snapshot(Objects.requireNonNull(candidate, "candidate"));
            changes = List.copyOf(changes);
            retained = List.copyOf(retained);
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean canApply() {
            return !changes.isEmpty() && diagnostics.isEmpty();
        }
    }

    public static Plan preview(AbilityProgram source, AbilityProgramDefinition definition,
                               Set<Identifier> capabilities) {
        Objects.requireNonNull(definition, "definition");
        var entitlements = Set.copyOf(capabilities);
        var original = snapshot(Objects.requireNonNull(source, "source"));
        var changes = new ArrayList<Change>();
        var retained = new ArrayList<Retained>();
        var nodes = new ArrayList<ProgramGraph.Node>(original.graph().nodes().size());
        for (var node : original.graph().nodes()) {
            var mapping = MAPPINGS.get(node.type());
            if (node.type().equals(CommonProgramNodeIds.INTEGER_DIVIDE)) {
                retained.add(new Retained(node.id(), node.type(), Reason.INTEGER_DIVISION_SEMANTICS));
                nodes.add(node);
                continue;
            }
            if (mapping == null) {
                nodes.add(node);
                continue;
            }
            var reason = check(node, mapping, original.schemaVersion(), definition);
            if (reason != null) {
                retained.add(new Retained(node.id(), node.type(), reason));
                nodes.add(node);
                continue;
            }
            var configuration = mapping.configuration();
            var type = CommonProgramNodeCatalog.INSTANCE.find(mapping.target());
            nodes.add(new ProgramGraph.Node(node.id(), mapping.target(), type.schemaVersion(), configuration));
            changes.add(new Change(node.id(), node.type(), mapping.target(), configuration));
        }
        var candidate = new AbilityProgram(original.schemaVersion(), original.id(), original.name(), original.category(),
                new ProgramGraph(nodes, original.graph().edges()), original.editorLayout());
        var diagnostics = new LinkedHashSet<ProgramDiagnostic>();
        diagnostics.addAll(definition.compile(original, entitlements).diagnostics());
        diagnostics.addAll(definition.compile(candidate, entitlements).diagnostics());
        return new Plan(original, candidate, changes, retained, List.copyOf(diagnostics));
    }

    private static @Nullable Reason check(ProgramGraph.Node node, Mapping mapping, int programVersion,
                                AbilityProgramDefinition definition) {
        if (programVersion != AbilityProgram.CURRENT_SCHEMA_VERSION || node.schemaVersion() != 1) {
            return Reason.UNSUPPORTED_SCHEMA_VERSION;
        }
        var raw = node.configuration();
        if (!raw.isJsonObject()) return Reason.INVALID_CONFIGURATION;
        if (raw.getAsJsonObject().size() != 0) return Reason.NONEMPTY_CONFIGURATION;
        var legacy = CommonProgramNodeCatalog.INSTANCE.find(node.type());
        var target = CommonProgramNodeCatalog.INSTANCE.find(mapping.target());
        if (legacy == null || target == null || definition.nodeLookup().find(node.type()) != legacy
                || definition.nodeLookup().find(mapping.target()) != target
                || definition.executors().find(node.type()) != CommonProgramExecutors.INSTANCE.find(node.type())
                || definition.executors().find(mapping.target()) != CommonProgramExecutors.INSTANCE.find(mapping.target())) {
            return Reason.EXTENSION_OR_CUSTOM_IMPLEMENTATION;
        }
        if (legacy.schemaVersion() != 1 || target.schemaVersion() != 1
                || legacy.role() != target.role() || legacy.purity() != target.purity()
                || !legacy.scope().equals(target.scope())) return Reason.RUNTIME_CONTRACT_MISMATCH;
        try {
            var oldSchema = resolvedSchema(legacy, raw);
            var newSchema = resolvedSchema(target, mapping.configuration());
            return oldSchema.equals(newSchema) ? null : Reason.SCHEMA_MISMATCH;
        } catch (RuntimeException invalid) {
            return Reason.INVALID_CONFIGURATION;
        }
    }

    private static <C> ProgramNodeSchema resolvedSchema(ProgramNodeType<C> type, JsonElement json) {
        var decoded = type.configurationCodec().parse(JsonOps.INSTANCE, json).getOrThrow();
        if (!type.configurationPorts(decoded).isEmpty()) {
            throw new IllegalArgumentException("Audited numeric operation unexpectedly has configuration inputs");
        }
        return type.resolvedSchema(decoded);
    }

    private static Map<Identifier, Mapping> mappings() {
        var result = new LinkedHashMap<Identifier, Mapping>();
        for (var kind : List.of("integer", "big_integer", "float")) {
            for (var operator : List.of("add", "subtract", "multiply", "divide", "modulo")) {
                if (kind.equals("integer") && operator.equals("divide")) continue;
                result.put(legacy(kind, operator), new Mapping(CommonProgramNodeIds.NUMERIC_ARITHMETIC, kind, operator));
            }
            for (var operator : List.of("equal", "less", "less_equal", "greater", "greater_equal")) {
                result.put(legacy(kind, operator), new Mapping(CommonProgramNodeIds.NUMERIC_COMPARE, kind, operator));
            }
        }
        return Map.copyOf(result);
    }

    private static Identifier legacy(String kind, String operation) {
        return AcademyCraft.academy("program/core/logic/" + kind + "/" + operation);
    }

    private record Mapping(Identifier target, String kind, String operator) {
        JsonObject configuration() {
            var result = new JsonObject();
            result.addProperty("type", kind);
            result.addProperty("operator", operator);
            return result;
        }
    }

    private static AbilityProgram snapshot(AbilityProgram program) {
        var nodes = program.graph().nodes().stream().map(node -> new ProgramGraph.Node(node.id(), node.type(),
                node.schemaVersion(), freezeJson(node.configuration()))).toList();
        return new AbilityProgram(program.schemaVersion(), program.id(), program.name(), program.category(),
                new ProgramGraph(nodes, program.graph().edges()), program.editorLayout());
    }

    private static JsonElement freezeJson(JsonElement value) {
        if (value.isJsonNull()) return JsonNull.INSTANCE;
        if (value.isJsonArray()) {
            var result = new JsonArray();
            value.getAsJsonArray().forEach(child -> result.add(freezeJson(child)));
            return result;
        }
        if (value.isJsonObject()) {
            var result = new JsonObject();
            value.getAsJsonObject().entrySet().forEach(entry -> result.add(entry.getKey(), freezeJson(entry.getValue())));
            return result;
        }
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return new JsonPrimitive(primitive.getAsBoolean());
        if (primitive.isString()) return new JsonPrimitive(primitive.getAsString());
        // Gson deepCopy retains an arbitrary Number object. Capture its exact wire text once instead.
        return new JsonPrimitive(new LazilyParsedNumber(primitive.getAsNumber().toString()));
    }
}
