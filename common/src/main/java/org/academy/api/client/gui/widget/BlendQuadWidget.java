package org.academy.api.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.animation.Animation;
import org.academy.api.client.resource.TextureResources;

public class BlendQuadWidget extends ImageWidget {
    public float marginTop = 2f;
    public float marginBottom = 2f;
    public float marginLeft = 2f;
    public float marginRight = 2f;
    public boolean drawLine = true;

    public BlendQuadWidget(float x, float y, float width, float height) {
        super(x, y, width, height, TextureResources.RenderTypes.RENDER_TYPE_BLEND_QUAD);
        this.centerScale = false;
        this.widthScale = 1f;
        this.heightScale = 1f;
    }

    @Override
    public void render(GuiGraphics guiGraphics, double mouseX, double mouseY, float partialTick) {
        if (animation != null) animation.beforeRender(guiGraphics, mouseX, mouseY, partialTick);
        Animation anim = animation;
        animation = null;
        if (!isVisible()) return;

        float origX = getX(), origY = getY();
        float origW = getWidth(), origH = getHeight();

        float x0 = origX, x1 = origX + origW;
        float y0 = origY, y1 = origY + origH;
        float[] xs = { x0, x0 + marginLeft, x1 - marginRight, x1 };
        float[] ys = { y0, y0 + marginTop, y1 - marginBottom, y1 };

        float savedU0 = this.u0, savedV0 = this.v0, savedU1 = this.u1, savedV1 = this.v1;
        RenderType savedType = this.renderType;
        float savedRed = this.red, savedGreen = this.green, savedBlue = this.blue, savedAlpha = this.alpha;

        float uvStep = 1f / 3f;
        for (int col = 0; col < 3; col++) {
            for (int row = 0; row < 3; row++) {
                this.u0 = col * uvStep;
                this.v0 = row * uvStep;
                this.u1 = this.u0 + uvStep;
                this.v1 = this.v0 + uvStep;

                setX(xs[col]);
                setY(ys[row]);
                setWidth(xs[col + 1] - xs[col]);
                setHeight(ys[row + 1] - ys[row]);

                super.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        this.renderType = TextureResources.RenderTypes.RENDER_TYPE_ELEMENT_LINE;
        this.u0 = 0f;
        this.v0 = 0f;
        this.u1 = 1f;
        this.v1 = 1f;
        this.red = 1f;
        this.green = 1f;
        this.blue = 1f;
        this.alpha = 1f;

        setX(origX + 0.2f);
        setY(origY - 2.5f);
        setWidth(origW - 0.4f);
        setHeight(5);
        if (drawLine) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        setY(origY + origH - 2.5f);
        setHeight(5);
        if (drawLine) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        this.u0 = savedU0;
        this.v0 = savedV0;
        this.u1 = savedU1;
        this.v1 = savedV1;
        this.renderType = savedType;
        this.red = savedRed;
        this.green = savedGreen;
        this.blue = savedBlue;
        this.alpha = savedAlpha;
        setX(origX);
        setY(origY);
        setWidth(origW);
        setHeight(origH);
        animation = anim;
        if (animation != null) animation.afterRender(guiGraphics, mouseX, mouseY, partialTick);
    }
}