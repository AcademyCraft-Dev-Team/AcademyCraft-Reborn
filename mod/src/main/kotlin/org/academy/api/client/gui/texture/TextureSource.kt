package org.academy.api.client.gui.texture

import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView

interface TextureSource : AutoCloseable {
    fun getTextureView(): GpuTextureView?

    fun getPreferredSampler(): GpuSampler? = null

    override fun close() {
    }
}
