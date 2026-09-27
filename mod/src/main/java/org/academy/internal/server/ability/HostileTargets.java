package org.academy.internal.server.ability;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.world.damagesource.PvpSetting;
import org.academy.internal.server.team.TeamRelations;

/**
 * Additional, actor-specific hostility for server-side skill and program target selection.
 */
public final class HostileTargets {
    public static final int MARK_DURATION_TICKS = 20 * 10;

    private HostileTargets() {
    }

    /**
     * Natural, targeted or deliberately marked hostility, after team and PVP protection.
     */
    public static boolean isHostile(LivingEntity actor, LivingEntity target) {
        if (actor == target || !target.isAlive() || target.isSpectator() || actor.level() != target.level()
                || TeamRelations.areAllied(actor, target)
                || TeamRelations.areAllied(target, actor)) return false;
        if (target instanceof Player player && player.isCreative()) return false;
        if (actor instanceof Player player
                && PvpSetting.shouldPrevent(player, target)) return false;
        return target instanceof Enemy || isMarked(actor, target)
                || target instanceof Mob mob && mob.getTarget() == actor
                || actor instanceof Mob mob && mob.getTarget() == target;
    }

    /**
     * Automatic weapon target admission: natural or marked hostility, excluding allied,
     * creative, PVP-protected and actor-owned pet targets.
     */
    public static boolean isDetectable(LivingEntity actor, LivingEntity target) {
        if (actor == null || target == null || actor == target
                || !target.isAlive() || target.isRemoved() || target.isSpectator()) return false;
        if (target instanceof Player victim && victim.isCreative()) return false;
        if (actor instanceof Player player) {
            if (PvpSetting.shouldPrevent(player, target)) return false;
            if (target instanceof TamableAnimal tameable && tameable.isOwnedBy(player)) {
                return false;
            }
        }
        if (TeamRelations.areAllied(actor, target)) return false;
        return isMarked(actor, target)
                || target instanceof Enemy
                || target instanceof Mob mob && mob.getTarget() == actor;
    }

    /**
     * Marks a target after an intentional attack, refreshing its ten-second lifetime.
     */
    public static boolean mark(LivingEntity attacker, LivingEntity target) {
        return HostileTargetRuntime.mark(attacker, target);
    }

    /**
     * Add to natural hostility, after the ability's own target admission checks.
     */
    public static boolean isMarked(LivingEntity attacker, LivingEntity target) {
        return HostileTargetRuntime.isMarked(attacker, target);
    }
}
