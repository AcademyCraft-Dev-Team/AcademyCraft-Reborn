package org.academy.api.client.gui.glyph.bitmap

import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import org.academy.AcademyCraft
import org.academy.api.client.gui.glyph.AtlasPage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * 开发辅助：把 R8 位图图集页导出为 PNG。
 *
 * [com.mojang.blaze3d.platform.TextureUtil.writeAsPNG] 只支持 RGBA8_UNORM，位图图集是
 * R8_UNORM，因此这里用同一套 copy→map 回读方式自实现单通道导出（灰度复制到 RGBA）。
 */
object BitmapAtlasDebugger {
    private val logger = AcademyCraft.getLogger()

    /** @return 成功写入的文件路径列表。 */
    fun dumpAtlas(atlas: BitmapAtlas, outputDir: Path, fileNamePrefix: String): List<Path> {
        RenderSystem.assertOnRenderThread()
        val pages = atlas.getPages()
        val written = ArrayList<Path>(pages.size)
        for (pageIndex in pages.indices) {
            written.add(dumpPage(pages[pageIndex], pageIndex, outputDir, fileNamePrefix))
        }
        return written
    }

    private fun dumpPage(page: AtlasPage, pageIndex: Int, outputDir: Path, fileNamePrefix: String): Path {
        val size = page.size
        val bufferLength = size.toLong() * size.toLong()
        require(bufferLength <= Int.MAX_VALUE) { "Bitmap atlas page is too large to export: $bufferLength bytes" }

        val file = outputDir.resolve(fileNamePrefix + pageIndex + ".png")
        val buffer = RenderSystem.getDevice().createBuffer(
            { "Bitmap atlas output buffer" },
            GpuBuffer.USAGE_COPY_DST or GpuBuffer.USAGE_MAP_READ,
            bufferLength
        )
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.copyTextureToBuffer(page.texture, buffer, 0, {
            try {
                Files.createDirectories(outputDir)
                buffer.map(true, false).use { view ->
                    val data = view.data()
                    NativeImage(NativeImage.Format.RGBA, size, size, false).use { image ->
                        for (y in 0 until size) {
                            for (x in 0 until size) {
                                val gray = data.get(y * size + x).toInt() and 0xFF
                                image.setPixelABGR(
                                    x, y,
                                    (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                                )
                            }
                        }
                        image.writeToFile(file)
                    }
                }
            } catch (e: IOException) {
                logger.error("Unable to write bitmap atlas page {}", pageIndex, e)
            } finally {
                buffer.close()
            }
        }, 0)
        return file
    }
}
