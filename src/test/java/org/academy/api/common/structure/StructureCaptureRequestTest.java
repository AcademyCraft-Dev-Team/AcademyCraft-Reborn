package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.api.common.structure.upgrade.StructureProvenance;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructureCaptureRequestTest {
    @Test
    void normalizesDuplicatePositionsIntoAnImmutableSelection() {
        var source = new ArrayList<BlockPos>();
        source.add(new BlockPos(1, 2, 3));
        source.add(new BlockPos(1, 2, 3));
        source.add(new BlockPos(4, 5, 6));
        var request = StructureCaptureRequest.of(source);
        assertEquals(2, request.positions().size());
        assertThrows(UnsupportedOperationException.class, () -> request.positions().add(new BlockPos(0, 0, 0)));
        source.add(new BlockPos(7, 8, 9));
        assertEquals(2, request.positions().size());
    }

    @Test
    void defaultsKeepTheStartingBudgets() {
        var request = StructureCaptureRequest.of(List.of(new BlockPos(0, 0, 0)));
        assertEquals(StructureCaptureRequest.MAXIMUM_BLOCKS, request.maximumBlocks());
        assertEquals(StructureCaptureRequest.MAXIMUM_SPAN, request.maximumSpan());
        assertTrue(request.permission().test(null, null));
        assertTrue(request.skillCreation().isEmpty());
    }

    @Test
    void physicalSkillIntentIsExplicitImmutableAndAssignedTheCaptureOperation() {
        var owner = UUID.randomUUID();
        var skill = Identifier.parse("academy:kinetic_throw");
        var request = new StructureCaptureRequest(List.of(BlockPos.ZERO), 1, 1, StructureCaptureRequest.ALLOW, true);
        var marked = request.withSkillCreation(owner, skill);
        assertTrue(request.skillCreation().isEmpty());
        assertEquals(request.positions(), marked.positions());
        assertSame(request.permission(), marked.permission());
        var operation = UUID.randomUUID();
        var metadata = marked.skillCreation().orElseThrow().capture(operation, 42);
        assertEquals(owner, metadata.ownerId().orElseThrow());
        assertEquals(skill, metadata.skillId());
        assertEquals(operation, metadata.creationOperationId());
        assertEquals(operation, metadata.rootLineageId());
        assertEquals(StructureProvenance.CreationKind.SKILL_CAPTURE, metadata.source());
        assertEquals(StructureProvenance.SKILL_REST_POLICY, metadata.restorePolicy().id());
        assertEquals(42, metadata.createdGameTime());
        assertTrue(metadata.parent().isEmpty());
    }

    @Test
    void genericNonPhysicalCaptureCannotAccidentallyBecomeAnAutomaticRestorationTarget() {
        var request = StructureCaptureRequest.of(List.of(BlockPos.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> request.withSkillCreation(UUID.randomUUID(), Identifier.parse("academy:kinetic_throw")));
    }

    @Test
    void rejectsBudgetsOutsideTheirBounds() {
        var position = List.of(new BlockPos(0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureCaptureRequest(position, 0, 8, StructureCaptureRequest.ALLOW));
        assertThrows(IllegalArgumentException.class, () -> new StructureCaptureRequest(position,
                StructureCaptureRequest.MAXIMUM_BLOCKS + 1, 8, StructureCaptureRequest.ALLOW));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureCaptureRequest(position, 8, 0, StructureCaptureRequest.ALLOW));
        assertThrows(IllegalArgumentException.class, () -> new StructureCaptureRequest(position, 8,
                StructureCaptureRequest.MAXIMUM_SPAN + 1, StructureCaptureRequest.ALLOW));
    }
}
