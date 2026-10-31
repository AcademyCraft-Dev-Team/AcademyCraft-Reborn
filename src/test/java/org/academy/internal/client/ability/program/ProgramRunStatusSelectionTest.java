package org.academy.internal.client.ability.program;

import net.minecraft.resources.Identifier;
import org.academy.api.common.ability.program.ProgramRunStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgramRunStatusSelectionTest {
    private static final UUID TOKEN = UUID.randomUUID(), PROGRAM = UUID.randomUUID();
    private static ProgramRunStatus.Target target(String dimension, int slot, long revision) {
        return new ProgramRunStatus.Target(Identifier.parse(dimension), Identifier.parse("academy:accelerator"), slot, PROGRAM, revision);
    }
    private static ProgramRunStatus value(UUID token, ProgramRunStatus.Target target, long run, long update, ProgramRunStatus.Phase phase) {
        return new ProgramRunStatus(token, target, run, update, phase, ProgramRunStatus.WaitKind.NONE,
                -1, -1, null, -1, -1, -1, 100, "", "", "", false);
    }
    @Test void olderRunAndDuplicateUpdateCannotOverwriteCurrentProgress() {
        var target = target("minecraft:overworld", 0, 3);
        var selection = new ProgramRunStatusSelection(TOKEN, target);
        assertTrue(selection.accept(value(TOKEN, target, 10, 1, ProgramRunStatus.Phase.WAITING)));
        assertFalse(selection.accept(value(TOKEN, target, 9, 2, ProgramRunStatus.Phase.COMPLETED)));
        assertTrue(selection.accept(value(TOKEN, target, 11, 3, ProgramRunStatus.Phase.RUNNING)));
        assertFalse(selection.accept(value(TOKEN, target, 11, 3, ProgramRunStatus.Phase.COMPLETED)));
        assertTrue(selection.accept(value(TOKEN, target, 11, 4, ProgramRunStatus.Phase.COMPLETED)));
    }
    @Test void oldScreenRevisionSlotAndDimensionAreIsolated() {
        var target = target("minecraft:overworld", 0, 3);
        var selection = new ProgramRunStatusSelection(TOKEN, target);
        assertFalse(selection.accept(value(UUID.randomUUID(), target, 1, 1, ProgramRunStatus.Phase.COMPLETED)));
        assertFalse(selection.accept(value(TOKEN, target("minecraft:overworld", 0, 2), 1, 1, ProgramRunStatus.Phase.COMPLETED)));
        assertFalse(selection.accept(value(TOKEN, target("minecraft:overworld", 1, 3), 1, 1, ProgramRunStatus.Phase.COMPLETED)));
        assertFalse(selection.accept(value(TOKEN, target("minecraft:the_nether", 0, 3), 1, 1, ProgramRunStatus.Phase.COMPLETED)));
        assertNull(selection.current());
    }
}
