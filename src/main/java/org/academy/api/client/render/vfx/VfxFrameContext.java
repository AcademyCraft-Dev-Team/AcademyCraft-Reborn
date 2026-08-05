package org.academy.api.client.render.vfx;

public record VfxFrameContext(VfxCamera camera, float dt, float gameTime, float partialTick) {
}
