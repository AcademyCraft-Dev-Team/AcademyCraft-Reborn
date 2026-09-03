package org.academy.internal.common.ability.accelerator.program;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Drives vector-program entity displacement through vanilla velocity and collision handling.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AcceleratorEntityMovementRuntime {
    private static final int MAX_CONTROL_TICKS = 100;
    private static final int MAX_BLOCKED_TICKS = 4;
    private static final double ARRIVAL_DISTANCE = 0.15;
    private static final double MIN_PROGRESS_SQUARED = 1.0e-5;
    private static final Map<UUID, Movement> ACTIVE = new HashMap<>();

    private AcceleratorEntityMovementRuntime() {
    }

    static Movement start(
            ServerPlayer controller,
            Entity target,
            Vec3 destination,
            double maximumSpeed
    ) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(destination, "destination");
        if (!(target.level() instanceof ServerLevel level)
                || controller.level() != level
                || !target.isAlive()
                || target.isRemoved()
                || !finite(destination)
                || !Double.isFinite(maximumSpeed)
                || maximumSpeed <= 0.0) {
            throw new IllegalStateException("Controlled entity movement became invalid");
        }
        var previous = ACTIVE.remove(target.getUUID());
        if (previous != null) previous.finish(false);
        var movement = new Movement(
                controller,
                level,
                target,
                destination,
                maximumSpeed,
                target.getDeltaMovement()
        );
        ACTIVE.put(target.getUUID(), movement);
        return movement;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        for (var movement : List.copyOf(ACTIVE.values())) movement.tick();
    }

    static final class Movement {
        private final ServerPlayer controller;
        private final ServerLevel level;
        private final Entity target;
        private final Vec3 destination;
        private final double maximumSpeed;
        private final Vec3 previousVelocity;
        private Vec3 lastPosition;
        private int controlledTicks;
        private int blockedTicks;
        private boolean active = true;

        private Movement(
                ServerPlayer controller,
                ServerLevel level,
                Entity target,
                Vec3 destination,
                double maximumSpeed,
                Vec3 previousVelocity
        ) {
            this.controller = controller;
            this.level = level;
            this.target = target;
            this.destination = destination;
            this.maximumSpeed = maximumSpeed;
            this.previousVelocity = previousVelocity;
            lastPosition = target.position();
        }

        private void tick() {
            if (!active) return;
            if (controller.hasDisconnected()
                    || !controller.isAlive()
                    || controller.level() != level
                    || target.level() != level
                    || !target.isAlive()
                    || target.isRemoved()
                    || target.isPassenger()
                    || target.isVehicle()
                    || !EntityMotionGuard.canApplyMotionFrom(controller, target)
                    || ++controlledTicks > MAX_CONTROL_TICKS) {
                finish(true);
                return;
            }

            var position = target.position();
            if (controlledTicks > 1
                    && position.distanceToSqr(lastPosition) < MIN_PROGRESS_SQUARED) {
                blockedTicks++;
            } else {
                blockedTicks = 0;
            }
            lastPosition = position;

            var displacement = destination.subtract(position);
            var distance = displacement.length();
            if (distance <= ARRIVAL_DISTANCE || blockedTicks >= MAX_BLOCKED_TICKS) {
                finish(true);
                return;
            }
            var speed = Math.min(maximumSpeed, distance);
            setVelocity(controller, target, displacement.scale(speed / distance));
        }

        void rollback() {
            if (!active) return;
            finish(false);
            if (target.level() == level && target.isAlive() && !target.isRemoved()) {
                EntityMotionGuard.runInternalCorrection(
                        target, () -> setVelocity(null, target, previousVelocity));
            }
        }

        private void finish(boolean stop) {
            if (!active) return;
            active = false;
            ACTIVE.remove(target.getUUID(), this);
            if (stop && target.level() == level && target.isAlive() && !target.isRemoved()) {
                setVelocity(controller, target, Vec3.ZERO);
            }
        }
    }

    private static void setVelocity(
            ServerPlayer controller,
            Entity target,
            Vec3 velocity
    ) {
        if (controller == null) target.setDeltaMovement(velocity);
        else EntityMotionGuard.runWithMotionSource(
                controller, () -> target.setDeltaMovement(velocity));
        target.hurtMarked = true;
        target.resetFallDistance();
        if (target instanceof ServerPlayer targetPlayer) {
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
        }
    }

    private static boolean finite(Vec3 value) {
        return value != null
                && Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
