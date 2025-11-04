package org.academy.internal.common.compatibility;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.helpers.IPlatformFluidHelper;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.library.plugins.debug.FluidSubtypeHandlerTest;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.internal.client.gui.screen.SolarGenScreen;
import org.academy.internal.client.gui.screen.WindGenScreen;
import org.academy.internal.client.gui.screen.WirelessNodeScreen;
import org.academy.internal.common.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@JeiPlugin
public final class JEIPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return AcademyCraft.academy("jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(WindGenScreen.class, new IScreenHandler<>() {
            @Override
            public @Nullable IGuiProperties apply(WindGenScreen guiScreen) {
                return null;
            }
        });
        registration.addGuiScreenHandler(WirelessNodeScreen.class, new IScreenHandler<>() {
            @Override
            public @Nullable IGuiProperties apply(WirelessNodeScreen guiScreen) {
                return null;
            }
        });
        registration.addGuiScreenHandler(SolarGenScreen.class, new IScreenHandler<>() {
            @Override
            public @Nullable IGuiProperties apply(SolarGenScreen guiScreen) {
                return null;
            }
        });
    }

/*    @Override
    public <T> void registerFluidSubtypes(ISubtypeRegistration registration, IPlatformFluidHelper<T> platformFluidHelper) {
        var plasma = Fluids.IMAGIPHASE_PLASMA.get();
        var ingredientType = platformFluidHelper.getFluidIngredientType();
        var subtype = new FluidSubtypeHandlerTest<>(ingredientType);
        registration.registerSubtypeInterpreter(ingredientType, plasma, subtype);
    }*/
}