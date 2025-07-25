package org.academy.internal.common.world.level.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.academy.AcademyCraft;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.util.ServerPlayerUtil;
import org.academy.internal.client.gui.world.WindGenWorldGUI;
import org.academy.internal.common.world.inventory.WindGenMenu;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public class WindGenBaseBlock extends MultiBlock {
    public static final MapCodec<WindGenBaseBlock> CODEC = simpleCodec(WindGenBaseBlock::new);
    public static final String WIND_GEN_SCREEN = "wind_gen_screen";
    public static final List<Vec3i> SUB_BLOCKS = List.of(
            new Vec3i(0, 1, 0)
    );
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public WindGenBaseBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {
            if (player.isShiftKeyDown()) {
                Vector3f startPos = player.getEyePosition().toVector3f();
                Vector3f endPos = new Vector3f();
                startPos.add(player.getLookAngle().scale(10).toVector3f(), endPos);

                AABB aabb = new AABB(-0.5, -5.0 / 16.0, -0.05, 0.5, 5.0 / 16.0, 0.05);

                PoseStack poseStack = new PoseStack();
                BlockPos mainPos = getMainBlockEntity(level, pos).mainPos;
                poseStack.translate(mainPos.getX(), mainPos.getY(), mainPos.getZ());
                poseStack.translate(0.5f, 1.5f, 0.5f);
                poseStack.mulPose(Axis.XP.rotationDegrees(180));

                float yRot = state.getValue(WindGenBaseBlock.FACING).getOpposite().toYRot();
                poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

                poseStack.translate(0, 0.3075f, 0.625f);
                poseStack.mulPose(Axis.XP.rotationDegrees(17.5f));

                Matrix4f matrix = poseStack.last().pose();

                Vector3f result = new Vector3f();
                boolean b = MathUtil.RayUtil.intersectRayTransformedAABB(startPos, endPos, aabb, matrix, result);
                if (b) {
                    Matrix4f worldToAABB = new Matrix4f(matrix).invert();
                    Vector3f localIntersectionPoint = worldToAABB.transformPosition(result, new Vector3f());

                    float aabbWidth = (float) (aabb.maxX - aabb.minX);
                    float aabbHeight = (float) (aabb.maxY - aabb.minY);

                    float normX = (localIntersectionPoint.x - (float) aabb.minX) / aabbWidth;
                    float normY = (localIntersectionPoint.y - (float) aabb.minY) / aabbHeight;

                    float guiX = (1.0f - normX) * WindGenWorldGUI.WIDTH;
                    float guiY = normY * WindGenWorldGUI.HEIGHT;

                    guiX = MathUtil.clamp(guiX, 0, WindGenWorldGUI.WIDTH);
                    guiY = MathUtil.clamp(guiY, 0, WindGenWorldGUI.HEIGHT);

                    AcademyCraft.LOGGER.info("Intersection in GUI coords: " + guiX + ", " + guiY);
                }
            }
            return InteractionResult.SUCCESS;
        } else {
            if (player instanceof ServerPlayer serverPlayer) {
                if (!serverPlayer.isShiftKeyDown() && level.getBlockEntity(pos) instanceof WindGenBaseBlockEntity windGenBaseBlockEntity) {
                    if (windGenBaseBlockEntity.getMain() instanceof WindGenBaseBlockEntity windGenBaseBlock) {
                        MenuProvider menuProvider = getMenuProvider(state, level, pos);
                        ServerPlayerUtil.openMenuScreen(serverPlayer, menuProvider, WIND_GEN_SCREEN, windGenBaseBlock.getBlockPos());
                    }
                }
            }
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean movedByPiston) {
        BlockEntity blockentity = level.getBlockEntity(pos);
        if (blockentity instanceof Container container) {
            Containers.dropContents(level, pos, container);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public List<Vec3i> getSubBlocks() {
        return SUB_BLOCKS;
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
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(FACING, pContext.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean canBeReplaced(@NotNull BlockState state, @NotNull BlockPlaceContext useContext) {
        return false;
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WindGenBaseBlockEntity windGenBaseBlockEntity) {
            if (windGenBaseBlockEntity.getMain() instanceof WindGenBaseBlockEntity windGenBaseBlock) {
                return new SimpleMenuProvider((containerId, playerInventory, player) -> new WindGenMenu(containerId, playerInventory, ContainerLevelAccess.create(level, pos), windGenBaseBlock), Component.empty());
            }
        }
        return null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof WindGenBaseBlockEntity windGenBaseBlockEntity) {
                if (windGenBaseBlockEntity.isMain()) {
                    windGenBaseBlockEntity.tick();
                }
            }
        };
    }
}