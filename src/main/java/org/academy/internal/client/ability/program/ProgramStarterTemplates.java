package org.academy.internal.client.ability.program;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.program.editor.ProgramEditorDocument;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinition;
import org.academy.internal.common.ability.program.registry.CommonProgramNodeIds;

import java.util.Set;

/** Small editable examples for a player's empty program slot. */
public final class ProgramStarterTemplates {
    private ProgramStarterTemplates() {
    }

    public enum Kind {
        MANUAL,
        CHAT
    }

    public static AbilityProgram create(
            AbilityProgram empty,
            AbilityProgramDefinition definition,
            Set<Identifier> capabilities,
            Kind kind,
            String chatKeyword,
            String outputText
    ) {
        if (!empty.graph().nodes().isEmpty()) {
            throw new IllegalArgumentException("Starter template requires an empty slot");
        }
        var catalog = definition.editorCatalog();
        var entryId = kind == Kind.CHAT ? CommonProgramNodeIds.TRIGGER_CHAT
                : catalog.entries().stream()
                .filter(entry -> entry.id().getPath().endsWith("entry/on_cast"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No manual program entry"))
                .id();
        var entryConfiguration = catalog.entry(entryId).defaultConfiguration().getAsJsonObject().deepCopy();
        if (kind == Kind.CHAT) {
            entryConfiguration.addProperty("mode", "equals");
            entryConfiguration.addProperty("keyword", chatKeyword);
            entryConfiguration.addProperty("sender", "self");
        }
        var outputConfiguration = catalog.entry(CommonProgramNodeIds.DEBUG_OUTPUT)
                .defaultConfiguration().getAsJsonObject().deepCopy();
        outputConfiguration.addProperty("value_type", "text");
        outputConfiguration.addProperty("text", "{value}");
        outputConfiguration.addProperty("audience", "self");
        var valueConfiguration = new JsonObject();
        valueConfiguration.addProperty("value", outputText);

        var document = new ProgramEditorDocument(empty, definition, capabilities);
        var entry = add(document, entryId, 12, 42, entryConfiguration);
        var output = add(entry.document, CommonProgramNodeIds.DEBUG_OUTPUT,
                260, 42, outputConfiguration);
        var value = add(output.document, CommonProgramNodeIds.TEXT_CONSTANT,
                120, 130, valueConfiguration);
        document = value.document;
        document = connect(document, entry.id, "flow", output.id, "flow");
        document = connect(document, value.id, "value", output.id, "value");
        if (!document.validation().valid()) {
            throw new IllegalStateException("Starter template did not compile");
        }
        return document.program();
    }

    private static Added add(
            ProgramEditorDocument document, Identifier type, double x, double y,
            JsonObject configuration
    ) {
        var before = document.program().graph().nodes().stream()
                .map(ProgramGraph.Node::id).collect(java.util.stream.Collectors.toSet());
        var result = document.addNode(type, x, y, configuration);
        if (!result.successful()) throw new IllegalStateException("Starter template node is unavailable");
        var updated = result.document();
        var id = updated.program().graph().nodes().stream()
                .map(ProgramGraph.Node::id).filter(candidate -> !before.contains(candidate))
                .findFirst().orElseThrow();
        return new Added(updated, id);
    }

    private static ProgramEditorDocument connect(
            ProgramEditorDocument document, int from, String fromPort, int to, String toPort
    ) {
        var result = document.connect(new ProgramGraph.Endpoint(from, fromPort),
                new ProgramGraph.Endpoint(to, toPort));
        if (!result.successful()) throw new IllegalStateException("Starter template connection failed");
        return result.document();
    }

    private record Added(ProgramEditorDocument document, int id) {
    }
}
