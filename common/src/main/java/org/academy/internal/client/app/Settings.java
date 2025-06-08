package org.academy.internal.client.app;

import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.widget.BlendQuadWidget;
import org.academy.api.client.gui.widget.PanelWidget;
import org.academy.api.client.hud.DataTerminalHUD;
import org.academy.api.client.resource.TextureResources;

public final class Settings implements DataTerminalHUD.App {
    public static final DataTerminalHUD.App INSTANCE = new Settings();
    private static final Runnable ON_CLICK = () -> DataTerminalHUD.setAppArea(
            new PanelWidget(0, 0, 200, 260) {
                {
                    BlendQuadWidget back = new BlendQuadWidget(0, 0, 200, 260);
                    back.drawLine = false;
                    back.alpha = 0.075f;
                    addChild("back", back);
                }
            }
    );

    private Settings() {
    }

    @Override
    public RenderType getIcon() {
        return TextureResources.RenderTypes.RENDER_TYPE_APP_SETTINGS;
    }

    @Override
    public String getName() {
        return "Settings";
    }

    @Override
    public Runnable onClick() {
        return ON_CLICK;
    }
}