package org.academy.internal.common.world.damagesource;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.academy.api.common.damage.SkillDamageSource;

/**
 * Global safety checks for all AcademyCraft damage sources, including third-party callers.
 */
@EventBusSubscriber
public final class SkillDamageEvents {
    private SkillDamageEvents() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource() instanceof SkillDamageSource
                && PvpSetting.shouldPrevent(
                PvpSetting.resolveAttacker(event.getSource()), event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof Player player && DamageTypes.isImmunePlayer(player, event.getSource())) {
            event.setCanceled(true);
        }
    }
}
