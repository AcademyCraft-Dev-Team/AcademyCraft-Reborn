package org.academy.internal.common.structure.transaction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructureCaptureClaimsTest {
    private static final Identifier OVERWORLD = Identifier.fromNamespaceAndPath("minecraft", "overworld");
    private static final Identifier NETHER = Identifier.fromNamespaceAndPath("minecraft", "the_nether");

    private static List<BlockPos> cells(int count) {
        var result = new java.util.ArrayList<BlockPos>(count);
        for (int index = 0; index < count; index++) result.add(new BlockPos(index, 64, 0));
        return List.copyOf(result);
    }

    @Test
    void claimsEveryPositionOrNothing() {
        var claims = new StructureCaptureClaims();
        var first = UUID.randomUUID();
        assertTrue(claims.tryClaim(OVERWORLD, cells(3), first));
        assertTrue(claims.isClaimed(OVERWORLD, new BlockPos(0, 64, 0)));
        assertEquals(first, claims.claimedBy(OVERWORLD, new BlockPos(2, 64, 0)));
        assertFalse(claims.isClaimed(OVERWORLD, new BlockPos(3, 64, 0)));

        var second = UUID.randomUUID();
        assertFalse(claims.tryClaim(OVERWORLD, List.of(new BlockPos(2, 64, 0), new BlockPos(9, 64, 0)), second),
                "An overlapping claim must be rejected as a whole");
        assertNull(claims.claimedBy(OVERWORLD, new BlockPos(9, 64, 0)),
                "A rejected claim must not leave partial ownership");
        assertFalse(claims.tryClaim(OVERWORLD, cells(2), first), "A transaction cannot claim twice");
    }

    @Test
    void releasesOnlyTheRequestedTransaction() {
        var claims = new StructureCaptureClaims();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        assertTrue(claims.tryClaim(OVERWORLD, cells(2), first));
        assertTrue(claims.tryClaim(OVERWORLD, List.of(new BlockPos(10, 64, 0)), second));
        claims.release(first);
        assertFalse(claims.isClaimed(OVERWORLD, new BlockPos(0, 64, 0)));
        assertTrue(claims.isClaimed(OVERWORLD, new BlockPos(10, 64, 0)));
        claims.release(first);
        assertEquals(1, claims.claimedTransactions());
        claims.clear();
        assertFalse(claims.isClaimed(OVERWORLD, new BlockPos(10, 64, 0)));
        assertEquals(0, claims.claimedTransactions());
    }

    @Test
    void dimensionsAndEmptySelectionsAreIndependent() {
        var claims = new StructureCaptureClaims();
        var transaction = UUID.randomUUID();
        assertTrue(claims.tryClaim(OVERWORLD, cells(1), transaction));
        assertFalse(claims.isClaimed(NETHER, new BlockPos(0, 64, 0)));
        assertTrue(claims.tryClaim(NETHER, cells(1), UUID.randomUUID()),
                "The same coordinates in another dimension are a different claim");
        assertFalse(claims.tryClaim(OVERWORLD, List.of(), UUID.randomUUID()),
                "An empty selection cannot form a claim");
    }
}
