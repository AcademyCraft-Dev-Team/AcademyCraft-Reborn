package org.academy.api.client.gui.msdf.font;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.lwjgl.util.msdfgen.MSDFGenExt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MsdfFontService {
    public static final MsdfFontService INSTANCE = new MsdfFontService();

    public static final Identifier defaultFontId = AcademyCraft.academy("fonts/source-sans-3-regular.ttf");

    private final long library;
    public final ConcurrentHashMap<Identifier, MsdfFont> loadedFonts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Identifier, ByteBuffer> fontBuffers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Identifier> fontSearchOrder = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Integer, Identifier> charToFontCache = new ConcurrentHashMap<>();

    private MsdfFontService() {
        long lib;
        try (var stack = MemoryStack.stackPush()) {
            var pp = stack.mallocPointer(1);
            if (FreeType.FT_Init_FreeType(pp) != 0) throw new RuntimeException("Failed to initialize FreeType");
            lib = pp.get(0);
        }
        library = lib;
        MSDFGenExt.msdf_ft_set_load_callback(nameAddress ->
                FreeType.getLibrary().getFunctionAddress(MemoryUtil.memASCII(nameAddress))
        );
    }

    public static void setFontSearchOrder(java.util.List<Identifier> availableFonts) {
        INSTANCE.fontSearchOrder.clear();
        INSTANCE.fontSearchOrder.add(defaultFontId);
        for (var id : availableFonts) {
            if (!id.equals(defaultFontId)) INSTANCE.fontSearchOrder.add(id);
        }
        INSTANCE.charToFontCache.clear();
    }

    private MsdfFont resolveFontForChar(int c) {
        var fontId = charToFontCache.computeIfAbsent(c, this::findFontIdForChar);
        return getFont(fontId);
    }

    private Identifier findFontIdForChar(int c) {
        for (var id : fontSearchOrder) {
            var font = loadedFonts.get(id);
            if (font != null) {
                if (FreeType.FT_Get_Char_Index(font.face, c) != 0) return id;
            }
        }
        return defaultFontId;
    }

    public static MsdfFont getFont(Identifier identifier) {
        var font = INSTANCE.loadedFonts.get(identifier);
        return font != null ? font : loadFont(identifier);
    }

    public static MsdfFont loadFont(Identifier identifier) {
        var buffer = INSTANCE.fontBuffers.computeIfAbsent(identifier, MsdfFontService::loadResourceToBuffer);
        try (var stack = MemoryStack.stackPush()) {
            var pp = stack.mallocPointer(1);
            if (FreeType.FT_New_Memory_Face(INSTANCE.library, buffer, 0, pp) != 0) {
                throw new RuntimeException("Failed to load font face: " + identifier);
            }
            var font = new MsdfFont(identifier, FT_Face.create(pp.get(0)));
            INSTANCE.loadedFonts.put(identifier, font);
            return font;
        }
    }

    private static ByteBuffer loadResourceToBuffer(Identifier identifier) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(identifier);
            var optionalResource = resource.orElseThrow(() -> new IOException("Resource not found: " + identifier));
            try (var is = optionalResource.open()) {
                var bytes = is.readAllBytes();
                var buffer = MemoryUtil.memAlloc(bytes.length);
                buffer.put(bytes);
                buffer.flip();
                return buffer;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        loadedFonts.values().forEach(MsdfFont::close);
        loadedFonts.clear();
        fontBuffers.values().forEach(MemoryUtil::memFree);
        fontBuffers.clear();
        FreeType.FT_Done_FreeType(library);
    }

    public static MsdfFont getFont(int c) {
        return INSTANCE.resolveFontForChar(c);
    }

    public static boolean isFont(Identifier location) {
        var path = location.getPath();
        return path.endsWith(".ttf") || path.endsWith(".otf");
    }

    public static void genDefaultGlyph() {
        for (var c = '!'; c <= '~'; c++) {
            getFont(c).getGlyph(c);
        }
    }
}