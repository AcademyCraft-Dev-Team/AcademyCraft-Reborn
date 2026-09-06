package org.academy.api.client.gui.texture

import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView

/**
 * 直接持有已解析 GPU view 的源 (借用视图, close 空实现).
 * 用于现有 `setTexture(GpuTextureView)` 直接视图路径.
 */
class GpuTextureViewSource(
    private val view: GpuTextureView?,
    private val preferredSampler: GpuSampler? = null
) : TextureSource {
    override fun getTextureView(): GpuTextureView? =
        if (view != null && !view.isClosed) view else null

    override fun getPreferredSampler(): GpuSampler? = preferredSampler

    override fun toString(): String = "GpuTextureViewSource($view)"
}