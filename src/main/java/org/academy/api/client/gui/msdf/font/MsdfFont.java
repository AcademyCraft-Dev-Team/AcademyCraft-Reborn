package org.academy.api.client.gui.msdf.font;

import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.msdf.atlas.MsdfAtlas;
import org.academy.api.client.gui.msdf.atlas.MsdfAtlasManager;
import org.academy.api.client.gui.msdf.atlas.MsdfGlyph;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.lwjgl.util.msdfgen.MSDFGen;
import org.lwjgl.util.msdfgen.MSDFGenExt;

public class MsdfFont {
    public final FontDescriptor descriptor;
    public final FT_Face face;
    public final MsdfAtlas atlas;
    public final long msdfFontHandle;
    public final MsdfFontMetrics metrics;

    public MsdfFont(Identifier identifier, FT_Face face) {
        this.face = face;
        descriptor = new FontDescriptor(
                identifier,
                FontStyle.of((int) face.style_flags())
        );
        atlas = MsdfAtlasManager.getAtlas(identifier);
        metrics = new MsdfFontMetrics(
                face.units_per_EM(),
                face.ascender(),
                face.descender(),
                face.height()
        );

        long handle;
        try (var stack = MemoryStack.stackPush()) {
            var pHandle = stack.callocPointer(1);
            if (MSDFGenExt.msdf_ft_adopt_font(face.address(), pHandle) != MSDFGen.MSDF_SUCCESS) {
                throw new RuntimeException("Failed to adopt FT_Face for msdfgen");
            }
            handle = pHandle.get();
        }
        msdfFontHandle = handle;
    }

    public @Nullable MsdfGlyph getGlyph(int character) {
        return atlas.getOrGenerate(face, msdfFontHandle, character);
    }

    public void close() {
        MSDFGenExt.msdf_ft_font_destroy(msdfFontHandle);
        FreeType.FT_Done_Face(face);
    }
}
