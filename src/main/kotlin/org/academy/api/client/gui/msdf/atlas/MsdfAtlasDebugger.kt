package org.academy.api.client.gui.msdf.atlas

import com.mojang.blaze3d.platform.TextureUtil
import net.minecraft.client.Minecraft
import org.academy.AcademyCraft
import java.io.IOException
import java.nio.file.Files
import java.util.function.IntUnaryOperator

object MsdfAtlasDebugger {
    private val logger = AcademyCraft.getLogger()

    fun dumpAtlas(atlas: MsdfAtlas, fileNamePrefix: String?) {
        val pages = atlas.pages

        for (pageIndex in pages.indices) {
            val page = pages[pageIndex]
            val gameDirectory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath()
            val debugTexturePath = TextureUtil.getDebugTexturePath(gameDirectory)
            try {
                Files.createDirectories(debugTexturePath)
            } catch (e: IOException) {
                logger.error("Failed to create directory {}", debugTexturePath, e)
                return
            }

            TextureUtil.writeAsPNG(
                debugTexturePath,
                fileNamePrefix + pageIndex,
                page.texture,
                0,
                IntUnaryOperator.identity()
            )
        }
    }
}