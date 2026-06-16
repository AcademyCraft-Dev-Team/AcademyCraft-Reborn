package org.academy.api.client.gui.msdf.atlas;

import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.msdf.Constants;

import java.util.concurrent.ConcurrentHashMap;

public final class MsdfAtlasManager {
    private MsdfAtlasManager() {
    }

    private static final ConcurrentHashMap<Identifier, MsdfAtlas> atlases = new ConcurrentHashMap<>();

    public static MsdfAtlas getAtlas(Identifier descriptor) {
        return atlases.computeIfAbsent(descriptor, _ -> new MsdfAtlas(
                Constants.DEFAULT_ATLAS_SIZE,
                Constants.DEFAULT_GLYPH_SIZE,
                Constants.DEFAULT_PX_RANGE
        ));
    }

    public static void closeAll() {
        atlases.values().forEach(MsdfAtlas::close);
        atlases.clear();
    }
}
