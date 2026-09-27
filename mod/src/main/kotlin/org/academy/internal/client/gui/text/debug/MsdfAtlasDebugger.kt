package org.academy.internal.client.gui.text.debug

import com.mojang.blaze3d.platform.TextureUtil
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import org.academy.AcademyCraft
import org.academy.api.client.gui.text.glyph.msdf.MsdfAtlas
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.IntUnaryOperator

object MsdfAtlasDebugger {
    private val logger = AcademyCraft.getLogger()

    fun dumpAtlas(atlas: MsdfAtlas, outputDir: Path, fileNamePrefix: String): List<Path> {
        RenderSystem.assertOnRenderThread()
        val pages = atlas.getPages()
        val written = ArrayList<Path>(pages.size)
        try {
            Files.createDirectories(outputDir)
        } catch (e: IOException) {
            logger.error("Failed to create directory {}", outputDir, e)
            return written
        }

        for (pageIndex in pages.indices) {
            val prefix = fileNamePrefix + pageIndex
            TextureUtil.writeAsPNG(outputDir, prefix, pages[pageIndex].texture, 0, IntUnaryOperator.identity())
            written.add(outputDir.resolve(prefix + "_0.png"))
        }
        return written
    }

    fun dumpAtlas(atlas: MsdfAtlas, fileNamePrefix: String) {
        val gameDirectory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath()
        dumpAtlas(atlas, TextureUtil.getDebugTexturePath(gameDirectory), fileNamePrefix)
    }
}
