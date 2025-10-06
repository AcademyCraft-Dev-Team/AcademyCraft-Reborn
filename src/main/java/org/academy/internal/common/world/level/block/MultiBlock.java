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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.academy.internal.common.world.level.block.entity.MultiBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class MultiBlock extends BaseEntityBlock {
    public static final EnumProperty<MultiBlockType> TYPE = EnumProperty.create("type", MultiBlockType.class);

    protected MultiBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    public MultiBlockEntity getMainBlockEntity(Level level, BlockPos anyPos) {
        var blockEntity = level.getBlockEntity(anyPos);
        if (blockEntity instanceof MultiBlockEntity multiBlockEntity) {
            return multiBlockEntity.getMain();
        } else return null;
    }

    public abstract List<Vec3i> getSubBlocks();

    public static List<BlockPos> getRotatedSubjectBlocks(BlockPos pos, Direction direction, List<Vec3i> subBlocks) {
        var subjectBlocks = new ArrayList<BlockPos>();

        for (var vec3i : subBlocks) {
            var offsetPos = switch (direction) {
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
        builder.add(TYPE, BlockStateProperties.HORIZONTAL_FACING);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, pContext.getHorizontalDirection());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MultiBlockEntity multiBlockEntity) {
            var blockPosList = getRotatedSubjectBlocks(Objects.requireNonNull(multiBlockEntity.mainPos), state.getValue(BlockStateProperties.HORIZONTAL_FACING), getSubBlocks());
            blockPosList.add(multiBlockEntity.mainPos);
            var broken = blockPosList.stream().anyMatch(blockPos -> level.getBlockState(blockPos).isAir() || !(level.getBlockEntity(blockPos) instanceof MultiBlockEntity));
            if (broken) {
                for (var subjectBlock : blockPosList) {
                    level.destroyBlock(subjectBlock, false);
                }
            }
        }
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        var subjectBlocks = getRotatedSubjectBlocks(pos, state.getValue(BlockStateProperties.HORIZONTAL_FACING), getSubBlocks());
        setSubBlocks(level, pos, state, subjectBlocks);
    }

    public static void setSubBlocks(Level level, BlockPos pos, BlockState state, List<BlockPos> subBlocks) {
        for (var subjectBlock : subBlocks) {
            level.setBlock(subjectBlock, state.setValue(TYPE, MultiBlockType.SUBJECT), 2);
            var blockEntity = level.getBlockEntity(subjectBlock);
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
        public String getSerializedName() {
            return name;
        }
    }
}