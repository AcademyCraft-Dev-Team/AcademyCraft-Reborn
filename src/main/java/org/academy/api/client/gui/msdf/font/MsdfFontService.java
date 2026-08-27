package org.academy.api.client.gui.msdf.font;

import net.minecraft.resources.Identifier;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.environment.UiEnvironment;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MsdfFontService {
    public static final MsdfFontService INSTANCE = new MsdfFontService();

    public static final Identifier DEFAULT_FONT_ID = AcademyCraft.academy(
            "fonts/source-sans-3-regular.otf"
    );

    private static final ExecutorService GLYPH_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
            r -> {
                var t = new Thread(r, "AcademyCraft-MSDF-Generator");
                t.setDaemon(true);
                return t;
            }
    );
    public final ConcurrentHashMap<Identifier, MsdfFont> loadedFonts = new ConcurrentHashMap<>();
    private final long library;
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
    }

    public static void setFontSearchOrder(List<Identifier> availableFonts) {
        INSTANCE.fontSearchOrder.clear();
        INSTANCE.fontSearchOrder.add(DEFAULT_FONT_ID);
        for (var id : availableFonts) {
            if (!id.equals(DEFAULT_FONT_ID)) INSTANCE.fontSearchOrder.add(id);
        }
        INSTANCE.charToFontCache.clear();
    }

    public static MsdfFont getFont(Identifier identifier) {
        var font = INSTANCE.loadedFonts.get(identifier);
        return font != null ? font : loadFont(identifier);
    }

    public static MsdfFont loadFont(Identifier identifier) {
        var buffer = INSTANCE.fontBuffers.computeIfAbsent(identifier, MsdfFontService::loadResourceToBuffer);
        try (var stack = MemoryStack.stackPush()) {
            var pp = stack.mallocPointer(1);
            if (FreeType.FT_New_Memory_Face(INSTANCE.library, buffer, 0, pp) != FreeType.FT_Err_Ok) {
                throw new RuntimeException("Failed to load font face: " + identifier);
            }
            var font = new MsdfFont(identifier, FT_Face.create(pp.get(0)), GLYPH_EXECUTOR);
            INSTANCE.loadedFonts.put(identifier, font);
            return font;
        }
    }

    private static ByteBuffer loadResourceToBuffer(Identifier identifier) {
        try {
            var is = UiEnvironment.get()
                    .openResource(identifier.getNamespace(), identifier.getPath());
            if (is == null) throw new IOException("Resource not found: " + identifier);
            try (is) {
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

    public static MsdfFont getFont(int c) {
        return INSTANCE.resolveFontForChar(c);
    }

    public static boolean isFont(Identifier location) {
        var path = location.getPath();
        return path.endsWith(".ttf") || path.endsWith(".otf") || path.endsWith(".ttc");
    }

    public static void genDefaultGlyph() {
        for (var c = ' '; c <= '~'; c++) {
            getFont(c).getGlyph(c);
        }
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
        return DEFAULT_FONT_ID;
    }

    public void close() {
        loadedFonts.values().forEach(MsdfFont::close);
        loadedFonts.clear();
        fontBuffers.values().forEach(MemoryUtil::memFree);
        fontBuffers.clear();
        FreeType.FT_Done_FreeType(library);
    }
}
