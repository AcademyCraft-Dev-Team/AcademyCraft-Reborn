package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.WanderStyle;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;

import java.util.EnumSet;

public final class MisakaFollowGoal extends Goal {
    private static final double FOLLOW_RANGE = 12.0;
    private static final double STOP_RANGE = 2.0;
    private static final double TIMID_STOP_RANGE = 1.25;
    private final MisakaSisterEntity sister;
    private boolean enabled = true;
    private Player followTarget;

    public MisakaFollowGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean canUse() {
        if (!enabled || !sister.isAwakened() || sister.getWanderStyle() != WanderStyle.FOLLOW) {
            return false;
        }
        followTarget = findFollowTarget();
        return followTarget != null;
    }

    @Override
    public boolean canContinueToUse() {
        return enabled && followTarget != null && followTarget.isAlive()
                && sister.distanceToSqr(followTarget) <= FOLLOW_RANGE * FOLLOW_RANGE;
    }

    @Override
    public void tick() {
        if (followTarget == null) {
            return;
        }
        sister.getLookControl().setLookAt(followTarget, 10.0f, 40.0f);
        boolean timid = sister.getPersonality() == org.academy.internal.common.world.entity.misaka.MisakaPersonality.TIMID;
        double stop = timid ? TIMID_STOP_RANGE : STOP_RANGE;
        if (sister.distanceToSqr(followTarget) > stop * stop) {
            float speed = timid ? 1.15f : 0.9f;
            sister.getNavigation().moveTo(followTarget, speed);
        } else {
            sister.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        followTarget = null;
        sister.getNavigation().stop();
    }

    /** Follow mode tracks the single privilege player only. */
    private Player findFollowTarget() {
        var record = sister.rosterRecord().orElse(null);
        if (record == null) {
            return null;
        }
        String privilege = record.lastInteractedBenevolentPlayerName;
        if (privilege == null || privilege.isEmpty() || !FavorService.isPrivilegePlayer(record, privilege)) {
            return null;
        }
        return sister.level().getEntitiesOfClass(
                ServerPlayer.class,
                sister.getBoundingBox().inflate(FOLLOW_RANGE),
                player -> player.getGameProfile().name().equals(privilege)
        ).stream().findFirst().orElse(null);
    }
}
