package org.academy.api.client.gui.msdf.font;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MsdfFontService implements AutoCloseable {
    private static final Identifier DEFAULT_FONT_ID = AcademyCraft.academy("fonts/source-sans-3-regular.ttf");

    private static final MsdfFontService INSTANCE = new MsdfFontService();
    private final long library;
    private final Map<Identifier, MsdfFont> loadedFonts = new ConcurrentHashMap<>();
    private final Map<Identifier, ByteBuffer> fontBuffers = new ConcurrentHashMap<>();

    private final List<Identifier> fontSearchOrder = new CopyOnWriteArrayList<>();
    private final Map<Integer, Identifier> charToFontCache = new ConcurrentHashMap<>();

    private MsdfFontService() {
        try (var stack = MemoryStack.stackPush()) {
            var pp = stack.mallocPointer(1);
            if (FreeType.FT_Init_FreeType(pp) != 0) throw new RuntimeException("Failed to initialize FreeType");
            library = pp.get(0);
        }
    }

    public static MsdfFontService getInstance() {
        return INSTANCE;
    }

    public static MsdfFont getDefaultFont() {
        return getInstance().getFont(DEFAULT_FONT_ID);
    }

    public static MsdfFont getFont(int c) {
        return getInstance().resolveFontForChar(c);
    }

    public static boolean isFont(Identifier location) {
        var path = location.getPath();
        return path.endsWith(".ttf") || path.endsWith(".otf");
    }

    public static void genDefaultGlyph() {
        var font = getDefaultFont();
        var atlas = font.getAtlas();
        var face = font.getFace();
        for (var c = '!'; c <= '~'; c++) atlas.getOrGenerate(face, c);
    }

    public void setFontSearchOrder(List<Identifier> availableFonts) {
        fontSearchOrder.clear();
        fontSearchOrder.add(DEFAULT_FONT_ID);
        for (var id : availableFonts) if (!id.equals(DEFAULT_FONT_ID)) fontSearchOrder.add(id);
        charToFontCache.clear();
    }

    private MsdfFont resolveFontForChar(int c) {
        var fontId = charToFontCache.computeIfAbsent(c, this::findFontIdForChar);
        return getFont(fontId);
    }

    private Identifier findFontIdForChar(int c) {
        for (var id : fontSearchOrder) {
            var font = loadedFonts.get(id);
            if (font != null) if (FreeType.FT_Get_Char_Index(font.getFace(), c) != 0) return id;
        }
        return DEFAULT_FONT_ID;
    }

    public MsdfFont getFont(Identifier identifier) {
        if (!loadedFonts.containsKey(identifier)) loadFont(identifier);
        return loadedFonts.get(identifier);
    }

    public void loadFont(Identifier identifier) {
        var buffer = fontBuffers.computeIfAbsent(identifier, this::loadResourceToBuffer);
        try (var stack = MemoryStack.stackPush()) {
            var pp = stack.mallocPointer(1);
            if (FreeType.FT_New_Memory_Face(library, buffer, 0, pp) != 0) {
                throw new RuntimeException("Failed to load font face: " + identifier);
            }
            loadedFonts.put(identifier, new MsdfFont(identifier, FT_Face.create(pp.get(0))));
        }
    }

    public Map<Identifier, MsdfFont> getLoadedFonts() {
        return loadedFonts;
    }

    private ByteBuffer loadResourceToBuffer(Identifier identifier) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(identifier);
            if (resource.isEmpty()) throw new IOException("Resource not found: " + identifier);
            try (var is = resource.get().open()) {
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

    @Override
    public void close() {
        loadedFonts.values().forEach(MsdfFont::close);
        loadedFonts.clear();
        fontBuffers.values().forEach(MemoryUtil::memFree);
        fontBuffers.clear();
        FreeType.FT_Done_FreeType(library);
    }
}