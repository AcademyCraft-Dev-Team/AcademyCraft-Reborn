package org.academy.api.client.gui.msdf.font;

import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.msdf.atlas.MsdfAtlas;
import org.academy.api.client.gui.msdf.atlas.MsdfAtlasManager;
import org.academy.api.client.gui.msdf.atlas.MsdfGlyph;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

public class MsdfFont implements AutoCloseable {
    private final FontDescriptor descriptor;
    private final FT_Face face;
    private final MsdfAtlas atlas;
    private final MsdfFontMetrics metrics;

    public MsdfFont(Identifier identifier, FT_Face face) {
        descriptor = new FontDescriptor(identifier,FontStyle.of((int) face.style_flags()));
        this.face = face;
        atlas = MsdfAtlasManager.getInstance().getAtlas(identifier);
        metrics = new MsdfFontMetrics(
                face.units_per_EM(),
                face.ascender(),
                face.descender(),
                face.height()
        );
    }

    public @Nullable MsdfGlyph getGlyph(int character) {
        return atlas.getOrGenerate(face, character);
    }

    public FT_Face getFace() {
        return face;
    }

    public MsdfAtlas getAtlas() {
        return atlas;
    }

    public FontDescriptor getDescriptor() {
        return descriptor;
    }

    public MsdfFontMetrics getMetrics() {
        return metrics;
    }

    @Override
    public void close() {
        FreeType.FT_Done_Face(face);
    }
}