package org.academy.internal.common.world.item.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.academy.internal.common.world.item.ItemDataComponents;
import org.academy.internal.common.world.item.Items;

/**
 * Shapeless application of one configured coating onto exactly one target item.
 */
public final class DarkmatterCoatingRecipe extends CustomRecipe {
    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findInputs(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        var inputs = findInputs(input);
        if (inputs == null) return ItemStack.EMPTY;
        var result = inputs.target.copyWithCount(1);
        result.set(ItemDataComponents.DARKMATTER_COATING_PROFILE.get(),
                DarkmatterItemUtil.shapingProfile(inputs.coating));
        return result;
    }

    private static Inputs findInputs(CraftingInput input) {
        ItemStack coating = ItemStack.EMPTY;
        ItemStack target = ItemStack.EMPTY;
        for (var slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(Items.DARKMATTER_COATING.get())) {
                if (!coating.isEmpty()) return null;
                coating = stack;
            } else {
                if (!target.isEmpty()) return null;
                target = stack;
            }
        }
        return coating.isEmpty() || target.isEmpty() ? null : new Inputs(coating, target);
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return RecipeSerializers.DARKMATTER_COATING.get();
    }

    private record Inputs(ItemStack coating, ItemStack target) {
    }
}
