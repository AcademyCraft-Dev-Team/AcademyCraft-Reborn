package org.academy.api.common.structure;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure safe-staticization preflight: explicit rejection codes for footprint, loading,
 * permission, occupation and rotatable block states. No game bootstrap is required.
 */
class StructureStaticizationTest {
    private static final BlockPos ANCHOR = new BlockPos(10, 64, 10);

    @Test
    void readyPlanMapsEveryLocalCellForTheRequestedQuarterTurns() {
        var world = new FakeWorld();
        var cells = List.of(
                new StructureStaticization.LocalCell(BlockPos.ZERO, false),
                new StructureStaticization.LocalCell(new BlockPos(1, 0, 0), false)
        );

        var plan = StructureStaticization.plan(
                ANCHOR, 1, true, cells, world, _ -> true);

        assertTrue(plan.ready());
        assertEquals(2, plan.placements());
        assertNull(plan.problemPosition());
        assertEquals(new BlockPos(10, 64, 10),
                StructureStaticization.targetOf(ANCHOR, BlockPos.ZERO, 1));
        assertEquals(new BlockPos(10, 64, 11),
                StructureStaticization.targetOf(ANCHOR, new BlockPos(1, 0, 0), 1));
        assertEquals(List.of(1, 1), world.placeQueries.stream()
                .map(query -> query.quarterTurns).toList());
    }

    @Test
    void duplicateTargetIsRejected() {
        var world = new FakeWorld();
        var cells = List.of(
                new StructureStaticization.LocalCell(BlockPos.ZERO, false),
                new StructureStaticization.LocalCell(BlockPos.ZERO, false)
        );

        var plan = StructureStaticization.plan(ANCHOR, 0, true, cells, world, _ -> true);

        assertEquals(StructureStaticization.Status.DUPLICATE_TARGET, plan.status());
        assertEquals(ANCHOR, plan.problemPosition());
        assertFalse(plan.ready());
    }

    @Test
    void emptyOrUnanchoredStructureIsInvalid() {
        var world = new FakeWorld();
        assertEquals(StructureStaticization.Status.INVALID_STRUCTURE,
                StructureStaticization.plan(null, 0, true,
                        List.of(new StructureStaticization.LocalCell(BlockPos.ZERO, false)),
                        world, _ -> true).status());
        assertEquals(StructureStaticization.Status.INVALID_STRUCTURE,
                StructureStaticization.plan(ANCHOR, 0, true, List.of(), world, _ -> true).status());
    }

    @Test
    void outOfWorldUnloadedPermissionAndOccupiedAreDistinctCodes() {
        var cells = List.of(new StructureStaticization.LocalCell(BlockPos.ZERO, false));
        var outOfWorld = new FakeWorld();
        outOfWorld.outOfWorld.add(ANCHOR);
        assertEquals(StructureStaticization.Status.OUT_OF_WORLD,
                StructureStaticization.plan(ANCHOR, 0, true, cells, outOfWorld, _ -> true).status());

        var unloaded = new FakeWorld();
        unloaded.unloaded.add(ANCHOR);
        assertEquals(StructureStaticization.Status.UNLOADED,
                StructureStaticization.plan(ANCHOR, 0, true, cells, unloaded, _ -> true).status());

        assertEquals(StructureStaticization.Status.PERMISSION_DENIED,
                StructureStaticization.plan(ANCHOR, 0, true, cells,
                        new FakeWorld(), _ -> false).status());

        var occupied = new FakeWorld();
        occupied.occupied.add(ANCHOR);
        var plan = StructureStaticization.plan(ANCHOR, 0, true, cells, occupied, _ -> true);
        assertEquals(StructureStaticization.Status.OCCUPIED, plan.status());
        assertEquals(ANCHOR, plan.problemPosition());
    }

    @Test
    void rotatedBlockEntityContentAndUnalignedPoseAreRejectedAsRotationUnsupported() {
        var rotatedEntity = List.of(new StructureStaticization.LocalCell(BlockPos.ZERO, true));
        assertEquals(StructureStaticization.Status.ROTATION_UNSUPPORTED,
                StructureStaticization.plan(ANCHOR, 1, true,
                        rotatedEntity, new FakeWorld(), _ -> true).status());
        assertEquals(StructureStaticization.Status.READY,
                StructureStaticization.plan(ANCHOR, 0, true,
                        rotatedEntity, new FakeWorld(), _ -> true).status());

        var plain = List.of(new StructureStaticization.LocalCell(BlockPos.ZERO, false));
        var plan = StructureStaticization.plan(ANCHOR, 0, false, plain, new FakeWorld(), _ -> true);
        assertEquals(StructureStaticization.Status.ROTATION_UNSUPPORTED, plan.status());
        assertEquals(ANCHOR, plan.problemPosition());
    }

    @Test
    void unsupportedPlacementIsNotPlaceableOrRotationUnsupported() {
        var cells = List.of(new StructureStaticization.LocalCell(BlockPos.ZERO, false));
        var world = new FakeWorld();
        world.unsupported.add(ANCHOR);
        assertEquals(StructureStaticization.Status.NOT_PLACEABLE,
                StructureStaticization.plan(ANCHOR, 0, true, cells, world, _ -> true).status());
        assertEquals(StructureStaticization.Status.ROTATION_UNSUPPORTED,
                StructureStaticization.plan(ANCHOR, 2, true, cells, world, _ -> true).status());

        assertFalse(StructureStaticization.Status.OCCUPIED.name().isBlank());
        assertTrue(StructureStaticization.Status.valueOf("CONTENT_UNAVAILABLE").ordinal() >= 0);
    }

    private record PlaceQuery(BlockPos position, int quarterTurns) {
    }

    private static final class FakeWorld implements StructureStaticization.World {
        private final Set<BlockPos> outOfWorld = new HashSet<>();
        private final Set<BlockPos> unloaded = new HashSet<>();
        private final Set<BlockPos> occupied = new HashSet<>();
        private final Set<BlockPos> unsupported = new HashSet<>();
        private final List<PlaceQuery> placeQueries = new ArrayList<>();

        @Override
        public boolean isInWorldBounds(BlockPos position) {
            return !outOfWorld.contains(position);
        }

        @Override
        public boolean isLoaded(BlockPos position) {
            return !unloaded.contains(position);
        }

        @Override
        public boolean isOccupied(BlockPos position) {
            return occupied.contains(position);
        }

        @Override
        public boolean canPlace(
                BlockPos position,
                StructureStaticization.LocalCell cell,
                int quarterTurns
        ) {
            placeQueries.add(new PlaceQuery(position, quarterTurns));
            return !unsupported.contains(position);
        }
    }
}
