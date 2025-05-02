package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.NotNull;

public class HorizontalSpriteWidget extends AbstractSpriteSheetWidget {
    public HorizontalSpriteWidget(
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
        int x = index * frameWidth;
        float u0 = (float) x / sheetWidth;
        float u1 = (float) (x + frameWidth) / sheetWidth;
        float v0 = 0f;
        float v1 = (float) frameHeight / sheetHeight;

        this.u0 = u0;
        this.u1 = u1;
        this.v0 = v0;
        this.v1 = v1;
    }
}
