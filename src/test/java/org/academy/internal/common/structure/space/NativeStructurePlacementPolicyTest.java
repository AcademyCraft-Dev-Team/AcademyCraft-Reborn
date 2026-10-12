package org.academy.internal.common.structure.space;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.academy.internal.common.structure.space.NativeStructurePlacementPolicy.Action.INTERACT;
import static org.academy.internal.common.structure.space.NativeStructurePlacementPolicy.Action.PLACE;
import static org.academy.internal.common.structure.space.NativeStructurePlacementPolicy.HandKind.EMPTY;
import static org.academy.internal.common.structure.space.NativeStructurePlacementPolicy.HandKind.PLACEABLE;
import static org.academy.internal.common.structure.space.NativeStructurePlacementPolicy.HandKind.UNSUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Registry-dependent whitelist checks belong to the actual game probe; hand routing is pure. */
class NativeStructurePlacementPolicyTest {
    @Test
    void ordinaryInteractiveBlocksWinBeforeEitherPlacementHand() {
        assertSelection(INTERACT, InteractionHand.MAIN_HAND, PLACEABLE, PLACEABLE, false, true);
        assertSelection(INTERACT, InteractionHand.MAIN_HAND, EMPTY, PLACEABLE, false, true);
        assertSelection(INTERACT, InteractionHand.MAIN_HAND, EMPTY, UNSUPPORTED, false, true);
    }

    @Test
    void secondaryUseSelectsPlacementAndKeepsMainHandPriority() {
        assertSelection(PLACE, InteractionHand.MAIN_HAND, PLACEABLE, PLACEABLE, true, true);
        assertSelection(PLACE, InteractionHand.OFF_HAND, EMPTY, PLACEABLE, true, true);
        assertNull(NativeStructurePlacementPolicy.selectAction(EMPTY, UNSUPPORTED, true, true));
    }

    @Test
    void emptyHandAdaptersKeepTheirExistingSneakingBehavior() {
        assertSelection(INTERACT, InteractionHand.MAIN_HAND, EMPTY, EMPTY, false, true);
        assertSelection(INTERACT, InteractionHand.MAIN_HAND, EMPTY, EMPTY, true, true);
        assertNull(NativeStructurePlacementPolicy.selectAction(EMPTY, EMPTY, false, false));
    }

    @Test
    void noninteractiveCellsUseMainBeforeOffhandInEitherSneakState() {
        for (var secondary : new boolean[]{false, true}) {
            assertSelection(PLACE, InteractionHand.MAIN_HAND, PLACEABLE, PLACEABLE, secondary, false);
            assertSelection(PLACE, InteractionHand.MAIN_HAND, PLACEABLE, UNSUPPORTED, secondary, false);
            assertSelection(PLACE, InteractionHand.OFF_HAND, EMPTY, PLACEABLE, secondary, false);
        }
    }

    @Test
    void unknownMainHandUseIsNeverReplacedByAnOffhandAttempt() {
        for (var off : NativeStructurePlacementPolicy.HandKind.values()) {
            for (var secondary : new boolean[]{false, true}) {
                for (var interactive : new boolean[]{false, true}) {
                    assertNull(NativeStructurePlacementPolicy.selectAction(UNSUPPORTED, off, secondary, interactive));
                }
            }
        }
    }

    private static void assertSelection(NativeStructurePlacementPolicy.Action action, InteractionHand hand,
                                        NativeStructurePlacementPolicy.HandKind main,
                                        NativeStructurePlacementPolicy.HandKind off,
                                        boolean secondary, boolean interactive) {
        assertEquals(new NativeStructurePlacementPolicy.Selection(action, hand),
                NativeStructurePlacementPolicy.selectAction(main, off, secondary, interactive));
    }
}
