package org.academy.internal.common.world.entity.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PotentSulfurBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PotentSulfurState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla 26.2 potent-sulfur spring detection for Misaka favor.
 * Hot-spring water = water column / surface within the noxious-gas radius of non-dry {@code potent_sulfur}.
 */
public final class MisakaHotSpring {
    /** Matches {@link net.minecraft.world.level.block.entity.PotentSulfurBlockEntity#EFFECT_RANGE}. */
    public static final double EFFECT_RANGE = 3.0;
    public static final int SOAK_TICKS = 20 * 60;
    public static final int COOLDOWN_DAYS = 3;
    /** How close player and sister must stay to count as soaking together. */
    public static final double TOGETHER_RANGE = 6.0;
    private static final int SEARCH_XZ = 5;
    private static final int SEARCH_DOWN = 5;
    private static final int SEARCH_UP = 1;

    private MisakaHotSpring() {
    }

    public static boolean isInHotSpringWater(LivingEntity entity) {
        if (!entity.isInWater()) {
            return false;
        }
        Level level = entity.level();
        BlockPos origin = entity.blockPosition();
        for (int dx = -SEARCH_XZ; dx <= SEARCH_XZ; dx++) {
            for (int dz = -SEARCH_XZ; dz <= SEARCH_XZ; dz++) {
                for (int dy = -SEARCH_DOWN; dy <= SEARCH_UP; dy++) {
                    BlockPos sulfurPos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(sulfurPos);
                    if (!state.is(Blocks.POTENT_SULFUR)) {
                        continue;
                    }
                    if (state.getValue(PotentSulfurBlock.STATE) == PotentSulfurState.DRY) {
                        continue;
                    }
                    BlockPos surface = findNoxiousGasSurface(level, sulfurPos);
                    if (surface == null) {
                        continue;
                    }
                    if (isEntityInSpringPool(entity, sulfurPos, surface)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isEntityInSpringPool(LivingEntity entity, BlockPos sulfurPos, BlockPos surface) {
        double dx = entity.getX() - (surface.getX() + 0.5);
        double dz = entity.getZ() - (surface.getZ() + 0.5);
        if (dx * dx + dz * dz > EFFECT_RANGE * EFFECT_RANGE) {
            return false;
        }
        double midY = entity.getY() + entity.getBbHeight() * 0.5;
        // Allow standing at the surface or submerged in the column above the sulfur.
        return midY >= sulfurPos.getY() && midY <= surface.getY() + 1.5;
    }

    /**
     * Mirrors vanilla {@code PotentSulfurBlockEntity.findNoxiousGasSourceBlock}:
     * climb the water column (≤4) and return the first air / passable block above it.
     */
    private static @Nullable BlockPos findNoxiousGasSurface(Level level, BlockPos sulfurPos) {
        int maxY = sulfurPos.getY() + PotentSulfurBlock.ALLOWED_WATER_BLOCKS_ABOVE + 1;
        CollisionContext context = CollisionContext.positionContext(sulfurPos.getY());
        BlockPos.MutableBlockPos pos = sulfurPos.above().mutable();
        while (pos.getY() <= maxY) {
            BlockState state = level.getBlockState(pos);
            boolean waterSource = level.getFluidState(pos).isSourceOfType(Fluids.WATER);
            if (!waterSource || !state.is(Blocks.WATER) && !isPassable(state, level, pos, context)) {
                if (state.isAir() || isPassable(state, level, pos, context)) {
                    return pos.immutable();
                }
                break;
            }
            pos.move(Direction.UP);
        }
        return null;
    }

    private static boolean isPassable(BlockState state, Level level, BlockPos pos, CollisionContext context) {
        return !state.isAir() && !state.is(Blocks.WATER)
                ? state.getCollisionShape(level, pos, context).isEmpty()
                : true;
    }

    public static boolean areSoakingTogether(LivingEntity a, LivingEntity b) {
        if (a.distanceToSqr(b) > TOGETHER_RANGE * TOGETHER_RANGE) {
            return false;
        }
        return isInHotSpringWater(a) && isInHotSpringWater(b);
    }

    public static Vec3 steamPoint(LivingEntity entity) {
        return new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.55, entity.getZ());
    }
}
