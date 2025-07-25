package org.academy.internal.common.world.item.crafting;

import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.HashMap;

public final class RecipeTypes {
    public static final HashMap<String, RecipeType<?>> RECIPE_TYPES = new HashMap<>();
    public static RecipeType<CraftingRecipe> OMNI_CRAFTING = register("omni_crafting");

    @SuppressWarnings("SameParameterValue")
    private static <T extends Recipe<?>> RecipeType<T> register(String name) {
        var recipeType = new RecipeType<T>() {
            @Override
            public String toString() {
                return name;
            }
        };
        RECIPE_TYPES.put(name, recipeType);
        return recipeType;
    }
}