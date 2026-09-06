package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.api.server.util.ServerPlayerUtil;
import org.academy.internal.common.world.inventory.AerospaceSignalCabinMenu;
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.jspecify.annotations.Nullable;

public final class AerospaceSignalCabinBlock extends BaseEntityBlock {
    public static final String SCREEN = "aerospace_signal_cabin_screen";
    public static final MapCodec<AerospaceSignalCabinBlock> CODEC = simpleCodec(AerospaceSignalCabinBlock::new);

    public AerospaceSignalCabinBlock(Properties properties) {
        super(properties.mapColor(MapColor.METAL).sound(SoundType.METAL).noOcclusion());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof AerospaceSignalCabinBlockEntity blockEntity) {
            var menuProvider = getMenuProvider(state, level, pos);
            ServerPlayerUtil.openMenuScreen(serverPlayer, menuProvider, SCREEN,
                    buf -> buf.writeBlockPos(blockEntity.getBlockPos()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> blockEntityType
    ) {
        return createTickerHelper(
                blockEntityType,
                BlockEntityTypes.AEROSPACE_SIGNAL_CABIN.get(),
                AerospaceSignalCabinBlockEntity::tick
        );
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AerospaceSignalCabinBlockEntity(pos, state);
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AerospaceSignalCabinBlockEntity blockEntity) {
            return new SimpleMenuProvider((containerId, playerInventory, player) ->
                    new AerospaceSignalCabinMenu(
                            containerId,
                            playerInventory,
                            ContainerLevelAccess.create(level, pos),
                            blockEntity
                    ), Component.translatable("block.academy.aerospace_signal_cabin")
            );
        }
        return super.getMenuProvider(state, level, pos);
    }
}
