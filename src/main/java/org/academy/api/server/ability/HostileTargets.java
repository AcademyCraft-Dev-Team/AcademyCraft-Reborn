package org.academy.api.server.ability;

import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.server.ability.HostileTargetRuntime;

/** Additional, actor-specific hostility for server-side skill and program target selection. */
public final class HostileTargets {
    public static final int MARK_DURATION_TICKS = 20 * 10;

    private HostileTargets() {}

    /** Natural, targeted or deliberately marked hostility, after team and PVP protection. */
    public static boolean isHostile(LivingEntity actor, LivingEntity target) {
        if (actor == target || !target.isAlive() || target.isSpectator() || actor.level() != target.level()
                || org.academy.api.server.team.TeamRelations.areAllied(actor, target)
                || org.academy.api.server.team.TeamRelations.areAllied(target, actor)) return false;
        if (target instanceof net.minecraft.world.entity.player.Player player && player.isCreative()) return false;
        if (actor instanceof net.minecraft.world.entity.player.Player player
                && org.academy.internal.common.world.damagesource.PvpSetting.shouldPrevent(player, target)) return false;
        return target instanceof net.minecraft.world.entity.monster.Enemy || isMarked(actor, target)
                || target instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == actor
                || actor instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == target;
    }

    /**
     * Automatic weapon target admission: natural or marked hostility, excluding allied,
     * creative, PVP-protected and actor-owned pet targets.
     */
    public static boolean isDetectable(LivingEntity actor, LivingEntity target) {
        if (actor == null || target == null || actor == target
                || !target.isAlive() || target.isRemoved() || target.isSpectator()) return false;
        if (target instanceof net.minecraft.world.entity.player.Player victim && victim.isCreative()) return false;
        if (actor instanceof net.minecraft.world.entity.player.Player player) {
            if (org.academy.internal.common.world.damagesource.PvpSetting.shouldPrevent(player, target)) return false;
            if (target instanceof net.minecraft.world.entity.TamableAnimal tameable && tameable.isOwnedBy(player)) {
                return false;
            }
        }
        if (org.academy.api.server.team.TeamRelations.areAllied(actor, target)) return false;
        return isMarked(actor, target)
                || target instanceof net.minecraft.world.entity.monster.Enemy
                || target instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == actor;
    }

    /** Marks a target after an intentional attack, refreshing its ten-second lifetime. */
    public static boolean mark(LivingEntity attacker, LivingEntity target) {
        return HostileTargetRuntime.mark(attacker, target);
    }

    /** Add to natural hostility, after the ability's own target admission checks. */
    public static boolean isMarked(LivingEntity attacker, LivingEntity target) {
        return HostileTargetRuntime.isMarked(attacker, target);
    }
}
