package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.world.entity.misaka.MisakaDayTime;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.WanderStyle;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

import java.util.EnumSet;

public final class MisakaSleepInBedGoal extends Goal {
    private static final int SEARCH_RADIUS = 10;
    private final MisakaSisterEntity sister;
    private BlockPos bedPos;

    public MisakaSleepInBedGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!sister.isAwakened() || sister.isSleeping()) {
            return false;
        }
        if (sister.getWanderStyle() == WanderStyle.WAITING) {
            return false;
        }
        if (!MisakaDayTime.isNight(sister.level())) {
            return false;
        }
        bedPos = findBed();
        return bedPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return bedPos != null
                && sister.getWanderStyle() != WanderStyle.WAITING
                && !sister.isSleeping()
                && sister.distanceToSqr(Vec3.atCenterOf(bedPos)) > 1.5;
    }

    @Override
    public void start() {
        if (bedPos != null) {
            sister.getNavigation().moveTo(bedPos.getX() + 0.5, bedPos.getY(), bedPos.getZ() + 0.5, 0.7);
        }
    }

    @Override
    public void tick() {
        if (bedPos == null) {
            return;
        }
        if (sister.distanceToSqr(Vec3.atCenterOf(bedPos)) <= 2.25) {
            sister.startSleeping(bedPos);
            sister.rosterRecord().ifPresent(record -> {
                if (!record.dailySleep) {
                    record.dailySleep = true;
                    var server = sister.level().getServer();
                    if (server != null) {
                        PerceptionService.gain(server, record, 1);
                        MisakaSisterRoster.get(server).setDirty();
                    }
                }
            });
            bedPos = null;
        }
    }

    private BlockPos findBed() {
        var origin = sister.blockPosition();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    var pos = origin.offset(dx, dy, dz);
                    BlockState state = sister.level().getBlockState(pos);
                    if (state.is(BlockTags.BEDS) && !state.getValue(BedBlock.OCCUPIED)) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }
}
