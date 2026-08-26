package org.academy.internal.client.ability.mentalout;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Category-specific storage and policy adapter for the shared modular program editor.
 */
public interface ModularProgramEditorSession {
    Component title();

    int slotCount();

    int selectedSlot();

    long revision();

    AbilityProgram editableProgram(int slot);

    AbilityProgram emptyProgram(int slot);

    @Nullable AbilityProgram restoredProgram(int slot);

    Set<Identifier> capabilities();

    void updateLocalProgram(int slot, AbilityProgram program);

    void selectSlot(int slot);

    void saveProgram(int slot, @Nullable AbilityProgram program, long expectedRevision);

    void closed(ModularProgramScreen screen);

    default boolean precisionRules() {
        return false;
    }

    default PrecisionGraph.Diagnostic diagnostic(int slot) {
        return PrecisionGraph.Diagnostic.OK;
    }

    default int diagnosticNode(int slot) {
        return -1;
    }

    default void clearDiagnostic(int slot) {
    }
}
