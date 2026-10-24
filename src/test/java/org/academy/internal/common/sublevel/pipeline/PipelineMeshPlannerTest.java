package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructurePipeline;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipelineMeshPlannerTest {
    private static final StructureRef REF = new StructureRef(UUID.randomUUID(),
            Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            Identifier.fromNamespaceAndPath("academy", "sublevel"), 1);
    private static final StructurePipeline.Epoch EPOCH = new StructurePipeline.Epoch(REF, UUID.randomUUID(), 1);
    private final PipelineMeshPlanner planner = new PipelineMeshPlanner();

    private static StructurePipeline.MeshRequest request(long revision, long lighting, long resource,
                                                         List<Long> dirty, int cells, boolean blockEntities) {
        return new StructurePipeline.MeshRequest(new StructurePipeline.Epoch(REF, EPOCH.runtimeId(), revision),
                lighting, resource, dirty, cells, blockEntities);
    }

    @Test
    void firstRequestIsAFullRebuild() {
        var plan = planner.plan(request(1, 1, 1, List.of(), 128, false));
        assertTrue(plan.rebuildSections().isEmpty());
        assertFalse(plan.reuseGeometry());
    }

    @Test
    void poseOnlyUpdatesReuseEverySectionEvenForSmallStructures() {
        planner.plan(request(1, 1, 1, List.of(), 9, false));
        var plan = planner.plan(request(1, 1, 1, List.of(), 9, false));
        assertTrue(plan.rebuildSections().isEmpty());
        assertTrue(plan.reuseGeometry());
        assertEquals(1, planner.cachedEpochs());
    }

    @Test
    void dirtySectionsRebuildTheirGeometry() {
        planner.plan(request(1, 1, 1, List.of(), 128, false));
        var plan = planner.plan(request(1, 1, 1, List.of(4L, 9L), 128, false));
        assertEquals(List.of(4L, 9L), plan.rebuildSections());
        assertFalse(plan.reuseGeometry());
    }

    @Test
    void lightingChangeCanReuseGeometryForTheDirtySections() {
        planner.plan(request(1, 1, 1, List.of(), 128, false));
        var plan = planner.plan(request(1, 2, 1, List.of(7L), 128, false));
        assertEquals(List.of(7L), plan.rebuildSections());
        assertTrue(plan.reuseGeometry());
    }

    @Test
    void resourceReloadForcesFullGeometryRebuild() {
        planner.plan(request(1, 1, 1, List.of(), 128, false));
        var plan = planner.plan(request(1, 1, 2, List.of(), 128, false));
        assertTrue(plan.rebuildSections().isEmpty());
        assertFalse(plan.reuseGeometry());
    }

    @Test
    void aZeroCellEpochDoesNotCountAsAnAlreadyBuiltMesh() {
        assertTrue(planner.plan(request(1, 1, 1, List.of(), 0, false)).reuseGeometry());
        assertFalse(planner.plan(request(1, 1, 1, List.of(), 9, false)).reuseGeometry());
    }

    @Test
    void blockEntitiesAlwaysRenderSeparately() {
        var plan = planner.plan(request(1, 1, 1, List.of(), 9, true));
        assertTrue(plan.renderBlockEntitiesSeparately());
    }

    @Test
    void revisionChangeCreatesANewEpochEntry() {
        planner.plan(request(1, 1, 1, List.of(), 128, false));
        var plan = planner.plan(request(2, 1, 1, List.of(), 128, false));
        assertFalse(plan.reuseGeometry());
        assertEquals(2, planner.cachedEpochs());
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> planner.plan(null));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(request(1, 1, 1, List.of(), -1, false)));
    }
}
