package org.academy.internal.common.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.academy.api.common.ability.darkmatter.DarkmatterBlockProfile;
import org.academy.internal.common.world.item.DarkmatterBlockItem;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.block.entity.DarkmatterBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

/** Full-cube dark matter whose physical behavior is supplied by its block entity profile. */
public final class DarkmatterConfigurableBlock extends FallingBlock implements EntityBlock {
    public static final BooleanProperty GRAVITY = BooleanProperty.create("gravity");
    public static final MapCodec<DarkmatterConfigurableBlock> CODEC =
            simpleCodec(DarkmatterConfigurableBlock::new);

    public DarkmatterConfigurableBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GRAVITY, false));
    }

    @Override
    protected MapCodec<? extends FallingBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(GRAVITY);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(GRAVITY,
                DarkmatterBlockItem.profile(context.getItemInHand()).gravity());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean isMoving) {
        if (state.getValue(GRAVITY)) level.scheduleTick(pos, this, getDelayAfterPlace());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
                                     ScheduledTickAccess scheduledTicks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        if (state.getValue(GRAVITY)) scheduledTicks.scheduleTick(pos, this, getDelayAfterPlace());
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(GRAVITY) || !isFree(level.getBlockState(pos.below()))
                || pos.getY() < level.getMinY()) return;
        var blockEntity = level.getBlockEntity(pos);
        var data = blockEntity == null ? null
                : blockEntity.saveWithoutMetadata(level.registryAccess());
        var falling = FallingBlockEntity.fall(level, pos, state);
        falling.blockData = data;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player,
                                       BlockGetter level, BlockPos pos) {
        var hardness = profile(level, pos).hardness();
        if (hardness <= 0.0f) return 1.0f;
        var divisor = net.neoforged.neoforge.event.EventHooks.doPlayerHarvestCheck(
                player, state, level, pos) ? 30.0f : 100.0f;
        return player.getDestroySpeed(state, pos) / hardness / divisor;
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level,
                                        BlockPos pos, Explosion explosion) {
        return profile(level, pos).explosionResistance();
    }

    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos,
                                  Explosion explosion,
                                  BiConsumer<ItemStack, BlockPos> onHit) {
        if (state.isAir() || explosion.getBlockInteraction()
                == Explosion.BlockInteraction.TRIGGER_BLOCK) return;
        var blockProfile = profile(level, pos);
        if (state.canDropFromExplosion(level, pos, explosion)) {
            onHit.accept(configuredStack(blockProfile), pos);
        }
        state.onBlockExploded(level, pos, explosion);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DarkmatterBlockEntity(pos, state);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005f);
        if (!level.isClientSide()) popResource(level, pos,
                configuredStack(blockEntity instanceof DarkmatterBlockEntity darkmatter
                        ? darkmatter.profile() : DarkmatterBlockProfile.DEFAULT));
    }

    @Override
    @Deprecated
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
                                          boolean includeData) {
        return configuredStack(profile(level, pos));
    }

    private static DarkmatterBlockProfile profile(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DarkmatterBlockEntity blockEntity
                ? blockEntity.profile() : DarkmatterBlockProfile.DEFAULT;
    }

    private static ItemStack configuredStack(DarkmatterBlockProfile profile) {
        var stack = new ItemStack(Items.DARKMATTER_BLOCK.get());
        DarkmatterBlockItem.setProfile(stack, profile);
        return stack;
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return 0x7777AA;
    }
}
