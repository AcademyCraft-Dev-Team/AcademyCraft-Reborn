package org.academy.internal.common.structure.space;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Side-neutral selection rules shared by the real client and the authoritative server. */
public final class NativeStructurePlacementPolicy {
    public enum Action { INTERACT, PLACE }
    public record Selection(Action action, InteractionHand hand) {
        public Selection {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(hand, "hand");
        }
    }
    enum HandKind { EMPTY, PLACEABLE, UNSUPPORTED }

    private NativeStructurePlacementPolicy() {}

    /** Audited vanilla placement only; component-driven state or container imports are excluded. */
    public static boolean canPlace(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty() || stack.getItem().getClass() != BlockItem.class
                || stack.has(DataComponents.BLOCK_STATE) || stack.has(DataComponents.BLOCK_ENTITY_DATA)
                || stack.has(DataComponents.CONTAINER)) return false;
        var block = ((BlockItem) stack.getItem()).getBlock();
        return block == Blocks.STONE || block == Blocks.COBBLESTONE || block == Blocks.OAK_PLANKS
                || block == Blocks.OAK_STAIRS || block == Blocks.OAK_SLAB || block == Blocks.LEVER;
    }

    /** A null result leaves unsupported held-item use outside the structure adapter. */
    public static @Nullable Selection selectAction(ItemStack main, ItemStack off, boolean secondaryUse,
                                                   BlockState clickedState) {
        Objects.requireNonNull(clickedState, "clickedState");
        return selectAction(classify(main), classify(off), secondaryUse,
                clickedState.is(Blocks.LEVER) || clickedState.is(Blocks.CHEST));
    }

    private static HandKind classify(ItemStack stack) {
        return stack.isEmpty() ? HandKind.EMPTY : canPlace(stack) ? HandKind.PLACEABLE : HandKind.UNSUPPORTED;
    }

    static @Nullable Selection selectAction(HandKind main, HandKind off, boolean secondaryUse,
                                            boolean interactive) {
        // An unknown main-hand item may consume use before an offhand attempt. Do not guess.
        if (main == HandKind.UNSUPPORTED) return null;
        boolean emptyHands = main == HandKind.EMPTY && off == HandKind.EMPTY;
        if (interactive && (!secondaryUse || emptyHands)) {
            return new Selection(Action.INTERACT, InteractionHand.MAIN_HAND);
        }
        if (main == HandKind.PLACEABLE) return new Selection(Action.PLACE, InteractionHand.MAIN_HAND);
        if (off == HandKind.PLACEABLE) return new Selection(Action.PLACE, InteractionHand.OFF_HAND);
        return null;
    }
}
