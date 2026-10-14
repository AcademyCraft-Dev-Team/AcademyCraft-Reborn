package org.academy.internal.client.ability.program;

import net.minecraft.network.chat.Component;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramDiagnostic;
import org.academy.internal.common.ability.program.editor.ProgramEditorNodeCatalog;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Localized diagnostics shared by the editor and execution feedback. */
public final class ProgramDiagnosticText {
    private ProgramDiagnosticText() {
    }

    public static Component describe(ProgramDiagnostic diagnostic) {
        return Component.translatable("screen.academy.program.diagnostic."
                + diagnostic.code().name().toLowerCase(Locale.ROOT));
    }

    public static Component describe(
            AbilityProgram program, ProgramEditorNodeCatalog catalog, ProgramDiagnostic diagnostic
    ) {
        return locate(program, catalog, diagnostic.nodeId(), diagnostic.port(), describe(diagnostic));
    }

    public static Component locate(
            AbilityProgram program, ProgramEditorNodeCatalog catalog,
            int nodeId, @Nullable String port, Component reason
    ) {
        var result = reason.copy();
        var entry = nodeEntry(program, catalog, nodeId);
        if (nodeId >= 0) {
            result.append(Component.translatable("screen.academy.program.diagnostic.node",
                    nodeLabel(entry, nodeId), nodeId));
        }
        if (port != null && !port.isBlank()) {
            result.append(Component.translatable("screen.academy.program.diagnostic.port",
                    portLabel(entry, port)));
        }
        return result;
    }

    /**
     * Formats a VM runtime failure. A non-blank {@code port} names the missing input of the
     * failing node; {@code detail} carries the machine-readable action failure reason.
     */
    public static Component locateRuntimeFailure(
            AbilityProgram program, ProgramEditorNodeCatalog catalog,
            int nodeId, @Nullable String port, @Nullable String detail, Component reason
    ) {
        var result = reason.copy();
        var entry = nodeEntry(program, catalog, nodeId);
        if (port != null && !port.isBlank()) {
            result.append(Component.translatable("screen.academy.program.diagnostic.missing_input_port",
                    nodeLabel(entry, nodeId), nodeId, portLabel(entry, port)));
        } else if (nodeId >= 0) {
            result.append(Component.translatable("screen.academy.program.diagnostic.node",
                    nodeLabel(entry, nodeId), nodeId));
        }
        if (detail != null && !detail.isBlank()) {
            result.append(Component.translatable("screen.academy.program.diagnostic.detail", detail));
        }
        return result;
    }

    private static ProgramEditorNodeCatalog.@Nullable Entry nodeEntry(
            AbilityProgram program, ProgramEditorNodeCatalog catalog, int nodeId
    ) {
        var node = program == null ? null : program.graph().nodes().stream()
                .filter(value -> value.id() == nodeId).findFirst().orElse(null);
        return node == null ? null : catalog.entry(node.type());
    }

    private static Component nodeLabel(
            ProgramEditorNodeCatalog.@Nullable Entry entry, int nodeId
    ) {
        return entry == null
                ? Component.literal("#" + nodeId)
                : Component.translatable(entry.translationKey());
    }

    private static Component portLabel(
            ProgramEditorNodeCatalog.@Nullable Entry entry, String port
    ) {
        return entry == null
                ? Component.literal(port)
                : Component.translatable(entry.portTranslationKey(port));
    }
}
