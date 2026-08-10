package org.academy.api.common.ability;

import net.minecraft.server.level.ServerPlayer;
import org.academy.api.common.wireless.WirelessUser;

public interface DevelopAction {
    String LEVEL_TARGET_ID = "academy:level";

    int getTotalTicks();

    default int getEnergyCost() {
        return 0;
    }

    default boolean validate(ServerPlayer player, WirelessUser developer) {
        return true;
    }

    void onComplete(ServerPlayer player, WirelessUser developer);

    default String getDescription() {
        return "Developing...";
    }

    /** Identifies the skill or level operation so clients can reconnect UI state after navigation. */
    default String getTargetId() {
        return "";
    }
}
