package org.academy.api.client.gui.widget;

import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractSpriteSheetWidget extends ImageWidget {
    public final int sheetWidth;
    public final int sheetHeight;
    public final int frameWidth;
    public final int frameHeight;
    public final int frameCount;
    protected int currentFrame = 0;

    public AbstractSpriteSheetWidget(
            float x, float y, float width, float height,
            @NotNull RenderType renderType,
            int sheetWidth, int sheetHeight,
            int frameWidth, int frameHeight,
            int frameCount
    ) {
        super(x, y, width, height, renderType);
        this.sheetWidth = sheetWidth;
        this.sheetHeight = sheetHeight;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.frameCount = frameCount;

        setFrameIndex(0);
    }

    public void setFrameIndex(int index) {
        if (index < 0 || index >= frameCount) {
            throw new IllegalArgumentException("Frame index out of bounds: " + index);
        }
        this.currentFrame = index;
        computeUV(index);
    }

    public int getFrameIndex() {
        return currentFrame;
    }

    protected abstract void computeUV(int index);
}