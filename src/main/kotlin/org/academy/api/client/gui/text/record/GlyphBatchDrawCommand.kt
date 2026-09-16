package org.academy.api.client.gui.text.record

import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.gui.text.model.GlyphKind
import org.academy.api.client.render.MsdfUniformData
import org.academy.api.client.render.Render
import org.academy.api.client.render.UniformPayload
import org.joml.Vector4f

class GlyphBatchDrawCommand(
    kind: GlyphKind,
    textureView: GpuTextureView,
    quads: List<GlyphQuad>,
    range: Float,
    thickness: Float
) : GlyphRunDrawCommand(
    if (kind == GlyphKind.MSDF) Render.RenderPipelines.MSDF_TEXT else Render.RenderPipelines.BITMAP_TEXT,
    textureView,
    quads,
    if (kind == GlyphKind.MSDF) listOf(
        UniformPayload(
            "MsdfUniforms",
            MsdfUniformData::class.java,
            MsdfUniformData(range, thickness, 0.0f, Vector4f(0f)),
            MsdfUniformData.UBO_SIZE
        )
    ) else emptyList()
)
