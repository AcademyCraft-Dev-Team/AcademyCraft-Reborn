package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.render.MatrixStack;

public class LayeredPanelWidget extends PanelWidget {
    public LayeredPanelWidget(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    @Override
    public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
        if (!isVisible()) return;

        stack.pushPose();
        stack.translate(getX(), getY(), getZ() + 1);

        for (var child : getChildren().values()) {
            child.render(stack, bufferSource, mouseX, mouseY, partialTick);
        }

        stack.popPose();
    }
}