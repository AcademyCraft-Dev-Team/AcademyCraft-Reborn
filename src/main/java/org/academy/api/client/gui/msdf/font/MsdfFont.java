package org.academy.api.client.gui.msdf.font;

import lovely.cane.jmsdfgen.ImportFont;
import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.msdf.atlas.MsdfAtlas;
import org.academy.api.client.gui.msdf.atlas.MsdfAtlasManager;
import org.academy.api.client.gui.msdf.atlas.MsdfGlyph;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.freetype.FT_Face;

import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static org.lwjgl.util.freetype.FreeType.FT_Done_Face;
import static org.lwjgl.util.freetype.FreeType.FT_Get_Char_Index;

public final class MsdfFont {
    public final FontDescriptor descriptor;
    public final FT_Face face;
    public final MsdfAtlas atlas;
    public final ImportFont.FontHandle fontHandle;
    public final MsdfFontMetrics metrics;
    private final ReentrantLock faceLock = new ReentrantLock();

    public MsdfFont(Identifier identifier, FT_Face face, Executor executor) {
        this.face = face;
        descriptor = new FontDescriptor(
                identifier,
                FontStyle.of((int) face.style_flags())
        );
        atlas = MsdfAtlasManager.getAtlas(identifier, executor);
        metrics = new MsdfFontMetrics(
                face.units_per_EM(),
                face.ascender(),
                face.descender(),
                face.height()
        );

        fontHandle = ImportFont.adoptFreetypeFont(face);
    }

    public @Nullable MsdfGlyph getGlyph(int character) {
        return atlas.getOrGenerate(face, faceLock, fontHandle, character);
    }

    public boolean hasGlyph(int character) {
        faceLock.lock();
        try {
            return FT_Get_Char_Index(face, character) != 0;
        } finally {
            faceLock.unlock();
        }
    }

    public long getKerning(long left, long right) {
        faceLock.lock();
        try {
            return MsdfKerningManager.getKerning(face, left, right);
        } finally {
            faceLock.unlock();
        }
    }

    public void close() {
        faceLock.lock();
        try {
            FT_Done_Face(face);
        } finally {
            faceLock.unlock();
        }
    }
}
