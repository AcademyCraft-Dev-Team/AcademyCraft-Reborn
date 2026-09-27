package org.academy.api.common.entitycontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockWorkRegionTest {
    private static final Identifier DIMENSION = AcademyCraft.academy("test_dimension");

    @Test
    void normalizesCornersAndReportsInclusiveDimensions() {
        var region = new BlockWorkRegion(
                DIMENSION,
                new BlockPos(7, 12, -2),
                new BlockPos(3, 10, -6)
        );

        assertEquals(new BlockPos(3, 10, -6), region.minimum());
        assertEquals(new BlockPos(7, 12, -2), region.maximum());
        assertEquals(5, region.sizeX());
        assertEquals(3, region.sizeY());
        assertEquals(5, region.sizeZ());
        assertEquals(75, region.volume());
        assertEquals(new BlockPos(5, 11, -4), region.center());
    }

    @Test
    void centeredRegionPreservesEvenAndOddRequestedSizes() {
        var center = new BlockPos(10, 64, 10);
        var region = BlockWorkRegion.centered(DIMENSION, center, 4, 3, 2);

        assertEquals(4, region.sizeX());
        assertEquals(3, region.sizeY());
        assertEquals(2, region.sizeZ());
        assertTrue(region.minimum().getX() <= center.getX());
        assertTrue(region.maximum().getX() >= center.getX());
    }

    @Test
    void rejectsUnsafeAxisAndVolumeSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> BlockWorkRegion.centered(DIMENSION, BlockPos.ZERO, 33, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> BlockWorkRegion.centered(DIMENSION, BlockPos.ZERO, 32, 32, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new BlockWorkRegion(
                        DIMENSION, BlockPos.ZERO, new BlockPos(0, 0, 32)));
    }
}
