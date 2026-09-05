package org.academy.internal.client.ability.program;

import net.minecraft.network.chat.Component;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramDiagnostic;
import org.academy.internal.common.ability.program.ProgramEditorNodeCatalog;

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
            int nodeId, String port, Component reason
    ) {
        var result = reason.copy();
        var node = program == null ? null : program.graph().nodes().stream()
                .filter(value -> value.id() == nodeId).findFirst().orElse(null);
        var entry = node == null ? null : catalog.entry(node.type());
        if (nodeId >= 0) {
            result.append(Component.translatable("screen.academy.program.diagnostic.node",
                    entry == null ? Component.literal("#" + nodeId)
                            : Component.translatable(entry.translationKey()), nodeId));
        }
        if (port != null && !port.isBlank()) {
            result.append(Component.translatable("screen.academy.program.diagnostic.port",
                    entry == null ? Component.literal(port)
                            : Component.translatable(entry.portTranslationKey(port))));
        }
        return result;
    }
}
