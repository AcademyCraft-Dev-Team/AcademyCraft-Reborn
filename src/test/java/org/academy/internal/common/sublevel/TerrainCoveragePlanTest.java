package org.academy.internal.common.sublevel;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.academy.internal.common.structure.physics.PhysicsPose;
import org.academy.internal.common.structure.physics.PhysicsQuaternion;
import org.academy.internal.common.structure.physics.PhysicsVector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
}
