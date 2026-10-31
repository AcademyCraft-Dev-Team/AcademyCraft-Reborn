package org.academy.internal.common.ability.program;

import com.google.gson.JsonObject;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.*;
import org.academy.internal.common.ability.program.registry.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.registry.BaseAbilityProgramDefinition;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProgramSessionCapacityTest {
    @Test
    void defaultCapacityRejects257thAndReleasesExactlyTheCancelledLifecycle() {
        var category = AcademyCraft.academy("accelerator");
        var definition = AbilityProgramDefinitions.require(category);
        var entry = BaseAbilityProgramDefinition.entryId(category);
        var compiled = definition.compile(new ProgramGraph(List.of(new ProgramGraph.Node(
                0, entry, 1, new JsonObject())), List.of()), Set.of());
        assertTrue(compiled.valid());
        var scheduler = new ProgramSessionScheduler<Integer>();
        var cancelled = new HashMap<Integer, Integer>();
        ProgramSessionScheduler.SessionListener<Integer> listener = (key, termination) -> {
            assertEquals(ProgramSessionScheduler.TerminationKind.CANCELLED, termination.kind());
            cancelled.merge(key, 1, Integer::sum);
        };
        for (int key = 0; key < ProgramSessionScheduler.DEFAULT_MAX_SESSIONS; key++) {
            assertTrue(scheduler.start(key, compiled.program(), definition.executors(), null, 1, 0, 100, listener));
        }
        assertEquals(256, scheduler.size());
        assertFalse(scheduler.start(256, compiled.program(), definition.executors(), null, 1, 0, 100, listener));
        assertFalse(scheduler.start(0, compiled.program(), definition.executors(), null, 1, 0, 100, listener));
        assertTrue(cancelled.isEmpty(), "Rejected admission must not invent a termination callback");
        assertTrue(scheduler.cancel(128));
        assertFalse(scheduler.cancel(128));
        assertEquals(Map.of(128, 1), cancelled);
        assertEquals(255, scheduler.size());
        assertTrue(scheduler.start(128, compiled.program(), definition.executors(), null, 1, 0, 100, listener),
                "The released key and capacity must admit a new session lifecycle");
        assertEquals(256, scheduler.size());
        assertFalse(scheduler.start(256, compiled.program(), definition.executors(), null, 1, 0, 100, listener));
        scheduler.clear();
        assertEquals(0, scheduler.size());
        assertEquals(257, cancelled.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(2, cancelled.get(128));
        assertEquals(256, cancelled.size());
        scheduler.clear();
        assertEquals(257, cancelled.values().stream().mapToInt(Integer::intValue).sum(),
                "Repeated cleanup must not terminate old lifecycles twice");
    }
}
