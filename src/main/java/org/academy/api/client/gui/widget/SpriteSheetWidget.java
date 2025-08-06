package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.RenderType;
import org.academy.api.client.gui.framework.Orientation;
import org.jetbrains.annotations.NotNull;

/**
 * A specialized ImageWidget that displays a single frame from a larger texture atlas (a sprite sheet).
 * It calculates the appropriate texture coordinates (UVs) based on a frame index and the sheet's layout.
 */
public class SpriteSheetWidget extends ImageWidget {
    protected final int sheetWidth;
    protected final int sheetHeight;
    protected final int frameWidth;
    protected final int frameHeight;
    protected final int frameCount;
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

        this.setFrameIndex(0);
    }

    public int getFrameIndex() {
        return this.currentFrame;
    }

    /**
     * Sets the currently displayed frame by its index.
     * @param index The zero-based index of the frame to display.
     * @throws IllegalArgumentException if the index is out of the valid range (0 to frameCount - 1).
     */
    public void setFrameIndex(int index) {
        if (index < 0 || index >= this.frameCount) {
            throw new IllegalArgumentException("Frame index " + index + " is out of bounds for frame count " + this.frameCount);
        }
        this.currentFrame = index;
        this.computeUV(index);
    }

    protected void computeUV(int index) {
        float newU0, newU1, newV0, newV1;

        if (this.orientation == Orientation.HORIZONTAL) {
            var x = index * this.frameWidth;
            newU0 = (float) x / this.sheetWidth;
            newU1 = (float) (x + this.frameWidth) / this.sheetWidth;
            newV0 = 0f;
            newV1 = (float) this.frameHeight / this.sheetHeight;
        } else {
            var y = index * this.frameHeight;
            newU0 = 0f;
            newU1 = (float) this.frameWidth / this.sheetWidth;
            newV0 = (float) y / this.sheetHeight;
            newV1 = (float) (y + this.frameHeight) / this.sheetHeight;
        }

        this.setTextureCoords(newU0, newV0, newU1, newV1);
    }
}