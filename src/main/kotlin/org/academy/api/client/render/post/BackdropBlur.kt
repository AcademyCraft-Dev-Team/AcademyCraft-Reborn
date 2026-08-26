package org.academy.api.client.render.post

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.resource.RenderTargetDescriptor
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.render.Render
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.render.UniformBinding
import org.joml.Vector2f
import org.joml.Vector4f
import org.lwjgl.system.MemoryStack
import java.util.*
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max

/**
 * 唯一的 GPU 模糊数学 / 持久化模糊金字塔引擎, 供 UI 合成 (UiCompositor)、HUD 后处理
 * (BlurEffect) 与编辑器视口 (EditorGlow) 共用.
 *
 * 连续性设计 (硬性约束):
 * - 内核沿用 [org.academy.api.client.render.Render.GaussianSamples]: 权重归一化, tap 偏移随
 *   radius 连续缩放, radius -> 0 收敛为恒等 (无模糊).
 * - 模糊用跨层混合: 连续层号 [radiusToLevel]; 采样 mix(L[floor(k)], L[ceil(k)], frac(k)).
 * - r = 0 => k = 0 => 只采 L0 (全分辨率未模糊基准) => 输出 == 未模糊 (恒等).
 */
object BackdropBlur {
    /** 基准 sigma 的 GUI scale 乘子 (s = SIGMA_SCALE * guiScale). */
    const val SIGMA_SCALE = 0.5f

    /** 金字塔硬性层数上限 (每层沿长/短边减半). */
    const val MAX_LAYERS = 8

    /** 层有效半径 B_i = s * (2^i - 1); 层号 i 处恰好对应整数层. */
    fun layerRadius(level: Int, sigma: Float): Float = sigma * ((1 shl level) - 1)

    /**
     * 连续层号 k = log2(1 + r/s).
     * - k(0) = 0, 单调递增, 连续.
     * - 整数层边界 r = s*(2^i - 1) 时 k = i 精确.
     */
    fun radiusToLevel(radius: Float, sigma: Float): Float {
        if (radius <= 0f) return 0f
        if (sigma <= 0f) return 0f
        return log2(1f + radius / sigma)
    }

    /** 由最大 radius 反推本帧所需层数 (>=1, <= MAX_LAYERS). */
    fun layersFor(radius: Float, sigma: Float): Int {
        val k = radiusToLevel(radius, sigma)
        if (k <= 0f) return 1
        val lo = floor(k).toInt()
        val need = lo + 1 + if (k > lo) 1 else 0
        return need.coerceAtMost(MAX_LAYERS).coerceAtLeast(1)
    }

    /** 层号裁剪到 [0, layerCount-1]; 层高 by 透视降采样. */
    fun clampLevel(level: Float, layerCount: Int): Float {
        val max = layerCount - 1
        if (level <= 0f) return 0f
        return level.coerceAtMost(max.toFloat())
    }

    /** 跨层权重: 返回采样所在层序 [floor(k), ceil(k)] 与 [frac(k)]. */
    fun levelSpan(radius: Float, sigma: Float, layerCount: Int): LevelSpan {
        val k = clampLevel(radiusToLevel(radius, sigma), layerCount)
        val lo = floor(k).toInt()
        val frac = k - floor(k)
        val hi = if (frac <= 0f) lo else (lo + 1).coerceAtMost(layerCount - 1)
        return LevelSpan(lo, hi, frac)
    }

    /** 金字塔各层尺寸 (w,h) 每次沿长/短边减半, 最小 1. */
    fun pyramidSize(width: Int, height: Int, levelCount: Int): List<Pair<Int, Int>> {
        val sizes = ArrayList<Pair<Int, Int>>(levelCount)
        var w = width
        var h = height
        for (i in 0 until levelCount) {
            sizes.add(w to h)
            w = max(1, w / 2)
            h = max(1, h / 2)
        }
        return sizes
    }

