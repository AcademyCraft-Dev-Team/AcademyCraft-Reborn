package org.academy.internal.common.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.AcademyCraft;
import org.academy.api.server.util.ServerPlayerUtil;
import org.academy.internal.common.world.inventory.WirelessNodeMenu;
import org.academy.internal.common.world.level.block.entity.WirelessNodeBlockEntity;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class WirelessNodeBlock extends BaseEntityBlock {
    public static final String WIRELESS_NODE_SCREEN = "wireless_node_screen";
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    private static final IntegerProperty ENERGY = IntegerProperty.create("energy", 0, 4);

    public WirelessNodeBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(CONNECTED, false).setValue(ENERGY, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(CONNECTED, ENERGY);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            String nodeName = "Node_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
            String password = "";
            int radius = 32;
            int maxConnections = 8;
            if (WorldData.WirelessNetworkData.get(serverLevel).registerNode(pos, nodeName, password, radius, maxConnections)) {
                AcademyCraft.LOGGER.debug("Registered wireless node at {} with name '{}'.", pos, nodeName);
            } else {
                AcademyCraft.LOGGER.warn("Failed to register wireless node at {} with name '{}'.", pos, nodeName);
            }
        }
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            WorldData.WirelessNetworkData.get(serverLevel).unregisterNode(pos, serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState pState, Level pLevel, @NotNull BlockPos pPos, @NotNull Player pPlayer, @NotNull InteractionHand pHand, @NotNull BlockHitResult pHit) {
        if (pLevel.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            if (pPlayer instanceof ServerPlayer serverPlayer) {
                if (pLevel.getBlockEntity(pPos) instanceof WirelessNodeBlockEntity wirelessNodeBlockEntity) {
                    MenuProvider menuProvider = getMenuProvider(pState, pLevel, pPos);
                    ServerPlayerUtil.openMenuScreen(serverPlayer, menuProvider, WIRELESS_NODE_SCREEN, wirelessNodeBlockEntity.getBlockPos());
                }
            }
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        return new WirelessNodeBlockEntity(blockPos, blockState);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof WirelessNodeBlockEntity wirelessNodeBlockEntity) {
                wirelessNodeBlockEntity.ticks++;
                if (wirelessNodeBlockEntity.getLevel() instanceof ServerLevel serverLevel) {
                    wirelessNodeBlockEntity.serverTick(serverLevel, pos);
                }
            }
        };
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WirelessNodeBlockEntity wirelessNodeBlockEntity) {
            return new SimpleMenuProvider((containerId, playerInventory, player) -> new WirelessNodeMenu(containerId, playerInventory, ContainerLevelAccess.create(level, pos), wirelessNodeBlockEntity), Component.empty());
        }
        return null;
    }
}