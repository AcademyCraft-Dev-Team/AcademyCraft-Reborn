package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.api.common.network.packet.S2CPacket;
import org.academy.api.common.util.FriendlyByteBufUtil;
import org.academy.api.common.vanilla.OpenScreenPacket;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class AbilityDeveloperBlock extends MultiBlock {
    public static final MapCodec<AbilityDeveloperBlock> CODEC = simpleCodec(AbilityDeveloperBlock::new);

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

    public AbilityDeveloperBlock(Properties properties) {
        super(BlockBehaviour.Properties.of().noOcclusion().strength(6.0F, 7.0F).requiresCorrectToolForDrops());
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite());
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
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, Player player, @NotNull BlockHitResult hitResult) {
        if (!player.isShiftKeyDown()) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                if (serverLevel.getBlockEntity(pos) instanceof AbilityDeveloperBlockEntity blockEntity) {
                    if (blockEntity.mainPos != null) {
                        serverPlayer.connection.send(new S2CPacket(
                                new OpenScreenPacket(ABILITY_DEVELOPER_SCREEN,
                                        FriendlyByteBufUtil.autoSerializable(blockEntity.mainPos))
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
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                  @NotNull BlockState state,
                                                                  @NotNull BlockEntityType<T> blockEntityType) {
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

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}