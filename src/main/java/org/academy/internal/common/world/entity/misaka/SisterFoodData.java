package org.academy.internal.common.world.entity.misaka;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.academy.internal.common.world.entity.misaka.favor.FavorContext;
import org.academy.internal.common.world.entity.misaka.favor.FavorRuleRegistry;
import org.academy.internal.common.world.entity.misaka.perception.PerceptionService;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;

/**
 * Player-style food / saturation for an awakened Misaka sister.
 * Starvation damage and hand/self-feed side effects live here so the entity stays thin.
 */
public final class SisterFoodData {
    private int foodLevel = 20;
    private float saturationLevel = 5.0f;
    private int foodTickTimer;
    private int starveTickTimer;

    public int getFoodLevel() {
        return foodLevel;
    }

    public void setFoodLevel(int foodLevel) {
        this.foodLevel = Mth.clamp(foodLevel, 0, 20);
    }

    public void setSaturation(float saturationLevel) {
        this.saturationLevel = Mth.clamp(saturationLevel, 0.0f, foodLevel);
    }

    public boolean needsFood() {
        return foodLevel < 20;
    }

    /** Cake / crop-style: saturation from nutrition × modifier × 2 (vanilla FoodData.eat(int, float)). */
    public void eat(int nutrition, float saturationModifier) {
        eatFood(nutrition, nutrition * saturationModifier * 2.0f);
    }

    /** Registry food: saturation value already absolute (vanilla FoodData.eat(FoodProperties)). */
    public void eatFood(int nutrition, float saturation) {
        foodLevel = Math.min(20, foodLevel + nutrition);
        saturationLevel = Math.min(foodLevel, saturationLevel + saturation);
    }

    public void tick(MisakaSisterEntity entity) {
        foodTickTimer++;
        if (foodTickTimer >= 80) {
            foodTickTimer = 0;
            if (foodLevel >= 18 && saturationLevel > 0.0f) {
                if (entity.getHealth() < entity.getMaxHealth()) {
                    entity.heal(1.0f);
                }
                saturationLevel = Math.max(0.0f, saturationLevel - 1.0f);
            } else if (foodLevel > 0) {
                if (saturationLevel > 0.0f) {
                    saturationLevel = Math.max(0.0f, saturationLevel - 1.0f);
                } else if (entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
                    foodLevel--;
                }
            }
        }
    }

    /** Hunger damage when empty; HP floor 1 (eat-dirt mode). */
    public void tickFoodAndStarvation(MisakaSisterEntity entity) {
        if (foodLevel == 0 && entity.tickCount % 80 == 0) {
            if (entity.getHealth() > 1.0f) {
                entity.hurt(entity.damageSources().starve(), 1.0f);
            }
            if (entity.getHealth() < 1.0f) {
                entity.setHealth(1.0f);
            }
        }
    }

    public void onSelfFed(MisakaSisterEntity sister, ItemStack stack) {
        sister.rosterRecord().ifPresent(record -> {
            if (MisakaFoodTraits.isFavorite(stack)) {
                PerceptionService.gain(sister.level().getServer(), record, 2);
            }
            MisakaSisterRoster.get(sister.level().getServer()).setDirty();
            MisakaSisterRosterSync.syncFromRecord(sister, record);
        });
    }

    public void onHandFed(MisakaSisterEntity sister, ServerPlayer player, ItemStack stack) {
        sister.rosterRecord().ifPresent(record -> {
            int sourceGain = MisakaFoodTraits.isTower(stack) ? 5 : 2;
            PerceptionService.gain(sister.level().getServer(), record, sourceGain);
            if (MisakaFoodTraits.isFavorite(stack)) {
                PerceptionService.gain(sister.level().getServer(), record, 2);
                FavorRuleRegistry.trigger(FavorContext.feedFavorite(record, sister.getMisakaUuid(), player));
            }
            if (stack.is(net.minecraft.world.item.Items.CAKE)) {
                FavorRuleRegistry.trigger(FavorContext.anniversaryCake(record, sister.getMisakaUuid(), player));
            }
            MisakaSisterRoster.get(sister.level().getServer()).setDirty();
            MisakaSisterRosterSync.syncFromRecord(sister, record);
            sister.syncFoodLevel();
        });
    }

    public void read(ValueInput input) {
        foodLevel = input.getIntOr("academy_food_level", 20);
        saturationLevel = input.getFloatOr("academy_food_saturation", 5.0f);
    }

    public void write(ValueOutput output) {
        output.putInt("academy_food_level", foodLevel);
        output.putFloat("academy_food_saturation", saturationLevel);
    }
}
