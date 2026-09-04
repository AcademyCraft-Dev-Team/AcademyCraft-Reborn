package org.academy.api.common.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStructureKineticsTest {
    @Test
    void legacyKineticOptionsDoNotSuppressSettlement() {
        var legacy = new BlockStructureKineticOptions(20, 2.0, 5, true);
        var holding = new BlockStructureKineticOptions(200, 1.0, 5, true, true);

        assertFalse(legacy.preventSettlementWhileActive());
        assertTrue(holding.preventSettlementWhileActive());
    }

    @Test
    void collisionResponseHasASpeedGateAndCaps() {
        assertEquals(0.0f, BlockStructureKinetics.collisionDamage(4, 0.34));
        assertEquals(2.4f,
                BlockStructureKinetics.collisionDamage(4, 1.0), 1.0e-6f);
        assertEquals(24.0f,
                BlockStructureKinetics.collisionDamage(4096, 100.0), 1.0e-6f);

        assertEquals(0.0,
                BlockStructureKinetics.collisionKnockback(4, 0.34));
        assertEquals(0.64,
                BlockStructureKinetics.collisionKnockback(4, 1.0), 1.0e-9);
        assertEquals(2.4,
                BlockStructureKinetics.collisionKnockback(4096, 100.0), 1.0e-9);
    }

    @Test
    void settlementResultReportsPlacedAndDroppedCells() {
        var result = BlockStructureSettlementResult.success(21, 12);

        assertTrue(result.succeeded());
        assertEquals(21, result.placedBlocks());
        assertEquals(12, result.droppedBlocks());
        assertEquals(33, result.affectedBlocks());

        var failure = BlockStructureSettlementResult.failure(
                BlockStructureSettlementResult.Status.WRONG_LEVEL);
        assertFalse(failure.succeeded());
        assertEquals(0, failure.affectedBlocks());
    }
}
