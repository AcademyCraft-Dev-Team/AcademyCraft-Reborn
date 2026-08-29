package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.common.ability.DevelopmentSource;
import org.academy.api.common.vanilla.OpenScreenPacket;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.academy.internal.common.world.level.block.entity.MultiBlockEntity;
import org.misaka.api.common.network.packet.S2CPacket;

import java.util.Arrays;
import java.util.List;

public final class AbilityDeveloperBlock extends MultiBlock {
    public static final MapCodec<AbilityDeveloperBlock> CODEC = simpleCodec(AbilityDeveloperBlock::new);
    private static final VoxelShape SLEEPING_COLLISION_SHAPE = box(0, 0, 0, 16, 9, 16);

    public static final List<Vec3i> SUBJECT_BLOCKS = Arrays.asList(
            new Vec3i(0, 1, 0),
            new Vec3i(0, 0, 1),
            new Vec3i(0, 1, 1),
            new Vec3i(0, 2, 1),
            new Vec3i(0, 0, 2),
            new Vec3i(0, 1, 2),
            new Vec3i(0, 2, 2)
    );
    public static final String ABILITY_DEVELOPER_SCREEN = "ability_developer_screen";

    public AbilityDeveloperBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, pContext.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return false;
    }

    @Override
    public List<Vec3i> getSubBlocks() {
        return SUBJECT_BLOCKS;
    }

    @Override
    protected float getShadeBrightness(BlockState p_308911_, BlockGetter p_308952_, BlockPos p_308918_) {
        return 1.0F;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return interact(level, pos, player);
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        return player.isShiftKeyDown()
                ? interact(level, pos, player)
                : super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    private InteractionResult interact(Level level, BlockPos pos, Player player) {
        var mainBlockEntity = getMainBlockEntity(level, pos);
        if (level.isClientSide() && mainBlockEntity instanceof AbilityDeveloperBlockEntity abilityDeveloper) {
            abilityDeveloper.startOpening();
        }

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (mainBlockEntity instanceof AbilityDeveloperBlockEntity abilityDeveloper) {
                if (player.isShiftKeyDown()) {
                    AbilityDeveloperSleep.startSleeping(serverPlayer, abilityDeveloper);
                } else {
                    openScreen(serverPlayer, DevelopmentSource.block(abilityDeveloper.getBlockPos()));
                }
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof LivingEntity sleeper
                && sleeper.getSleepingPos().filter(pos::equals).isPresent()
                && AbilityDeveloperSleep.isSleepingAtDeveloper(sleeper)) {
            return SLEEPING_COLLISION_SHAPE;
        }
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, LivingEntity sleeper) {
        return true;
    }

    @Override
    public void setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied) {
    }

    @Override
    public Direction getBedDirection(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
    }

    public static void openScreen(ServerPlayer player, DevelopmentSource source) {
        var payloadBuffer = new FriendlyByteBuf(Unpooled.buffer());
        DevelopmentSource.CODEC.encode(payloadBuffer, source);
        var dataPayload = new byte[payloadBuffer.readableBytes()];
        payloadBuffer.readBytes(dataPayload);
        player.connection.send(new S2CPacket(
                new OpenScreenPacket(ABILITY_DEVELOPER_SCREEN, dataPayload)
        ));
    }

    @Override
    public MultiBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AbilityDeveloperBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                  BlockState state,
                                                                  BlockEntityType<T> blockEntityType) {
        return (level1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof AbilityDeveloperBlockEntity abe) {
                if (abe.isMain()) {
                    if (level1.isClientSide()) {
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
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
