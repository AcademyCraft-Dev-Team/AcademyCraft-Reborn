package org.academy.internal.client.gui.world;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.widget.PanelWidget;
import org.academy.api.client.render.MatrixStack;
import org.jetbrains.annotations.NotNull;

public class WindGenWorldGUI {
    public static final float WIDTH = 640;
    public static final float HEIGHT = 400;
    public final AbstractContainerWidget rootContainer = new PanelWidget(0, 0, WIDTH, HEIGHT);
    public double mouseX, mouseY;

    public void render(@NotNull MatrixStack stack, @NotNull MultiBufferSource bufferSource, float partialTicks) {
    }

    public void onInit() {
    }
}