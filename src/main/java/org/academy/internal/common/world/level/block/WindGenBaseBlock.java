package org.academy.internal.common.world.level.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.AcademyCraft;
import org.academy.api.common.util.MathUtil;
import org.academy.api.server.util.ServerPlayerUtil;
import org.academy.internal.client.gui.world.WindGenWorldGUI;
import org.academy.internal.common.world.inventory.WindGenMenu;
import org.academy.internal.common.world.level.block.entity.BlockEntityTypes;
import org.academy.internal.common.world.level.block.entity.WindGenBaseBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public final class WindGenBaseBlock extends MultiBlock {
    public static final MapCodec<WindGenBaseBlock> CODEC = simpleCodec(WindGenBaseBlock::new);
    public static final String WIND_GEN_SCREEN = "wind_gen_screen";
    public static final List<Vec3i> SUB_BLOCKS = List.of(
            new Vec3i(0, 1, 0)
    );

    public WindGenBaseBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                var startPos = player.getEyePosition().toVector3f();
                var endPos = new Vector3f();
                startPos.add(player.getLookAngle().scale(10).toVector3f(), endPos);

                var aabb = new AABB(-0.5, -5.0 / 16.0, -0.05, 0.5, 5.0 / 16.0, 0.05);

                var poseStack = new PoseStack();
                var mainPos = getMainBlockEntity(level, pos).mainPos;
                poseStack.translate(mainPos.getX(), mainPos.getY(), mainPos.getZ());
                poseStack.translate(0.5f, 1.5f, 0.5f);
                poseStack.mulPose(Axis.XP.rotationDegrees(180));

                var yRot = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite().toYRot();
                poseStack.mulPose(Axis.YP.rotationDegrees(yRot));

                poseStack.translate(0, 0.3075f, 0.625f);
                poseStack.mulPose(Axis.XP.rotationDegrees(17.5f));

                var matrix = poseStack.last().pose();

                var result = new Vector3f();
                var b = MathUtil.RayUtil.intersectRayTransformedAABB(startPos, endPos, aabb, matrix, result);
                if (b) {
                    var worldToAABB = new Matrix4f(matrix).invert();
                    var localIntersectionPoint = worldToAABB.transformPosition(result, new Vector3f());

                    float aabbWidth = (float) (aabb.maxX - aabb.minX);
                    float aabbHeight = (float) (aabb.maxY - aabb.minY);

                    float normX = (localIntersectionPoint.x - (float) aabb.minX) / aabbWidth;
                    float normY = (localIntersectionPoint.y - (float) aabb.minY) / aabbHeight;

                    float guiX = (1.0f - normX) * WindGenWorldGUI.WIDTH;
                    float guiY = normY * WindGenWorldGUI.HEIGHT;

                    guiX = MathUtil.clamp(guiX, 0, WindGenWorldGUI.WIDTH);
                    guiY = MathUtil.clamp(guiY, 0, WindGenWorldGUI.HEIGHT);

                    AcademyCraft.LOGGER.info("Intersection in GUI coords: {}, {}", guiX, guiY);
                }
            }
            return InteractionResult.SUCCESS;
        } else {
            if (player instanceof ServerPlayer serverPlayer) {
                if (!serverPlayer.isShiftKeyDown() && level.getBlockEntity(pos) instanceof WindGenBaseBlockEntity windGenBaseBlockEntity) {
                    if (windGenBaseBlockEntity.getMain() instanceof WindGenBaseBlockEntity windGenBaseBlock) {
                        var menuProvider = getMenuProvider(state, level, pos);
                        ServerPlayerUtil.openMenuScreen(serverPlayer, menuProvider, WIND_GEN_SCREEN,
                                buf -> buf.writeBlockPos(windGenBaseBlock.getBlockPos()));
                    }
                }
            }
            return InteractionResult.CONSUME;
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TYPE) == MultiBlockType.SUBJECT ? Block.cube(8.8692435136, 16, 8.8692435136) : super.getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TYPE) == MultiBlockType.SUBJECT ? getShape(state, level, pos, context) : super.getCollisionShape(state, level, pos, context);
    }

    @Override
    public List<Vec3i> getSubBlocks() {
        return SUB_BLOCKS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindGenBaseBlockEntity(pos, state);
    }

    @Override
    public BlockState rotate(BlockState pState, Rotation pRotation) {
        return pState.setValue(BlockStateProperties.HORIZONTAL_FACING, pRotation.rotate(pState.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return this.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, pContext.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return false;
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WindGenBaseBlockEntity windGenBaseBlockEntity) {
            if (windGenBaseBlockEntity.getMain() instanceof WindGenBaseBlockEntity windGenBaseBlock) {
                return new SimpleMenuProvider((containerId, playerInventory, player) -> new WindGenMenu(containerId, playerInventory, ContainerLevelAccess.create(level, pos), windGenBaseBlock), Component.empty());
            }
        }
        return null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BlockEntityTypes.WIND_GEN_BASE.get(), WindGenBaseBlockEntity::tick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}