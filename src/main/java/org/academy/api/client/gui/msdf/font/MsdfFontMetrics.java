package org.academy.api.client.gui.msdf.font;

public record MsdfFontMetrics(
        short unitsPerEm,
        short ascender,
        short descender,
        short lineHeight
) {
}