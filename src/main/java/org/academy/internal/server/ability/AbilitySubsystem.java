package org.academy.internal.server.ability;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CopyOnWriteArrayList;

public interface AbilitySubsystem {
    default void tick(ServerPlayer player) {
    }

    default void onPlayerLogin(@NotNull ServerPlayer player) {
    }

    default void onPlayerLogout(@NotNull ServerPlayer player) {
    }

    void processSync(@NotNull ServerPlayer player);
}