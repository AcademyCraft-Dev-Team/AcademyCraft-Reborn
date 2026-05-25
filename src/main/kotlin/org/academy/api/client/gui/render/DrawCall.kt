package org.academy.api.client.gui.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformBinding

data class DrawCall(
    val pipeline: RenderPipeline,
    val scissorArea: ScissorRect?,
    val textures: List<TextureBinding>,
    val uniforms: List<UniformBinding>,
    val baseVertex: Int,
    val indexCount: Int
) 