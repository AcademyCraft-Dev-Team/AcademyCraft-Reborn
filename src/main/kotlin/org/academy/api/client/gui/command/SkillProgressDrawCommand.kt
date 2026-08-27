package org.academy.api.client.gui.command

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.render.Render
import org.academy.api.client.render.SkillProgressUniforms
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload

class SkillProgressDrawCommand : PosTexColorRectDrawCommand {
    constructor(
        outlineTexture: GpuTextureView,
        maskTexture: GpuTextureView,
        width: Float,
        height: Float,
        progress: Float,
        alpha: Float
    ) : super(
        Render.RenderPipelines.SKILL_PROGRESS,
        width, height,
        0f, 0f, 1f, 1f,
        1f, 1f, 1f, alpha,
        listOf(
            TextureBinding("Sampler0", outlineTexture, SAMPLER),
            TextureBinding("Sampler1", maskTexture, SAMPLER)
        ),
        listOf(
            UniformPayload(
                "SkillProgress",
                SkillProgressUniforms::class.java,
                SkillProgressUniforms(progress),
                SkillProgressUniforms.UBO_SIZE
            )
        )
    )

    companion object{
        val SAMPLER = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
    }
}
