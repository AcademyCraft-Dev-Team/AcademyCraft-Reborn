package org.academy.internal.common.ability.darkmatter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle;

import java.util.UUID;

/**
 * Shared dark-matter network membership, target selection, and damage admission rules.
 */
public final class DarkmatterTargeting {
    private static final ThreadLocal<PvpBypass> PVP_BYPASS = new ThreadLocal<>();

    private DarkmatterTargeting() {
    }

    public static boolean isNetworkMember(Entity entity) {
        return entity instanceof DarkmatterBeetle;
    }

    /** Includes owner links that vanilla teams cannot represent for the custom beetle mob. */
    public static boolean areAllied(Entity first, Entity second) {
        if (first == null || second == null) return false;
        if (first == second || first.isAlliedTo(second) || second.isAlliedTo(first)) return true;
        if (first instanceof DarkmatterBeetle beetle && beetle.isOwnerAlly(second)) return true;
        return second instanceof DarkmatterBeetle beetle && beetle.isOwnerAlly(first);
    }

    /**
     * Returns whether an explicitly aimed or area dark-matter effect may affect the target.
     * Players on a different team remain valid even when vanilla server PVP is disabled.
     */
    public static boolean isAttackableBy(ServerPlayer owner, LivingEntity target) {
        if (owner == null || target == null || target == owner || !target.isAlive()
                || target.isRemoved() || isNetworkMember(target)) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (areAllied(owner, target)) return false;
        return !(target instanceof TamableAnimal tame && tame.isOwnedBy(owner));
    }

    /**
     * Hostile-only automatic targeting: hostile mobs plus non-team players.
     */
    public static boolean isEnemyTarget(ServerPlayer owner, LivingEntity target) {
        if (!isAttackableBy(owner, target)) return false;
        if (target instanceof Player) return true;
        return target instanceof Enemy
                || target instanceof Mob mob && mob.getTarget() == owner;
    }

    public static boolean isDarkmatterDamage(DamageSource source) {
        return source instanceof SkillDamageSource skillSource
                && skillSource.getSkill().getCategory() instanceof Darkmatter;
    }

    /**
     * Applies dark-matter skill damage after enforcing network and team protection. For a
     * non-team player rejected only by vanilla's global PVP switch, the normal armor-aware
     * damage stage is entered directly so the skill behaves consistently with its target rule.
     */
    public static boolean hurt(
            ServerLevel level,
            LivingEntity target,
            DamageSource source,
            float amount
    ) {
        if (!isDarkmatterDamage(source)) return target.hurtServer(level, source, amount);
        if (isNetworkMember(target)) return false;
        if (!(source instanceof SkillDamageSource skillSource)
                || !(source.getEntity() instanceof ServerPlayer owner)
                || !isAttackableBy(owner, target)) return false;
        if (target instanceof ServerPlayer player && !player.canHarmPlayer(owner)) {
            var previous = PVP_BYPASS.get();
            PVP_BYPASS.set(new PvpBypass(player.getUUID(), owner.getUUID()));
            try {
                return target.hurtServer(level, skillSource, amount);
            } finally {
                if (previous == null) PVP_BYPASS.remove();
                else PVP_BYPASS.set(previous);
            }
        }
        return target.hurtServer(level, source, amount);
    }

    public static boolean shouldBypassPvpCheck(ServerPlayer victim, Player attacker) {
        var bypass = PVP_BYPASS.get();
        return bypass != null && victim != null && attacker != null
                && bypass.victim().equals(victim.getUUID())
                && bypass.attacker().equals(attacker.getUUID());
    }

    private record PvpBypass(UUID victim, UUID attacker) {
    }
}
