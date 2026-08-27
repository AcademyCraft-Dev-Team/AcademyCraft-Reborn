package org.academy.desktop.grapheditor.viewport

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.render.Render
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformBinding
import org.academy.api.client.render.post.BackdropBlur
import org.academy.api.client.render.post.GlowEffect
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer
import org.academy.api.client.render.vfxgraph.render.GraphCamera
import org.academy.api.client.render.vfxgraph.render.RenderSpec
import org.academy.api.client.render.vfxgraph.render.VfxGraphRenderer
import org.academy.api.client.render.vfxgraph.render.WorldTransform
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer
import org.joml.Vector4f
import org.lwjgl.system.MemoryStack

/**
 * 编辑器视口 glow 复用（M21）：把 glow 混合效果的发光主体（GLOW 输出规格）渲进离屏输入，
 * 复用游戏同款 GAUSSIAN_BLUR 降采样模糊，再以 GLOW_BLEND 叠加回视口 —— 与游戏内
 * bloom 观感一致，无需复刻整个 GlowEffect 管线。
 */
class EditorGlow(
    private val renderer: VfxGraphRenderer,
) {
    private var input: TextureTarget? = null
    private var blurB: TextureTarget? = null
    private var glowUbo: GpuBuffer? = null
    private var blackTexture: GpuTexture? = null
    private var blackView: GpuTextureView? = null

    /** 释放全部 GPU 资源（视口/预览销毁时调用，防泄漏）。 */
    fun destroy() {
        input?.destroyBuffers(); input = null
        blurB?.destroyBuffers(); blurB = null
        glowUbo?.close(); glowUbo = null
        blackView?.close(); blackView = null
        blackTexture?.close(); blackTexture = null
    }

    fun render(
        viewport: GpuTextureView,
        buffer: ParticleBuffer,
        arcBuffer: ArcBuffer?,
        camera: GraphCamera,
        specs: List<RenderSpec>,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        if (buffer.count() == 0 && (arcBuffer == null || arcBuffer.count() == 0)) return
        val device = RenderSystem.getDevice()
        if (glowUbo == null) {
            glowUbo = device.createBuffer(
                { "EditorGlow UBO" },
                GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
                GlowEffect.GlowUniforms.UBO_SIZE.toLong(),
            )
        }
        input = ensureTarget(input, "EditorGlow Input", viewportWidth, viewportHeight)
        blurB = ensureTarget(blurB, "EditorGlow BlurB", viewportWidth / 2, viewportHeight / 2)
        val inputView = input?.getColorTextureView() ?: return
        val blurBView = blurB?.getColorTextureView() ?: return

        // 1) 发光主体（bloomPass=true：只画 GLOW 输出规格，translucent 层不参与 glow）渲进清黑的输入（additive）
        device.createCommandEncoder().clearColorTexture(inputView.texture(), CLEAR_BLACK)
        if (arcBuffer != null) renderer.setArcBuffer(arcBuffer)
        renderer.render(inputView, null, buffer, camera, false, specs, WorldTransform.identity(), true)

        // 2) 半分辨率高斯模糊 (复用唯一 BackdropBlur 引擎); 细亮芯 + 更宽模糊 = 明显光晕
        BackdropBlur.applyGaussian(inputView, blurBView, null, blurB!!.width, blurB!!.height, 6f)

        // 3) GLOW_BLEND 叠加回视口（Sampler0 = 视口当前内容，模糊层 2/3 用纯黑占位）
        writeGlowUniforms(1f, 1.35f)
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        Render.runBlitPass(
            viewport, Render.RenderPipelines.GLOW_BLEND,
            Render.Buffers.getInstance().fsQuadVBNDC,
            listOf(
                TextureBinding("Sampler0", viewport, sampler),
                TextureBinding("BlurTexture1", blurBView, sampler),
                TextureBinding("BlurTexture2", ensureBlack(device), sampler),
                TextureBinding("BlurTexture3", ensureBlack(device), sampler),
            ),
            listOf(UniformBinding("GlowInfo", glowUbo!!.slice())),
            false,
        )
    }

    private fun writeGlowUniforms(radius: Float, intensity: Float) {
        val ubo = glowUbo ?: return
        MemoryStack.stackPush().use { stack ->
            val builder = Std140Builder.onStack(stack, GlowEffect.GlowUniforms.UBO_SIZE)
            GlowEffect.GlowUniforms(radius, intensity).write(builder)
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(ubo.slice(), builder.get())
        }
    }

    private fun ensureBlack(device: com.mojang.blaze3d.systems.GpuDevice): GpuTextureView {
        blackView?.let { return it }
        val texture = device.createTexture(
            { "EditorGlow Black" }, GpuTexture.USAGE_COPY_DST or GpuTexture.USAGE_TEXTURE_BINDING,
            GpuFormat.RGBA8_UNORM, 1, 1, 1, 1,
        )
        val bytes =
            java.nio.ByteBuffer.allocateDirect(4).put(0.toByte()).put(0.toByte()).put(0.toByte()).put(0.toByte())
        bytes.flip()
        device.createCommandEncoder().writeToTexture(texture, bytes, 0, 0, 0, 0, 1, 1)
        blackTexture = texture
        blackView = device.createTextureView(texture)
        return blackView!!
    }

    private fun ensureTarget(cur: TextureTarget?, name: String, w: Int, h: Int): TextureTarget {
        val width = w.coerceAtLeast(64)
        val height = h.coerceAtLeast(64)
        if (cur != null && cur.width == width && cur.height == height) return cur
        cur?.destroyBuffers()
        return TextureTarget(name, width, height, false, GpuFormat.RGBA8_UNORM)
    }

    companion object {
        private val CLEAR_BLACK = Vector4f(0f, 0f, 0f, 1f)
    }
}
