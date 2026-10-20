package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePipeline;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubLevelPipelineDraftTest {
    private static final StructurePipeline.WorkLimit LIMIT =
            new StructurePipeline.WorkLimit(1_000_000L, 128, 1);
    private static final StructureRef REF = new StructureRef(UUID.randomUUID(),
            Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            Identifier.fromNamespaceAndPath("academy", "sublevel"), 1);
    private static final StructurePipeline.Epoch EPOCH =
            new StructurePipeline.Epoch(REF, UUID.randomUUID(), 1);

    @Test
    void captureSliceRejectsAnInvalidRequestWithoutTouchingTheServer() {
        var draft = new SubLevelPipelineDraft();
        assertEquals(StructurePipeline.CaptureStage.FAILED,
                draft.advanceCapture(null, UUID.randomUUID(), LIMIT).stage());
        assertEquals("invalid_request", draft.advanceCapture(null, UUID.randomUUID(), LIMIT).detail());
        assertEquals("invalid_request", draft.advanceCapture(null, null, LIMIT).detail());
        assertEquals("invalid_request", draft.advanceCapture(null, UUID.randomUUID(), null).detail());
    }

    @Test
    void geometryPortDelegatesAndPreservesTheEpoch() {
        var draft = new SubLevelPipelineDraft();
        var input = new StructurePipeline.GeometryInput(EPOCH, List.of(
                new StructurePipeline.CellShape(new BlockPos(0, 0, 0),
                        List.of(new AABB(0, 0, 0, 1, 1, 1)), 1)), false);
        var geometry = draft.compileGeometry(input, LIMIT);
        assertEquals(EPOCH, geometry.epoch());
        assertTrue(geometry.complete());
        assertEquals(1, geometry.parts().size());
    }

    @Test
    void meshPortCachesAcrossPoseOnlyRequests() {
        var draft = new SubLevelPipelineDraft();
        var request = new StructurePipeline.MeshRequest(EPOCH, 1, 1, List.of(), 9, false);
        assertFalse(draft.planMesh(request).reuseGeometry());
        assertTrue(draft.planMesh(request).reuseGeometry());
        assertEquals(1, draft.cachedMeshEpochs());
    }

    @Test
    void terrainPortRejectsANullHostWithoutThrowing() {
        var draft = new SubLevelPipelineDraft();
        var receipt = draft.publishTerrain(null, null);
        assertEquals(StructurePipeline.PublicationStatus.UNAVAILABLE, receipt.status());
    }
}
