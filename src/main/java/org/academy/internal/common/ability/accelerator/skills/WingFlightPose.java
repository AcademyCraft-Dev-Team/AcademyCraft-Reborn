package org.academy.internal.common.ability.accelerator.skills;

import net.minecraft.server.level.ServerPlayer;
import org.academy.internal.common.attachment.AttachmentTypes;

/**
 * Synchronizes the wing boost animation without mutating vanilla fall-flying entity state.
 */
public final class WingFlightPose {
    public static final long BOOST_GRACE_TICKS = 5L;

    private WingFlightPose() {
    }

    public static boolean isBoosting(long now, Long lastBoostTick) {
        return lastBoostTick != null
                && now >= lastBoostTick
                && now - lastBoostTick <= BOOST_GRACE_TICKS;
    }

    public static void sync(ServerPlayer player, boolean boosting) {
        var type = AttachmentTypes.WING_BOOST_POSE.get();
        if (player.getData(type) == boosting) return;
        player.setData(type, boosting);
        player.syncData(type);
    }
}
