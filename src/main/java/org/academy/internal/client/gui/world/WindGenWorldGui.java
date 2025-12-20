package org.academy.internal.client.gui.world;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.widget.AbstractWidgetContainer;
import org.academy.api.client.gui.widget.FrameLayoutWidget;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.NotNull;

public class WindGenWorldGui {
    public static final float WIDTH = 640;
    public static final float HEIGHT = 400;
    public final AbstractWidgetContainer rootContainer = new FrameLayoutWidget();
    public double mouseX, mouseY;

    public void render(@NotNull MatrixStack stack, @NotNull MultiBufferSource bufferSource, float partialTicks) {
    }

    public void onInit() {
    }
}