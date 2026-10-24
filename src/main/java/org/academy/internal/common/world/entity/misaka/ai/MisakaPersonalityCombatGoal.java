package org.academy.internal.common.world.entity.misaka.ai;

import org.academy.internal.common.ability.Skills;
import org.academy.api.common.ability.Skill;
import org.academy.api.server.ability.AbilityCastResult;
import org.academy.api.server.ability.AbilityCastRequest;
import org.academy.api.server.ability.EntityAbilityService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.world.entity.misaka.MisakaPersonality;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.MobRelation;
import org.academy.internal.common.world.entity.misaka.WanderStyle;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;

import java.util.EnumSet;
import java.util.List;

public final class MisakaPersonalityCombatGoal extends Goal {
    private static final double THREAT_RANGE = 32.0;
    private static final double MELEE_RANGE = 2.0;
    private final MisakaSisterEntity sister;
    private LivingEntity threat;

    public MisakaPersonalityCombatGoal(MisakaSisterEntity sister) {
        this.sister = sister;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!EntityAbilityService.canAct(sister)
                || sister.getWanderStyle() == WanderStyle.WAITING) {
            return false;
        }
        if (Math.floorMod(sister.tickCount + sister.getId(), 10) != 0) return false;
        threat = findThreat();
        return threat != null;
    }

    @Override
    public boolean canContinueToUse() {
        return threat != null
                && threat.isAlive()
                && EntityAbilityService.canAct(sister)
                && sister.getWanderStyle() != WanderStyle.WAITING
                && sister.distanceToSqr(threat) <= THREAT_RANGE * THREAT_RANGE;
    }

    @Override
    public void tick() {
        if (threat == null) {
            return;
        }
        var personality = sister.getPersonality();
        if (Math.floorMod(sister.tickCount + sister.getId(), 10) == 0) {
            var replacement = findThreat();
            if (replacement != null) threat = replacement;
            castDefensiveSkill(threat);
        }
        if (personality == MisakaPersonality.BRAVE && threat.getHealth() < sister.getHealth()) {
            chaseAndMelee(threat);
            return;
        }
        fleeFrom(threat, personality == MisakaPersonality.TIMID ? 1.35 : 1.0);
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    private void castDefensiveSkill(LivingEntity target) {
        if (!EntityAbilityService.mayTarget(sister, target)) return;
        var nearby = sister.level().getEntitiesOfClass(LivingEntity.class, sister.getBoundingBox().inflate(6),
                candidate -> sister.distanceToSqr(candidate) <= 36
                        && EntityAbilityService.mayTarget(sister, candidate));
        if (nearby.size() >= 3 && cast(Skills.LIGHTNING_NOVA.get(), null)) return;
        if (sister.distanceToSqr(target) > 64 && cast(Skills.THUNDER_LANCE.get(), target)) return;
        if (!cast(Skills.ARC_GENERATE.get(), target))
            cast(Skills.THUNDER_LANCE.get(), target);
    }

    private boolean cast(Skill skill, LivingEntity target) {
        return EntityAbilityService.tryCast(sister,
                AbilityCastRequest.autonomous(skill.getKey(), target))
                == AbilityCastResult.ACCEPTED;
    }

    @Override
    public void stop() {
        threat = null;
        sister.getNavigation().stop();
    }

    private void chaseAndMelee(LivingEntity target) {
        sister.getLookControl().setLookAt(target, 30.0f, 30.0f);
        sister.getNavigation().moveTo(target, 1.1);
        if (sister.tickCount % 20 == 0 && isHostile(target) && sister.distanceToSqr(target) <= MELEE_RANGE * MELEE_RANGE
                && sister.level() instanceof ServerLevel serverLevel) {
            sister.doHurtTarget(serverLevel, target);
        }
    }

    private LivingEntity findThreat() {
        List<LivingEntity> nearby = sister.level().getEntitiesOfClass(
                LivingEntity.class,
                sister.getBoundingBox().inflate(THREAT_RANGE),
                entity -> entity != sister && entity.isAlive() && isHostile(entity)
        );
        if (!nearby.isEmpty()) {
            return nearby.stream().min(java.util.Comparator
                    .comparingInt((LivingEntity entity) -> entity instanceof Mob mob && mob.getTarget() == sister ? 0 : 1)
                    .thenComparingDouble(sister::distanceToSqr)).orElse(null);
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
        return EntityAbilityService.mayTarget(sister, entity);
    }

    private void fleeFrom(LivingEntity source, double speedScale) {
        var away = sister.position().subtract(source.position()).normalize().scale(8.0).add(sister.position());
        sister.getNavigation().moveTo(away.x, away.y, away.z, 1.0 * speedScale);
        sister.getLookControl().setLookAt(source, 30.0f, 30.0f);
    }
}
