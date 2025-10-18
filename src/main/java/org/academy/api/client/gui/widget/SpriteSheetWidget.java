package org.academy.api.client.gui.widget;

import net.minecraft.resources.ResourceLocation;
import org.academy.api.client.gui.framework.Orientation;

public class SpriteSheetWidget extends ImageWidget {
    protected final int sheetWidth;
    protected final int sheetHeight;
    protected final int frameWidth;
    protected final int frameHeight;
    protected final int frameCount;
    protected int currentFrame = 0;
    protected final Orientation orientation;

    public SpriteSheetWidget(
            float x,
            float y,
            float width,
            float height,
            ResourceLocation texture,
            Orientation orientation,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int frameCount
    ) {
        super(x, y, width, height, texture);
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

    public void setFrameIndex(int index) {
        if (index < 0 || index >= frameCount) {
            throw new IllegalArgumentException("Frame index " + index + " is out of bounds for frame count " + frameCount);
        }
        currentFrame = index;
        computeUV(index);
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

        setUv(newU0, newV0, newU1, newV1);
    }
}