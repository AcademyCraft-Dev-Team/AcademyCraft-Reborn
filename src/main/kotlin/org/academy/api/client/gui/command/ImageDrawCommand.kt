package org.academy.api.client.gui.command

import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.Render
import org.academy.api.client.render.TextureBinding

class ImageDrawCommand(
    texture: GpuTextureView, sampler: GpuSampler,
    width: Float, height: Float,
    u0: Float, v0: Float, u1: Float, v1: Float,
    red: Float, green: Float, blue: Float, alpha: Float
) : PosTexColorRectDrawCommand(
    Render.RenderPipelines.IMAGE,
    width, height,
    u0, v0, u1, v1,
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