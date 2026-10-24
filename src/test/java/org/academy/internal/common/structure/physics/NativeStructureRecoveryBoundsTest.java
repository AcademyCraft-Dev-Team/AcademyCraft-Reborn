package org.academy.internal.common.structure.physics;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeStructureRecoveryBoundsTest {
    private static final Identifier HOST = Identifier.parse("minecraft:overworld");
    private static final UUID ID = new UUID(0, 1);
    private static final UUID OTHER_ID = new UUID(0, 2);
    private static final AABB LIMITS = new AABB(-1000, -64, -1000, 1000, 320, 1000);
    private static final AABB FIRST_SAVED = new AABB(-222, 56.25, 145, -201, 75.25, 168);
    private static final AABB SECOND_SAVED = new AABB(-208, 57.25, 125, -189, 77.25, 144);
    // Collision bounds captured from the two complete cohorts in the legacy test fixture.
    private static final List<AABB> FIRST_OCCUPIED = List.of(
            new AABB(-202.92854841480977, 64.2160287108774, 153.25958892110287,
                    -201.3714798520112, 65.8917171852107, 154.6637392473142),
            new AABB(-216.00000005748743, 61.999999984599356, 150.999999981535,
                    -206.99999999388564, 65.00000003817715, 162.0000000416771));
    private static final List<AABB> SECOND_OCCUPIED = List.of(
            new AABB(-195.88777984468834, 70.92478835036063, 136.24877490448264,
                    -189.88777984468834, 75.92478835036063, 142.24877490448264),
            new AABB(-204.00000007326508, 62.99999937372878, 130.99999996555331,
                    -196.99999927912094, 67.00000087333247, 138.0000000973291));

    @Test
    void suspendedTestCohortGetsRoomToFallWithACompactHorizontalReservation() {
        var old = new AABB(-222, 56.25, 145, -201, 75.25, 168);
        var other = new NativeStructurePhysicsRegionLayout.Region(new UUID(0, 2), HOST,
                new AABB(-208, 57.25, 125, -189, 77.25, 144));
        var occupied = List.of(new AABB(-203, 64.5, 152.8, -201.2, 66.4, 154.4),
                new AABB(-216, 62, 151, -207, 67, 162));
        var result = NativeStructureRecoveryBounds.plan(ID, HOST, old, occupied,
                List.of(new NativeStructurePhysicsRegionLayout.Region(ID, HOST, old), other), LIMITS);

        assertEquals(20.8, result.getXsize(), 1.0e-12);
        assertEquals(24, result.getYsize());
        assertEquals(17, result.getZsize());
        assertTrue(result.minY < 56, "Recovery must leave downward travel beyond the occupied OBB");
        for (var box : occupied) assertContains(result, box);
        assertDoesNotThrow(() -> NativeStructurePhysicsRegionLayout.validate(List.of(
                new NativeStructurePhysicsRegionLayout.Region(ID, HOST, result), other)));
    }

    @Test
    void adjacentLegacyCohortsKeepRotationClearanceWhenRecoveredInSavedOrder() {
        var secondReservation = new NativeStructurePhysicsRegionLayout.Region(OTHER_ID, HOST, SECOND_SAVED);
        var first = NativeStructureRecoveryBounds.plan(ID, HOST, FIRST_SAVED, FIRST_OCCUPIED,
                List.of(new NativeStructurePhysicsRegionLayout.Region(ID, HOST, FIRST_SAVED),
                        secondReservation), LIMITS);
        var second = NativeStructureRecoveryBounds.plan(OTHER_ID, HOST, SECOND_SAVED, SECOND_OCCUPIED,
                List.of(new NativeStructurePhysicsRegionLayout.Region(ID, HOST, first),
                        secondReservation), LIMITS);

        assertEquals(24, first.getYsize());
        assertEquals(24, second.getYsize());
        for (var box : FIRST_OCCUPIED) assertContains(first, box);
        for (var box : SECOND_OCCUPIED) assertContains(second, box);
        assertDoesNotThrow(() -> NativeStructurePhysicsRegionLayout.validate(List.of(
                new NativeStructurePhysicsRegionLayout.Region(ID, HOST, first),
                new NativeStructurePhysicsRegionLayout.Region(OTHER_ID, HOST, second))));
        // The settling tree's last valid OBB before the former maxZ=144 domain was exhausted.
        var settlingTree = new AABB(-195.82991895025847, 63.34083718415572, 136.4615070611576,
                -188.61622033489192, 71.24440580867041, 143.9688752579673);
        assertContains(second, settlingTree);
        assertTrue(second.maxZ - settlingTree.maxZ >= 1.0,
                "The recovered neighbor must retain additional room for the tree's rotation");
        assertEquals(SECOND_SAVED, secondReservation.bounds());
    }

    @Test
    void reverseRecoveryOrderKeepsBothReservationsLegalWithoutClaimingAnOccupiedGap() {
        var firstReservation = new NativeStructurePhysicsRegionLayout.Region(ID, HOST, FIRST_SAVED);
        var second = NativeStructureRecoveryBounds.plan(OTHER_ID, HOST, SECOND_SAVED, SECOND_OCCUPIED,
                List.of(firstReservation,
                        new NativeStructurePhysicsRegionLayout.Region(OTHER_ID, HOST, SECOND_SAVED)), LIMITS);
        assertDoesNotThrow(() -> NativeStructurePhysicsRegionLayout.validate(List.of(firstReservation,
                new NativeStructurePhysicsRegionLayout.Region(OTHER_ID, HOST, second))));
        assertTrue(second.maxZ <= FIRST_SAVED.minZ - NativeStructurePhysicsRegionLayout.MIN_SEPARATION,
                "The first recovery cannot borrow space from the untouched neighbor's saved reservation");
        var first = NativeStructureRecoveryBounds.plan(ID, HOST, FIRST_SAVED, FIRST_OCCUPIED,
                List.of(firstReservation,
                        new NativeStructurePhysicsRegionLayout.Region(OTHER_ID, HOST, second)), LIMITS);
        for (var box : FIRST_OCCUPIED) assertContains(first, box);
        for (var box : SECOND_OCCUPIED) assertContains(second, box);
        assertDoesNotThrow(() -> NativeStructurePhysicsRegionLayout.validate(List.of(
                new NativeStructurePhysicsRegionLayout.Region(ID, HOST, first),
                new NativeStructurePhysicsRegionLayout.Region(OTHER_ID, HOST, second))));
        assertEquals(FIRST_SAVED, firstReservation.bounds());
    }

    @Test
    void horizontalMarginNeverExpandsAnAxisBeyondTheRegionLimit() {
        var old = new AABB(0, 64, 0, 24, 88, 24);
        var occupied = new AABB(1, 66, 1, 23, 69, 22);
        var result = NativeStructureRecoveryBounds.plan(ID, HOST, old, List.of(occupied), List.of(), LIMITS);
        assertEquals(24, result.getXsize());
        assertEquals(24, result.getYsize());
        assertEquals(24, result.getZsize());
        assertContains(result, occupied);
    }

    @Test
    void includesEveryMemberWhenOnlyTheRequestedMemberWouldFit() {
        var old = new AABB(0, 64, 0, 24, 88, 24);
        assertThrows(IllegalStateException.class, () -> NativeStructureRecoveryBounds.plan(ID, HOST, old,
                List.of(new AABB(1, 65, 1, 2, 66, 2), new AABB(30, 65, 1, 31, 66, 2)),
                List.of(new NativeStructurePhysicsRegionLayout.Region(ID, HOST, old)), LIMITS));
    }

    @Test
    void refusesAReservationConflictWithoutDroppingTheOtherOwner() {
        var old = new AABB(0, 64, 0, 20, 84, 20);
        var blocked = new NativeStructurePhysicsRegionLayout.Region(new UUID(0, 2), HOST,
                new AABB(21, 64, 0, 45, 88, 24));
        assertThrows(IllegalStateException.class, () -> NativeStructureRecoveryBounds.plan(ID, HOST, old,
                List.of(new AABB(22, 66, 2, 23, 67, 3)), List.of(blocked), LIMITS));
    }

    @Test
    void emptyCollisionCohortRetainsItsLegalReservation() {
        var old = new AABB(0, 64, 0, 20, 84, 20);
        assertEquals(old, NativeStructureRecoveryBounds.plan(ID, HOST, old, List.of(),
                List.of(new NativeStructurePhysicsRegionLayout.Region(ID, HOST, old)), LIMITS));
    }

    @Test
    void clipsFullVolumeAtBuildHeightWhileKeepingActualGeometryInside() {
        var old = new AABB(0, -64, 0, 20, -44, 20);
        var occupied = new AABB(3, -63, 3, 7, -60, 7);
        var result = NativeStructureRecoveryBounds.plan(ID, HOST, old, List.of(occupied), List.of(), LIMITS);
        assertEquals(-64, result.minY);
        assertEquals(24, result.getYsize());
        assertContains(result, occupied);
    }

    @Test
    void rejectsOutOfWorldAndNonfiniteGeometryBeforePlanning() {
        var old = new AABB(0, 64, 0, 20, 84, 20);
        for (var box : List.of(new AABB(0, -65, 0, 1, -64, 1),
                new AABB(0, 64, 0, Double.POSITIVE_INFINITY, 65, 1))) {
            assertThrows(IllegalArgumentException.class, () ->
                    NativeStructureRecoveryBounds.plan(ID, HOST, old, List.of(box), List.of(), LIMITS));
        }
    }

    private static void assertContains(AABB outer, AABB inner) {
        assertTrue(outer.minX <= inner.minX && outer.minY <= inner.minY && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX && outer.maxY >= inner.maxY && outer.maxZ >= inner.maxZ);
    }
}
