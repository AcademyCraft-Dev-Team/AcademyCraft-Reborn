package org.academy.internal.common.ability.accelerator.reflection.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;

import java.util.*;

/**
 * Limits only the audiovisual feedback produced by continuous positional
 * environmental damage. Damage interception and ability execution remain in
 * their existing call sites.
 */
public final class VectorEnvironmentalFeedbackController {
    static final long VISUAL_INTERVAL_TICKS = 20L;
    static final long SOUND_INTERVAL_TICKS = 40L;
    static final long CONTACT_EXPIRY_TICKS = 12L;
    private static final long STALE_STATE_TICKS = 200L;
    private static final double SCAN_MARGIN = 0.125;
    private static final double FLOOR_SCAN_DEPTH = 0.35;
    private static final double DIRECTION_EPSILON = 1.0E-8;
    private static final Map<FeedbackKey, FeedbackState> STATES = new HashMap<>();

    private VectorEnvironmentalFeedbackController() {
    }

    public static void emitReflection(
            ServerPlayer defender,
            DamageSource source,
            Vec3 fallbackDirection,
            Vec3 fallbackPosition
    ) {
        if (emitEnvironmental(defender, source, VectorRedirectKind.REFLECTION)) return;
        VectorReflection.Server.spawnGlowCircle(defender, fallbackDirection, fallbackPosition);
        VectorReflection.Server.playReflectionSound(defender);
    }

    public static void emitRefraction(
            ServerPlayer defender,
            DamageSource source,
            long fallbackAttackKey,
            Vec3 fallbackDirection,
            Vec3 fallbackPosition
    ) {
        if (emitEnvironmental(defender, source, VectorRedirectKind.REFRACTION)) return;
        VectorCompatibilityEffectLimiter.emit(
                defender,
                fallbackAttackKey,
                fallbackDirection,
                fallbackPosition
        );
    }

    public static void clear(ServerPlayer defender) {
        if (defender == null) return;
        var defenderId = defender.getUUID();
        STATES.keySet().removeIf(key -> key.defenderId.equals(defenderId));
    }

    static boolean isSupportedDamageType(String damageTypeId) {
        return EnvironmentKind.fromDamageType(damageTypeId) != null;
    }

    static boolean shouldEmit(long now, long lastEmission, long interval) {
        return lastEmission == Long.MIN_VALUE || now - lastEmission >= interval;
    }

    static boolean isExpired(long now, long lastSeen) {
        return lastSeen == Long.MIN_VALUE || now - lastSeen > CONTACT_EXPIRY_TICKS;
    }

    static Optional<EnvironmentalFeedbackOrigin> originFromSource(
            AABB defenderBounds,
            Vec3 sourcePosition,
            boolean downwardFallback
    ) {
        if (defenderBounds == null || !isFinite(sourcePosition)) return Optional.empty();
        var center = defenderBounds.getCenter();
        var toSource = sourcePosition.subtract(center);
        if (!isFinite(toSource) || toSource.lengthSqr() < DIRECTION_EPSILON) {
            if (!downwardFallback) return Optional.empty();
            toSource = new Vec3(0.0, -1.0, 0.0);
        }
        var normal = toSource.normalize();
        var ringPosition = surfacePoint(defenderBounds, center, normal).add(normal.scale(0.05));
        return Optional.of(new EnvironmentalFeedbackOrigin(sourcePosition, ringPosition, normal));
    }

    private static boolean emitEnvironmental(
            ServerPlayer defender,
            DamageSource source,
            VectorRedirectKind redirectKind
    ) {
        if (defender == null || source == null || redirectKind == null) return false;
        var damageTypeId = VectorCompatProfile.damageTypeId(source);
        var environmentKind = EnvironmentKind.fromDamageType(damageTypeId);
        if (environmentKind == null) return false;

        var now = defender.level().getGameTime();
        var key = new FeedbackKey(
                defender.getUUID(),
                defender.level().dimension(),
                redirectKind,
                damageTypeId
        );
        var state = STATES.get(key);
        if (state == null || isExpired(now, state.lastSeenTick)) {
            state = new FeedbackState();
            STATES.put(key, state);
        }
        state.lastSeenTick = now;

        if (shouldEmit(now, state.lastVisualTick, VISUAL_INTERVAL_TICKS)) {
            var origin = resolveOrigin(defender, environmentKind).orElse(null);
            if (origin != null) {
                state.lastOrigin = origin;
                state.lastOriginTick = now;
            } else if (state.lastOrigin != null
                    && now - state.lastOriginTick <= CONTACT_EXPIRY_TICKS) {
                origin = state.lastOrigin;
            }
            if (origin != null) {
                VectorReflection.Server.spawnGlowCircle(
                        defender,
                        origin.normal,
                        origin.ringPosition
                );
            }
            state.lastVisualTick = now;
        }

        if (shouldEmit(now, state.lastSoundTick, SOUND_INTERVAL_TICKS)) {
            VectorReflection.Server.playReflectionSound(defender);
            state.lastSoundTick = now;
        }

        if (STATES.size() > 512) {
            STATES.entrySet().removeIf(entry -> now - entry.getValue().lastSeenTick > STALE_STATE_TICKS);
        }
        return true;
    }

