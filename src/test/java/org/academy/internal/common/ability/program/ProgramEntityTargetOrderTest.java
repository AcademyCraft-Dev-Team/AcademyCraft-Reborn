package org.academy.internal.common.ability.program;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ProgramEntityTargetOrderTest {
    @Test
    void retainsLegacyStringOrderingAcrossTheUuidSignBoundaryAndResultLimits() {
        var values = new ArrayList<>(List.of(
                UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                UUID.fromString("80000000-0000-0000-0000-000000000000"),
                UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff"),
                UUID.fromString("00000000-0000-0000-0000-000000000000")));
        var random = new Random(0x51A8E);
        for (int count = 0; count < 1024; count++) values.add(new UUID(random.nextLong(), random.nextLong()));
        Collections.shuffle(values, new Random(123));
        var before = List.copyOf(values);
        for (int limit : List.of(0, 1, 32, 128, 1024, 4096)) {
            assertEquals(values.stream().sorted(Comparator.comparing(UUID::toString)).limit(limit).toList(),
                    ProgramEntityTargetOrder.sortedLimit(values, limit, value -> value));
        }
        assertEquals(before, values, "Query ordering must not mutate the spatial-query candidate list");
        assertNotEquals(values.stream().sorted().limit(128).toList(),
                ProgramEntityTargetOrder.sortedLimit(values, 128, value -> value),
                "UUID natural ordering is not a compatible substitute for UUID-string ordering");
    }

    @Test
    void snapshotsEachCandidateIdentityOnceAndKeepsEqualKeyEncounterOrder() {
        var same = new UUID(0, 1);
        var first = new Candidate("first", same);
        var second = new Candidate("second", same);
        var higher = new Candidate("higher", new UUID(0, 2));
        var calls = new AtomicInteger();
        var result = ProgramEntityTargetOrder.sortedLimit(List.of(higher, first, second), 2, value -> {
            calls.incrementAndGet(); return value.uuid;
        });
        assertEquals(List.of(first, second), result);
        assertEquals(3, calls.get(), "Only the current call's candidates should be sampled, once each");
        assertThrows(UnsupportedOperationException.class, () -> result.add(higher));
    }

    @Test
    void aLaterCallReadsChangedIdentityAndNeverReusesAnEarlierQuerySnapshot() {
        var first = new Candidate("first", new UUID(0, 1));
        var second = new Candidate("second", new UUID(0, 2));
        var input = List.of(first, second);
        assertEquals(List.of(first), ProgramEntityTargetOrder.sortedLimit(input, 1, value -> value.uuid));
        first.uuid = new UUID(0, 3);
        assertEquals(List.of(second), ProgramEntityTargetOrder.sortedLimit(input, 1, value -> value.uuid));
    }

    @Test
    void emptySingletonAndZeroLimitKeepTheOriginalShortCircuitBehavior() {
        var value = UUID.randomUUID();
        var calls = new AtomicInteger();
        assertEquals(List.of(), ProgramEntityTargetOrder.sortedLimit(List.<UUID>of(), 128, id -> {
            calls.incrementAndGet(); return id;
        }));
        assertEquals(List.of(value), ProgramEntityTargetOrder.sortedLimit(List.of(value), 128, id -> {
            calls.incrementAndGet(); return id;
        }));
        assertEquals(List.of(), ProgramEntityTargetOrder.sortedLimit(List.of(value, value), 0, id -> {
            calls.incrementAndGet(); return id;
        }));
        assertEquals(0, calls.get());
    }

    private static final class Candidate {
        private final String label;
        private UUID uuid;
        private Candidate(String label, UUID uuid) { this.label = label; this.uuid = uuid; }
        @Override public String toString() { return label; }
    }
}
