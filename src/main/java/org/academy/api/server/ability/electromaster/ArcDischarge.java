package org.academy.api.server.ability.electromaster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.structure.StructureQueries;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.ability.electromaster.arc.ElectromasterArcEffects;
import org.academy.internal.common.ability.electromaster.arc.ElectromasterArcTargeting;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.FriendlyFireSetting;

import java.util.List;

/** Single-target electric discharge shared by living actors; callers own costs and cooldowns. */
public final class ArcDischarge {
    private ArcDischarge() {}

    public static boolean fire(LivingEntity caster, LivingEntity target, Skill skill, float damage) {
        if (!(caster.level() instanceof ServerLevel level) || target == null
                || target.level() != level || !caster.isAlive() || !target.isAlive()
                || caster == target || !Float.isFinite(damage) || damage <= 0
                || !ElectromasterArcTargeting.canDamageAlongArc(target)
                || TeamRelations.areAllied(caster, target)
                || caster instanceof ServerPlayer player && FriendlyFireSetting.shouldPrevent(player, target)) {
            return false;
        }
        var start = caster.position().add(0, caster.getEyeHeight() * 0.8, 0);
        var end = target.getBoundingBox().getCenter();
        if (!StructureQueries.clearSightline(level, start, end)) return false;
        ElectromasterArcEffects.spawnArc(level,
                List.of(ElectromasterArcEffects.arc(start, end, level.getRandom().nextLong())), 20, start);
        caster.playSound(SoundEvents.ARC_WEAK.get(), 1, 1);
        return target.hurtServer(level, SkillDamageSource.of(caster, skill).withElectricalChargePoints(1), damage);
    }
}
