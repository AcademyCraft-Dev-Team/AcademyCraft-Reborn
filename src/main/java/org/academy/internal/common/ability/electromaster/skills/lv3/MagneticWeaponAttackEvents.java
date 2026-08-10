package org.academy.internal.common.ability.electromaster.skills.lv3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.academy.AcademyCraft;

/** Keeps the remote blade from inheriting the player's physical crit and sweep state. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class MagneticWeaponAttackEvents {
    private MagneticWeaponAttackEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void suppressPhysicalCriticalHit(CriticalHitEvent event) {
        if (MagneticWeaponAttackContext.isCurrentAttack(event.getEntity(), event.getTarget())) {
            event.setCriticalHit(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void suppressPhysicalSweep(SweepAttackEvent event) {
        if (MagneticWeaponAttackContext.isCurrentAttack(event.getEntity(), event.getTarget())) {
            event.setSweeping(false);
        }
    }

    @SubscribeEvent
    public static void applyMagnetizedSlowness(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0.0f
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || event.getSource().getDirectEntity() != attacker
                || !MagneticWeaponEnchantments.isMagnetized(attacker.getMainHandItem())) return;
        event.getEntity().addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS, 40, 0, false, true, true));
    }
}
