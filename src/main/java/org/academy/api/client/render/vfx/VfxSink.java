package org.academy.api.client.render.vfx;

/**
 * @deprecated graph contexts and blocks replace immediate per-frame render-data submission
 */
@Deprecated(since = "0.0.4")
public interface VfxSink {
    <T extends VfxRenderData> void push(T data);
}
