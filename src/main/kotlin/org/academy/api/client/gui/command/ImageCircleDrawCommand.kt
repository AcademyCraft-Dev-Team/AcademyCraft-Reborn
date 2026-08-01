package org.academy.api.client.gui.command

import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.render.Render
import org.academy.api.client.render.TextureBinding

class ImageCircleDrawCommand : PosTexColorRectDrawCommand {
    constructor(
        texture: GpuTextureView,
        sampler: GpuSampler,
        width: Float,
        height: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        u2: Float,
        v2: Float,
        u3: Float,
        v3: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) : super(
        Render.RenderPipelines.IMAGE_CIRCLE,
        width, height,
        u0, v0, u1, v1, u2, v2, u3, v3,
        red, green, blue, alpha,
        listOf(
            TextureBinding(
                "Sampler0",
                texture,
                sampler
            )
        ),
        mutableListOf()
    )
}
