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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.api.server.util.ServerPlayerUtil;
import org.academy.internal.common.world.inventory.SolarGenMenu;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.block.entity.SolarGenBlockEntity;
import org.jspecify.annotations.Nullable;

public final class SolarGenBlock extends BaseEntityBlock {
    public static final String SOLAR_GEN_SCREEN = "solar_gen_screen";
    public static final MapCodec<SolarGenBlock> CODEC = simpleCodec(SolarGenBlock::new);

    public SolarGenBlock(Properties properties) {
        super(properties
                .mapColor(MapColor.STONE)
                .sound(SoundType.STONE)
                .noOcclusion()
                .strength(20.0f)
                .requiresCorrectToolForDrops()
        );
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (!serverPlayer.isShiftKeyDown() && level.getBlockEntity(pos) instanceof SolarGenBlockEntity blockEntity) {
                var menuProvider = getMenuProvider(state, level, pos);
                ServerPlayerUtil.openMenuScreen(serverPlayer, menuProvider, SOLAR_GEN_SCREEN,
                        buf -> buf.writeBlockPos(blockEntity.getBlockPos()));
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, pContext.getHorizontalDirection());
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BlockEntityTypes.SOLAR_GEN.get(), SolarGenBlockEntity::tick);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, rot.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarGenBlockEntity(pos, state);
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SolarGenBlockEntity blockEntity) {
            return new SimpleMenuProvider((containerId, playerInventory, player) ->
                    new SolarGenMenu(
                            containerId,
                            playerInventory,
                            ContainerLevelAccess.create(level, pos),
                            blockEntity
                    ), Component.empty()
            );
        }
        return super.getMenuProvider(state, level, pos);
    }
}