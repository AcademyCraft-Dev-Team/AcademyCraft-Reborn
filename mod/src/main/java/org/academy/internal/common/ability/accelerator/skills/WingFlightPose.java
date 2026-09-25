package org.academy.internal.common.ability.accelerator.skills;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.attachment.AttachmentTypes;

/**
 * Synchronizes wing-flight animation state. Fast flight reuses the vanilla compact
 * fall-flying pose dimensions without enabling vanilla fall-flying movement state.
 */
public final class WingFlightPose {
    public static final long BOOST_GRACE_TICKS = 5L;
    public static final double COASTING_MIN_HORIZONTAL_SPEED_SQR = 1.0E-4;
    public static final double COASTING_MIN_VERTICAL_SPEED = 0.1;

    private WingFlightPose() {
    }

    public static boolean isBoosting(long now, Long lastBoostTick) {
        return lastBoostTick != null
                && now >= lastBoostTick
                && now - lastBoostTick <= BOOST_GRACE_TICKS;
    }

    public static boolean isCoasting(Vec3 movement) {
        if (movement == null) return false;
        var horizontalSpeedSqr = movement.x * movement.x + movement.z * movement.z;
        var verticalSpeed = Math.abs(movement.y);
        return Double.isFinite(horizontalSpeedSqr)
                && Double.isFinite(verticalSpeed)
                && (horizontalSpeedSqr >= COASTING_MIN_HORIZONTAL_SPEED_SQR
                || verticalSpeed >= COASTING_MIN_VERTICAL_SPEED);
    }

    public static Pose coastingPose(Avatar avatar) {
        return isCoasting(avatar.getDeltaMovement()) ? Pose.SLOW : Pose.IDLE;
    }

    public static boolean usesCompactCollision(Avatar avatar) {
        return hasActiveWing(avatar)
                && avatar.getData(AttachmentTypes.WING_FLIGHT_POSE.get()) == Pose.FAST;
    }

    public static boolean hasActiveFlightPose(Avatar avatar) {
        return hasActiveWing(avatar)
                && avatar.getData(AttachmentTypes.WING_FLIGHT_POSE.get()) != Pose.IDLE;
    }

    public static boolean hasActiveWing(Avatar avatar) {
        return avatar.getData(AttachmentTypes.ACTIVATED_STORM_WING.get())
                || avatar.getData(AttachmentTypes.ACTIVATED_BLACK_WING.get())
                || avatar.getData(AttachmentTypes.ACTIVATED_WHITE_WING.get())
                || avatar.getData(AttachmentTypes.ACTIVATED_PLATINUM_WING.get());
    }

    public static void sync(ServerPlayer player, Pose pose) {
        var type = AttachmentTypes.WING_FLIGHT_POSE.get();
        if (player.getData(type) == pose) return;
        player.setData(type, pose);
        player.syncData(type);
    }

    public enum Pose {
        IDLE,
        SLOW,
        FAST;

        public static final StreamCodec<ByteBuf, Pose> STREAM_CODEC =
                ByteBufCodecs.idMapper(index -> values()[index], Enum::ordinal);
    }
}
