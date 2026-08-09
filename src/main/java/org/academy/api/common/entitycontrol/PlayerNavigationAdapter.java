package org.academy.api.common.entitycontrol;

import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/** Public extension point for player, flight-skill, and vehicle navigation implementations. */
public interface PlayerNavigationAdapter {
    boolean matches(ServerPlayer subject);

    Set<PlayerMovementMode> modes(ServerPlayer subject);

    ControlBinding activate(
            ControlContext context,
            ServerPlayer subject,
            ControlDirective.MoveTo directive
    );
}
