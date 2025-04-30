package org.academy.internal.common.world.level.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.internal.common.world.inventory.WindGenMenu;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("deprecation")
public class WindGenBaseBlock extends MultiBlock {
    public static final String WIND_GEN_SCREEN = "wind_gen_screen";
    public static final List<Vec3i> SUB_BLOCKS = List.of(
            new Vec3i(0, 1, 0)
    );
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public WindGenBaseBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState pState, Level pLevel, @NotNull BlockPos pPos, @NotNull Player pPlayer, @NotNull InteractionHand pHand, @NotNull BlockHitResult pHit) {
        if (pLevel.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            if (pPlayer instanceof ServerPlayer serverPlayer) {
                if (pLevel.getBlockEntity(pPos) instanceof WindGenBaseBlockEntity windGenBaseBlockEntity) {
                    if (windGenBaseBlockEntity.getMain() instanceof WindGenBaseBlockEntity windGenBaseBlock) {
                        if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
                            serverPlayer.closeContainer();
                        }
                        serverPlayer.nextContainerCounter();
                        MenuProvider menuProvider = getMenuProvider(pState, pLevel, pPos);
                        assert menuProvider != null;
                        AbstractContainerMenu abstractcontainermenu = menuProvider.createMenu(serverPlayer.containerCounter, pPlayer.getInventory(), pPlayer);
                        if (abstractcontainermenu == null) {
                            if (serverPlayer.isSpectator()) {
                                serverPlayer.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
                            }
                        } else {
                            serverPlayer.connection.send(new S2CPacket(Packets.S2C_OPEN_SCREEN, WIND_GEN_SCREEN, abstractcontainermenu.containerId, menuProvider.getDisplayName(), windGenBaseBlock.getBlockPos()));
                            serverPlayer.initMenu(abstractcontainermenu);
                            serverPlayer.containerMenu = abstractcontainermenu;
                        }
                    }
                }
            }
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean movedByPiston) {
        BlockEntity blockentity = level.getBlockEntity(pos);
        if (blockentity instanceof Container container) {
            Containers.dropContents(level, pos, container);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public List<Vec3i> getSubBlocks() {
        return SUB_BLOCKS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(TYPE, FACING);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new WindGenBaseBlockEntity(pos, state);
    }

    @Override
    public @NotNull BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }

    @Override
    public boolean canBeReplaced(@NotNull BlockState state, @NotNull BlockPlaceContext useContext) {
        return false;
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WindGenBaseBlockEntity windGenBaseBlockEntity) {
            if (windGenBaseBlockEntity.getMain() instanceof WindGenBaseBlockEntity windGenBaseBlock) {
                return new SimpleMenuProvider((containerId, playerInventory, player) -> new WindGenMenu(containerId, playerInventory, ContainerLevelAccess.create(level, pos), windGenBaseBlock), Component.empty());
            }
        }
        return null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof WindGenBaseBlockEntity windGenBaseBlockEntity) {
                if (windGenBaseBlockEntity.isMain()) {
                    windGenBaseBlockEntity.tick();
                }
            }
        };
    }
}