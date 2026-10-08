package org.academy.api.client.render.vfxgraph.runtime;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StableEffectAdmissionTest {
    @Test void overloadedStationaryCandidatesKeepExactlyTheSame192Residents() {
        for (int population : new int[] {193, 256, 1024}) {
            var admission = new StableEffectAdmission<Integer>(192, 4, .85);
            for (int i = 0; i < population; i++) admission.offer(i, i < 16 ? 3 : 1, 400 + i);
            var original = Set.copyOf(admission.reconcile(0));
            assertEquals(192, original.size());
            for (int tick = 1; tick <= 240; tick++) {
                for (int i = 0; i < population; i++) admission.offer(i, i < 16 ? 3 : 1, 400 + i);
                assertEquals(original, admission.reconcile(tick), "No FIFO graph recreation under overload");
            }
        }
    }

    @Test void localOrContactPriorityCanReplaceAResidentDuringItsHold() {
        var admission = new StableEffectAdmission<String>(2, 4, .85);
        admission.offer("near", 1, 25); admission.offer("far", 1, 100);
        assertEquals(Set.of("near", "far"), admission.reconcile(0));
        admission.offer("local", 3, 1000);
        assertEquals(Set.of("near", "local"), admission.reconcile(1));
        admission.remove("local");
        assertEquals(Set.of("near", "far"), admission.reconcile(2));
    }

    @Test void distanceHysteresisUsesDistanceSquaredAndWaitsForMinimumHold() {
        var admission = new StableEffectAdmission<String>(1, 4, .85);
        admission.offer("resident", 1, 100); admission.reconcile(0);
        admission.offer("small_motion", 1, 81);
        assertEquals(Set.of("resident"), admission.reconcile(5));
        admission.offer("closer", 1, 64);
        assertEquals(Set.of("closer"), admission.reconcile(6));
        admission.offer("nearest", 1, 1);
        assertEquals(Set.of("closer"), admission.reconcile(7));
        assertEquals(Set.of("nearest"), admission.reconcile(10));
    }

    @Test void aHeldWorstDoesNotHideAMatureEligibleResident() {
        var admission = new StableEffectAdmission<String>(2, 4, .85);
        admission.offer("mature", 1, 100); admission.reconcile(0);
        admission.offer("held", 1, 200); admission.reconcile(4);
        admission.offer("closer", 1, 49);
        assertEquals(Set.of("held", "closer"), admission.reconcile(5));
        admission.clear();
        assertTrue(admission.selected().isEmpty());
        assertTrue(admission.reconcile(6).isEmpty());
    }

    @Test void invalidDistancesCannotDisplaceVisibleCandidates() {
        var admission = new StableEffectAdmission<String>(1, 0, .85);
        admission.offer("visible", 1, 16); admission.offer("invalid", 1, Double.NaN);
        assertEquals(Set.of("visible"), admission.reconcile(0));
    }
}
