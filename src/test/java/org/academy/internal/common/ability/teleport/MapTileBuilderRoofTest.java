package org.academy.internal.common.ability.teleport;

import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roof handling, on the two pure functions that production code uses.
 *
 * <p>Two bugs live here and both produced visibly wrong maps:
 * <ul>
 *   <li>the Nether showed a flat sheet of bedrock, because a per-column "thin layer" test could not see the
 *       lid when rock was welded to it;</li>
 *   <li>plains showed grey-brown stone, because a shallow cave is also "a solid level with air below it",
 *       so it was classified as a roof and the map drew the cave ceiling instead of the grass above it.</li>
 * </ul>
 * {@link MapTileBuilder#isRoofPlane} is what separates those cases, and
 * {@link MapTileBuilder#resolveColumnY} is what descends past a real lid.
 */
class MapTileBuilderRoofTest {
    private static final int NO_CEILING = Integer.MIN_VALUE;

    /** Builds a solidity predicate from inclusive Y ranges given as (from, to) pairs. */
    private static IntPredicate solid(int... ranges) {
        return y -> {
            for (var i = 0; i + 1 < ranges.length; i += 2) {
                if (y >= ranges[i] && y <= ranges[i + 1]) return true;
            }
            return false;
        };
    }

    // ---------------------------------------------------------------- plane test

    @Test
    void aSolidLevelWithAVoidBeneathIsARoof() {
        // Nether-shaped: every column solid at the lid, a large void under it, near the top of the column.
        assertTrue(MapTileBuilder.isRoofPlane(16, 16, 24, 1));
    }

    @Test
    void aCaveUnderFlatGroundIsNotARoof() {
        // Solid at the surface level, so a naive test would accept it, but the void below is one block of
        // soil. This is the plains bug: accepting it made the map draw stone instead of grass.
        assertFalse(MapTileBuilder.isRoofPlane(16, 16, 1, 0),
                "a one-block gap is soil, not a roof");
    }

    @Test
    void aDeepSolidLevelIsNotARoof() {
        // Even with a large void, being far below the column top makes it a cave, not a lid.
        assertFalse(MapTileBuilder.isRoofPlane(16, 16, 30, 9));
    }

    @Test
    void aMostlyOpenLevelIsNotARoof() {
        assertFalse(MapTileBuilder.isRoofPlane(8, 16, 30, 1),
                "a level that is half air is not a lid");
    }

    @Test
    void anUnprobedLevelIsNotARoof() {
        assertFalse(MapTileBuilder.isRoofPlane(0, 0, 0, 0));
    }

    // ---------------------------------------------------------------- column walk

    @Test
    void withNoRoofTheColumnTopIsTheSurface() {
        var probe = solid(0, 70);
        assertEquals(70, MapTileBuilder.resolveColumnY(70, NO_CEILING, 0, probe));
    }

    @Test
    void aRoofedColumnResolvesToTheGroundBeneathTheLid() {
        // Lid 120..122, air under it, ground 70..90.
        var probe = solid(120, 122, 70, 90);
        assertEquals(90, MapTileBuilder.resolveColumnY(122, 119, 0, probe));
    }

    @Test
    void rockWeldedToTheLidIsDescendedThrough() {
        // Lid plus attached rock: descending must pass all of it, not stop at the first gap it meets.
        var probe = solid(114, 122, 60, 80);
        assertEquals(80, MapTileBuilder.resolveColumnY(122, 119, 0, probe));
    }

    @Test
    void anEmptyColumnIsReportedAsEmpty() {
        IntPredicate nothing = y -> false;
        assertEquals(-1, MapTileBuilder.resolveColumnY(-1, NO_CEILING, 0, nothing));
    }

    @Test
    void aColumnWithNothingUnderTheVoidFallsBackToTheLid() {
        // Nothing below the lid at all, so the lid is the only thing there is to show.
        var probe = solid(120, 122);
        assertEquals(122, MapTileBuilder.resolveColumnY(122, 119, 0, probe));
    }

    // ---------------------------------------------------------------- ceiling-dimension slice

    @Test
    void netherSliceFindsThePlayableFloorInsteadOfTheBedrockRoof() {
        // The lid at 120..127 is intentionally irrelevant: Y 80 is open above the playable terrain.
        var probe = solid(-64, 64, 120, 127);
        assertEquals(64, MapTileBuilder.resolveCeilingDimensionColumnY(80, -64, 256,
                y -> !probe.test(y)));
    }

    @Test
    void solidNetherSliceFindsTheSurfaceImmediatelyAboveIt() {
        var probe = solid(-64, 84, 120, 127);
        assertEquals(84, MapTileBuilder.resolveCeilingDimensionColumnY(80, -64, 256,
                y -> !probe.test(y)));
    }

    @Test
    void enclosedNetherSliceDoesNotFallBackToTheRoof() {
        var probe = solid(-64, 110, 120, 127);
        assertEquals(-65, MapTileBuilder.resolveCeilingDimensionColumnY(80, -64, 256,
                y -> !probe.test(y)));
    }
}
