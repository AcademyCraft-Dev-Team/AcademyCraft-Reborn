package org.academy.api.client.gui.msdf.atlas;

import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.msdf.core.MsdfConstants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MsdfAtlasManager {
    private static final MsdfAtlasManager INSTANCE = new MsdfAtlasManager();
    private final Map<Identifier, MsdfAtlas> atlases = new ConcurrentHashMap<>();

    private MsdfAtlasManager() {
    }

    public static MsdfAtlasManager getInstance() {
        return INSTANCE;
    }

    public MsdfAtlas getAtlas(Identifier descriptor) {
        return atlases.computeIfAbsent(descriptor, _ -> new MsdfAtlas(MsdfConstants.DEFAULT_ATLAS_SIZE, MsdfConstants.DEFAULT_GLYPH_SIZE, MsdfConstants.DEFAULT_PX_RANGE));
    }

    public void closeAll() {
        atlases.values().forEach(MsdfAtlas::close);
        atlases.clear();
    }
}