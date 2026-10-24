package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
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
import org.academy.internal.common.world.inventory.AerospaceSignalCabinMenu;
import org.academy.internal.common.world.level.block.entity.AerospaceSignalCabinBlockEntity;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.block.entity.MultiBlockEntity;
import org.academy.internal.common.world.level.block.entity.OwnedDevice;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Vertical 1×2 aerospace signal cabin; geo mesh is ~2.13 tall (radar tip may slightly exceed). */
public final class AerospaceSignalCabinBlock extends MultiBlock {
    public static final int HEIGHT = 2;
    public static final String SCREEN = "aerospace_signal_cabin_screen";
    public static final MapCodec<AerospaceSignalCabinBlock> CODEC = simpleCodec(AerospaceSignalCabinBlock::new);
    public static final List<Vec3i> SUBJECT_BLOCKS = List.of(new Vec3i(0, 1, 0));

    public AerospaceSignalCabinBlock(Properties properties) {
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
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof AerospaceSignalCabinBlockEntity blockEntity) {
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
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
    public MultiBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AerospaceSignalCabinBlockEntity(pos, state);
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AerospaceSignalCabinBlockEntity blockEntity) {
            var main = blockEntity.mainEntity();
            if (main == null) {
                return null;
            }
            return new SimpleMenuProvider((containerId, playerInventory, player) ->
                    new AerospaceSignalCabinMenu(
                            containerId,
                            playerInventory,
                            ContainerLevelAccess.create(level, main.getBlockPos()),
                            main
                    ),
                    Component.translatable("block.academy.aerospace_signal_cabin")
            );
        }
        return null;
    }
}
