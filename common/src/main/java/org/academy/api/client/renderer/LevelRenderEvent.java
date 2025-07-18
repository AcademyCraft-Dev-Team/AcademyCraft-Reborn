package org.academy.api.client.renderer;

import net.neoforged.bus.api.Event;
import org.academy.api.client.render.MatrixStack;

public class LevelRenderEvent extends Event {
    /**
     * 坐标系原点是相机位置, 而非世界原点
     */
    private final MatrixStack matrixStack;
    private final float partialTick;

    public LevelRenderEvent(float partialTick, MatrixStack matrixStack) {
        this.matrixStack = matrixStack;
        this.partialTick = partialTick;
    }

    public MatrixStack getMatrixStack() {
        return matrixStack;
    }

    public float getPartialTick() {
        return partialTick;
    }
}