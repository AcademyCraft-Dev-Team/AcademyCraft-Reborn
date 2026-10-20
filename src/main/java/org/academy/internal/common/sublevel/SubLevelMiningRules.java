package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Shared authoritative mining/placement boundary for plot-backed structures. */
final class SubLevelMiningRules {
    private SubLevelMiningRules() {
    }

    static boolean canMine(ServerPlayer player, ServerLevel storage, BlockPos position,
                           BlockState state, @Nullable BlockEntity blockEntity) {
        if (state.isAir() || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
        float hardness = state.getDestroySpeed(storage, position);
        if (!Float.isFinite(hardness) || hardness < 0.0f && !player.hasInfiniteMaterials()) return false;
        if (player.blockActionRestricted(storage, position, player.gameMode.getGameModeForPlayer())) return false;
        var tool = player.getMainHandItem();
        // Plot structures may contain arbitrary captured blocks; any real item that can destroy
        // the state is valid, not only the audited native-structure tool list.
        if (!tool.isEmpty() && !tool.canDestroyBlock(state, storage, position, player)) return false;
        return state.hasBlockEntity() ? blockEntity != null && !blockEntity.isRemoved()
                && blockEntity.getType().isValid(state) : blockEntity == null;
    }

    /** Vanilla per-tick break delta; the server is the only clock. */
    static double increment(float hardness, float speed, boolean harvest) {
        if (!Float.isFinite(hardness) || hardness < 0.0f || !Float.isFinite(speed) || speed < 0.0f) return 0.0;
        if (hardness == 0.0f) return 1.0;
        return (double) speed / hardness / (harvest ? 30.0 : 100.0);
    }

    static boolean canUseMenu(ServerPlayer player) {
        return player.connection != null && player.connection.isAcceptingMessages()
                && player.connection.getPlayer() == player && !player.isSpectator()
                && !player.isUsingItem() && player.containerMenu == player.inventoryMenu
                && player.isAlive() && !player.isRemoved();
    }

    static boolean sameTool(ItemStack current, ItemStack expected) {
        return ItemStack.matches(current, expected);
    }
}
