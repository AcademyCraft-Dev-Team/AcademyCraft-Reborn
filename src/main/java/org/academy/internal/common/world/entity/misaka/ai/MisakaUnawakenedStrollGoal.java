package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;

import java.util.EnumSet;

public final class MisakaUnawakenedStrollGoal extends Goal {
    private static final int INTERVAL = 120;
    private final MisakaSisterEntity sister;
    private double targetX;
    private double targetY;
    private double targetZ;
    private boolean hasTarget;

    public MisakaUnawakenedStrollGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (sister.isAwakened() || sister.getNavigation().isInProgress()) {
            return false;
        }
        if (sister.getRandom().nextInt(INTERVAL) != 0) {
            return false;
        }
        var pos = sister.blockPosition();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = pos.getX() + sister.getRandom().nextInt(13) - 6;
            int z = pos.getZ() + sister.getRandom().nextInt(13) - 6;
            int y = sister.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var candidate = new Vec3(x + 0.5, y, z + 0.5);
            if (sister.getNavigation().createPath(candidate.x, candidate.y, candidate.z, 0) != null) {
                targetX = candidate.x;
                targetY = candidate.y;
                targetZ = candidate.z;
                hasTarget = true;
                return true;
            }
        }
        hasTarget = false;
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return !sister.isAwakened() && hasTarget && !sister.getNavigation().isDone();
    }

    @Override
    public void start() {
        sister.getNavigation().moveTo(targetX, targetY, targetZ, 0.35);
    }

    @Override
    public void stop() {
        hasTarget = false;
    }
}
