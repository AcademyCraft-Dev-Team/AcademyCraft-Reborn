package org.academy.internal.client.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.StructureBackendRouter;

/** Disposable fixture contents only; normal gameplay never calls the destructive cleanup. */
final class StructurePayloadFixtureSupport {
    static final boolean ENABLED = Boolean.getBoolean("academy.structurePayloadRoutes");
    static void chest(ServerPlayer player, BlockPos position) {
        player.level().setBlock(position, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 2);
        ((Container) player.level().getBlockEntity(position)).setItem(0, new ItemStack(Items.DIAMOND, 19));
    }
    static void verify(ServerPlayer player, StructureRef reference, BlockPos local) {
        var data = StructureSpaces.resolve(player.level().getServer(), reference).orElseThrow().world().read(local);
        if (!data.state().is(Blocks.CHEST) || !data.state().getValue(BlockStateProperties.WATERLOGGED)) throw new IllegalStateException("route lost waterlogged chest");
        var entity = BlockEntity.loadStatic(local, data.state(), data.blockEntityData(), player.level().registryAccess());
        var stack = ((Container) entity).getItem(0);
        if (!stack.is(Items.DIAMOND) || stack.getCount() != 19) throw new IllegalStateException("route lost inventory");
    }
    static boolean cleanup(ServerPlayer player, StructureRef reference) {
        return StructureBackendRouter.captureTarget(player.level(), true).orElseThrow().releaseEmpty(reference);
    }
}
