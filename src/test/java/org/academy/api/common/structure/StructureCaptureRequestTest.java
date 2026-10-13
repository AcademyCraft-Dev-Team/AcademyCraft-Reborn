package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
