package org.academy.forge.internal.common.world.level.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.academy.api.common.network.NetworkResourceLocations;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.forge.internal.common.world.level.block.entity.forge.AbilityDeveloperBlockEntityForge;
import org.academy.forge.internal.common.world.level.block.entity.forge.RadioFrequencyEnergyOutputBridgeBlockEntity;
import org.academy.internal.common.world.item.AcademyCraftItems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("deprecation")
public class AbilityDeveloperBlockForge extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<MultiBlockType> TYPE = EnumProperty.create("type", MultiBlockType.class);
    public static final List<Vec3i> SUBJECT_BLOCKS = Arrays.asList(
            // 以 South
            new Vec3i(0, 1, 0),   // 上
            new Vec3i(0, 0, 1),   // 前
            new Vec3i(0, 1, 1),   // 前上
            new Vec3i(0, 2, 1),   // 前上上
            new Vec3i(0, 0, 2),   // 前前
            new Vec3i(0, 1, 2),   // 前前上
            new Vec3i(0, 2, 2)    // 前前上上
    );

    public AbilityDeveloperBlockForge(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TYPE, MultiBlockType.MAIN).setValue(FACING, Direction.NORTH));
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || state.getValue(TYPE) == MultiBlockType.SUBJECT) return null;
        return (serverLevel, blockPos, blockState, blockEntity) -> {
            if (blockEntity instanceof AbilityDeveloperBlockEntityForge abilityDeveloperBlockEntity) {
                BlockEntity radio = serverLevel.getBlockEntity(blockPos.below());
                if (radio instanceof RadioFrequencyEnergyOutputBridgeBlockEntity radioFrequencyEnergyOutputBridgeBlockEntity) {
                    radioFrequencyEnergyOutputBridgeBlockEntity.getCapability(ForgeCapabilities.ENERGY).ifPresent(iEnergyStorage -> {
                        int extractEnergy = iEnergyStorage.extractEnergy(iEnergyStorage.getEnergyStored(), false);
                        if (extractEnergy > 0) {
                            abilityDeveloperBlockEntity.energyStored += extractEnergy;
                        }
                    });
                }
            }
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(TYPE, FACING);
    }

    @Override
    protected void spawnDestroyParticles(@NotNull Level level, @NotNull Player player, @NotNull BlockPos pos, @NotNull BlockState state) {
    }

    @Override
    public boolean canBeReplaced(@NotNull BlockState state, @NotNull BlockPlaceContext useContext) {
        return false;
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block neighborBlock, @NotNull BlockPos neighborPos, boolean movedByPiston) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AbilityDeveloperBlockEntityForge abilityDeveloperBlockEntityForge) {
            List<BlockPos> blockPosList = getRotatedSubjectBlocks(abilityDeveloperBlockEntityForge.mainPos, state.getValue(FACING));
            blockPosList.add(abilityDeveloperBlockEntityForge.mainPos);
            boolean broken = blockPosList.stream().anyMatch(blockPos -> level.getBlockState(blockPos).isAir() || !(level.getBlockEntity(blockPos) instanceof AbilityDeveloperBlockEntityForge));
            if (broken) {
                for (BlockPos subjectBlock : blockPosList) {
                    level.destroyBlock(subjectBlock, false);
                }
            }
        }
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                if (level.getBlockEntity(pos) instanceof AbilityDeveloperBlockEntityForge abilityDeveloperBlockEntity) {
                    if (!abilityDeveloperBlockEntity.isEmpty()) {
                        abilityDeveloperBlockEntity.setItem(0, ItemStack.EMPTY);
                        player.addItem(new ItemStack(AcademyCraftItems.ABILITY_DEVELOPER_COMPUTATIONAL_CHIP_ITEM.asItem()));
                    }
                }
            }
        } else {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                if (serverLevel.getBlockEntity(pos) instanceof AbilityDeveloperBlockEntityForge abilityDeveloperBlockEntity) {
                    serverPlayer.connection.send(new S2CPacket(
                            NetworkResourceLocations.S2C_OPEN_ABILITY_DEVELOPER_FRAGMENT_PACKET,
                            new BlockPos(abilityDeveloperBlockEntity.mainPos)
                    ));
                }
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        List<BlockPos> subjectBlocks = getRotatedSubjectBlocks(pos, state.getValue(FACING));
        for (BlockPos subjectBlock : subjectBlocks) {
            level.setBlock(subjectBlock, state.setValue(TYPE, MultiBlockType.SUBJECT), 2);
            BlockEntity blockEntity = level.getBlockEntity(subjectBlock);
            if (blockEntity instanceof AbilityDeveloperBlockEntityForge abilityDeveloperBlockEntityForge) {
                abilityDeveloperBlockEntityForge.setMainPos(pos);
            }
        }
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction direction = context.getHorizontalDirection();
        return super.getStateForPlacement(context).setValue(FACING, direction);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AbilityDeveloperBlockEntityForge(pos, state);
    }

    public static List<BlockPos> getRotatedSubjectBlocks(BlockPos pos, Direction direction) {
        final List<BlockPos> subjectBlocks = new ArrayList<>();

        for (Vec3i vec3i : SUBJECT_BLOCKS) {
            BlockPos offsetPos = switch (direction) {
                case NORTH -> pos.offset(vec3i.getX(), vec3i.getY(), -vec3i.getZ());
                case SOUTH -> pos.offset(-vec3i.getX(), vec3i.getY(), vec3i.getZ());
                case EAST -> pos.offset(vec3i.getZ(), vec3i.getY(), vec3i.getX());
                case WEST -> pos.offset(-vec3i.getZ(), vec3i.getY(), -vec3i.getX());
                default -> pos;
            };
            subjectBlocks.add(offsetPos);
        }

        return subjectBlocks;
    }

    @Override
    public boolean skipRendering(@NotNull BlockState state, @NotNull BlockState adjacentState, @NotNull Direction direction) {
        return false;
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