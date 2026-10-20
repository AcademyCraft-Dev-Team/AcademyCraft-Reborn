package org.academy.internal.common.sublevel.pipeline;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.structure.StructurePipeline;
import org.academy.api.common.structure.StructureRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipelineGeometryCompilerTest {
    private static final StructureRef REF = new StructureRef(UUID.randomUUID(),
            Identifier.fromNamespaceAndPath("minecraft", "overworld"),
            Identifier.fromNamespaceAndPath("academy", "sublevel"), 1);
    private static final StructurePipeline.Epoch EPOCH = new StructurePipeline.Epoch(REF, UUID.randomUUID(), 1);
    private static final StructurePipeline.WorkLimit LIMIT =
            new StructurePipeline.WorkLimit(1_000_000_000L, 10_000, 1);
    private final PipelineGeometryCompiler compiler = new PipelineGeometryCompiler();

    private static StructurePipeline.CellShape full(int x, int y, int z, int material) {
        return new StructurePipeline.CellShape(new BlockPos(x, y, z),
                List.of(new AABB(0, 0, 0, 1, 1, 1)), material);
    }

    @Test
    void fullCellsMergeIntoOneExactPart() {
        var cells = new ArrayList<StructurePipeline.CellShape>();
        for (int x = 0; x < 8; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 8; z++) cells.add(full(x, y, z, 7));
        var geometry = compiler.compile(new StructurePipeline.GeometryInput(EPOCH, cells, false), LIMIT);
        assertTrue(geometry.complete());
        assertEquals(128, geometry.visitedCells());
        assertEquals(1, geometry.parts().size());
        var part = geometry.parts().getFirst();
        assertEquals(7, part.material());
        assertEquals(0.0, part.bounds().minX, 1.0E-9);
        assertEquals(8.0, part.bounds().maxX, 1.0E-9);
        assertEquals(2.0, part.bounds().maxY, 1.0E-9);
        assertEquals(8.0, part.bounds().maxZ, 1.0E-9);
    }

    @Test
    void materialsStaySeparate() {
        var cells = List.of(full(0, 0, 0, 1), full(1, 0, 0, 2));
        var geometry = compiler.compile(new StructurePipeline.GeometryInput(EPOCH, cells, false), LIMIT);
        assertEquals(2, geometry.parts().size());
        assertEquals(java.util.Set.of(1, 2), geometry.parts().stream()
                .map(StructurePipeline.ShapePart::material).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void partialShapesKeepTheirRealBoxes() {
        var slab = new StructurePipeline.CellShape(new BlockPos(3, 4, 5),
                List.of(new AABB(0, 0, 0, 1, 0.5, 1)), 4);
        var geometry = compiler.compile(new StructurePipeline.GeometryInput(EPOCH, List.of(slab), false), LIMIT);
        assertEquals(1, geometry.parts().size());
        assertEquals(new AABB(3, 4, 5, 4, 4.5, 6), geometry.parts().getFirst().bounds());
    }

    @Test
    void hollowCenterIsNotFilledByTheHull() {
        var cells = new ArrayList<StructurePipeline.CellShape>();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            if (x == 1 && z == 1) continue;
            cells.add(full(x, 0, z, 3));
        }
        var geometry = compiler.compile(new StructurePipeline.GeometryInput(EPOCH, cells, false), LIMIT);
        assertEquals(8, geometry.parts().stream().mapToDouble(p ->
                p.bounds().getXsize() * p.bounds().getYsize() * p.bounds().getZsize()).sum(), 1.0E-9);
        for (var part : geometry.parts()) {
            assertFalse(part.bounds().contains(1.5, 0.5, 1.5), "the missing center must stay empty");
        }
    }

    @Test
    void cellBudgetReturnsAnIncompleteSlice() {
        var cells = new ArrayList<StructurePipeline.CellShape>();
        for (int x = 0; x < 32; x++) cells.add(full(x, 0, 0, 1));
        var geometry = compiler.compile(new StructurePipeline.GeometryInput(EPOCH, cells, false),
                new StructurePipeline.WorkLimit(1_000_000_000L, 10, 1));
        assertFalse(geometry.complete());
        assertEquals(10, geometry.visitedCells());
        assertFalse(geometry.parts().isEmpty());
    }

    @Test
    void duplicateCellIsRejected() {
        var duplicate = new StructurePipeline.GeometryInput(EPOCH,
                List.of(full(0, 0, 0, 1), full(0, 0, 0, 1)), false);
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(duplicate, LIMIT));
    }

    @Test
    void invalidBoxIsRejected() {
        var invalid = new StructurePipeline.CellShape(new BlockPos(0, 0, 0),
                List.of(new AABB(0, 0, 0, 0, 1, 1)), 1);
        assertThrows(IllegalArgumentException.class,
                () -> compiler.compile(new StructurePipeline.GeometryInput(EPOCH, List.of(invalid), false), LIMIT));
    }

    @Test
    void emptyInputIsComplete() {
        var geometry = compiler.compile(new StructurePipeline.GeometryInput(EPOCH, List.of(), true), LIMIT);
        assertTrue(geometry.complete());
        assertEquals(0, geometry.visitedCells());
        assertTrue(geometry.parts().isEmpty());
    }
}
