package org.academy.api.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.AcademyCraftEntityTypes;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Optional;

public class LevelUtil {
    @SuppressWarnings("resource")
    public static double getValidViewDistance(Entity entity, double targetDistance) {
        Vec3 startPos = entity.position();
        Vec3 direction = Vec3.directionFromRotation(entity.getXRot(), entity.getYRot()).scale(targetDistance);
        Vec3 targetPos = startPos.add(direction);

        HitResult hitResult = entity.level().clip(new ClipContext(startPos, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        return (hitResult.getType() != HitResult.Type.MISS) ? hitResult.getLocation().distanceTo(startPos) : targetDistance;
    }

    @SuppressWarnings("DataFlowIssue")
    public static boolean canBreakBlock(BlockState blockState, int miningLevel) {
        if (blockState.getDestroySpeed(null, null) < 0) return false;
        return switch (miningLevel) {
            case 3 ->
                    blockState.is(BlockTags.NEEDS_DIAMOND_TOOL) || blockState.is(BlockTags.NEEDS_IRON_TOOL) || blockState.is(BlockTags.NEEDS_STONE_TOOL);
            case 2 -> blockState.is(BlockTags.NEEDS_IRON_TOOL) || blockState.is(BlockTags.NEEDS_STONE_TOOL);
            case 1 -> blockState.is(BlockTags.NEEDS_STONE_TOOL);
            default -> true;
        };
    }

    public static Optional<Pair<Boolean, Double>> destroyBlocksAlongPath(Level level, Vec3 start, Vec3 end, float radius, int miningLevel, boolean dropBlock, boolean spawnParticles, boolean canBlock) {
        double pathLength = start.distanceTo(end);
        Vec3 direction = end.subtract(start).normalize();
        double stepSize = 0.2;
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockState blockState;
        final BlockState air = Blocks.AIR.defaultBlockState();
        double radiusSquared = radius * radius;
        for (double distanceTraveled = 0; distanceTraveled <= pathLength; distanceTraveled += stepSize) {
            Vec3 currentPosition = start.add(direction.scale(distanceTraveled));
            for (double offsetX = -radius; offsetX <= radius; offsetX += stepSize) {
                for (double offsetY = -radius; offsetY <= radius; offsetY += stepSize) {
                    for (double offsetZ = -radius; offsetZ <= radius; offsetZ += stepSize) {
                        double distSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
                        if (distSquared <= radiusSquared) {
                            blockPos.set(Mth.floor(currentPosition.x + offsetX), Mth.floor(currentPosition.y + offsetY), Mth.floor(currentPosition.z + offsetZ));
                            blockState = level.getBlockState(blockPos);
                            if (canBreakBlock(blockState, miningLevel)) {
                                if (level instanceof ServerLevel serverLevel) {
                                    serverLevel.setBlock(blockPos, air, 2);
                                    if (spawnParticles) {
                                        serverLevel.levelEvent(2001, blockPos, Block.getId(blockState));
                                    }
                                    if (dropBlock) {
                                        BlockEntity blockEntity = blockState.hasBlockEntity() ? serverLevel.getBlockEntity(blockPos) : null;
                                        Block.dropResources(blockState, serverLevel, blockPos, blockEntity, null, ItemStack.EMPTY);
                                    }
                                }
                            } else if (canBlock) {
                                return Optional.of(Pair.of(true, distanceTraveled));
                            }
                        }
                    }
                }
            }
        }
        return Optional.of(Pair.of(false, pathLength));
    }

    public static void attackEntitiesAlongPath(Level level, Vec3 start, Vec3 end, float size, DamageSource damageSource, float damage) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        Vec3 direction = end.subtract(start).normalize();
        double stepSize = 0.2;
        double totalDistance = start.distanceTo(end);
        double sizeSquared = size * size;

        for (double traveled = 0; traveled <= totalDistance; traveled += stepSize) {
            Vec3 currentPos = start.add(direction.scale(traveled));
            AABB area = new AABB(currentPos.x - size, currentPos.y - size, currentPos.z - size, currentPos.x + size, currentPos.y + size, currentPos.z + size);

            List<Entity> entities = serverLevel.getEntities(null, area);
            for (Entity entity : entities) {
                if (entity.getType() == AcademyCraftEntityTypes.HIGH_SPEED_ELECTRON_BEAM_ENTITY_TYPE) continue;

                if (entity.position().distanceToSqr(currentPos) <= sizeSquared) {
                    if (entity instanceof EnderDragon enderDragon) {
                        enderDragon.reallyHurt(damageSource, damage);
                    } else {
                        entity.hurt(damageSource, damage);
                    }
                }
            }
        }
    }
}