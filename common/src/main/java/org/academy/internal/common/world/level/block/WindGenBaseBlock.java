package org.academy.internal.common.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("deprecation")
public class WindGenBaseBlock extends MultiBlock {
    public static final List<Vec3i> SUB_BLOCKS = List.of(
            new Vec3i(0, 1, 0)
    );
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public WindGenBaseBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    public List<Vec3i> getSubBlocks() {
        return SUB_BLOCKS;
    }

    @Override
    public @NotNull InteractionResult use(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull Player player,
            @NotNull InteractionHand hand,
            @NotNull BlockHitResult hit
    ) {
        Vec3 rayOPos = player.getEyePosition();
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        double rayYawRad = Math.toRadians(yaw);
        double rayPitchRad = Math.toRadians(pitch);
        double rayDx = -Math.cos(rayPitchRad) * Math.sin(rayYawRad);
        double rayDy = -Math.sin(rayPitchRad);
        double rayDz = Math.cos(rayPitchRad) * Math.cos(rayYawRad);

        double panelPitch = -90.0, panelYaw = 180.0;
        double panelYawRad = Math.toRadians(panelYaw);
        double panelPitchRad = Math.toRadians(panelPitch);
        double nx = -Math.cos(panelPitchRad) * Math.sin(panelYawRad);
        double ny = -Math.sin(panelPitchRad);
        double nz = Math.cos(panelPitchRad) * Math.cos(panelYawRad);

        player.displayClientMessage(
                Component.literal(String.format(
                        "Ray Dir = [%.3f, %.3f, %.3f], Panel Normal = [%.3f, %.3f, %.3f]",
                        rayDx, rayDy, rayDz, nx, ny, nz
                )), false
        );

        double[] outIntersection = new double[3];
        boolean hitPanel = MathUtil.rayIntersectPanelFastAngles(
                rayOPos.x, rayOPos.y, rayOPos.z,
                pitch, yaw,
                pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                panelPitch, panelYaw,
                outIntersection
        );

        if (hitPanel) {
            double dx = outIntersection[0] - (pos.getX() + 0.5);
            double dy = outIntersection[1] - (pos.getY() + 1);
            double dz = outIntersection[2] - (pos.getZ() + 0.5);

            player.displayClientMessage(
                    Component.literal("Hit at: " +
                            String.format("[%.2f, %.2f, %.2f]", outIntersection[0], outIntersection[1], outIntersection[2])
                    ), false
            );
            player.displayClientMessage(
                    Component.literal("Offset from center: " +
                            String.format("dx=%.5f, dy=%.5f, dz=%.5f", dx, dy, dz)
                    ), false
            );
        } else {
            player.displayClientMessage(Component.literal("Missed panel"), true);
        }

        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(TYPE, FACING);
    }

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new WindGenBaseBlockEntity(pos, state);
    }

    @Override
    public @NotNull BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(FACING, pRotation.rotate(pState.getValue(FACING)));
    }

    @Override
    public boolean canBeReplaced(@NotNull BlockState state, @NotNull BlockPlaceContext useContext) {
        return false;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof WindGenBaseBlockEntity windGenBaseBlockEntity) {
                windGenBaseBlockEntity.ticks++;
                if (level1.isClientSide()) {
                }
            }
        };
    }
}