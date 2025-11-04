package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.api.server.util.ServerPlayerUtil;
import org.academy.internal.common.world.inventory.OmniCraftingMenu;
import org.academy.internal.common.world.level.block.entity.MultiBlockEntity;
import org.academy.internal.common.world.level.block.entity.OmniCraftingTableBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class OmniCraftingTableBlock extends MultiBlock {
    public static final MapCodec<OmniCraftingTableBlock> CODEC = simpleCodec(OmniCraftingTableBlock::new);
    public static final List<Vec3i> SUBJECT_BLOCKS = Arrays.asList(
            new Vec3i(0, 1, 0),
            new Vec3i(1, 0, 0),
            new Vec3i(1, 1, 0)
    );
    public static final String OMNI_CRAFTING_TABLE_SCREEN = "omni_crafting_table_screen";

    public OmniCraftingTableBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    public List<Vec3i> getSubBlocks() {
        return SUBJECT_BLOCKS;
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
    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(BlockStateProperties.HORIZONTAL_FACING, pRotation.rotate(pState.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, pContext.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            var menuProvider = getMenuProvider(state, level, pos);
            ServerPlayerUtil.openMenuScreen(serverPlayer, menuProvider, OMNI_CRAFTING_TABLE_SCREEN,
                    buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        } else {
            return InteractionResult.SUCCESS;
        }
    }

    @Override
    public <T extends BlockEntity> @NotNull BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof OmniCraftingTableBlockEntity omniCraftingTableBlockEntity) {
                omniCraftingTableBlockEntity.tick();
            }
        };
    }

    @Override
    public MultiBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OmniCraftingTableBlockEntity(pos, state);
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof OmniCraftingTableBlockEntity container) {
            return new SimpleMenuProvider((containerId, playerInventory, player)
                    -> new OmniCraftingMenu(
                    containerId,
                    playerInventory,
                    ContainerLevelAccess.create(level, pos),
                    container
            ),
                    Component.empty()
            );
        } else {
            return null;
        }
    }
}