package org.academy.api.common.ability;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.wireless.WirelessUser;

public interface DevelopAction {
    int getTotalTicks();

    void onComplete(ServerPlayer player, WirelessUser developer);

    default String getDescription() {
        return "Developing...";
    }
}
