package org.academy.api.common.ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.ability.aeromanip.AirMobilitySyncPacket;

import java.util.Map;
import java.util.WeakHashMap;

/** Shared, entity-scoped movement priority and prediction for air support effects. */
public final class AirMobility {
    public static final int NONE = 0;
    public static final int SLOW_FALL = 1;
    public static final int HOVER = 2;
    private static final Map<LivingEntity, State> STATES = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LivingEntity, Long> PROPULSION = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private AirMobility() {
    }

    /** Active propulsion temporarily suspends passive support, without changing momentum. */
    public static void prioritizePropulsion(LivingEntity entity, int ticks) {
        PROPULSION.put(entity, entity.level().getGameTime() + Math.clamp(ticks, 1, 200));
        setSupport(entity, NONE, 0.0);
    }

    public static boolean hasMovementPriority(LivingEntity entity) {
        return entity.isShiftKeyDown() || entity.isFallFlying() || entity.isInWater()
                || entity.isPassenger()
                || entity instanceof Player player && player.getAbilities().flying
                || PROPULSION.getOrDefault(entity, Long.MIN_VALUE) >= entity.level().getGameTime();
    }

    public static void setSupport(LivingEntity entity, int mode, double targetY) {
        var next = new State(mode, mode == HOVER ? targetY : 0.0);
        var previous = STATES.getOrDefault(entity, new State(NONE, 0.0));
        if (next.equals(previous)) return;
        if (mode == NONE) STATES.remove(entity);
        else STATES.put(entity, next);
        if (entity instanceof ServerPlayer player) new AirMobilitySyncPacket(mode, next.targetY()).sendTo(player);
    }

    public static int mode(LivingEntity entity) {
        return STATES.getOrDefault(entity, new State(NONE, 0.0)).mode();
    }

    public static Vec3 supportedVelocity(Vec3 velocity, int mode, double heightError) {
        if (velocity == null || !Double.isFinite(velocity.x) || !Double.isFinite(velocity.y)
                || !Double.isFinite(velocity.z)) return Vec3.ZERO;
        var y = switch (mode) {
            case SLOW_FALL -> Math.max(-0.12, velocity.y);
            case HOVER -> Double.isFinite(heightError) ? Math.clamp(heightError * 0.35, -0.25, 0.25) : velocity.y;
            default -> velocity.y;
        };
        return new Vec3(velocity.x, y, velocity.z);
    }

    public static void applySupport(LivingEntity entity) {
        var state = STATES.get(entity);
        if (state == null || hasMovementPriority(entity) || entity.onGround()) return;
        entity.setDeltaMovement(supportedVelocity(entity.getDeltaMovement(), state.mode(), state.targetY() - entity.getY()));
        entity.resetFallDistance();
        // Player movement is predicted locally; do not overwrite their horizontal input every tick.
        if (!(entity instanceof Player)) entity.hurtMarked = true;
    }

    private record State(int mode, double targetY) {
    }
}
