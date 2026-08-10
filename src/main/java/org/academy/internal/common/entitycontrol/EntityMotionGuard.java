package org.academy.internal.common.entitycontrol;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.accelerator.skills.lv3.VectorReduction;
import org.academy.internal.common.ability.accelerator.skills.lv4.ReflectionFilter;
import org.academy.internal.common.ability.accelerator.skills.lv4.VectorReflection;
import org.academy.internal.common.world.effect.StatusEffects;

import java.lang.StackWalker.StackFrame;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Guards vanilla entity motion entry points for imprisonment and vector-reflection protection.
 */
public final class EntityMotionGuard {
    private static final double POSITION_EPSILON_SQUARED = 1.0e-8;
    private static final StackWalker STACK_WALKER = StackWalker.getInstance(
            Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE)
    );
    private static final Map<UUID, Imprisonment> IMPRISONMENTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<IdentityHashMap<Entity, Integer>> INTERNAL_CORRECTIONS =
            new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<Entity>> MOTION_SOURCES = new ThreadLocal<>();
    private static final Set<String> GUARDED_ENTRY_METHODS = Set.of(
            "move", "setDeltaMovement", "addDeltaMovement", "lerpMotion",
            "teleport", "teleportTo", "teleportRelative", "teleportSetPosition",
            "absSnapTo", "snapTo", "setPos", "setPosRaw", "copyPosition",
            "randomTeleport"
    );
    private static final Set<String> FORCED_PLAYER_METHODS = Set.of(
            "knockback", "push", "teleport", "teleportTo", "teleportRelative",
            "teleportSetPosition", "absSnapTo", "snapTo", "copyPosition",
            "setPosRaw", "randomTeleport", "lerpMotion", "lerpPositionAndRotationStep"
    );

    private EntityMotionGuard() {
    }

    public static boolean shouldBlockMovement(Entity entity) {
        return shouldBlockMotion(entity);
    }

    public static boolean shouldBlockVelocity(Entity entity) {
        return shouldBlockMotion(entity);
    }

    public static boolean shouldBlockTeleport(Entity entity) {
        return shouldBlockMotion(entity);
    }

    public static boolean shouldBlockPositionSnap(Entity entity) {
        return shouldBlockMotion(entity);
    }

    public static boolean canApplyMotionFrom(Entity source, Entity target) {
        if (target == null) return false;
        if (target.level().isClientSide()) return true;
        var imprisoned = target instanceof LivingEntity living && isImprisoned(living);
        if (imprisoned) return false;
        var protectedPlayer = hasForcedMovementProtection(target);
        var shouldProtect = EntityMotionPolicy.shouldBlock(
                false,
                false,
                protectedPlayer,
                source != null,
                source == target,
                false
        );
        return !shouldProtect
                || !VectorReflection.Server.tryProtectForcedMovement((ServerPlayer) target);
    }

    public static boolean canBeImprisoned(LivingEntity entity) {
        return entity != null
                && !entity.level().isClientSide()
                && !ignoresImprisonment(entity);
    }

    public static void imprison(LivingEntity entity, String sourceId, long durationTicks) {
        if (entity == null || entity.level().isClientSide()
                || sourceId == null || sourceId.isBlank() || durationTicks <= 0L) {
            return;
        }
        if (ignoresImprisonment(entity)) return;
        var now = gameTime(entity);
        IMPRISONMENTS.compute(entity.getUUID(), (ignored, current) -> {
            if (current == null
                    || current.entity.get() != entity
                    || !current.dimensionId.equals(dimensionId(entity))) {
                current = new Imprisonment(entity, entity.position(), dimensionId(entity));
            }
            current.expirations.merge(sourceId, now + durationTicks, Math::max);
            return current;
        });
        applyImprisonmentEffects(entity, durationTicks);
        runInternalCorrection(entity, () -> entity.setDeltaMovement(Vec3.ZERO));
        entity.hurtMarked = true;
    }

    private static void applyImprisonmentEffects(LivingEntity entity, long durationTicks) {
        var effectDuration = (int) Math.min(Integer.MAX_VALUE, durationTicks);
        entity.addEffect(new MobEffectInstance(
                StatusEffects.IMPRISONED,
                effectDuration,
                0,
                false,
                false,
                true
        ));
        entity.addEffect(new MobEffectInstance(
                MobEffects.MINING_FATIGUE,
                effectDuration,
                0,
                false,
                false,
                true
        ));
    }

    public static void release(LivingEntity entity, String sourceId) {
        if (entity == null || entity.level().isClientSide() || sourceId == null) return;
        IMPRISONMENTS.computeIfPresent(entity.getUUID(), (ignored, state) -> {
            state.expirations.remove(sourceId);
            return state.expirations.isEmpty() ? null : state;
        });
    }

    public static boolean isImprisoned(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return false;
        var state = activeState(entity);
        return state != null && !ignoresImprisonment(entity);
    }

    public static void tick(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        var state = activeState(entity);
        if (state == null || ignoresImprisonment(entity)) return;
        runInternalCorrection(entity, () -> {
            entity.setDeltaMovement(Vec3.ZERO);
            if (entity.position().distanceToSqr(state.anchor) > POSITION_EPSILON_SQUARED) {
                entity.absSnapTo(state.anchor.x, state.anchor.y, state.anchor.z,
                        entity.getYRot(), entity.getXRot());
            }
        });
        entity.resetFallDistance();
        entity.hurtMarked = true;
    }

    public static boolean correctImprisonedPlayer(ServerPlayer player, float yRot, float xRot) {
        if (player == null) return false;
        var state = activeState(player);
        if (state == null || ignoresImprisonment(player)) return false;
        var safeYRot = Float.isFinite(yRot) ? yRot : player.getYRot();
        var safeXRot = Float.isFinite(xRot) ? xRot : player.getXRot();
        runInternalCorrection(player, () -> {
            player.setDeltaMovement(Vec3.ZERO);
            if (player.connection != null) {
                player.connection.teleport(
                        state.anchor.x, state.anchor.y, state.anchor.z,
                        safeYRot, safeXRot
                );
            } else {
                player.absSnapTo(
                        state.anchor.x, state.anchor.y, state.anchor.z,
                        safeYRot, safeXRot
                );
            }
        });
        player.resetFallDistance();
        player.hurtMarked = true;
        return true;
    }

    public static void runInternalCorrection(Entity entity, Runnable action) {
        if (entity == null || action == null) return;
        var depths = INTERNAL_CORRECTIONS.get();
        if (depths == null) {
            depths = new IdentityHashMap<>();
            INTERNAL_CORRECTIONS.set(depths);
        }
        depths.merge(entity, 1, Integer::sum);
        try {
            action.run();
        } finally {
            var depth = depths.getOrDefault(entity, 0);
            if (depth <= 1) depths.remove(entity);
            else depths.put(entity, depth - 1);
            if (depths.isEmpty()) INTERNAL_CORRECTIONS.remove();
        }
    }

    public static void runWithMotionSource(Entity source, Runnable action) {
        if (source == null || action == null) {
            if (action != null) action.run();
            return;
        }
        var sources = MOTION_SOURCES.get();
        if (sources == null) {
            sources = new ArrayDeque<>();
            MOTION_SOURCES.set(sources);
        }
        sources.push(source);
        try {
            action.run();
        } finally {
            sources.pop();
            if (sources.isEmpty()) MOTION_SOURCES.remove();
        }
    }

    public static <T> T callWithMotionSource(Entity source, Supplier<T> action) {
        if (action == null) return null;
        if (source == null) return action.get();
        var sources = MOTION_SOURCES.get();
        if (sources == null) {
            sources = new ArrayDeque<>();
            MOTION_SOURCES.set(sources);
        }
        sources.push(source);
        try {
            return action.get();
        } finally {
            sources.pop();
            if (sources.isEmpty()) MOTION_SOURCES.remove();
        }
    }

    private static boolean shouldBlockMotion(Entity entity) {
        if (entity == null || entity.level().isClientSide()) return false;
        if (isInternalCorrection(entity)) return false;
        var imprisoned = entity instanceof LivingEntity living && isImprisoned(living);
        if (imprisoned) return true;
        var protectedPlayer = hasForcedMovementProtection(entity);
        if (!protectedPlayer) return false;
        var source = currentMotionSource();
        var fallbackSelfSource = source == null && isPlayerSelfSourced((ServerPlayer) entity);
        var shouldProtect = EntityMotionPolicy.shouldBlock(
                false,
                false,
                true,
                source != null,
                source == entity,
                fallbackSelfSource
        );
        return shouldProtect
                && VectorReflection.Server.tryProtectForcedMovement((ServerPlayer) entity);
    }

    private static boolean hasForcedMovementProtection(Entity entity) {
        return entity instanceof ServerPlayer player
                && VectorReflection.Server.isActive(player)
                && ReflectionFilter.isForcedMovementProtectionEnabled(player);
    }

    private static boolean ignoresImprisonment(LivingEntity entity) {
        return entity instanceof ServerPlayer player
                && (VectorReflection.Server.isActive(player)
                || VectorReduction.Server.isActive(player));
    }

    private static Entity currentMotionSource() {
        var sources = MOTION_SOURCES.get();
        return sources == null ? null : sources.peek();
    }

    private static boolean isPlayerSelfSourced(ServerPlayer player) {
        return STACK_WALKER.walk(frames -> frames
                .filter(frame -> !isGuardInfrastructure(frame))
                .map(frame -> classifyPlayerFrame(player.getClass(), frame))
                .filter(decision -> decision != FrameDecision.SKIP)
                .findFirst()
                .orElse(FrameDecision.DENY) == FrameDecision.ALLOW);
    }

    private static boolean isGuardInfrastructure(StackFrame frame) {
        var owner = frame.getDeclaringClass();
        if (owner == EntityMotionGuard.class) return true;
        var name = owner.getName();
        if (name.startsWith("org.academy.mixin.")) return true;
        return owner == Entity.class && GUARDED_ENTRY_METHODS.contains(frame.getMethodName());
    }

    private static FrameDecision classifyPlayerFrame(
            Class<?> concretePlayerClass,
            StackFrame frame
    ) {
        var owner = frame.getDeclaringClass();
        var method = frame.getMethodName();
        if (owner.getName().equals("net.minecraft.server.network.ServerGamePacketListenerImpl")
                && method.equals("handleMovePlayer")) {
            return FrameDecision.ALLOW;
        }
        if (owner.isAssignableFrom(concretePlayerClass)) {
            return FORCED_PLAYER_METHODS.contains(method) ? FrameDecision.DENY : FrameDecision.ALLOW;
        }
        if (owner.getName().startsWith("java.lang.invoke.")
                || owner.getName().startsWith("org.spongepowered.asm.mixin.")) {
            return FrameDecision.SKIP;
        }
        return FrameDecision.DENY;
    }

    private static boolean isInternalCorrection(Entity entity) {
        var depths = INTERNAL_CORRECTIONS.get();
        return depths != null && depths.getOrDefault(entity, 0) > 0;
    }

    private static Imprisonment activeState(LivingEntity entity) {
        if (entity.level().isClientSide()) return null;
        var state = IMPRISONMENTS.get(entity.getUUID());
        if (state == null) return null;
        if (entity.isRemoved()
                || state.entity.get() != entity
                || !state.dimensionId.equals(dimensionId(entity))) {
            IMPRISONMENTS.remove(entity.getUUID(), state);
            return null;
        }
        var now = gameTime(entity);
        state.expirations.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (!state.expirations.isEmpty()) return state;
        IMPRISONMENTS.remove(entity.getUUID(), state);
        return null;
    }

    private static long gameTime(Entity entity) {
        return entity.level().getGameTime();
    }

    private static String dimensionId(Entity entity) {
        return entity.level().dimension().identifier().toString();
    }

    private enum FrameDecision {
        SKIP,
        ALLOW,
        DENY
    }

    private static final class Imprisonment {
        private final WeakReference<LivingEntity> entity;
        private final Vec3 anchor;
        private final String dimensionId;
        private final Map<String, Long> expirations = new HashMap<>();

        private Imprisonment(LivingEntity entity, Vec3 anchor, String dimensionId) {
            this.entity = new WeakReference<>(entity);
            this.anchor = anchor;
            this.dimensionId = dimensionId;
        }
    }
}
