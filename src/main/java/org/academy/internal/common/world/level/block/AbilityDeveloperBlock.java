package org.academy.internal.common.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("deprecation")
public class AbilityDeveloperBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<MultiBlockType> TYPE = EnumProperty.create("type", MultiBlockType.class);
    public static final List<Vec3i> SUBJECT_BLOCKS = Arrays.asList(
            // 以 North
            new Vec3i(0, 1, 0),   // 上
            new Vec3i(0, 0, 1),   // 后
            new Vec3i(0, 1, 1),   // 后上
            new Vec3i(0, 1, 2),   // 后上上
            new Vec3i(0, 0, 2),   // 后后
            new Vec3i(0, 1, 2),   // 后后上
            new Vec3i(0, 2, 2)    // 后后上上
    );

    public AbilityDeveloperBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TYPE, MultiBlockType.MAIN).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(TYPE, FACING);
    }

    @Override
    protected void spawnDestroyParticles(@NotNull Level level, @NotNull Player player, @NotNull BlockPos pos, @NotNull BlockState state) {
        // 粒子不合适
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block neighborBlock, @NotNull BlockPos neighborPos, boolean movedByPiston) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AbilityDeveloperBlockEntity abilityDeveloperBlockEntity) {
            List<BlockPos> blockPosList = getRotatedSubjectBlocks(abilityDeveloperBlockEntity.mainPos, state.getValue(FACING));
            blockPosList.add(abilityDeveloperBlockEntity.mainPos);
            boolean broken = blockPosList.stream().anyMatch(blockPos -> level.getBlockState(blockPos).isAir() || !(level.getBlockEntity(blockPos) instanceof AbilityDeveloperBlockEntity));
            if (broken) {
                for (BlockPos subjectBlock : blockPosList) {
                    level.destroyBlock(subjectBlock, false);
                }
            }
        }
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        List<BlockPos> subjectBlocks = getRotatedSubjectBlocks(pos, state.getValue(FACING));
        for (BlockPos subjectBlock : subjectBlocks) {
            level.setBlock(subjectBlock, state.setValue(TYPE, MultiBlockType.SUBJECT), 2);
            BlockEntity blockEntity = level.getBlockEntity(subjectBlock);
            if (blockEntity instanceof AbilityDeveloperBlockEntity abilityDeveloperBlockEntity) {
                abilityDeveloperBlockEntity.setMainPos(pos);
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction direction = context.getHorizontalDirection().getOpposite();
        return super.getStateForPlacement(context).setValue(FACING, direction);
    }

    @Override
    public @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState p_49545_) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AbilityDeveloperBlockEntity(pos, state);
    }

    public static List<BlockPos> getRotatedSubjectBlocks(BlockPos pos, Direction direction) {
        final List<BlockPos> subjectBlocks = new ArrayList<>();

        for (Vec3i vec3i : SUBJECT_BLOCKS) {
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