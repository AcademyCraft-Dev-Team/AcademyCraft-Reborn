package org.academy.api.client.gui.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.academy.api.client.gui.render.UIRenderContext;
import org.academy.api.client.gui.widget.WidgetContainer;
import org.jetbrains.annotations.Nullable;

public interface IUIScreen {
    @Nullable
    RenderTarget getRenderTarget();

    WidgetContainer getRootContainer();

    UIRenderContext getUIRenderContext();
}