    /**
     * 两轴高斯模糊, 供 HUD 后处理 (BlurEffect) 与编辑器视口 (EditorGlow) 复用.
     *
     * @param depth 非空时走模板裁切路径 (CUTOUT_GAUSSIAN_BLUR + INVERSE_CUTOUT), 否则整纹理模糊.
     */
    @JvmStatic
    fun applyGaussian(
        sampler: GpuTextureView,
        output: GpuTextureView,
        depth: GpuTextureView?,
        width: Int,
        height: Int,
        radius: Float,
    ) {
        val desc = RenderTargetDescriptor(width, height, false, Vector4f(0f), GpuFormat.RGBA8_UNORM)
        val swap = Render.Buffers.getResourcePool().acquire(desc)
        try {
            val swapView = swap.getColorTextureView() ?: return
            val blurUboSlice = Render.BlurUniforms.getBlurUniformsBuffer().slice()
            val gpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            val textures = listOf(TextureBinding("Sampler0", sampler, gpuSampler))
            val uniforms = listOf(UniformBinding("BlurInfo", blurUboSlice))
            val vec2 = Vector2f(width.toFloat(), height.toFloat())

            val horizontalPipeline = if (depth != null) Render.RenderPipelines.CUTOUT_GAUSSIAN_BLUR
            else Render.RenderPipelines.GAUSSIAN_BLUR

            Render.BlurUniforms.writeBlurUniforms(vec2, 1f, 0f, radius)
            Render.runBlitPass(
                swapView, depth, false, false, horizontalPipeline,
                Render.Buffers.getInstance().fsQuadVBNDC, textures, uniforms
            )
            if (depth != null) {
                Render.runBlitPass(
                    swapView, depth, false, false,
                    Render.RenderPipelines.BLIT_SCREEN_WITHOUT_BLEND_INVERSE_CUTOUT,
                    Render.Buffers.getInstance().fsQuadVBNDC, textures, emptyList()
                )
            }
            Render.BlurUniforms.writeBlurUniforms(vec2, 0f, 1f, radius)
            Render.runBlitPass(
                output, depth, false, false, horizontalPipeline,
                Render.Buffers.getInstance().fsQuadVBNDC,
                listOf(TextureBinding("Sampler0", swapView, gpuSampler)), uniforms
            )
        } finally {
            Render.Buffers.getResourcePool().release(desc, swap)
        }
    }

    data class LevelSpan(val lo: Int, val hi: Int, val frac: Float) {
        val isIdentity: Boolean
            get() = lo == 0 && hi == 0
    }
}

/**
 * 持久化模糊金字塔 (L0 全分辨率未模糊基准, L1..Ln 逐层降采样/模糊), 供 UI 合成逐区域采样.
 * 不再每帧 acquire 抖动; 目标在窗口尺寸变化时重建一次.
 */
class BackdropBlurEngine {
    private var levels: Array<TextureTarget?> = arrayOfNulls(BackdropBlur.MAX_LAYERS)
    private var ubo: GpuBuffer? = null
    private var baseW = -1
    private var baseH = -1
    private var baseLayers = 0

    private val sigma: Float
        get() = BackdropBlur.SIGMA_SCALE * UiEnvironment.get().guiScale

    fun close() {
        for (level in levels) level?.destroyBuffers()
        levels = arrayOfNulls(BackdropBlur.MAX_LAYERS)
        ubo?.close()
        ubo = null
        baseW = -1
        baseH = -1
        baseLayers = 0
    }

    fun capture(source: GpuTextureView, maxRadius: Float) {
        val count = BackdropBlur.layersFor(maxRadius, sigma).coerceIn(1, BackdropBlur.MAX_LAYERS)
        val env = UiEnvironment.get()
        val w = env.physicalWidth
        val h = env.physicalHeight
        ensureTargets(w, h, count)

        val l0 = levels[0]?.getColorTextureView() ?: return
        Render.runBlitPass(
            l0, null, true, false,
            Render.RenderPipelines.BLIT_SCREEN_WITHOUT_BLEND,
            Render.Buffers.getInstance().fsQuadVBNDC,
            listOf(
                TextureBinding(
                    "Sampler0", source,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                )
            ),
            emptyList()
        )

        val s = sigma
        for (i in 1 until count) {
            val prev = levels[i - 1]?.getColorTextureView() ?: continue
            blurDown(levels[i] ?: continue, prev, s * i)
        }
    }

    /** 把模糊后的 backdrop 区域 [x,y,w,h] (逻辑 GUI 坐标) 以 [radius] 强度且可带 [tint] 写入 [target]. */
    fun fillRegion(
        target: GpuTextureView,
        x: Float, y: Float, w: Float, h: Float,
        radius: Float,
        tint: Vector4f,
    ) {
        val env = UiEnvironment.get()
        val guiScale = env.guiScale
        val physicalWidth = env.physicalWidth
        val physicalHeight = env.physicalHeight
        val rectWidth = w * guiScale
        val rectHeight = h * guiScale
        if (rectWidth <= 0f || rectHeight <= 0f) return

        val xPx = x * guiScale
        val yPx = physicalHeight - (y + h) * guiScale
        val sx = xPx.toInt().coerceIn(0, physicalWidth)
        val sy = yPx.toInt().coerceIn(0, physicalHeight)
        val sw = rectWidth.toInt().coerceAtMost(physicalWidth - sx)
        val sh = rectHeight.toInt().coerceAtMost(physicalHeight - sy)
        if (sw <= 0 || sh <= 0) return

        val s = sigma
        val layerCount = BackdropBlur.layersFor(radius, s)
        if (layerCount > countActiveLevels()) return
        val span = BackdropBlur.levelSpan(radius, s, layerCount)

        writeBackdropUniforms(
            xPx / physicalWidth,
            (physicalHeight - (y + h) * guiScale) / physicalHeight,
            rectWidth / physicalWidth,
            rectHeight / physicalHeight,
            tint,
            physicalWidth.toFloat(), physicalHeight.toFloat(),
            span.frac,
            cornerRadius = 0f
        )

        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        val lo = levels[span.lo]?.getColorTextureView() ?: return
        val hi = levels[span.hi]?.getColorTextureView() ?: return

        val encoder = RenderSystem.getDevice().createCommandEncoder()
        encoder.createRenderPass({ "Backdrop fillRegion -> $target" }, target, Optional.empty()).use { rp ->
            rp.setPipeline(Render.RenderPipelines.BACKDROP_SAMPLE)
            rp.bindTexture("Sampler0", lo, sampler)
            rp.bindTexture("Sampler1", hi, sampler)
            rp.setUniform("BackdropInfo", requireUbo().slice())
            rp.setVertexBuffer(0, Render.Buffers.getInstance().fsQuadVBNDC.slice())
            val seq = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
            rp.setIndexBuffer(seq.getBuffer(6), seq.type())
            rp.enableScissor(sx, sy, sw, sh)
            rp.drawIndexed(6, 1, 0, 0, 0)
            rp.disableScissor()
        }
    }

