package org.academy.api.common.entitycontrol;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.academy.internal.common.ability.mentalout.control.PlayerNavigationRuntime;

import java.util.Optional;

public final class PlayerNavigationApi {
    private PlayerNavigationApi() {
    }

    public static void registerAdapter(Identifier id, int priority, PlayerNavigationAdapter adapter) {
        PlayerNavigationRuntime.registerAdapter(id, priority, adapter);
    }

    public static Optional<PlayerNavigationAdapter> findAdapter(
            ServerPlayer subject,
            PlayerMovementMode mode
    ) {
        return PlayerNavigationRuntime.findAdapter(subject, mode);
    }
}
