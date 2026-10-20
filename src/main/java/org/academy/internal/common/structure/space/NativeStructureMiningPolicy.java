package org.academy.internal.common.structure.space;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Selection boundary; the authoritative path applies vanilla hardness, tool and protection rules. */
public final class NativeStructureMiningPolicy {
    private NativeStructureMiningPolicy() {}

    /** Client selection uses state only; the authoritative path additionally checks the real BE. */
    public static boolean canMine(ItemStack tool, BlockState state) {
        return canUseTool(tool) && supportedState(state);
    }

    public static boolean canMine(BlockState state, @Nullable BlockEntity entity) {
        if (!supportedState(state)) return false;
        return state.hasBlockEntity() ? entity != null && !entity.isRemoved()
                && entity.getType().isValid(state) : entity == null;
    }

    public static boolean canUseTool(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return stack.isEmpty() || stack.getCount() > 0 && stack.getCount() <= stack.getMaxStackSize();
    }

    private static boolean supportedState(BlockState state) {
        Objects.requireNonNull(state, "state");
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock);
    }
}
