package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.StructureCaptureRequest;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureCaptureEvidenceTest {
    private static final Identifier DIMENSION = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier BACKEND = Identifier.fromNamespaceAndPath("academy", "sublevel");

    @Test
    void onlyThePreviewedSubmissionCanBindTheCompletedSource() {
        var position = new BlockPos(-79, 120, 47);
        var request = request(position);
        var reference = new StructureRef(UUID.randomUUID(), DIMENSION, BACKEND, 1);
        var evidence = new StructureCaptureEvidence(DIMENSION, reference, request,
                List.of(new StructureCaptureEvidence.Cell(position, "minecraft:iron_block")), UUID.randomUUID(), 3);
        assertTrue(evidence.matches(DIMENSION, reference, request));
        var copied = request(position);
        assertEquals(request, copied);
        assertFalse(evidence.matches(DIMENSION, reference, copied), "Value equality cannot forge the submitted request");
        assertFalse(evidence.matches(DIMENSION, reference, request(position.east())));
        assertFalse(evidence.matches(DIMENSION,
                new StructureRef(UUID.randomUUID(), DIMENSION, BACKEND, 1), request));
        assertFalse(evidence.matches(Identifier.fromNamespaceAndPath("minecraft", "the_nether"), reference, request));
    }

    private static StructureCaptureRequest request(BlockPos position) {
        return new StructureCaptureRequest(List.of(position), 256, 24, StructureCaptureRequest.ALLOW, true);
    }
}
