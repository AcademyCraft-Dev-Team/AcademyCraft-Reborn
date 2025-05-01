package org.academy.api.client.gui.widget;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.framework.AbstractWidget;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class ImageWidget extends AbstractWidget {
    public float u0 = 0;
    public float v0 = 0;
    public float u1 = 1;
    public float v1 = 1;
    public float red = 1f;
    public float green = 1f;
    public float blue = 1f;
    public float alpha = 1f;
    public RenderType renderType;
    public float widthScale = 1.0f;
    public float heightScale = 1.0f;
    public boolean centerScale = true;

    public ImageWidget(float x, float y, float width, float height, @NotNull RenderType renderType) {
        super(x, y, width, height);
        this.renderType = renderType;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (animation != null) {
            animation.beforeRender(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (!isVisible()) return;
        VertexConsumer vertexConsumer = guiGraphics.bufferSource().getBuffer(renderType);

        guiGraphics.pose().pushPose();
        Matrix4f matrix4f = guiGraphics.pose().last().pose();

        float scaledWidth = getWidth() * widthScale;
        float scaledHeight = getHeight() * heightScale;

        matrix4f.translate(getX(), getY(), getZ());
        if (centerScale) {
            matrix4f.translate((getWidth() - scaledWidth) / 2f, (getHeight() - scaledHeight) / 2f, 0);
        }

        matrix4f.scale(scaledWidth, scaledHeight, 1);

        vertexConsumer.vertex(matrix4f, 0, 0, 0).color(red, green, blue, alpha).uv(u0, v0).endVertex();
        vertexConsumer.vertex(matrix4f, 0, 1, 0).color(red, green, blue, alpha).uv(u0, v1).endVertex();
        vertexConsumer.vertex(matrix4f, 1, 1, 0).color(red, green, blue, alpha).uv(u1, v1).endVertex();
        vertexConsumer.vertex(matrix4f, 1, 0, 0).color(red, green, blue, alpha).uv(u1, v0).endVertex();

        guiGraphics.pose().popPose();
        if (animation != null) {
            animation.afterRender(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
}