package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructureAnchorTest {
    @Test
    void localAddressCopiesMutablePositionsAndAllowsNegativeCells() {
        var position = new BlockPos.MutableBlockPos(-3, 4, -5);
        var block = new SpaceBlockRef(reference(0L), position);
        position.set(10, 20, 30);

        assertEquals(new BlockPos(-3, 4, -5), block.localPosition());
        assertNotEquals(reference(0L), reference(1L));
    }

    @Test
    void anchorFollowsTheLocalCellAndRotatesItsNormal() {
        var anchor = new StructureAnchor(
                new SpaceBlockRef(reference(0L), new BlockPos(-1, 2, 0)),
                new Vec3(1.035, 0.5, 0.5), new Vec3(2.0, 0.0, 0.0), 7L);
        var pose = StructurePose.fromYawAroundPivot(
                new Vec3(10.0, 20.0, 30.0), 90.0, Vec3.ZERO);

        var point = anchor.worldPoint(pose);
        assertEquals(9.5, point.x, 1.0e-12);
        assertEquals(22.5, point.y, 1.0e-12);
        assertEquals(30.035, point.z, 1.0e-12);
        var normal = anchor.worldNormal(pose);
        assertEquals(0.0, normal.x, 1.0e-12);
        assertEquals(0.0, normal.y, 1.0e-12);
        assertEquals(1.0, normal.z, 1.0e-12);
        assertEquals(7L, anchor.blockGeneration());
    }

    @Test
    void generationBindingIsExplicitAndInvalidAnchorDataIsRejected() {
        var block = new SpaceBlockRef(reference(0L), BlockPos.ZERO);
        var anchor = new StructureAnchor(block, Vec3.ZERO,
                new Vec3(0.0, Double.MAX_VALUE, 0.0));

        assertEquals(StructureAnchor.UNBOUND_GENERATION, anchor.blockGeneration());
        assertEquals(new Vec3(0.0, 1.0, 0.0), anchor.localNormal());
        assertThrows(IllegalArgumentException.class,
                () -> new StructureAnchor(block, Vec3.ZERO, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureAnchor(block, new Vec3(Double.NaN, 0.0, 0.0),
                        new Vec3(0.0, 1.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new StructureAnchor(block, Vec3.ZERO, new Vec3(0.0, 1.0, 0.0), -2L));
        assertThrows(IllegalArgumentException.class, () -> reference(-1L));
    }

    private static StructureRef reference(long generation) {
        return new StructureRef(new UUID(0L, 1L),
                Identifier.fromNamespaceAndPath("minecraft", "overworld"),
                StructureRef.LEGACY_ENTITY_BACKEND, generation);
    }
}
