package org.academy.api.common.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.academy.internal.common.world.damagesource.CategoryDamageRuntime;

/**
 * Reusable server-thread category effects for skills, precision operations and non-player actors.
 * Call only after a confirmed hit when bypassing Academy's normal damage entry points.
 */
public final class AbilityHitEffects {
    private AbilityHitEffects() {}

    /** Law-mark/exposure detonation; keeps source attribution and the darkmatter targeting policy. */
    public static boolean detonateLaw(LivingEntity target, SkillDamageSource source, float amount) {
        return org.academy.internal.common.ability.darkmatter.DarkmatterLawMark.damageDetonation(target, source, amount);
    }

    /** Adds charge, consuming five points per paralysis discharge. Returns discharge count. */
    public static int addElectricalCharge(LivingEntity target, int points) {
        return CategoryDamageRuntime.addCharge(target, points);
    }

    /** Adds charge with attacker attribution for the direct electrical discharge damage. */
    public static int addElectricalCharge(LivingEntity target, int points,
                                         DamageSource source) {
        return CategoryDamageRuntime.addCharge(target, points, source);
    }

    public static int electricalCharge(LivingEntity target) {
        return CategoryDamageRuntime.electricalCharge(target);
    }

    public static boolean isParalyzed(LivingEntity target) {
        return CategoryDamageRuntime.isParalyzed(target);
    }

    /** Applies the four persistent level-I radiation debuffs without downgrading stronger effects. */
    public static void applyRadiation(LivingEntity target) {
        CategoryDamageRuntime.applyRadiation(target);
    }

    /** Equipment-only wear; repeated hits share the target's short wear budget. */
    public static void damageEquipment(LivingEntity target, int amount, boolean includeHands) {
        CategoryDamageRuntime.damageEquipment(target, amount, includeHands);
    }
}
