package org.academy.internal.common.ability.mentalout.control;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.entitycontrol.*;
import org.academy.internal.common.ability.mentalout.PlayerControlSessionManager;
import org.academy.internal.common.entitycontrol.EntityMotionGuard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns an effective impression-alliance lease into an explicitly requested riding controller.
 * The relation remains the authorization source while a separate direct-control lease owns the
 * mount's movement and prevents its autonomous AI from overwriting rider input.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class ImpressionRidingManager {
    private static final Identifier SOURCE = AcademyCraft.academy("impression_riding");
    private static final int CONTROL_PRIORITY = 275;
    private static final int RELEASE_SHIFT_TIMEOUT_TICKS = 40;
    private static final double MAX_PENDING_DISTANCE_SQUARED = 36.0;
    private static final Map<UUID, RideSession> BY_RIDER = new HashMap<>();
    private static final Map<UUID, RideSession> BY_MOUNT = new HashMap<>();
    private static final ThreadLocal<MountAttempt> MOUNT_ATTEMPT = new ThreadLocal<>();

    private ImpressionRidingManager() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !player.isShiftKeyDown()
                || !(event.getTarget() instanceof LivingEntity target)
                || !hasControllerImpression(player, target)) {
            return;
        }
        var result = requestMount(player, target);
        feedback(player, result.feedbackKey);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * Requests a mount using the same checks as Shift+right-click. Mounting is deferred until
     * Shift is released so vanilla's dismount input cannot immediately undo the new ride.
     */
    public static MountResult requestMount(ServerPlayer rider, LivingEntity target) {
        if (rider == null || target == null || rider == target
                || !rider.isAlive() || rider.hasDisconnected()
                || !target.isAlive() || target.isRemoved()
                || rider.level() != target.level()
                || !hasControllerImpression(rider, target)) {
            return MountResult.NO_IMPRESSION;
        }

        var current = BY_RIDER.get(rider.getUUID());
        if (current != null && current.mount == target) {
            stop(current, false);
            return MountResult.DISMOUNTED;
        }
        if ((!(target instanceof Mob) && !(target instanceof ServerPlayer))
                || !MentalControlApi.evaluate(target, ControlCapability.DIRECT_CONTROL).supported()) {
            return MountResult.UNSUPPORTED;
        }
        if (current != null || rider.isPassenger() || target.isPassenger() || target.isVehicle()
                || BY_MOUNT.containsKey(target.getUUID())) {
            return MountResult.BUSY;
        }

        var session = new RideSession(
                UUID.randomUUID(),
                rider,
                target,
                rider.level().getGameTime() + RELEASE_SHIFT_TIMEOUT_TICKS
        );
        BY_RIDER.put(rider.getUUID(), session);
        BY_MOUNT.put(target.getUUID(), session);
        return MountResult.PENDING;
    }

    public static void tick(MinecraftServer server) {
        for (var session : List.copyOf(BY_RIDER.values())) {
            if (!isRetained(session)) {
                stop(session, session.active);
                continue;
            }
            if (session.active) {
                if (session.mount instanceof ServerPlayer player) {
                    applyControlledPlayerMovement(session, player);
                }
                continue;
            }
            var now = session.rider.level().getGameTime();
            if (now >= session.releaseShiftDeadline) {
                stop(session, false);
                feedback(session.rider, MountResult.SHIFT_TIMEOUT.feedbackKey);
                continue;
            }
            var input = Optional.ofNullable(session.rider.getLastClientInput()).orElse(Input.EMPTY);
            if (input.shift()) continue;
            activate(session);
        }
    }

    public static Optional<PlayerControlSessionManager.MobDirectInput> directInput(Mob mount) {
        if (mount == null) return Optional.empty();
        var session = BY_MOUNT.get(mount.getUUID());
        if (session == null || !session.active || session.rider.getVehicle() != mount
                || !isRetained(session)) {
            return Optional.empty();
        }
        return Optional.of(new PlayerControlSessionManager.MobDirectInput(
                mount.level().getGameTime(),
                controlFrame(session)
        ));
    }

    /**
     * Rejects movement authored by a ridden player's own client. The riding controller is the
     * only motion source while the direct-control lease is effective.
     */
    public static boolean validateControlledPlayerMovement(ServerPlayer player) {
        if (player == null) return false;
        var session = BY_MOUNT.get(player.getUUID());
        if (session == null || !session.active || session.mount != player || !isRetained(session)) {
            return false;
        }
        var frame = controlFrame(session);
        var position = session.lastGoodPosition;
        EntityMotionGuard.runInternalCorrection(player, () -> player.connection.teleport(
                position.x, position.y, position.z, frame.yaw(), frame.pitch()
        ));
        player.hurtMarked = true;
        return true;
    }

    /** Returns whether a ridden player's own interaction packets must be ignored. */
    public static boolean blocksUntrustedWorldAction(ServerPlayer player) {
        if (player == null) return false;
        var session = BY_MOUNT.get(player.getUUID());
        return session != null && session.active && session.mount == player && isRetained(session);
    }

    /**
     * Allows Entity.startRiding to treat a player as a vehicle only for this manager's exact,
     * server-thread mount attempt. Vanilla rejects player vehicles because they are not serialized.
     */
    public static boolean permitsNonSerializableVehicle(Entity rider, Entity vehicle) {
        var attempt = MOUNT_ATTEMPT.get();
        return attempt != null && attempt.rider == rider && attempt.mount == vehicle;
    }

    public static void releaseEntity(UUID entityId) {
        if (entityId == null) return;
        var session = BY_RIDER.get(entityId);
        if (session == null) session = BY_MOUNT.get(entityId);
        if (session != null) stop(session, false);
    }

    public static void clear() {
        List.copyOf(BY_RIDER.values()).forEach(session -> stop(session, false));
        BY_RIDER.clear();
        BY_MOUNT.clear();
        MOUNT_ATTEMPT.remove();
    }

    private static void activate(RideSession session) {
        try {
            session.handle = MentalControlApi.apply(ControlRequest.scopedPermanent(
                    session.rider,
                    session.mount,
                    SOURCE,
                    session.id,
                    CONTROL_PRIORITY,
                    List.of(new ControlDirective.DirectControl())
            ));
            if (!session.handle.isEffective() || !startRiding(session)) {
                stop(session, false);
                feedback(session.rider, MountResult.BUSY.feedbackKey);
                return;
            }
            session.active = true;
            session.lastGoodPosition = session.mount.position();
            feedback(session.rider, MountResult.MOUNTED.feedbackKey);
        } catch (RuntimeException exception) {
            AcademyCraft.LOGGER.debug(
                    "Impression riding could not control {} for {}",
                    session.mount.getUUID(),
                    session.rider.getGameProfile().name(),
                    exception
            );
            stop(session, false);
            feedback(session.rider, MountResult.UNSUPPORTED.feedbackKey);
        }
    }

    private static boolean startRiding(RideSession session) {
        MOUNT_ATTEMPT.set(new MountAttempt(session.rider, session.mount));
        try {
            return session.rider.startRiding(session.mount, true, true);
        } finally {
            MOUNT_ATTEMPT.remove();
        }
    }

    private static boolean isRetained(RideSession session) {
        var rider = session.rider;
        var mount = session.mount;
        var controlLost = session.handle != null && (session.handle.isClosed()
                || (session.active && !session.handle.isEffective()));
        if (!rider.isAlive() || rider.hasDisconnected()
                || !mount.isAlive() || mount.isRemoved()
                || rider.level() != mount.level()
                || !hasControllerImpression(rider, mount)
                || controlLost) {
            return false;
        }
        if (!session.active) {
            return rider.distanceToSqr(mount) <= MAX_PENDING_DISTANCE_SQUARED
                    && !rider.isPassenger() && !mount.isVehicle();
        }
        return rider.getVehicle() == mount;
    }

    private static PlayerControlFrame controlFrame(RideSession session) {
        var rider = session.rider;
        var mount = session.mount;
        var input = Optional.ofNullable(rider.getLastClientInput()).orElse(Input.EMPTY);
        var forward = (input.forward() ? 1.0f : 0.0f) - (input.backward() ? 1.0f : 0.0f);
        var strafe = (input.left() ? 1.0f : 0.0f) - (input.right() ? 1.0f : 0.0f);
        var mode = movementMode(mount, input);
        return new PlayerControlFrame(
                forward,
                strafe,
                Mth.wrapDegrees(rider.getYRot()),
                Mth.clamp(rider.getXRot(), -90.0f, 90.0f),
                input.jump(),
                false,
                input.sprint(),
                false,
                false,
                mode
        );
    }

    private static PlayerMovementMode movementMode(LivingEntity mount, Input input) {
        if (mount instanceof Mob mob && MobDirectControlBinding.isFreeFlying(mob)) {
            return PlayerMovementMode.FLY;
        }
        if (mount instanceof ServerPlayer player && player.getAbilities().flying) {
            return PlayerMovementMode.FLY;
        }
        if (mount.isFallFlying()) return PlayerMovementMode.GLIDE;
        if (mount.isInWater()) return PlayerMovementMode.SWIM;
        if (mount.onClimbable()) return PlayerMovementMode.CLIMB;
        return input.jump() ? PlayerMovementMode.JUMP : PlayerMovementMode.WALK;
    }

    private static void applyControlledPlayerMovement(RideSession session, ServerPlayer player) {
        var frame = controlFrame(session);
        var oldPosition = player.position();
        var oldYaw = player.getYRot();
        var oldPitch = player.getXRot();
        try {
            player.setYRot(frame.yaw());
            player.setXRot(frame.pitch());
            player.setYHeadRot(frame.yaw());
            player.setYBodyRot(frame.yaw());
            player.setShiftKeyDown(false);
            player.setSprinting(frame.sprint()
                    && (Math.abs(frame.forward()) > 0.01f || Math.abs(frame.strafe()) > 0.01f));

            var verticalInput = verticalInput(frame);
            EntityMotionGuard.runWithMotionSource(session.rider, () -> {
                if (frame.jump() && player.onGround()
                        && frame.mode() != PlayerMovementMode.SWIM
                        && frame.mode() != PlayerMovementMode.FLY) {
                    var velocity = player.getDeltaMovement();
                    player.setDeltaMovement(velocity.x, Math.max(0.42, velocity.y), velocity.z);
                }
                player.travel(new Vec3(frame.strafe(), verticalInput, frame.forward()));
            });
            session.lastGoodPosition = player.position();
            player.hurtMarked = true;

            if (oldPosition.distanceToSqr(player.position()) > 1.0e-8
                    || Math.abs(Mth.wrapDegrees(oldYaw - frame.yaw())) > 0.01f
                    || Math.abs(oldPitch - frame.pitch()) > 0.01f) {
                var position = player.position();
                EntityMotionGuard.runInternalCorrection(player, () -> player.connection.teleport(
                        position.x, position.y, position.z, frame.yaw(), frame.pitch()
                ));
            }
        } catch (RuntimeException exception) {
            AcademyCraft.LOGGER.error(
                    "Failed to apply impression-riding input to player {}",
                    player.getGameProfile().name(),
                    exception
            );
            stop(session, true);
        }
    }

    private static double verticalInput(PlayerControlFrame frame) {
        if (frame.mode() == PlayerMovementMode.FLY) {
            if (frame.jump()) return 1.0;
            return Mth.clamp(
                    Vec3.directionFromRotation(frame.pitch(), frame.yaw()).y * frame.forward(),
                    -1.0,
                    1.0
            );
        }
        return switch (frame.mode()) {
            case CLIMB, SWIM -> frame.jump() ? 1.0 : 0.0;
            default -> 0.0;
        };
    }

    private static boolean hasControllerImpression(ServerPlayer controller, LivingEntity target) {
        return MentalControlApi.inspect(target, ControlCapability.RELATION_CONTROL)
                .filter(inspection -> inspection.controllerId().equals(controller.getUUID()))
                .filter(inspection -> inspection.directive()
                        instanceof ControlDirective.ImpressionAlliance)
                .isPresent();
    }

    private static void stop(RideSession session, boolean notify) {
        if (session == null || session.closed) return;
        session.closed = true;
        BY_RIDER.remove(session.rider.getUUID(), session);
        BY_MOUNT.remove(session.mount.getUUID(), session);
        if (session.rider.getVehicle() == session.mount) session.rider.stopRiding();
        if (session.handle != null) session.handle.close();
        if (notify) feedback(session.rider, MountResult.ENDED.feedbackKey);
    }

    private static void feedback(ServerPlayer player, String key) {
        if (player != null && key != null && !key.isBlank()) {
            player.sendOverlayMessage(Component.translatable(key));
        }
    }

    public enum MountResult {
        PENDING("message.academy.mentalout.impression_riding.pending"),
        MOUNTED("message.academy.mentalout.impression_riding.mounted"),
        DISMOUNTED("message.academy.mentalout.impression_riding.ended"),
        NO_IMPRESSION("message.academy.mentalout.impression_riding.no_impression"),
        UNSUPPORTED("message.academy.mentalout.impression_riding.unsupported"),
        BUSY("message.academy.mentalout.impression_riding.busy"),
        SHIFT_TIMEOUT("message.academy.mentalout.impression_riding.shift_timeout"),
        ENDED("message.academy.mentalout.impression_riding.ended");

        private final String feedbackKey;

        MountResult(String feedbackKey) {
            this.feedbackKey = feedbackKey;
        }
    }

    private static final class RideSession {
        private final UUID id;
        private final ServerPlayer rider;
        private final LivingEntity mount;
        private final long releaseShiftDeadline;
        private ControlHandle handle;
        private Vec3 lastGoodPosition;
        private boolean active;
        private boolean closed;

        private RideSession(UUID id, ServerPlayer rider, LivingEntity mount, long releaseShiftDeadline) {
            this.id = id;
            this.rider = rider;
            this.mount = mount;
            this.releaseShiftDeadline = releaseShiftDeadline;
            lastGoodPosition = mount.position();
        }
    }

    private record MountAttempt(ServerPlayer rider, LivingEntity mount) {
    }
}
