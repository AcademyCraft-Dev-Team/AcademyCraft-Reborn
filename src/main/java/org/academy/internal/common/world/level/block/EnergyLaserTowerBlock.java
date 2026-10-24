package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.api.server.util.ServerPlayerUtil;
import org.academy.internal.common.world.inventory.EnergyLaserTowerMenu;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.block.entity.EnergyLaserTowerBlockEntity;
import org.academy.internal.common.world.level.block.entity.MultiBlockEntity;
import org.academy.internal.common.world.level.block.entity.OwnedDevice;
import org.academy.internal.server.world.level.storage.MisakaRelayRegistry;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Single-block laser tower. Mesh is 16×16.2×16 px (exactly one block wide;
 * emitter apex may exceed the block by ~0.01).
 */
public final class EnergyLaserTowerBlock extends MultiBlock {
    public static final int HEIGHT = 1;
    /**
     * Emitter apex in block space. Bedrock tip ({@code laser_outline} top) is y=16.2 px,
     * centered on X/Z. GeckoLib places model origin at the block center-bottom (16 px = 1 block).
     */
    public static final double BEAM_ORIGIN_Y = 16.2 / 16.0;
    public static final double BEAM_ORIGIN_XZ = 0.5;
    public static final String SCREEN = "energy_laser_tower_screen";
    public static final MapCodec<EnergyLaserTowerBlock> CODEC = simpleCodec(EnergyLaserTowerBlock::new);
    public static final List<Vec3i> SUBJECT_BLOCKS = List.of();

    public EnergyLaserTowerBlock(Properties properties) {
        super(properties.mapColor(MapColor.METAL).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(TYPE, MultiBlockType.MAIN)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    public List<Vec3i> getSubBlocks() {
        return SUBJECT_BLOCKS;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(TYPE, MultiBlockType.MAIN)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player) {
            OwnedDevice.assignPlacer(level.getBlockEntity(pos), player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof EnergyLaserTowerBlockEntity blockEntity) {
            var main = blockEntity.mainEntity();
            if (main == null) {
                return InteractionResult.FAIL;
            }
            var menuProvider = getMenuProvider(state, level, main.getBlockPos());
            ServerPlayerUtil.openMenuScreen(serverPlayer, menuProvider, SCREEN,
                    buf -> buf.writeBlockPos(main.getBlockPos()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> blockEntityType
    ) {
        return createTickerHelper(
                blockEntityType,
                BlockEntityTypes.ENERGY_LASER_TOWER.get(),
                EnergyLaserTowerBlockEntity::tick
        );
    }

    @Override
    public MultiBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyLaserTowerBlockEntity(pos, state);
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            BlockPos mainPos = pos;
            if (level.getBlockEntity(pos) instanceof EnergyLaserTowerBlockEntity tower && tower.mainPos != null) {
                mainPos = tower.mainPos;
            }
            MisakaRelayRegistry.get(serverLevel.getServer())
                    .onLaserRemoved(serverLevel.getServer(), serverLevel.dimension(), mainPos);
        }
        super.destroy(level, pos, state);
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof EnergyLaserTowerBlockEntity blockEntity) {
            var main = blockEntity.mainEntity();
            if (main == null) {
                return null;
            }
            return new SimpleMenuProvider((containerId, playerInventory, player) ->
                    new EnergyLaserTowerMenu(
                            containerId,
                            playerInventory,
                            ContainerLevelAccess.create(level, main.getBlockPos()),
                            main
                    ), Component.translatable("block.academy.energy_laser_tower")
            );
        }
        return null;
    }
}
