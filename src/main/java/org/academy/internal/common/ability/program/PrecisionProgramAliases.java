package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Compatibility aliases for legacy Precision nodes whose semantics now live in the common algebra.
 */
public final class PrecisionProgramAliases {
    private static final Map<PrecisionGraph.NodeKind, Alias> BY_KIND;
    private static final Map<Identifier, Alias> BY_CANONICAL_TYPE;

    static {
        var byKind = new EnumMap<PrecisionGraph.NodeKind, Alias>(PrecisionGraph.NodeKind.class);
        var byCanonical = new HashMap<Identifier, Alias>();
        register(byKind, byCanonical, new Alias(
                PrecisionGraph.NodeKind.ENTITY_TO_SET,
                CommonProgramNodeIds.collection("entity", "singleton"),
                Map.of("entity", "value"),
                Map.of("entities", "values")
        ));
        register(byKind, byCanonical, new Alias(
                PrecisionGraph.NodeKind.UNION,
                CommonProgramNodeIds.collection("entity", "union"),
                Map.of("left", "left", "right", "right"),
                Map.of("entities", "values")
        ));
        register(byKind, byCanonical, new Alias(
                PrecisionGraph.NodeKind.INTERSECTION,
                CommonProgramNodeIds.collection("entity", "intersection"),
                Map.of("left", "left", "right", "right"),
                Map.of("entities", "values")
        ));
        register(byKind, byCanonical, new Alias(
                PrecisionGraph.NodeKind.SUBTRACT_SET,
                CommonProgramNodeIds.collection("entity", "difference"),
                Map.of("left", "left", "right", "right"),
                Map.of("entities", "values")
        ));
        register(byKind, byCanonical, new Alias(
                PrecisionGraph.NodeKind.ENTITY_POSITION,
                CommonProgramNodeIds.ENTITY_POSITION,
                Map.of("entity", "entity"),
                Map.of("destination", "position")
        ));
        BY_KIND = Map.copyOf(byKind);
        BY_CANONICAL_TYPE = Map.copyOf(byCanonical);
    }

    private PrecisionProgramAliases() {
    }

    public static @Nullable Alias legacy(PrecisionGraph.NodeKind kind) {
        return BY_KIND.get(kind);
    }

    public static @Nullable Alias canonical(Identifier type) {
        return BY_CANONICAL_TYPE.get(type);
    }

    public static boolean isLegacyAlias(PrecisionGraph.NodeKind kind) {
        return BY_KIND.containsKey(kind);
    }

    public static AbilityProgram canonicalize(AbilityProgram program) {
        var aliases = new HashMap<Integer, Alias>();
        var nodes = new ArrayList<ProgramGraph.Node>(program.graph().nodes().size());
        for (var node : program.graph().nodes()) {
            var kind = PrecisionProgramNodeIds.kind(node.type());
            var alias = kind == null ? null : legacy(kind);
            if (alias == null) {
                nodes.add(node);
                continue;
            }
            aliases.put(node.id(), alias);
            var type = CommonProgramNodeCatalog.INSTANCE.find(alias.canonicalType());
            if (type == null) {
                throw new IllegalStateException("Missing common alias node " + alias.canonicalType());
            }
            nodes.add(new ProgramGraph.Node(
                    node.id(),
                    alias.canonicalType(),
                    type.schemaVersion(),
                    new JsonObject()
            ));
        }
        if (aliases.isEmpty()) return program;
        var edges = program.graph().edges().stream().map(edge -> {
            var from = aliases.get(edge.from().nodeId());
            var to = aliases.get(edge.to().nodeId());
            return new ProgramGraph.Edge(
                    new ProgramGraph.Endpoint(
                            edge.from().nodeId(),
                            from == null ? edge.from().port() : from.canonicalOutput(edge.from().port())
                    ),
                    new ProgramGraph.Endpoint(
                            edge.to().nodeId(),
                            to == null ? edge.to().port() : to.canonicalInput(edge.to().port())
                    )
            );
        }).toList();
        return new AbilityProgram(
                program.schemaVersion(),
                program.id(),
                program.name(),
                program.category(),
                new ProgramGraph(nodes, edges),
                program.editorLayout()
        );
    }

    public static ProgramBook canonicalize(ProgramBook book) {
        var changed = false;
        var slots = new ArrayList<ProgramBook.Slot>(book.slots().size());
        for (var slot : book.slots()) {
            if (slot.empty()) {
                slots.add(slot);
                continue;
            }
            var normalized = canonicalize(slot.program());
            changed |= normalized != slot.program();
            slots.add(new ProgramBook.Slot(normalized));
        }
        return changed
                ? new ProgramBook(book.schemaVersion(), book.revision(), book.selectedSlot(), slots)
                : book;
    }

    private static void register(
            Map<PrecisionGraph.NodeKind, Alias> byKind,
            Map<Identifier, Alias> byCanonical,
            Alias alias
    ) {
        if (byKind.putIfAbsent(alias.legacyKind(), alias) != null
                || byCanonical.putIfAbsent(alias.canonicalType(), alias) != null) {
            throw new IllegalStateException("Duplicate Precision program alias " + alias.legacyKind());
        }
    }

    public record Alias(
            PrecisionGraph.NodeKind legacyKind,
            Identifier canonicalType,
            Map<String, String> inputPorts,
            Map<String, String> outputPorts
    ) {
        public Alias {
            inputPorts = Map.copyOf(inputPorts);
            outputPorts = Map.copyOf(outputPorts);
        }

        public String canonicalInput(String legacyPort) {
            return inputPorts.getOrDefault(legacyPort, legacyPort);
        }

        public String canonicalOutput(String legacyPort) {
            return outputPorts.getOrDefault(legacyPort, legacyPort);
        }

        public String legacyInput(String canonicalPort) {
            return reverse(inputPorts, canonicalPort);
        }

        public String legacyOutput(String canonicalPort) {
            return reverse(outputPorts, canonicalPort);
        }

        private static String reverse(Map<String, String> ports, String canonicalPort) {
            return ports.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(canonicalPort))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(canonicalPort);
        }
    }
}
