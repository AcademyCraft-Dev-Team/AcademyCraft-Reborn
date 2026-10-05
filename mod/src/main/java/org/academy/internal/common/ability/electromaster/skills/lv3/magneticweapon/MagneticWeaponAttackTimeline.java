package org.academy.internal.common.ability.electromaster.skills.lv3.magneticweapon;

import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion;

final class MagneticWeaponAttackTimeline {
    private final int strikeTicks;
    private final int returnTicks;
    private int elapsed;
    private boolean cancelled;
    private boolean impactClaimed;

    MagneticWeaponAttackTimeline(MagneticWeaponBladeMotion.Timing timing) {
        strikeTicks = timing.strikeTicks();
        returnTicks = timing.returnTicks();
    }

    int advance() {
        return ++elapsed;
    }

    int elapsed() {
        return elapsed;
    }

    int impactElapsed() {
        return strikeTicks;
    }

    boolean isFinished() {
        return elapsed > strikeTicks + returnTicks + (int) Math.ceil(MagneticWeaponBladeMotion.RETURN_GRACE_TICKS);
    }

    void cancel() {
        cancelled = true;
    }

    boolean claimImpact() {
        if (cancelled || impactClaimed || elapsed != strikeTicks) return false;
        impactClaimed = true;
        return true;
    }
}
