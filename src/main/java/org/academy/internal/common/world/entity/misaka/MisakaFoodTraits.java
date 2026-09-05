package org.academy.internal.common.world.entity.misaka;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.academy.AcademyCraft;

public final class MisakaFoodTraits {
    public static final TagKey<Block> FORAGE_CONTAINERS =
            TagKey.create(Registries.BLOCK, AcademyCraft.academy("misaka/forage_containers"));

    private static final Identifier MISAKA_TOWER_ID = AcademyCraft.academy("misaka_tower");
    private static final Identifier MISAKA_TOWER_PROMAX_ID = AcademyCraft.academy("misaka_tower_promax");

    private MisakaFoodTraits() {
    }

    /** Ordinary 御·塔 only — Promax is not a normal tower food. */
    public static boolean isTower(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return MISAKA_TOWER_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean isPromax(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return MISAKA_TOWER_PROMAX_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean isFavorite(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // Favorite list: meat nutrition≥8, cake, ordinary 御·塔 (not Promax)
        if (stack.is(Items.CAKE) || isTower(stack)) {
            return true;
        }
        var food = stack.get(DataComponents.FOOD);
        return food != null && food.nutrition() >= 8 && stack.is(ItemTags.MEAT);
    }

    /** Items the forage goal can actually consume from a container. */
    public static boolean isForageConsumable(ItemStack stack) {
        if (stack.isEmpty() || isPromax(stack)) {
            return false;
        }
        return stack.is(Items.CAKE) || isTower(stack) || stack.get(DataComponents.FOOD) != null;
    }

    public static void applyTo(MisakaSisterEntity sister, ItemStack stack) {
        if (stack.is(Items.CAKE)) {
            sister.getFoodData().eat(10, 0.1f);
        } else {
            var food = stack.get(DataComponents.FOOD);
            if (food != null) {
                // FoodProperties.saturation() is absolute, same as FoodData.eat(FoodProperties).
                sister.getFoodData().eatFood(food.nutrition(), food.saturation());
            }
        }
        applyConsumeEffects(sister, stack);
    }

    /**
     * Applies {@link net.minecraft.world.item.component.Consumable} on-consume effects
     * (status effects, chorus teleport, etc.) without shrinking the stack — callers own consumption.
     */
    private static void applyConsumeEffects(MisakaSisterEntity sister, ItemStack stack) {
        if (sister.level().isClientSide()) {
            return;
        }
        var consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) {
            return;
        }
        for (var effect : consumable.onConsumeEffects()) {
            effect.apply(sister.level(), stack, sister);
        }
    }

    public static boolean isForageContainer(BlockEntity blockEntity) {
        return blockEntity != null && blockEntity.getBlockState().is(FORAGE_CONTAINERS);
    }
}
