package org.academy.internal.common.ability.meltdowner;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;

/** Centralizes Meltdowner target eligibility without bypassing the friendly-fire setting. */
public final class MeltdownerTargeting {
    private MeltdownerTargeting() {
    }

    public static boolean canAffectNegatively(ServerPlayer attacker, Entity target) {
        if (attacker == null || target == null || target == attacker) return false;
        return allowsTarget(
                attacker.isAlliedTo(target),
                target instanceof Player,
                FriendlyFireSetting.isFriendlyFireEnabled(attacker)
        );
    }

    static boolean allowsTarget(boolean allied, boolean playerTarget, boolean friendlyFireEnabled) {
        return !allied || playerTarget && friendlyFireEnabled;
    }
}
