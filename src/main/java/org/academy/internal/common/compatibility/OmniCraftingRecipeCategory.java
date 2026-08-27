package org.academy.internal.common.compatibility;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.BedBlock;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.inventory.OmniCraftingMenu;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.material.Fluids;

import java.util.List;

public final class OmniCraftingRecipeCategory
        extends AbstractRecipeCategory<OmniCraftingRecipeCategory.DisplayRecipe> {
    public static final IRecipeType<DisplayRecipe> TYPE = IRecipeType.create(
            AcademyCraft.academy("omni_crafting"),
            DisplayRecipe.class
    );
    public static final DisplayRecipe ABILITY_DEVELOPER = new DisplayRecipe(
            AcademyCraft.academy("omni_crafting/ability_developer")
    );

    private final IDrawable arrow;

    public OmniCraftingRecipeCategory(IGuiHelper guiHelper) {
        super(
                TYPE,
                Component.translatable("jei.academy.omni_crafting"),
                guiHelper.createDrawableItemLike(Items.OMNI_CRAFTING_TABLE.get()),
                158,
                67
        );
        arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DisplayRecipe recipe, IFocusGroup focuses) {
        addItem(builder, 0, 0, net.minecraft.world.item.Items.STAINED_GLASS_PANE.blue());
        addItem(builder, 1, 0, net.minecraft.world.item.Items.STAINED_GLASS_PANE.blue());
        addItem(builder, 2, 0, Items.ABILITY_CONTROL_TABLET.get());
        addItem(builder, 0, 1, Items.WIND_GEN_BASE_SCREEN.get());
        builder.addInputSlot(3 + 18, 3 + 18)
                .setStandardSlotBackground()
                .addItemStacks(BEDS);
        addItem(builder, 2, 1, Items.IMAG_PHASE_CIRCUIT.get());
        addItem(builder, 0, 2, Items.IMAG_PHASE_INGOT.get());
        addItem(builder, 1, 2, Items.IMAG_PHASE_INGOT.get());
        addItem(builder, 2, 2, Items.IMAG_PHASE_INGOT.get());

        builder.addSlot(RecipeIngredientRole.INPUT, 68, 7)
                .setStandardSlotBackground()
                .setFluidRenderer(OmniCraftingMenu.SPECIAL_RECIPE_FLUID, true, 16, 16)
                .add(Fluids.IMAG_PHASE.get(), OmniCraftingMenu.SPECIAL_RECIPE_FLUID);
        builder.addOutputSlot(137, 22)
                .setOutputSlotBackground()
                .add(Items.ABILITY_DEVELOPER.get());
    }

    @Override
    public void createRecipeExtras(
            IRecipeExtrasBuilder builder,
            DisplayRecipe recipe,
            IFocusGroup focuses
    ) {
        builder.addDrawable(arrow, 101, 22);
        builder.addText(
                        Component.translatable(
                                "jei.academy.omni_crafting.energy",
                                OmniCraftingMenu.SPECIAL_RECIPE_ENERGY
                        ),
                        84,
                        10
                )
                .setPosition(66, 48)
                .setTextAlignment(HorizontalAlignment.CENTER)
                .setTextAlignment(VerticalAlignment.CENTER)
                .setColor(0xFF9AA7B0);
    }

    @Override
    public Identifier getIdentifier(DisplayRecipe recipe) {
        return recipe.id();
    }

    private static void addItem(
            IRecipeLayoutBuilder builder,
            int column,
            int row,
            ItemLike item
    ) {
        builder.addInputSlot(3 + column * 18, 3 + row * 18)
                .setStandardSlotBackground()
                .add(item);
    }

    private static final List<ItemStack> BEDS = BuiltInRegistries.ITEM.stream()
            .filter(item -> item instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof BedBlock)
            .map(ItemStack::new)
            .toList();

    public record DisplayRecipe(Identifier id) {
    }
}
