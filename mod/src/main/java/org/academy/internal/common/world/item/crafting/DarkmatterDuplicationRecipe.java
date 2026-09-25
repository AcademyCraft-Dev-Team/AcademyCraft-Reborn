package org.academy.internal.common.world.item.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.item.DarkmatterItemUtil;

public final class DarkmatterDuplicationRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        var darkmatterFound = false;
        var materialFound = false;
        for (var slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (DarkmatterItemUtil.isDarkmatter(stack)) {
                if (darkmatterFound) return false;
                darkmatterFound = true;
            } else if (DarkmatterItemUtil.isDuplicableMaterial(stack)) {
                if (materialFound) return false;
                materialFound = true;
            } else {
                return false;
            }
        }
        return darkmatterFound && materialFound;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        for (var slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (DarkmatterItemUtil.isDuplicableMaterial(stack)) {
                return DarkmatterItemUtil.createDuplicatedMaterialResult(stack);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return RecipeSerializers.DARKMATTER_DUPLICATION.get();
    }
}