    private static Optional<EnvironmentalFeedbackOrigin> resolveOrigin(
            ServerPlayer defender,
            EnvironmentKind environmentKind
    ) {
        var bounds = defender.getBoundingBox();
        var scanDepth = environmentKind.floorBiased ? FLOOR_SCAN_DEPTH : SCAN_MARGIN;
        var scanBounds = new AABB(
                bounds.minX - SCAN_MARGIN,
                bounds.minY - scanDepth,
                bounds.minZ - SCAN_MARGIN,
                bounds.maxX + SCAN_MARGIN,
                bounds.maxY + SCAN_MARGIN,
                bounds.maxZ + SCAN_MARGIN
        );
        var from = BlockPos.containing(scanBounds.minX, scanBounds.minY, scanBounds.minZ);
        var to = BlockPos.containing(scanBounds.maxX, scanBounds.maxY, scanBounds.maxZ);
        var center = bounds.getCenter();
        Vec3 bestSource = null;
        var bestDistance = Double.POSITIVE_INFINITY;

        for (var pos : BlockPos.betweenClosed(from, to)) {
            var state = defender.level().getBlockState(pos);
            if (!environmentKind.matches(defender.level(), pos, state)) continue;
            var hazardBounds = environmentKind.hazardBounds(defender.level(), pos, state);
            if (!hazardBounds.intersects(scanBounds)) continue;
            var sourcePoint = closestPoint(hazardBounds, center);
            if (sourcePoint.distanceToSqr(center) < DIRECTION_EPSILON) {
                sourcePoint = hazardBounds.getCenter();
            }
            var distance = sourcePoint.distanceToSqr(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSource = sourcePoint;
            }
        }

        return originFromSource(bounds, bestSource, environmentKind.floorBiased);
    }

    private static Vec3 closestPoint(AABB bounds, Vec3 point) {
        return new Vec3(
                Math.clamp(point.x, bounds.minX, bounds.maxX),
                Math.clamp(point.y, bounds.minY, bounds.maxY),
                Math.clamp(point.z, bounds.minZ, bounds.maxZ)
        );
    }

    private static Vec3 surfacePoint(AABB bounds, Vec3 center, Vec3 direction) {
        var distance = Double.POSITIVE_INFINITY;
        if (direction.x > DIRECTION_EPSILON) {
            distance = Math.min(distance, (bounds.maxX - center.x) / direction.x);
        } else if (direction.x < -DIRECTION_EPSILON) {
            distance = Math.min(distance, (bounds.minX - center.x) / direction.x);
        }
        if (direction.y > DIRECTION_EPSILON) {
            distance = Math.min(distance, (bounds.maxY - center.y) / direction.y);
        } else if (direction.y < -DIRECTION_EPSILON) {
            distance = Math.min(distance, (bounds.minY - center.y) / direction.y);
        }
        if (direction.z > DIRECTION_EPSILON) {
            distance = Math.min(distance, (bounds.maxZ - center.z) / direction.z);
        } else if (direction.z < -DIRECTION_EPSILON) {
            distance = Math.min(distance, (bounds.minZ - center.z) / direction.z);
        }
        return Double.isFinite(distance) ? center.add(direction.scale(distance)) : center;
    }

    private static boolean isFinite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private enum EnvironmentKind {
        LAVA(true),
        CACTUS(false),
        HOT_FLOOR(true),
        IN_FIRE(false),
        CAMPFIRE(true),
        SWEET_BERRY_BUSH(false);

        private final boolean floorBiased;

        EnvironmentKind(boolean floorBiased) {
            this.floorBiased = floorBiased;
        }

        private static EnvironmentKind fromDamageType(String damageTypeId) {
            if (damageTypeId == null) return null;
            return switch (damageTypeId.toLowerCase(Locale.ROOT)) {
                case "lava", "minecraft:lava" -> LAVA;
                case "cactus", "minecraft:cactus" -> CACTUS;
                case "hot_floor", "minecraft:hot_floor" -> HOT_FLOOR;
                case "in_fire", "minecraft:in_fire" -> IN_FIRE;
                case "campfire", "minecraft:campfire" -> CAMPFIRE;
                case "sweet_berry_bush", "minecraft:sweet_berry_bush" -> SWEET_BERRY_BUSH;
                default -> null;
            };
        }

        private boolean matches(Level level, BlockPos pos, BlockState state) {
            return switch (this) {
                case LAVA -> state.getFluidState().is(FluidTags.LAVA)
                        || state.is(Blocks.LAVA_CAULDRON);
                case CACTUS -> state.is(Blocks.CACTUS);
                case HOT_FLOOR -> state.is(Blocks.MAGMA_BLOCK);
                case IN_FIRE -> state.is(BlockTags.FIRE);
                case CAMPFIRE -> CampfireBlock.isLitCampfire(state);
                case SWEET_BERRY_BUSH -> state.is(Blocks.SWEET_BERRY_BUSH);
            };
        }

        private AABB hazardBounds(Level level, BlockPos pos, BlockState state) {
            if (this == LAVA && state.getFluidState().is(FluidTags.LAVA)) {
                var height = state.getFluidState().getHeight(level, pos);
                if (!(height > 0.0f) || !Float.isFinite(height)) height = 1.0f;
                return new AABB(
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1.0, pos.getY() + height, pos.getZ() + 1.0
                );
            }
            var shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
            }
            return new AABB(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0
            );
        }
    }

    record EnvironmentalFeedbackOrigin(Vec3 sourcePosition, Vec3 ringPosition, Vec3 normal) {
    }

    private record FeedbackKey(
            UUID defenderId,
            ResourceKey<Level> dimension,
            VectorRedirectKind redirectKind,
            String damageTypeId
    ) {
    }

    private static final class FeedbackState {
        private long lastSeenTick = Long.MIN_VALUE;
        private long lastVisualTick = Long.MIN_VALUE;
        private long lastSoundTick = Long.MIN_VALUE;
        private long lastOriginTick = Long.MIN_VALUE;
        private EnvironmentalFeedbackOrigin lastOrigin;
    }
}
