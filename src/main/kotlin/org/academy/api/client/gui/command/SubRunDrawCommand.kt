package org.academy.api.client.gui.command

import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.gui.text.subrun.TextKind
import org.academy.api.client.render.MsdfUniformData
import org.academy.api.client.render.Render
import org.academy.api.client.render.UniformPayload
import org.joml.Vector4f

/**
 * 一整段文本（同一 pipeline、同一图集页）的多实例命令。
 *
 * - 位图段：`BITMAP_TEXT` 管线，无 uniform。
 * - MSDF 段：`MSDF_TEXT` 管线 + `MsdfUniforms`。
 *
 * 一段文本因此只提交一条 [TextDrawCommand]，而不是逐字形一条。
 */
class SubRunDrawCommand(
    kind: TextKind,
    textureView: GpuTextureView,
    quads: List<GlyphQuad>,
    range: Float,
    thickness: Float
) : TextDrawCommand(
    if (kind == TextKind.MSDF) Render.RenderPipelines.MSDF_TEXT else Render.RenderPipelines.BITMAP_TEXT,
    textureView,
    quads,
    if (kind == TextKind.MSDF) listOf(
        UniformPayload(
            "MsdfUniforms",
            MsdfUniformData::class.java,
            MsdfUniformData(range, thickness, 0.0f, Vector4f(0f)),
            MsdfUniformData.UBO_SIZE
        )
    ) else emptyList()
)
