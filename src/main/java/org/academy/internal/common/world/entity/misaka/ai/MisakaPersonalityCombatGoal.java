package org.academy.internal.common.world.entity.misaka.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import java.util.List;

public final class MisakaPersonalityCombatGoal extends Goal {
    private static final double THREAT_RANGE = 10.0;
    private static final double MELEE_RANGE = 2.0;
    private static final double ZAP_RANGE = 2.5;
    private static final int ZAP_COOLDOWN_TICKS = 40;
    private final MisakaSisterEntity sister;
    private LivingEntity threat;
    private int zapCooldown;

    public MisakaPersonalityCombatGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!sister.isAwakened()
                || sister.isStarving()
                || sister.getWanderStyle() == WanderStyle.WAITING) {
            return false;
        }
        if (zapCooldown > 0) {
            zapCooldown--;
        }
        threat = findThreat();
        return threat != null;
    }

    @Override
    public boolean canContinueToUse() {
        return threat != null
                && threat.isAlive()
                && sister.getWanderStyle() != WanderStyle.WAITING
                && sister.distanceToSqr(threat) <= THREAT_RANGE * THREAT_RANGE;
    }

    @Override
    public void tick() {
        if (threat == null) {
            return;
        }
        if (zapCooldown > 0) {
            zapCooldown--;
        }
        var personality = sister.getPersonality();
        if (personality == MisakaPersonality.BRAVE && threat.getHealth() < sister.getHealth()) {
            chaseAndMelee(threat);
            return;
        }
        fleeFrom(threat, personality == MisakaPersonality.TIMID ? 1.35 : 1.0);
        if ((personality == MisakaPersonality.LIVELY || personality == MisakaPersonality.COLD)
                && zapCooldown <= 0
                && sister.distanceToSqr(threat) <= ZAP_RANGE * ZAP_RANGE) {
            zap(threat);
            zapCooldown = ZAP_COOLDOWN_TICKS;
        }
    }

    @Override
    public void stop() {
        threat = null;
        sister.getNavigation().stop();
    }

    private void chaseAndMelee(LivingEntity target) {
        sister.getLookControl().setLookAt(target, 30.0f, 30.0f);
        sister.getNavigation().moveTo(target, 1.1);
        if (sister.distanceToSqr(target) <= MELEE_RANGE * MELEE_RANGE
                && sister.level() instanceof ServerLevel serverLevel) {
            sister.doHurtTarget(serverLevel, target);
        }
    }

    private void zap(LivingEntity victim) {
        if (sister.level() instanceof ServerLevel serverLevel) {
            var start = sister.position().add(0.0, sister.getEyeHeight() * 0.8, 0.0);
            var end = victim.position().add(0.0, victim.getEyeHeight() * 0.5, 0.0);
            ElectromasterArcEffects.spawnChainArc(serverLevel, start, end);
        }
        victim.hurt(sister.level().damageSources().lightningBolt(), 3.0f);
    }

    private LivingEntity findThreat() {
        List<LivingEntity> nearby = sister.level().getEntitiesOfClass(
                LivingEntity.class,
                sister.getBoundingBox().inflate(THREAT_RANGE),
                entity -> entity != sister && entity.isAlive() && isHostile(entity)
        );
        if (!nearby.isEmpty()) {
            return nearby.getFirst();
        }
        if (sister.getPersonality() == MisakaPersonality.TIMID
                && sister.getWanderStyle() != WanderStyle.FOLLOW) {
            return findIndifferentPlayer();
        }
        return null;
    }

    private LivingEntity findIndifferentPlayer() {
        return sister.level().getEntitiesOfClass(
                Player.class,
                sister.getBoundingBox().inflate(THREAT_RANGE),
                player -> player.isAlive() && isIndifferentPlayer(player)
        ).stream().findFirst().orElse(null);
    }

    private boolean isIndifferentPlayer(Player player) {
        return sister.rosterRecord()
                .map(record -> FavorService.relation(record, player.getGameProfile().name()) == MobRelation.INDIFFERENT)
                .orElse(false);
    }

    private boolean isHostile(LivingEntity entity) {
        if (entity instanceof Player player) {
            return sister.rosterRecord()
                    .map(record -> {
                        var relation = FavorService.relation(record, player.getGameProfile().name());
                        return relation == MobRelation.HOSTILE || relation == MobRelation.DEADLY_ENEMY;
                    })
                    .orElse(false);
        }
        return entity instanceof Monster || (entity instanceof Mob mob && mob.getTarget() == sister);
    }

    private void fleeFrom(LivingEntity source, double speedScale) {
        var away = sister.position().subtract(source.position()).normalize().scale(8.0).add(sister.position());
        sister.getNavigation().moveTo(away.x, away.y, away.z, 1.0 * speedScale);
        sister.getLookControl().setLookAt(source, 30.0f, 30.0f);
    }
}
