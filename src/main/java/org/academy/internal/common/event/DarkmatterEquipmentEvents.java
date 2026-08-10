package org.academy.internal.common.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.DarkmatterEnchantments;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import net.minecraft.util.Mth;

public final class DarkmatterEquipmentEvents {
    private DarkmatterEquipmentEvents() {
    }

    static float damageMultiplier(int protectedPieces) {
        return Math.max(0, 1.0f - Mth.clamp(protectedPieces, 0, 4) * 0.1f);
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onAttackEntity(AttackEntityEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(event.getTarget() instanceof LivingEntity target)
                    || target == player || player.isAlliedTo(target)) return;
            if (!DarkmatterItemUtil.hasEnchantment(player.getMainHandItem(),
                    DarkmatterEnchantments.DARKMATTER)) return;
            var damage = 4.0f * AbilitySystemServer.getSystem(player)
                    .getPlayerDamageMultiplier(player.getUUID());
            target.hurtServer(level,
                    SkillDamageSource.of(player, Skills.DARKMATTER_SHAPING.get()), damage);
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
            var protectedPieces = 0;
            for (var slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                var armor = player.getItemBySlot(slot);
                if (DarkmatterItemUtil.hasFamilyEnchantment(armor)) protectedPieces++;
            }
            event.setAmount(event.getAmount() * damageMultiplier(protectedPieces));
        }

        @SubscribeEvent
        public static void onAnvilUpdate(AnvilUpdateEvent event) {
            if (event.getLeft().isEmpty() || !DarkmatterItemUtil.isDarkmatter(event.getRight())) return;
            var result = DarkmatterItemUtil.createAnvilUpgradeResult(
                    event.getPlayer().registryAccess(), event.getLeft(), event.getName());
            if (result.isEmpty()) return;
            event.setOutput(result);
            event.setMaterialCost(1);
            event.setXpCost(3);
        }
    }
}
