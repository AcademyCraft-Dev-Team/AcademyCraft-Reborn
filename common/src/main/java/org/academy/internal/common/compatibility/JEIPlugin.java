package org.academy.internal.common.compatibility;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import org.academy.AcademyCraft;
import org.academy.internal.client.gui.screen.WindGenScreen;
import org.academy.internal.client.gui.screen.WirelessNodeScreen;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@JeiPlugin
public class JEIPlugin implements IModPlugin {
    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return new ResourceLocation(AcademyCraft.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(@NotNull IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(WindGenScreen.class, new IGuiContainerHandler<>() {
            @Override
            public @NotNull List<Rect2i> getGuiExtraAreas(@NotNull WindGenScreen containerScreen) {
                return List.of(new Rect2i(containerScreen.getLeftPos() + containerScreen.getImageWidth() + 3, containerScreen.getTopPos() - 22, 110, 105));
            }
        });
        registration.addGuiContainerHandler(WirelessNodeScreen.class, new IGuiContainerHandler<>() {
            @Override
            public @NotNull List<Rect2i> getGuiExtraAreas(@NotNull WirelessNodeScreen containerScreen) {
                return List.of(new Rect2i(containerScreen.getLeftPos() + containerScreen.getImageWidth() + 3, containerScreen.getTopPos() - 22, 110, 140));
            }
        });
    }
}