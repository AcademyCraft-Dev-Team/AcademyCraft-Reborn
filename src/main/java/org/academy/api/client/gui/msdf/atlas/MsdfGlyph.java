package org.academy.api.client.gui.msdf.atlas;

import org.jspecify.annotations.Nullable;

public record MsdfGlyph(
        @Nullable AtlasPage page,
        float u0,
        float v0,
        float u1,
        float v1,
        long advance,
        long bearingX,
        long bearingY,
        double planeLeft,
        double planeBottom,
        double planeRight,
        double planeTop
) {
}