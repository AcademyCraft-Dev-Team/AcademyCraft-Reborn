package org.academy.internal.common.ability.electromaster.skills.lv3;

import org.academy.internal.common.world.entity.skill.MagneticWeaponBladeMotion;

final class MagneticWeaponAttackTimeline {
    private int attackTick = 1;
    private boolean cancelled;
    private boolean impactClaimed;

    int advance() {
        return ++attackTick;
    }

    int attackTick() {
        return attackTick;
    }

    boolean isFinished() {
        return attackTick > MagneticWeaponBladeMotion.ATTACK_END_TICK;
    }

    void cancel() {
        cancelled = true;
    }

    boolean claimImpact() {
        if (cancelled || impactClaimed || attackTick != MagneticWeaponBladeMotion.IMPACT_TICK) return false;
        impactClaimed = true;
        return true;
    }
}
