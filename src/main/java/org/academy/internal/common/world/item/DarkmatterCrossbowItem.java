package org.academy.internal.common.world.item;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public final class DarkmatterCrossbowItem extends CrossbowItem implements DarkmatterShapedItem {
    private boolean startSoundPlayed;
    private boolean midLoadSoundPlayed;

    public DarkmatterCrossbowItem(Properties properties) {
        super(DarkmatterNativeItemSupport.equipmentProperties(properties));
    }

    @Override public DarkmatterShape darkmatterShape() { return DarkmatterShape.CROSSBOW; }
    @Override public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return stack -> super.getSupportedHeldProjectiles().test(stack)
                || DarkmatterNativeItemSupport.isSupportedArrow(stack);
    }
    @Override public Predicate<ItemStack> getAllSupportedProjectiles() {
        return DarkmatterNativeItemSupport::isSupportedArrow;
    }
    @Override public ItemStack getDefaultCreativeAmmo(
            @Nullable Player player, ItemStack projectileWeaponItem) {
        return DarkmatterNativeItemSupport.infiniteDarkmatterArrow(projectileWeaponItem);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var weapon = player.getItemInHand(hand);
        var charged = weapon.getOrDefault(
                DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        if (!charged.isEmpty()) return super.use(level, player, hand);
        startSoundPlayed = false;
        midLoadSoundPlayed = false;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity,
                          ItemStack weapon, int ticksRemaining) {
        if (level.isClientSide()) return;
        var chargeTicks = getChargeDuration(weapon, entity);
        var progress = (float) (weapon.getUseDuration(entity) - ticksRemaining)
                / Math.max(1, chargeTicks);
        if (progress < 0.2f) {
            startSoundPlayed = false;
            midLoadSoundPlayed = false;
        }
        if (progress >= 0.2f && !startSoundPlayed) {
            startSoundPlayed = true;
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.CROSSBOW_LOADING_START, SoundSource.PLAYERS, 0.5f, 1.0f);
        }
        if (progress >= 0.5f && !midLoadSoundPlayed) {
            midLoadSoundPlayed = true;
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS, 0.5f, 1.0f);
        }
        if (progress < 1.0f || isCharged(weapon)) return;
        var projectile = entity.getProjectile(weapon);
        if (projectile.isEmpty()) {
            projectile = DarkmatterNativeItemSupport.infiniteDarkmatterArrow(weapon);
        }
        List<ItemStack> drawn = draw(weapon, projectile, entity);
        if (drawn.isEmpty()) return;
        weapon.set(DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.ofNonEmpty(drawn));
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.CROSSBOW_LOADING_END, entity.getSoundSource(), 1.0f,
                1.0f / (level.getRandom().nextFloat() * 0.5f + 1.0f) + 0.2f);
    }

    @Override public boolean isCombineRepairable(ItemStack stack) { return false; }
    @Override public float getXpRepairRatio(ItemStack stack) { return 0.0f; }
    @Override public boolean canGrindstoneRepair(ItemStack stack) { return false; }
    @Override public boolean isBarVisible(ItemStack stack) { return DarkmatterNativeItemSupport.isBarVisible(stack); }
    @Override public int getBarWidth(ItemStack stack) { return DarkmatterNativeItemSupport.barWidth(stack); }
    @Override public int getBarColor(ItemStack stack) { return DarkmatterNativeItemSupport.barColor(stack); }
    @Override public boolean shouldCauseReequipAnimation(
            ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return DarkmatterNativeItemSupport.shouldCauseReequipAnimation(
                oldStack, newStack, slotChanged);
    }
    @Override public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return DarkmatterNativeItemSupport.shouldCauseBlockBreakReset(oldStack, newStack);
    }
    @Override public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return DarkmatterNativeItemSupport.supportsEnchantment(
                stack, enchantment, super.supportsEnchantment(stack, enchantment));
    }
}
