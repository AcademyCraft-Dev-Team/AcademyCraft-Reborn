package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.framework.Orientation;
import org.jetbrains.annotations.NotNull;

public class SpriteSheetWidget extends ImageWidget {
    public final int sheetWidth;
    public final int sheetHeight;
    public final int frameWidth;
    public final int frameHeight;
    public final int frameCount;
    protected int currentFrame = 0;
    protected final Orientation orientation;

    public SpriteSheetWidget(
            float x, float y, float width, float height,
            @NotNull RenderType renderType,
            @NotNull Orientation orientation,
            int sheetWidth, int sheetHeight,
            int frameWidth, int frameHeight,
            int frameCount
    ) {
        super(x, y, width, height, renderType);
        this.orientation = orientation;
        this.sheetWidth = sheetWidth;
        this.sheetHeight = sheetHeight;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frameCount = frameCount;

        setFrameIndex(0);
    }

    public int getFrameIndex() {
        return currentFrame;
    }

    public void setFrameIndex(int newIndex) {
        if (newIndex < 0 || newIndex >= frameCount) {
            throw new IllegalArgumentException("Frame index out of bounds: " + newIndex);
        }
        currentFrame = newIndex;
        computeUV(newIndex);
    }

    protected void computeUV(int index) {
        float newU0, newU1, newV0, newV1;

        if (orientation == Orientation.HORIZONTAL) {
            var x = index * frameWidth;
            newU0 = (float) x / sheetWidth;
            newU1 = (float) (x + frameWidth) / sheetWidth;
            newV0 = 0f;
            newV1 = (float) frameHeight / sheetHeight;
        } else {
            var y = index * frameHeight;
            newU0 = 0f;
            newU1 = (float) frameWidth / sheetWidth;
            newV0 = (float) y / sheetHeight;
            newV1 = (float) (y + frameHeight) / sheetHeight;
        }

        u0 = newU0;
        u1 = newU1;
        v0 = newV0;
        v1 = newV1;
    }
}