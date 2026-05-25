package org.academy.internal.common.compatibility;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.internal.client.gui.screen.SolarGenScreen;
import org.academy.internal.client.gui.screen.WindGenScreen;
import org.academy.internal.client.gui.screen.WirelessNodeScreen;

import java.util.List;

@JeiPlugin
public final class JEIPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return AcademyCraft.academy("jei_plugin");
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
    }
}
