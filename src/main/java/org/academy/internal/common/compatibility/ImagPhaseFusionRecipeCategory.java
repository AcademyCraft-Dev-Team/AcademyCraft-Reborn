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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.material.Fluids;
import org.academy.internal.common.world.level.material.ImagPhaseItemFusion;

public final class ImagPhaseFusionRecipeCategory
        extends AbstractRecipeCategory<ImagPhaseItemFusion.DisplayRecipe> {
    public static final IRecipeType<ImagPhaseItemFusion.DisplayRecipe> TYPE = IRecipeType.create(
            AcademyCraft.academy("imag_phase_fusion"),
            ImagPhaseItemFusion.DisplayRecipe.class
    );

    private final IDrawable arrow;

    public ImagPhaseFusionRecipeCategory(IGuiHelper guiHelper) {
        super(
                TYPE,
                Component.translatable("jei.academy.imag_phase_fusion"),
                guiHelper.createDrawableItemLike(Items.IMAG_PHASE_UNIT.get()),
                130,
                44
        );
        arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            ImagPhaseItemFusion.DisplayRecipe recipe,
            IFocusGroup focuses
    ) {
        builder.addInputSlot(3, 7)
                .setStandardSlotBackground()
                .add(recipe.input());
        builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 36, 7)
                .setStandardSlotBackground()
                .setFluidRenderer(1_000, true, 16, 16)
                .add(Fluids.IMAG_PHASE.get(), 1_000);
        builder.addOutputSlot(108, 7)
                .setOutputSlotBackground()
                .add(recipe.output());
    }

    @Override
    public void createRecipeExtras(
            IRecipeExtrasBuilder builder,
            ImagPhaseItemFusion.DisplayRecipe recipe,
            IFocusGroup focuses
    ) {
        builder.addDrawable(arrow, 73, 7);
        builder.addText(
                        Component.translatable(
                                "jei.academy.imag_phase_fusion.time",
                                recipe.intervalTicks()
                        ),
                        68,
                        11
                )
                .setPosition(55, 31)
                .setTextAlignment(HorizontalAlignment.CENTER)
                .setTextAlignment(VerticalAlignment.CENTER)
                .setColor(0xFF9AA7B0);
    }

    @Override
    public Identifier getIdentifier(ImagPhaseItemFusion.DisplayRecipe recipe) {
        return recipe.id();
    }
}
