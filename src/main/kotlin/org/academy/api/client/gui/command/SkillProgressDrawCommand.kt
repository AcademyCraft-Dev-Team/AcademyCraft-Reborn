package org.academy.api.client.gui.command

import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.render.Render
import org.academy.api.client.render.SkillProgressUniforms
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformPayload

class SkillProgressDrawCommand : PosTexColorRectDrawCommand {
    constructor(
        outlineTexture: GpuTextureView,
        maskTexture: GpuTextureView,
        sampler: GpuSampler,
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
            TextureBinding("Sampler0", outlineTexture, sampler),
            TextureBinding("Sampler1", maskTexture, sampler)
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
}
