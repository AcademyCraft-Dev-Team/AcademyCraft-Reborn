package org.academy.internal.common.world.damagesource;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;

public final class CTADamageUtil {
    public static final float CTA_DAMAGE_AMOUNT = 4.0f;
    private static final ThreadLocal<Boolean> IN_CTA = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<LivingEntity> CTA_ATTACKER = new ThreadLocal<>();

    private CTADamageUtil() {
    }

    public static boolean isInCtaLogic() {
        return IN_CTA.get();
    }

    public static void runGuarded(Runnable action) {
        runGuarded(null, action);
    }

    public static void runGuarded(LivingEntity attacker, Runnable action) {
        if (IN_CTA.get()) {
            action.run();
            return;
        }
        IN_CTA.set(true);
        if (attacker != null) CTA_ATTACKER.set(attacker);
        try {
            action.run();
        } finally {
            CTA_ATTACKER.remove();
            IN_CTA.set(false);
        }
    }

    public static LivingEntity getCtaAttackerOrNull() {
        return CTA_ATTACKER.get();
    }

    public static void applyCompositeDamage(LivingEntity target, LivingEntity attacker,
                                            DamageSource source, float damage) {
        if (target == null || attacker == null || source == null || damage <= 0.0f) return;
        if (target == attacker || !target.isAlive()) return;
        if (target instanceof Player player && DamageTypes.isImmunePlayer(player)) return;
        if (attacker instanceof Player player && CtaFriendlyFireWhitelist.shouldProtect(player, target)) return;
        new CTAEntityActuallyHurt(target).actuallyHurt(source, damage, true);
    }
}
