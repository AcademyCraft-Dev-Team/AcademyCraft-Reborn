package org.academy.internal.common.ability.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AreaTeleportStateTest {
    private final UUID player = UUID.randomUUID();
    private final ResourceKey<Level> dimensionA = ResourceKey.create(
            Registries.DIMENSION, Identifier.fromNamespaceAndPath("test", "a"));
    private final ResourceKey<Level> dimensionB = ResourceKey.create(
            Registries.DIMENSION, Identifier.fromNamespaceAndPath("test", "b"));

    @AfterEach
    void clearState() {
        AreaTeleportState.clear(player);
    }

    @Test
    void selectionIsClampedAndDestinationKeepsItsShape() {
        AreaTeleportState.setFirstCorner(player, dimensionA, new BlockPos(10, 20, 30));
        var source = AreaTeleportState.complete(player, dimensionA, new BlockPos(100, 100, 100));

        assertEquals(AreaTeleportState.MAX_REGION_SIZE, source.sizeX());
        assertEquals(AreaTeleportState.MAX_REGION_SIZE, source.sizeY());
        assertEquals(AreaTeleportState.MAX_REGION_SIZE, source.sizeZ());
        assertTrue(source.withinLimit());

        AreaTeleportState.setDestination(player, dimensionA, new BlockPos(-20, 5, -10));
        var destination = AreaTeleportState.destination(player);
        assertEquals(source.sizeX(), destination.sizeX());
        assertEquals(source.sizeY(), destination.sizeY());
        assertEquals(source.sizeZ(), destination.sizeZ());
    }

    @Test
    void secondCornerFromAnotherDimensionIsRejected() {
        AreaTeleportState.setFirstCorner(player, dimensionA, BlockPos.ZERO);
        assertNull(AreaTeleportState.complete(player, dimensionB, new BlockPos(1, 1, 1)));
    }
}
