package org.academy.api.client.gui.msdf.atlas;

import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.msdf.Constants;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class MsdfAtlasManager {
    private static final ConcurrentHashMap<Identifier, MsdfAtlas> atlases = new ConcurrentHashMap<>();

    private MsdfAtlasManager() {
    }

    public static MsdfAtlas getAtlas(Identifier descriptor, Executor executor) {
        return atlases.computeIfAbsent(descriptor, _ -> new MsdfAtlas(
                Constants.DEFAULT_ATLAS_SIZE,
                Constants.DEFAULT_GLYPH_SIZE,
                Constants.DEFAULT_PX_RANGE,
                executor
        ));
    }

    public static void closeAll() {
        atlases.values().forEach(MsdfAtlas::close);
        atlases.clear();
    }
}
