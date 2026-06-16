package org.academy.api.client.gui.msdf.atlas;

public record MsdfGlyph(AtlasPage page, float u0, float v0, float u1, float v1, int advance,
                        float planeLeft, float planeBottom, float planeRight, float planeTop) {
}
