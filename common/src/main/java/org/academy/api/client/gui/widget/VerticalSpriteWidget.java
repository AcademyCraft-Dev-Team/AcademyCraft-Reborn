package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.NotNull;

public class VerticalSpriteWidget extends AbstractSpriteSheetWidget {
    public VerticalSpriteWidget(
            float x, float y, float width, float height,
            @NotNull RenderType renderType,
            int sheetWidth, int sheetHeight,
            int frameWidth, int frameHeight,
            int frameCount
    ) {
        super(x, y, width, height, renderType,
                sheetWidth, sheetHeight, frameWidth, frameHeight, frameCount);
    }

    @Override
    protected void computeUV(int index) {
        int y = index * frameHeight;
        float u0 = 0f;
        float u1 = (float) frameWidth / sheetWidth;
        float v0 = (float) y / sheetHeight;
        float v1 = (float) (y + frameHeight) / sheetHeight;

        this.u0 = u0;
        this.u1 = u1;
        this.v0 = v0;
        this.v1 = v1;
    }
}