package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.WanderStyle;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

import java.util.EnumSet;
import java.util.List;

public final class MisakaSocialGoal extends Goal {
    private static final double SOCIAL_RANGE = 6.0;
    private final MisakaSisterEntity sister;
    private MisakaSisterEntity partner;
    private int socialTicks;

    public MisakaSocialGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!sister.isAwakened() || sister.getWanderStyle() == WanderStyle.WAITING) {
            return false;
        }
        partner = findNearbySister();
        return partner != null;
    }

    @Override
    public boolean canContinueToUse() {
        return partner != null
                && partner.isAlive()
                && sister.getWanderStyle() != WanderStyle.WAITING
                && sister.distanceToSqr(partner) <= SOCIAL_RANGE * SOCIAL_RANGE;
    }

    @Override
    public void start() {
        socialTicks = 60;
    }

    @Override
    public void tick() {
        if (partner == null) {
            return;
        }
        sister.getLookControl().setLookAt(partner, 30.0f, 30.0f);
        partner.getLookControl().setLookAt(sister, 30.0f, 30.0f);
        if (--socialTicks <= 0) {
            grantSocialPerception(sister);
            grantSocialPerception(partner);
            partner = null;
        }
    }

    private static void grantSocialPerception(MisakaSisterEntity participant) {
        participant.rosterRecord().ifPresent(record -> {
            if (record.dailySocial) {
                return;
            }
            record.dailySocial = true;
            var server = participant.level().getServer();
            if (server != null) {
                PerceptionService.gain(server, record, 2);
                MisakaSisterRoster.get(server).setDirty();
            }
        });
    }

    @Override
    public void stop() {
        partner = null;
        socialTicks = 0;
    }

    private MisakaSisterEntity findNearbySister() {
        List<MisakaSisterEntity> nearby = sister.level().getEntitiesOfClass(
                MisakaSisterEntity.class,
                sister.getBoundingBox().inflate(SOCIAL_RANGE),
                other -> other != sister && other.isAwakened()
        );
        return nearby.isEmpty() ? null : nearby.get(sister.getRandom().nextInt(nearby.size()));
    }
}
