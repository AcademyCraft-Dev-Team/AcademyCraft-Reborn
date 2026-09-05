package org.academy.api.client.gui.texture

import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView

/**
 * 通用纹理源抽象. 控件只与 [TextureSource] 交互, 不关心具体来源: 静态资源文件 /
 * texture manager 中已注册的动态纹理 key / 直接持有的 GPU view.
 *
 * 线程契约 (对齐 Android 的 UI 线程亲和):
 * - [getTextureView] / [getPreferredSampler] 仅渲染期在 UI 线程调用, 必须廉价、无锁.
 * - [close] 在 UI 线程调用.
 * - 三态生命周期: Pending([getTextureView] 返回 null, 控件跳过绘制) -> Ready -> Failed/关闭.
 */
interface TextureSource : AutoCloseable {
    /** 当前可用的 GPU view; null = 未就绪/不可用. */
    fun getTextureView(): GpuTextureView?

    /** 源自带的偏好采样器 (如二维码用 NEAREST clamp); null = 交给控件覆盖或默认. */
    fun getPreferredSampler(): GpuSampler? = null

    /** 释放源拥有的 GPU 资源; 默认空实现 (借用视图 / texture manager 托管的源不释放). */
    override fun close() {
    }
}