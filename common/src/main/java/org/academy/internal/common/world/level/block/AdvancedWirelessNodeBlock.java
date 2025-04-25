package org.academy.internal.common.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import org.academy.internal.common.world.level.block.entity.AdvancedWirelessNodeBlockEntity;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.server.world.level.storage.WorldData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class AdvancedWirelessNodeBlock extends BaseEntityBlock {
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    private static final IntegerProperty ENERGY = IntegerProperty.create("energy", 0, 4);

    public AdvancedWirelessNodeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CONNECTED, false).setValue(ENERGY, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(CONNECTED, ENERGY);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            String nodeName = "Node_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
            String password = "123456";
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
            WorldData.WirelessNetworkData.get(serverLevel).unregisterNode(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AdvancedWirelessNodeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : createTickerHelper(blockEntityType, BlockEntityTypes.ADVANCED_WIRELESS_NODE, (level1, pos, state1, blockEntity) -> AdvancedWirelessNodeBlockEntity.tick(level1, pos, blockEntity));
    }
}