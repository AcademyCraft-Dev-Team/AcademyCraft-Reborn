package org.academy.internal.common.ability.accelerator.vector;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.academy.AcademyCraft;

/** Covers native damage chains which bypass Projectile collision hooks, without replacing hits. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class VectorEffectDamageGuard {
    private VectorEffectDamageGuard() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void protectIncoming(LivingIncomingDamageEvent event) { cancelRejected(event); }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void enforceIncoming(LivingIncomingDamageEvent event) { cancelRejected(event); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectApplied(LivingDamageEvent.Pre event) { neutralizeRejected(event); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void enforceApplied(LivingDamageEvent.Pre event) { neutralizeRejected(event); }

    private static void cancelRejected(LivingIncomingDamageEvent event) {
        if (!rejected(event.getSource(), event.getEntity())) return;
        event.setCanceled(true);
        event.getContainer().setShouldCauseSideEffects(false);
    }

    private static void neutralizeRejected(LivingDamageEvent.Pre event) {
        if (!rejected(event.getSource(), event.getEntity())) return;
        event.setNewDamage(0);
        event.getContainer().setShouldCauseSideEffects(false);
    }

    private static boolean rejected(DamageSource source, Entity target) {
        // Never guess from a shooter: another projectile or a melee action may share that owner.
        return source.getDirectEntity() instanceof Projectile projectile
                && VectorOperationRuntime.blocksHit(projectile, target);
    }
}
