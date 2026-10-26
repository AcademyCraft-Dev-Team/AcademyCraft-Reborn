package org.academy.internal.common.structure.sable;

import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SableRetirementProgressTest {
    private static StructureRef reference() {
        return new StructureRef(UUID.randomUUID(), Identifier.parse("minecraft:overworld"), Identifier.parse("academy:sable"), 0);
    }

    @Test void repeatedStorageFailureBacksOffButChangedEvidenceGetsAShortRetry() {
        var progress = new SableRetirementProgress(); var ref = reference();
        progress.record(ref, SableRetirementStatus.STORAGE_FAILURE, 40, true);
        assertFalse(progress.due(ref, 59)); assertTrue(progress.due(ref, 60));
        progress.record(ref, SableRetirementStatus.STORAGE_FAILURE, 60, true);
        assertEquals(100, progress.get(ref).nextTick());
        progress.record(ref, SableRetirementStatus.STORAGE_FAILURE, 100, true);
        assertEquals(180, progress.get(ref).nextTick());
        progress.record(ref, SableRetirementStatus.EMPTY_SLOT_DURABLE_INDEX, 180, true);
        assertEquals(200, progress.get(ref).nextTick());
        assertEquals(4, progress.get(ref).attempts());
    }

    @Test void longFailureStaysBoundedAndDoesNotDelayAnotherSource() {
        var progress = new SableRetirementProgress(); var blocked = reference();
        long tick = 0;
        for (int i = 0; i < 100; i++) {
            progress.record(blocked, SableRetirementStatus.HOLDING_POINTER_CONFLICT, tick, true);
            long next = progress.get(blocked).nextTick();
            assertTrue(next > tick && next - tick <= 600);
            assertTrue(progress.due(reference(), tick));
            tick = next;
        }
        assertEquals(600, progress.get(blocked).nextTick() - progress.get(blocked).lastTick());
    }

    @Test void completionRetainsBoundedHistoryAndDoesNotCarryRetryDelayIntoNewWork() {
        var progress = new SableRetirementProgress(); var ref = reference();
        progress.record(ref, SableRetirementStatus.CREATION_CONFLICT, 10, true);
        progress.record(ref, SableRetirementStatus.RETIRED, 30, false);
        assertEquals(2, progress.recent().getFirst().attempts());
        assertEquals(SableRetirementStatus.QUEUED, progress.get(ref).status());
        assertTrue(progress.due(ref, 30));
        for (int i=0;i<40;i++) progress.record(reference(), SableRetirementStatus.INTENT_WRITE_FAILED, 40+i, false);
        assertEquals(SableRetirementProgress.RECENT_LIMIT, progress.recent().size());
        assertThrows(UnsupportedOperationException.class, () -> progress.recent().clear());
        assertTrue(progress.recent().stream().noneMatch(value -> value.reference().equals(ref)));
    }
}
