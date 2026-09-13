package org.academy.internal.common.world.entity.misaka;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.electromaster.ElectromasterArcEffects;
import org.academy.internal.common.ability.electromaster.ElectromasterArcTargeting;
import org.academy.internal.common.ability.electromaster.skills.lv1.ArcGenerate;
import org.academy.internal.common.sounds.SoundEvents;

import java.util.List;

/**
 * Sister combat entry for Arc Generate. Does not touch {@link ArcGenerate}'s player
 * CP / unlock path — reuses public skill damage scaling and Electromaster arc FX.
 */
public final class MisakaArcAttack {
    private MisakaArcAttack() {
    }

    public static boolean tryFire(MisakaSisterEntity sister, LivingEntity target) {
        if (!(sister.level() instanceof ServerLevel level)
                || target == null
                || !target.isAlive()
                || sister == target
                || !ElectromasterArcTargeting.canDamageAlongArc(target)
                || TeamRelations.areAllied(sister, target)) {
            return false;
        }

        var start = sister.position().add(0.0, sister.getEyeHeight() * 0.8, 0.0);
        var end = target.getBoundingBox().getCenter();
        var damage = ArcGenerate.programDamage(1.0f, 1.0f);
        var source = SkillDamageSource.of(sister, Skills.ARC_GENERATE.get());

        ElectromasterArcEffects.spawnArc(
                level,
                List.of(ElectromasterArcEffects.arc(start, end, level.getRandom().nextLong())),
                20,
                start
        );
        sister.playSound(SoundEvents.ARC_WEAK.get(), 1.0f, 1.0f);
        return target.hurtServer(level, source, damage);
    }
}
