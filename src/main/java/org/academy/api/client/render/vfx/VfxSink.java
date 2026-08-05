package org.academy.api.client.render.vfx;

public interface VfxSink {
    <T extends VfxRenderData> void push(T data);
}
