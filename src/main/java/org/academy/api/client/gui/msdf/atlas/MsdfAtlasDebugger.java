package org.academy.api.client.gui.msdf.atlas;

import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.Minecraft;
import org.academy.AcademyCraft;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.util.function.IntUnaryOperator;

public final class MsdfAtlasDebugger {
    private MsdfAtlasDebugger() {
    }

    private static final Logger logger = AcademyCraft.getLogger();

    public static void dumpAtlas(MsdfAtlas atlas, String fileNamePrefix) {
        var pages = atlas.getPages();

        for (var pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            var page = pages.get(pageIndex);
            var gameDirectory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath();
            var debugTexturePath = TextureUtil.getDebugTexturePath(gameDirectory);
            try {
                Files.createDirectories(debugTexturePath);
            } catch (IOException e) {
                logger.error("Failed to create directory {}", debugTexturePath, e);
                return;
            }

            TextureUtil.writeAsPNG(
                    debugTexturePath,
                    fileNamePrefix + pageIndex,
                    page.texture,
                    0,
                    IntUnaryOperator.identity()
            );
        }
    }
}