    private fun blurDown(out: TextureTarget, source: GpuTextureView, radius: Float) {
        val w = out.width
        val h = out.height
        val desc = RenderTargetDescriptor(w, h, false, Vector4f(0f), GpuFormat.RGBA8_UNORM)
        val swap = Render.Buffers.getResourcePool().acquire(desc)
        try {
            val swapView = swap.getColorTextureView() ?: return
            val blurUboSlice = Render.BlurUniforms.getBlurUniformsBuffer().slice()
            val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            val uniforms = listOf(UniformBinding("BlurInfo", blurUboSlice))

            Render.BlurUniforms.writeBlurUniforms(Vector2f(w.toFloat(), h.toFloat()), 1f, 0f, radius)
            Render.runBlitPass(
                swapView, null, true, false,
                Render.RenderPipelines.GAUSSIAN_BLUR,
                Render.Buffers.getInstance().fsQuadVBNDC,
                listOf(TextureBinding("Sampler0", source, sampler)), uniforms
            )
            Render.BlurUniforms.writeBlurUniforms(Vector2f(w.toFloat(), h.toFloat()), 0f, 1f, radius)
            Render.runBlitPass(
                out.getColorTextureView()!!, null, true, false,
                Render.RenderPipelines.GAUSSIAN_BLUR,
                Render.Buffers.getInstance().fsQuadVBNDC,
                listOf(TextureBinding("Sampler0", swapView, sampler)), uniforms
            )
        } finally {
            Render.Buffers.getResourcePool().release(desc, swap)
        }
    }

    private fun countActiveLevels(): Int =
        levels.indexOfLast { it != null } + 1

    private fun ensureTargets(width: Int, height: Int, layerCount: Int) {
        if (baseW == width && baseH == height && baseLayers >= layerCount) return
        for (level in levels) level?.destroyBuffers()
        levels = arrayOfNulls(BackdropBlur.MAX_LAYERS)
        val sizes = BackdropBlur.pyramidSize(width, height, layerCount)
        for (i in 0 until layerCount) {
            val (lw, lh) = sizes[i]
            levels[i] = TextureTarget("BackdropBlur-L$i", lw, lh, false, GpuFormat.RGBA8_UNORM)
        }
        baseW = width
        baseH = height
        baseLayers = layerCount
    }

    private fun requireUbo(): GpuBuffer {
        ubo?.let { return it }
        val uboUsage = GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST
        val buffer = RenderSystem.getDevice().createBuffer(
            { "BackdropBlur UBO" }, uboUsage, BackdropUniforms.UBO_SIZE.toLong()
        )
        ubo = buffer
        return buffer
    }

    private fun writeBackdropUniforms(
        rectX: Float, rectY: Float, rectW: Float, rectH: Float,
        tint: Vector4f,
        outW: Float, outH: Float,
        levelLerp: Float,
        cornerRadius: Float,
    ) {
        val ubo = requireUbo()
        MemoryStack.stackPush().use { stack ->
            val builder = Std140Builder.onStack(stack, BackdropUniforms.UBO_SIZE)
            BackdropUniforms(
                Vector4f(rectX, rectY, rectW, rectH),
                tint,
                Vector2f(outW, outH),
                levelLerp,
                cornerRadius
            ).write(builder)
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(ubo.slice(), builder.get())
        }
    }

    private data class BackdropUniforms(
        val regionRect: Vector4f,
        val tint: Vector4f,
        val outSize: Vector2f,
        val levelLerp: Float,
        val cornerRadius: Float,
    ) {
        fun write(builder: Std140Builder) {
            builder.putVec4(regionRect).putVec4(tint).putVec2(outSize).putFloat(levelLerp).putFloat(cornerRadius)
        }

        companion object {
            val UBO_SIZE: Int = Std140SizeCalculator().putVec4().putVec4().putVec2().putFloat().putFloat().get()
        }
    }
}
