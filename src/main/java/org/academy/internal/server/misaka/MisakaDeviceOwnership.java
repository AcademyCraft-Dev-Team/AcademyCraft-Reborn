package org.academy.internal.server.misaka;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.academy.internal.common.world.level.block.entity.OwnedDevice;

/**
 * Asset-ownership transfer for physical Misaka devices (design §15.2).
 *
 * <p>Transfer is deliberately one-directional: only the current owner (or an operator) may
 * hand a device away, so this entry point can never be used to seize someone else's device.
 */
public final class MisakaDeviceOwnership {
    /** Squared reach guard so a transfer needs the device to actually be in front of the player. */
    private static final double MAX_REACH_SQR = 64.0;

    public enum Result {
        TRANSFERRED,
        NOT_OWNER,
        NO_DEVICE,
        TARGET_OFFLINE,
        ALREADY_OWNER
    }

    private MisakaDeviceOwnership() {
    }

    public static Result transfer(
            ServerLevel level,
            BlockPos devicePos,
            ServerPlayer actor,
            String targetName
    ) {
        if (level == null || devicePos == null || actor == null) {
            return Result.NO_DEVICE;
        }
        if (!level.isLoaded(devicePos)
                || actor.distanceToSqr(devicePos.getX() + 0.5, devicePos.getY() + 0.5, devicePos.getZ() + 0.5)
                > MAX_REACH_SQR) {
            return Result.NO_DEVICE;
        }
        if (!(level.getBlockEntity(devicePos) instanceof OwnedDevice device)) {
            return Result.NO_DEVICE;
        }
        if (!device.isOwner(actor)) {
            return Result.NOT_OWNER;
        }
        var server = level.getServer();
        if (server == null || targetName == null || targetName.isBlank()) {
            return Result.TARGET_OFFLINE;
        }
        // Resolving an online player keeps the target exact; a bare name cannot yield an
        // offline UUID, which is the same constraint the network member panel works under.
        var target = MisakaPlayers.findOnlineByName(server, targetName);
        if (target == null) {
            return Result.TARGET_OFFLINE;
        }
        var current = device.getOwnerUuid();
        if (current != null && current.equals(target.getUUID())) {
            return Result.ALREADY_OWNER;
        }
        device.setOwnerUuid(target.getUUID());
        return Result.TRANSFERRED;
    }

    /** Translation key for the chat feedback matching {@code result}. */
    public static String feedbackKey(Result result) {
        return switch (result) {
            case TRANSFERRED -> "message.academy.device_transfer_ok";
            case NOT_OWNER -> "message.academy.device_transfer_not_owner";
            case NO_DEVICE -> "message.academy.device_transfer_no_device";
            case TARGET_OFFLINE -> "message.academy.device_transfer_target_offline";
            case ALREADY_OWNER -> "message.academy.device_transfer_already_owner";
        };
    }
}
