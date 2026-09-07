package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.block.entity.HibernationPodBlockEntity;
import org.academy.internal.common.world.level.block.entity.MultiBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/** Vertical 1×3 hibernation pod multiblock. Opens once via right-click and releases a Misaka Sister. */
public final class HibernationPodBlock extends MultiBlock {
    public static final int HEIGHT = 3;
    public static final MapCodec<HibernationPodBlock> CODEC = simpleCodec(HibernationPodBlock::new);
    public static final List<Vec3i> SUBJECT_BLOCKS = Arrays.asList(
            new Vec3i(0, 1, 0),
            new Vec3i(0, 2, 0)
    );

    public HibernationPodBlock(Properties properties) {
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
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof MisakaSisterEntity sister
                && level.getBlockEntity(pos) instanceof HibernationPodBlockEntity part) {
            HibernationPodBlockEntity main = part.mainEntity();
            if (main != null && main.containsSister(sister)) {
                return Shapes.empty();
            }
        }
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult
    ) {
        var main = getMainBlockEntity(level, pos);
        if (!(main instanceof HibernationPodBlockEntity pod)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return pod.isOpenedOrOpening() ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
        }
        return pod.tryOpen(player) ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> blockEntityType
    ) {
        return createTickerHelper(
                blockEntityType,
                BlockEntityTypes.HIBERNATION_POD.get(),
                HibernationPodBlockEntity::tick
        );
    }

    @Override
    public MultiBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HibernationPodBlockEntity(pos, state);
    }
}
