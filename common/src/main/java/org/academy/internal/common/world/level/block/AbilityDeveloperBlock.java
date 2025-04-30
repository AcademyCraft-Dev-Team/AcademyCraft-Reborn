package org.academy.internal.common.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.api.common.network.Packets;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("deprecation")
public class AbilityDeveloperBlock extends MultiBlock {
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
    public static final String ABILITY_DEVELOPER_SCREEN = "ability_developer_screen";

    public AbilityDeveloperBlock() {
        super(BlockBehaviour.Properties.of().noOcclusion().strength(6.0F, 7.0F).requiresCorrectToolForDrops());
    }

    @Override
    public boolean canBeReplaced(@NotNull BlockState state, @NotNull BlockPlaceContext useContext) {
        return false;
    }

    @Override
    public List<Vec3i> getSubBlocks() {
        return SUBJECT_BLOCKS;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                if (serverLevel.getBlockEntity(pos) instanceof AbilityDeveloperBlockEntity abilityDeveloperBlockEntity) {
                    if (abilityDeveloperBlockEntity.mainPos != null) {
                        serverPlayer.connection.send(new S2CPacket(
                                Packets.S2C_OPEN_SCREEN,
                                ABILITY_DEVELOPER_SCREEN,
                                abilityDeveloperBlockEntity.mainPos
                        ));
                    }
                }
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AbilityDeveloperBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof AbilityDeveloperBlockEntity abe) {
                if (abe.isMain()) {
                    if (level1.isClientSide) {
                        abe.clientTick();
                    } else {
                        if (level1 instanceof ServerLevel serverLevel) {
                            abe.serverTick(serverLevel);
                        }
                    }
                }
            }
        };
    }

    @Override
    public boolean skipRendering(@NotNull BlockState state, @NotNull BlockState adjacentState, @NotNull Direction direction) {
        return false;
    }
}