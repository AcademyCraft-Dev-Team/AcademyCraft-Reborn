package org.academy.internal.common.compatibility;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.client.gui.screen.OmniCraftingTableScreen;
import org.academy.internal.client.gui.screen.SolarGenScreen;
import org.academy.internal.client.gui.screen.WindGenScreen;
import org.academy.internal.client.gui.screen.WirelessNodeScreen;
import org.academy.internal.common.world.item.Items;
import org.academy.internal.common.world.level.material.ImagPhaseItemFusion;

import java.util.List;

@JeiPlugin
public final class JEIPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return AcademyCraft.academy("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new ImagPhaseFusionRecipeCategory(guiHelper),
                new OmniCraftingRecipeCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
                ImagPhaseFusionRecipeCategory.TYPE,
                ImagPhaseItemFusion.displayRecipes()
        );
        registration.addRecipes(
                OmniCraftingRecipeCategory.TYPE,
                List.of(OmniCraftingRecipeCategory.ABILITY_DEVELOPER)
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(
                OmniCraftingRecipeCategory.TYPE,
                Items.OMNI_CRAFTING_TABLE.get()
        );
        registration.addCraftingStation(
                ImagPhaseFusionRecipeCategory.TYPE,
                Items.IMAG_PHASE_UNIT.get()
        );
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(WindGenScreen.class,
                new IGuiContainerHandler<WindGenScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(WindGenScreen containerScreen) {
                        return List.of(new Rect2i(0, 0, containerScreen.width, containerScreen.height));
                    }
                }
        );
        registration.addGenericGuiContainerHandler(WirelessNodeScreen.class,
                new IGuiContainerHandler<WirelessNodeScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(WirelessNodeScreen containerScreen) {
                        return List.of(new Rect2i(0, 0, containerScreen.width, containerScreen.height));
                    }
                }
        );
        registration.addGenericGuiContainerHandler(SolarGenScreen.class,
                new IGuiContainerHandler<SolarGenScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(SolarGenScreen containerScreen) {
                        return List.of(new Rect2i(0, 0, containerScreen.width, containerScreen.height));
                    }
                }
        );
        registration.addGenericGuiContainerHandler(OmniCraftingTableScreen.class,
                new IGuiContainerHandler<OmniCraftingTableScreen>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(OmniCraftingTableScreen containerScreen) {
                        return List.of(new Rect2i(0, 0, containerScreen.width, containerScreen.height));
                    }
                }
        );
    }
}
