package org.academy.internal.client.render.vfx;

import org.academy.api.client.render.vfx.VfxRenderData;

import java.nio.ByteBuffer;

public record WingData(WingKind kind, Layer layer, ByteBuffer instances) implements VfxRenderData {
    public enum Layer {
        STABLE,
        ASCENSION
    }
}
