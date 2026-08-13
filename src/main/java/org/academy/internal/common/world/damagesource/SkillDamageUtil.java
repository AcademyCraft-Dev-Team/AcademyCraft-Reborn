package org.academy.internal.common.world.damagesource;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.mixin.common.LivingEntityDamageInvoker;
import org.jspecify.annotations.Nullable;

/**
 * Central entry point for AcademyCraft category damage.
 */
public final class SkillDamageUtil {
    private static final float EPSILON = 0.0001f;

    private SkillDamageUtil() {
    }

    public static boolean apply(ServerPlayer attacker, LivingEntity target, Skill skill,
                                ResourceKey<DamageType> type, float amount) {
        if (attacker == null || target == null || skill == null || type == null) return false;
        if (!(amount > 0.0f) || !Float.isFinite(amount) || !target.isAlive()) return false;
        if (target == attacker || DamageTypes.isImmunePlayer(target instanceof Player p ? p : null)) {
            return false;
        }
        if (!(target.level() instanceof ServerLevel level)) return false;

        var source = SkillDamageSource.of(attacker, skill, type);
        if (DamageTypes.usesVerifiedTrueHealth(type)) {
            return new CTAEntityActuallyHurt(target).actuallyHurt(source, amount, true);
        }
        if (DamageTypes.usesDirectActuallyHurt(type)) {
            return applyDirectWithFallback(level, attacker, target, skill, source, amount);
        }
        return target.hurtServer(level, source, amount);
    }

    public static boolean applyDirect(ServerLevel level, LivingEntity target,
                                      SkillDamageSource source, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return false;
        if (DamageTypes.isImmunePlayer(target instanceof Player p ? p : null)) {
            return false;
        }
        if (DamageTypes.usesVerifiedTrueHealth(source)) {
            return new CTAEntityActuallyHurt(target).actuallyHurt(source, amount, true);
        }
        return applyDirectWithFallback(level, attacker, target, source.getSkill(), source, amount);
    }

    private static boolean applyDirectWithFallback(ServerLevel level, ServerPlayer attacker,
                                                   LivingEntity target, Skill skill,
                                                   DamageSource source, float amount) {
        var beforeHealth = target.getHealth();
        var beforeAbsorption = target.getAbsorptionAmount();
        var invoker = (LivingEntityDamageInvoker) target;
        var containers = invoker.academy$getDamageContainers();
        var container = new DamageContainer(source, amount);
        containers.push(container);

        var completed = false;
        try {
            invoker.academy$actuallyHurt(level, source, amount);
            completed = true;
        } catch (Throwable error) {
            AcademyCraft.getLogger().warn(
                    "Direct actuallyHurt failed for {}; falling back when no damage was committed",
                    target.getStringUUID(),
                    error
            );
            PlayerAttributeRuntime.clearDamageContext();
        } finally {
            if (!containers.isEmpty() && containers.peek() == container) containers.pop();
            else containers.remove(container);
        }

        var healthDamage = Math.max(0.0f, beforeHealth - target.getHealth());
        var absorptionDamage = Math.max(0.0f, beforeAbsorption - target.getAbsorptionAmount());
        var committed = healthDamage > EPSILON || absorptionDamage > EPSILON;
        if (!completed && !committed) {
            return target.hurtServer(level, attacker.damageSources().playerAttack(attacker), amount);
        }
        if (!committed) {
            return target.hurtServer(level, attacker.damageSources().playerAttack(attacker), amount);
        }

        completeDirectDamage(
                level, target, source, amount, healthDamage + absorptionDamage,
                attacker, skill, target.isDeadOrDying(), true
        );
        return true;
    }

    /**
     * Completes the outer half of vanilla's damage flow after a verified direct health mutation.
     * The health mutation itself may bypass {@code hurtServer}, but kill attribution, criteria,
     * death events, loot, experience, and the normal death-removal animation still use vanilla.
     */
    static void completeDirectDamage(
            ServerLevel level,
            LivingEntity target,
            DamageSource source,
            float originalAmount,
            float inflictedAmount,
            @Nullable ServerPlayer attacker,
            @Nullable Skill skill,
            boolean confirmedLethal,
            boolean checkDeathProtection
    ) {
        if (attacker != null) {
            target.setLastHurtByPlayer(attacker, 100);
            target.setLastHurtByMob(attacker);
        }
        level.broadcastDamageEvent(target, source);
        if (attacker != null && skill != null) {
            skill.onHurt(attacker, target, inflictedAmount);
        }

        if (target instanceof ServerPlayer hurtPlayer) {
            CriteriaTriggers.ENTITY_HURT_PLAYER.trigger(
                    hurtPlayer, source, originalAmount, inflictedAmount, false
            );
        }
        if (attacker != null) {
            CriteriaTriggers.PLAYER_HURT_ENTITY.trigger(
                    attacker, target, source, originalAmount, inflictedAmount, false
            );
        }

        if (!confirmedLethal) return;
        var protectedFromDeath = checkDeathProtection
                && ((LivingEntityDamageInvoker) target).academy$checkTotemDeathProtection(source);
        if (!protectedFromDeath) target.die(source);
    }
}
