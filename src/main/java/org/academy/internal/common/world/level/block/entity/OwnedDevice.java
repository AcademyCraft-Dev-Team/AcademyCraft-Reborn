package org.academy.internal.common.world.level.block.entity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Physical Misaka/network devices record a placing player's UUID for break/ops protection.
 */
public interface OwnedDevice {
    String OWNER_NBT_KEY = "academy_owner_uuid";

    @Nullable
    UUID getOwnerUuid();

    void setOwnerUuid(@Nullable UUID ownerUuid);

    default boolean isOwner(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && net.minecraft.commands.Commands.hasPermission(
                        net.minecraft.commands.Commands.LEVEL_GAMEMASTERS)
                .test(serverPlayer.createCommandSourceStack())) {
            return true;
        }
        UUID owner = getOwnerUuid();
        return owner != null && owner.equals(player.getUUID());
    }

    default void saveOwner(ValueOutput output) {
        UUID owner = getOwnerUuid();
        if (owner != null) {
            output.putString(OWNER_NBT_KEY, owner.toString());
        }
    }

    default void loadOwner(ValueInput input) {
        setOwnerUuid(input.getString(OWNER_NBT_KEY)
                .map(id -> {
                    try {
                        return UUID.fromString(id);
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                })
                .orElse(null));
    }

    static void assignPlacer(Object blockEntity, @Nullable Player placer) {
        if (blockEntity instanceof OwnedDevice owned && placer != null) {
            owned.setOwnerUuid(placer.getUUID());
        }
    }
}
