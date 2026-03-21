package org.academy.internal.server.ability;

import net.minecraft.server.level.ServerPlayer;

public interface AbilitySubsystem {
    default void tick(ServerPlayer player) {
    }

    default void onPlayerLogin(ServerPlayer player) {
    }

    default void onPlayerLogout(ServerPlayer player) {
    }

    void processSync(ServerPlayer player);
}