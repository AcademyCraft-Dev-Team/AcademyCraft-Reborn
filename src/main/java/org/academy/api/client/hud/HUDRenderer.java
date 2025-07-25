package org.academy.api.client.hud;

import net.minecraft.client.renderer.MultiBufferSource;
import org.academy.api.client.render.MatrixStack;

public interface HUDRenderer {
    void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, float partialTick);
}