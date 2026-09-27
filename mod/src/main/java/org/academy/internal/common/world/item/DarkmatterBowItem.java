package org.academy.internal.common.world.item;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

public final class DarkmatterBowItem extends BowItem implements DarkmatterShapedItem {
    public DarkmatterBowItem(Properties properties) {
        super(DarkmatterNativeItemSupport.equipmentProperties(properties));
    }

    @Override
    public DarkmatterShape darkmatterShape() {
        return DarkmatterShape.BOW;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return DarkmatterNativeItemSupport::isSupportedArrow;
    }

    @Override
    public ItemStack getDefaultCreativeAmmo(
            @Nullable Player player, ItemStack projectileWeaponItem) {
        return DarkmatterNativeItemSupport.infiniteDarkmatterArrow(projectileWeaponItem);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var weapon = player.getItemInHand(hand);
        var foundProjectile = !player.getProjectile(weapon).isEmpty();
        var result = EventHooks.onArrowNock(
                weapon, level, player, hand, foundProjectile);
        if (result != null) return result;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack weapon, Level level,
                                LivingEntity entity, int remainingTime) {
        if (!(entity instanceof Player player)) return false;
        var projectile = player.getProjectile(weapon);
        if (projectile.isEmpty()) {
            projectile = DarkmatterNativeItemSupport.infiniteDarkmatterArrow(weapon);
        }
        var timeHeld = getUseDuration(weapon, entity) - remainingTime;
        timeHeld = EventHooks.onArrowLoose(
                weapon, level, player, timeHeld, true);
        if (timeHeld < 0) return false;
        var power = getPowerForTime(timeHeld);
        if (power < 0.1f) return false;
        var firedProjectiles = draw(weapon, projectile, player);
        if (level instanceof ServerLevel serverLevel && !firedProjectiles.isEmpty()) {
            shoot(serverLevel, player, player.getUsedItemHand(), weapon,
                    firedProjectiles, power * 3.0f, 1.0f, power == 1.0f, null);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f,
                1.0f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        player.awardStat(Stats.ITEM_USED.get(this));
        return true;
    }

    @Override
    public boolean isCombineRepairable(ItemStack stack) {
        return false;
    }

    @Override
    public float getXpRepairRatio(ItemStack stack) {
        return 0.0f;
    }

    @Override
    public boolean canGrindstoneRepair(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return DarkmatterNativeItemSupport.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return DarkmatterNativeItemSupport.barWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return DarkmatterNativeItemSupport.barColor(stack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(
            ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return DarkmatterNativeItemSupport.shouldCauseReequipAnimation(
                oldStack, newStack, slotChanged);
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return DarkmatterNativeItemSupport.shouldCauseBlockBreakReset(oldStack, newStack);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return DarkmatterNativeItemSupport.supportsEnchantment(
                stack, enchantment, super.supportsEnchantment(stack, enchantment));
    }
}
