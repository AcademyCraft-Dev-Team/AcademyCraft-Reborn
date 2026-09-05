package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.internal.common.world.entity.misaka.MisakaPersonality;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.MobRelation;
import org.academy.internal.common.world.entity.misaka.WanderStyle;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;

import java.util.EnumSet;

/**
 * Close-range zap when no higher-priority combat goal owns the sister
 * (e.g. threat just outside flee radius but still in zap range is handled in combat).
 * Kept for LIVELY/COLD when combat goal is inactive.
 */
public final class MisakaArcZapGoal extends Goal {
    private static final double ZAP_RANGE = 2.5;
    private static final int COOLDOWN_TICKS = 40;
    private final MisakaSisterEntity sister;
    private LivingEntity target;
    private int cooldown;

    public MisakaArcZapGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
        }
        if (!sister.isAwakened()
                || sister.isStarving()
                || sister.getWanderStyle() == WanderStyle.WAITING
                || cooldown > 0) {
            return false;
        }
        var personality = sister.getPersonality();
        if (personality != MisakaPersonality.LIVELY && personality != MisakaPersonality.COLD) {
            return false;
        }
        target = findZapTarget();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null
                && target.isAlive()
                && sister.getWanderStyle() != WanderStyle.WAITING
                && sister.distanceToSqr(target) <= ZAP_RANGE * ZAP_RANGE;
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        sister.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (sister.distanceToSqr(target) <= ZAP_RANGE * ZAP_RANGE) {
            zap(target);
            cooldown = COOLDOWN_TICKS;
            target = null;
        }
    }

    @Override
    public void stop() {
        target = null;
    }

    private LivingEntity findZapTarget() {
        return sister.level().getEntitiesOfClass(
                LivingEntity.class,
                sister.getBoundingBox().inflate(ZAP_RANGE),
                this::isZapTarget
        ).stream().findFirst().orElse(null);
    }

    private boolean isZapTarget(LivingEntity entity) {
        if (entity == sister || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof Monster) {
            return true;
        }
        if (entity instanceof Player player) {
            return sister.rosterRecord()
                    .map(record -> {
                        var relation = FavorService.relation(record, player.getGameProfile().name());
                        return relation == MobRelation.HOSTILE || relation == MobRelation.DEADLY_ENEMY;
                    })
                    .orElse(false);
        }
        return false;
    }

    private void zap(LivingEntity victim) {
        if (sister.level() instanceof ServerLevel serverLevel) {
            var start = sister.position().add(0.0, sister.getEyeHeight() * 0.8, 0.0);
            var end = victim.position().add(0.0, victim.getEyeHeight() * 0.5, 0.0);
            ElectromasterArcEffects.spawnChainArc(serverLevel, start, end);
        }
        victim.hurt(sister.level().damageSources().lightningBolt(), 3.0f);
    }
}
