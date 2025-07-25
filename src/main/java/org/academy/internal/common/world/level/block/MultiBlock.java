package org.academy.internal.common.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.academy.internal.common.world.level.block.entity.MultiBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public abstract class MultiBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<MultiBlockType> TYPE = EnumProperty.create("type", MultiBlockType.class);

    protected MultiBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    public MultiBlockEntity getMainBlockEntity(@NotNull Level level, @NotNull BlockPos anyPos) {
        BlockEntity blockEntity = level.getBlockEntity(anyPos);
        if (blockEntity instanceof MultiBlockEntity multiBlockEntity) {
            return multiBlockEntity.getMain();
        } else return null;
    }

    public abstract List<Vec3i> getSubBlocks();

    public static List<BlockPos> getRotatedSubjectBlocks(BlockPos pos, Direction direction, List<Vec3i> subBlocks) {
        final List<BlockPos> subjectBlocks = new ArrayList<>();

        for (Vec3i vec3i : subBlocks) {
            BlockPos offsetPos = switch (direction) {
                case NORTH -> pos.offset(vec3i.getX(), vec3i.getY(), vec3i.getZ());
                case SOUTH -> pos.offset(-vec3i.getX(), vec3i.getY(), -vec3i.getZ());
                case EAST -> pos.offset(-vec3i.getZ(), vec3i.getY(), -vec3i.getX());
                case WEST -> pos.offset(vec3i.getZ(), vec3i.getY(), vec3i.getX());
                default -> pos;
            };
            subjectBlocks.add(offsetPos);
        }

        return subjectBlocks;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(TYPE, FACING);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection());
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block neighborBlock, @NotNull BlockPos neighborPos, boolean movedByPiston) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MultiBlockEntity multiBlockEntity) {
            List<BlockPos> blockPosList = getRotatedSubjectBlocks(multiBlockEntity.mainPos, state.getValue(FACING), getSubBlocks());
            blockPosList.add(multiBlockEntity.mainPos);
            boolean broken = blockPosList.stream().anyMatch(blockPos -> level.getBlockState(blockPos).isAir() || !(level.getBlockEntity(blockPos) instanceof MultiBlockEntity));
            if (broken) {
                for (BlockPos subjectBlock : blockPosList) {
                    level.destroyBlock(subjectBlock, false);
                }
            }
        }
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        List<BlockPos> subjectBlocks = getRotatedSubjectBlocks(pos, state.getValue(FACING), getSubBlocks());
        setSubBlocks(level, pos, state, subjectBlocks);
    }

    public static void setSubBlocks(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull List<BlockPos> subBlocks) {
        for (BlockPos subjectBlock : subBlocks) {
            level.setBlock(subjectBlock, state.setValue(TYPE, MultiBlockType.SUBJECT), 2);
            BlockEntity blockEntity = level.getBlockEntity(subjectBlock);
            if (blockEntity instanceof MultiBlockEntity multiBlockEntity) {
                multiBlockEntity.setMainPos(pos);
            }
        }
    }

    public enum MultiBlockType implements StringRepresentable {
        MAIN("main"),
        SUBJECT("subject");

        public final String name;

        MultiBlockType(String name) {
            this.name = name;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name;
        }
    }
}