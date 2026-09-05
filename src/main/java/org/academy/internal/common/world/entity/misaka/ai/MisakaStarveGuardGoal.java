package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;

import java.util.EnumSet;

/** Keeps HP at least 1 while food is empty; damage cadence stays in entity tick. */
public final class MisakaStarveGuardGoal extends Goal {
    private final MisakaSisterEntity sister;

    public MisakaStarveGuardGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        return sister.isAwakened() && sister.getFoodLevel() == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (sister.getHealth() < 1.0f) {
            sister.setHealth(1.0f);
        }
    }
}
