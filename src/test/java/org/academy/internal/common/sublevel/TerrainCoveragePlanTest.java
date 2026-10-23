package org.academy.internal.common.sublevel;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TerrainCoveragePlanTest {
    @Test
    void separatedBodiesKeepTheirOwnRequiredSectionsWithoutEnumeratingTheGap() {
        var first = new AABB(8, 65, 8, 9, 66, 9);
        var second = new AABB(8200, 65, 8, 8201, 66, 9);
        var plan = TerrainColliderCache.planCoverage(List.of(first, second), -64, 320);
        assertTrue(plan.bounded());
        assertEquals(List.of(new TerrainColliderCache.SectionKey(0, 4, 0),
                new TerrainColliderCache.SectionKey(512, 4, 0)), plan.required());
        assertTrue(plan.required().size() + plan.nearby().size() <= 54);
        assertTrue(plan.nearby().stream().allMatch(key -> key.x() <= 1 || key.x() >= 511));
        // This was the performance regression: an enclosing union makes distant empty space
        // compete with both bodies' actual terrain and previously silently selected 16 sections.
        assertFalse(TerrainColliderCache.planCoverage(List.of(first.minmax(second)), -64, 320).bounded());
    }

    @Test
    void voidAndAboveWorldBodiesCannotStarveAnInWorldStructure() {
        var current = new AABB(8, 65, 8, 9, 66, 9);
        var plan = TerrainColliderCache.planCoverage(List.of(
                new AABB(-100_000, -100_000, -100_000, 100_000, -65, 100_000),
                new AABB(-100_000, 320, -100_000, 100_000, 100_000, 100_000), current), -64, 320);
        assertTrue(plan.bounded());
        assertEquals(List.of(new TerrainColliderCache.SectionKey(0, 4, 0)), plan.required());
        assertTrue(plan.nearby().stream().allMatch(key -> key.y() >= -4 && key.y() < 20));
    }

    @Test
    void touchingSectionBoundaryIncludesItsSupportingSection() {
        var plan = TerrainColliderCache.planCoverage(List.of(new AABB(8, 80, 8, 9, 81, 9)), -64, 320);
        assertTrue(plan.required().contains(new TerrainColliderCache.SectionKey(0, 4, 0)));
        assertTrue(plan.required().contains(new TerrainColliderCache.SectionKey(0, 5, 0)));
    }

    @Test
    void requiredCoverageOverflowIsExplicitAndBoundedInsteadOfAir() {
        var bounds = new ArrayList<AABB>();
        for (int index = 0; index <= TerrainColliderCache.MAX_CANDIDATE_SECTIONS; index++) {
            bounds.add(new AABB(index * 32 + 8, 65, 8, index * 32 + 9, 66, 9));
        }
        var plan = TerrainColliderCache.planCoverage(bounds, -64, 320);
        assertFalse(plan.bounded());
        assertEquals(TerrainColliderCache.MAX_CANDIDATE_SECTIONS, plan.required().size());
        assertTrue(plan.nearby().isEmpty());
    }

    @Test
    void firstFastMovingTickRequiresTheNextSectionBeforeThePoseChanges() {
        var current = new AABB(8, 65, 8, 9, 66, 9);
        var motion = new SubLevelPhysicsUniverse.Motion(new PhysicsPose(new PhysicsVector(8, 65, 8),
                PhysicsQuaternion.IDENTITY), new PhysicsVector(400, 0, 0), PhysicsVector.ZERO,
                new PhysicsVector(0.5, 0.5, 0.5), 1, false);
        var swept = SubLevelRuntime.sweptTerrainBounds(current, new AABB(0, 0, 0, 1, 1, 1), motion, 0.05);
        assertTrue(swept.maxX >= 29);
        assertTrue(TerrainColliderCache.planCoverage(List.of(swept), -64, 320).required()
                .contains(new TerrainColliderCache.SectionKey(1, 4, 0)));
    }

    @Test
    void firstAngularCommandCoversItsEntireArcBeforeAnyAngularVelocityExists() {
        var bounds = new AABB(0, 0, 0, 12, 1, 1);
        var motion = new SubLevelPhysicsUniverse.Motion(new PhysicsPose(new PhysicsVector(8, 65, 8),
                PhysicsQuaternion.IDENTITY), new PhysicsVector(-20, 10, 30), PhysicsVector.ZERO,
                new PhysicsVector(2, 0.5, 0.5), 12, true);
        var swept = SubLevelRuntime.angularTerrainBounds(bounds, motion, 0.05);
        var pose = org.academy.api.common.structure.StructurePose.identity(new Vec3(8, 65, 8));
        var localCenter = new Vec3(2, 0.5, 0.5);
        for (int sample = 0; sample <= 36; sample++) {
            var rotation = org.academy.internal.common.structure.upgrade.control.OrientationTargets.axisAngle(
                    new Vec3(0, 1, 1), sample * Math.PI / 18);
            var rotated = org.academy.internal.common.structure.upgrade.control.OrientationTargets.aroundCenterOfMass(
                    pose, localCenter, rotation);
            for (int corner = 0; corner < 8; corner++) {
                var point = rotated.localToWorldPoint(new Vec3((corner & 1) == 0 ? 0 : 12,
                        (corner & 2) == 0 ? 0 : 1, (corner & 4) == 0 ? 0 : 1));
                assertTrue(swept.inflate(1.0E-8).contains(point));
                assertTrue(swept.inflate(1.0E-8).contains(point.add(-1, 0.5 - 4.905 * 0.05 * 0.05, 1.5)));
            }
        }
    }

    @Test
    void requiredChunkTicketsExcludeHaloAndDeduplicateVerticalSections() {
        var coverage = TerrainColliderCache.planCoverage(List.of(
                new AABB(8, 64, 8, 9, 96, 9), new AABB(8200, 65, 8, 8201, 66, 9)), -64, 320);
        var chunks = TerrainColliderCache.planRequiredChunks(coverage.required());
        assertTrue(chunks.bounded());
        assertEquals(List.of(new ChunkPos(0, 0), new ChunkPos(512, 0)), chunks.required());
        assertTrue(coverage.nearby().stream().anyMatch(section -> section.z() != 0));
    }

    @Test
    void chunkTicketLimitAllowsExactly64AndRejects65WithoutGrowingThePlan() {
        var sections = new ArrayList<TerrainColliderCache.SectionKey>();
        for (int index = 0; index < TerrainColliderCache.MAX_REQUIRED_CHUNKS; index++) {
            sections.add(new TerrainColliderCache.SectionKey(index, 4, -2));
            sections.add(new TerrainColliderCache.SectionKey(index, 5, -2));
        }
        var full = TerrainColliderCache.planRequiredChunks(sections);
        assertTrue(full.bounded());
        assertEquals(TerrainColliderCache.MAX_REQUIRED_CHUNKS, full.required().size());
        sections.add(new TerrainColliderCache.SectionKey(TerrainColliderCache.MAX_REQUIRED_CHUNKS, 4, -2));
        var overflow = TerrainColliderCache.planRequiredChunks(sections);
        assertFalse(overflow.bounded());
        assertEquals(full.required(), overflow.required());
    }

    @Test
    void outOfWorldBodiesDoNotAcquireTickets() {
        var coverage = TerrainColliderCache.planCoverage(List.of(
                new AABB(-100_000, -100_000, -100_000, 100_000, -65, 100_000),
                new AABB(-100_000, 320, -100_000, 100_000, 100_000, 100_000)), -64, 320);
        var chunks = TerrainColliderCache.planRequiredChunks(coverage.required());
        assertTrue(chunks.bounded());
        assertTrue(chunks.required().isEmpty());
    }

    @Test
    void motionWithinCoveredSectionsReusesPlanButCrossingBoundaryExpandsCoverage() {
        var planner = new TerrainColliderCache.CoveragePlanner();
        var first = planner.plan(List.of(new AABB(2, 66, 2, 3, 67, 3)), -64, 320);
        assertSame(first, planner.plan(List.of(new AABB(12, 68, 10, 13, 69, 11)), -64, 320));
        var crossed = planner.plan(List.of(new AABB(15.8, 68, 10, 16.8, 69, 11)), -64, 320);
        assertNotSame(first, crossed);
        assertTrue(crossed.required().contains(new TerrainColliderCache.SectionKey(1, 4, 0)));
    }

    @Test
    void memoizedCoverageRetainsFastSweepAndDoesNotReuseInvalidOrDifferentHeightPlans() {
        var planner = new TerrainColliderCache.CoveragePlanner();
        var small = List.of(new AABB(8, 65, 8, 9, 66, 9));
        var first = planner.plan(small, -64, 320);
        var fast = planner.plan(List.of(new AABB(8, 65, 8, 29, 66, 9)), -64, 320);
        assertNotSame(first, fast);
        assertTrue(fast.required().contains(new TerrainColliderCache.SectionKey(1, 4, 0)));
        var invalid = planner.plan(List.of(new AABB(Double.NaN, 65, 8, 9, 66, 9)), -64, 320);
        assertFalse(invalid.bounded());
        var recovered = planner.plan(small, -64, 320);
        assertTrue(recovered.bounded());
        assertNotSame(first, recovered);
        var aboveWorld = planner.plan(small, -64, 64);
        assertTrue(aboveWorld.required().isEmpty());
        planner.clear();
        assertNotSame(aboveWorld, planner.plan(small, -64, 64));
    }

    @Test
    void aMissingDistantSectionCannotInvalidateCoveredLocalBodies() {
        var local = new AABB(8, 65, 8, 9, 66, 9);
        var distant = new AABB(8200, 65, 8, 8201, 66, 9);
        var localPlan = TerrainColliderCache.planRequiredCoverage(List.of(local), -64, 320);
        var distantPlan = TerrainColliderCache.planRequiredCoverage(List.of(distant), -64, 320);
        var receipts = Set.copyOf(localPlan.required());
        assertFalse(TerrainColliderCache.covers(
                TerrainColliderCache.planRequiredCoverage(List.of(local, distant), -64, 320), receipts::contains));
        assertTrue(TerrainColliderCache.covers(localPlan, receipts::contains));
        assertFalse(TerrainColliderCache.covers(distantPlan, receipts::contains));
    }

    @Test
    void oversizedIndividualDemandDoesNotPoisonAnotherBodiesAdmission() {
        var oversized = new AABB(-100_000, 65, -100_000, 100_000, 66, 100_000);
        var local = new AABB(8, 65, 8, 9, 66, 9);
        var aggregate = TerrainColliderCache.planRequiredCoverage(List.of(oversized, local), -64, 320);
        assertFalse(aggregate.bounded());
        var receipts = Set.copyOf(aggregate.required());
        assertFalse(TerrainColliderCache.covers(
                TerrainColliderCache.planRequiredCoverage(List.of(oversized), -64, 320), receipts::contains));
        assertTrue(TerrainColliderCache.covers(
                TerrainColliderCache.planRequiredCoverage(List.of(local), -64, 320), receipts::contains));
    }

    @Test
    void oversizedSolidBodyCannotReserveItsPartialPrefixAndStarveASmallerBody() {
        var large = new ArrayList<TerrainColliderCache.SectionKey>();
        // 72 sections in just nine chunks: this is within the candidate/ticket limits,
        // but cannot fit the 64-solid-section cache even if it is the nearest request.
        for (int x = 0; x < 9; x++) for (int y = 0; y < 8; y++) {
            large.add(new TerrainColliderCache.SectionKey(x, y, 0));
        }
        var small = List.of(new TerrainColliderCache.SectionKey(20, 4, 0));
        var admission = new TerrainColliderCache.AdmissionBudget(64, 28_000);
        assertTrue(TerrainColliderCache.planRequiredChunks(large).bounded());
        assertFalse(admission.fitsKnown(large, key -> 1));
        assertFalse(admission.fitsComplete(large, key -> 1));
        assertTrue(admission.required().isEmpty(), "Failed groups must reserve no partial prefix");
        assertTrue(admission.fitsComplete(small, key -> 1));
        admission.reserve(small);
        assertEquals(Set.copyOf(small), admission.required());
    }

    @Test
    void incompleteRequestsCannotReserveTicketsOrNativeBudget() {
        var unknown = List.of(new TerrainColliderCache.SectionKey(0, 4, 0));
        var known = List.of(new TerrainColliderCache.SectionKey(1, 4, 0));
        var costs = Map.of(known.getFirst(), 3);
        var admission = new TerrainColliderCache.AdmissionBudget(1, 4);
        assertTrue(admission.fitsKnown(unknown, key -> costs.getOrDefault(key, -1)));
        assertFalse(admission.fitsComplete(unknown, key -> costs.getOrDefault(key, -1)));
        assertTrue(admission.required().isEmpty());
        assertTrue(admission.fitsComplete(known, key -> costs.getOrDefault(key, -1)));
        admission.reserve(known);
        assertFalse(admission.fitsComplete(unknown, key -> 3));
        assertEquals(Set.copyOf(known), admission.required());
    }

    @Test
    void completeGroupsShareReceiptsAndEmptySectionsDoNotConsumeNativeBudget() {
        var first = new TerrainColliderCache.SectionKey(0, 4, 0);
        var empty = new TerrainColliderCache.SectionKey(0, 5, 0);
        var second = new TerrainColliderCache.SectionKey(1, 4, 0);
        var costs = Map.of(first, 3, empty, 0, second, 2);
        var admission = new TerrainColliderCache.AdmissionBudget(2, 5);
        assertTrue(admission.fitsComplete(List.of(first, empty), costs::get));
        admission.reserve(List.of(first, empty));
        assertTrue(admission.fitsComplete(List.of(first, second), costs::get));
        admission.reserve(List.of(first, second));
        assertEquals(Set.of(first, empty, second), admission.required());
    }

    @Test
    void impossibleChunkGroupCannotClaimFirst64TicketsAheadOfALocalRequest() {
        var large = new ArrayList<TerrainColliderCache.SectionKey>();
        for (int x = 0; x < 65; x++) large.add(new TerrainColliderCache.SectionKey(x, 4, 0));
        var admission = new TerrainColliderCache.AdmissionBudget(64, 28_000);
        assertFalse(admission.fitsKnown(large, key -> -1));
        assertTrue(admission.required().isEmpty());
        assertTrue(admission.fitsComplete(List.of(new TerrainColliderCache.SectionKey(100, 4, 0)), key -> 1));
    }

    @Test
    void admissionUsesDistanceToThePhysicalBoundsAndStableThirtyTwoBlockBands() {
        var player = List.of(new Vec3(0, 65, 0));
        assertEquals(0, SubLevelRuntime.terrainDistanceBand(new AABB(1, 65, 1, 2, 66, 2), player));
        assertEquals(0, SubLevelRuntime.terrainDistanceBand(new AABB(25, 65, 1, 26, 66, 2), player));
        assertTrue(SubLevelRuntime.terrainDistanceBand(new AABB(1000, 65, 1, 1001, 66, 2), player) > 20);
        assertEquals(0, SubLevelRuntime.terrainDistanceBand(new AABB(-300, 60, -5, 3, 70, 5), player));
        assertEquals(Integer.MAX_VALUE, SubLevelRuntime.terrainDistanceBand(
                new AABB(1, 65, 1, 2, 66, 2), List.of()));
    }
}
