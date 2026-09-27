package org.academy.api.client.gui.environment

import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView

/** 已解析的可绑定贴图：[Identifier] 对应的 GPU 视图与采样器。 */
data class ResolvedTexture(
    val view: GpuTextureView,
    val sampler: GpuSampler,
)
