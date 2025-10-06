package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import org.academy.internal.server.world.level.storage.WirelessNetworkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WirelessNodeBlock extends BaseEntityBlock {
    public static final MapCodec<WirelessNodeBlock> CODEC = simpleCodec(WirelessNodeBlock::new);
    public static final String WIRELESS_NODE_SCREEN = "wireless_node_screen";
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    private static final IntegerProperty ENERGY = IntegerProperty.create("energy_cost", 0, 4);

    public WirelessNodeBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(CONNECTED, false).setValue(ENERGY, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(CONNECTED, ENERGY);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            var nodeName = "Node_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
            var password = "";
            int radius = 32;
            int maxConnections = 8;
            if (WirelessNetworkData.get(serverLevel).registerNode(pos, nodeName, password, radius, maxConnections)) {
                AcademyCraft.LOGGER.debug("Registered wireless node at {} with name '{}'.", pos, nodeName);
            } else {
                AcademyCraft.LOGGER.warn("Failed to register wireless node at {} with name '{}'.", pos, nodeName);
            }
        }
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            WirelessNetworkData.get(serverLevel).unregisterNode(pos, serverLevel);
        }
        super.destroy(level, pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        } else {
            if (player instanceof ServerPlayer serverPlayer) {
                if (level.getBlockEntity(pos) instanceof WirelessNodeBlockEntity wirelessNodeBlockEntity) {
                    var menuProvider = getMenuProvider(state, level, pos);
                    ServerPlayerUtil.openMenuScreen(serverPlayer, menuProvider, WIRELESS_NODE_SCREEN,
                            buf -> buf.writeBlockPos(wirelessNodeBlockEntity.getBlockPos()));
                }
            }
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return new WirelessNodeBlockEntity(blockPos, blockState);
    }

    @Override
    public <T extends BlockEntity> @NotNull BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
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
    public @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WirelessNodeBlockEntity wirelessNodeBlockEntity) {
            return new SimpleMenuProvider((containerId, playerInventory, player) ->
                    new WirelessNodeMenu(containerId, playerInventory, ContainerLevelAccess.create(level, pos), wirelessNodeBlockEntity), Component.empty());
        }
        return null;
    }
}