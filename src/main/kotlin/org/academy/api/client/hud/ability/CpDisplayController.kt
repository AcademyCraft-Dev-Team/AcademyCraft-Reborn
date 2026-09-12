package org.academy.api.client.hud.ability

import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.util.Mth
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.render.Render
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.util.Chase
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * CP 条的连续渲染控制器。
 *
 * 设计目标：
 *  1. 填充前沿 [displayProgress] 用帧率无关的指数逼近连续追逐实际值——没有离散跳变、
 *     没有 animator 生命周期、没有取消弹跳，天然容忍连续回蓝与频繁增减反转。
 *  2. 粒子不是"发射"出来的，而是一个固定锚点网格上的解析场：每个粒子锚定在固定的进度
 *     位置，透明度与绘制位置都是"锚点到前沿的间隙"的连续函数（gap 包络 + 向内牵引）。
 *     没有步长、没有阈值累积、没有 spawn 事件、没有相位回绕/传送。
 *  3. 粒子永远位于空区域（进度高于前沿一侧），右边缘被钳制在前沿左侧；贴近前沿的粒子
 *     透明度按包络连续降到 0，因此"前沿到达粒子归位点"的瞬间粒子已透明——不存在
 *     "进度先占据粒子要飞回位置"的帧。方向反转时同一包络自然成立，无需任何特殊处理。
 */
class CpDisplayController {
    var displayProgress: Float = 0f
        private set

    /** 累计运行时间（秒），驱动粒子闪烁相位。 */
    var timeSeconds: Float = 0f
        private set

    private var targetProgress: Float = 0f
    private var activity: Float = 0f
    private var hasInitialized: Boolean = false

    /** 前沿前方可视窗口宽度（进度单位）。 */
    var window: Float = 0.3f

    var activityTimeConstantMs: Float = 200f

    /** 前沿速度达到该值（进度/秒）时粒子场全亮。 */
    var activityReferenceSpeed: Float = 0.6f

    /** 粒子锚点数量（固定，不随几何/时间增减，避免任何离散增减）。 */
    var moteCount: Int = 60

    /** 单粒子边长（像素）。 */
    var moteSize: Float = 2.5f

    /** 粒子闪烁角速度（弧度/秒）。 */
    var shimmerSpeed: Float = 1.6f

    /** 向前沿牵引系数：绘制位置 = 前沿 + 间隙 * pullFactor，间隙归零时粒子恰好汇入前沿。 */
    var pullFactor: Float = 0.6f

    fun update(actualCp: Float, maxCp: Float) {
        updateProgress((actualCp / maxCp).coerceIn(0f, 1f), Chase.deltaMillis())
    }

    fun updateProgress(target: Float, dtMs: Float) {
        targetProgress = target.coerceIn(0f, 1f)
        if (!hasInitialized) {
            // 首帧直接对齐实际值，避免 HUD 初次出现时从 0 播放补动画
            hasInitialized = true
            displayProgress = targetProgress
            return
        }
        if (dtMs <= 0f) return
        val dt = dtMs / 1000f
        timeSeconds += dt
        val prev = displayProgress
        displayProgress = Chase.approach(displayProgress, targetProgress, dtMs)
        val delta = abs(displayProgress - prev)
        val speed = delta / dt
        val targetActivity = (speed / activityReferenceSpeed).coerceIn(0f, 1f)
        activity = Chase.approach(activity, targetActivity, dtMs, activityTimeConstantMs)
    }

    /**
     * 计算当前帧的粒子场快照。纯函数式：只依赖本控制器状态与时间，无副作用。
     *
     * 粒子锚点均匀铺满进度 [0, 1]；只有当锚点落在前沿前方的窗口内（gap ∈ (0, window]）
     * 才可见，透明度由 gap/window 的连续包络决定，gap→0（前沿到达）时恰好透明。
     */
    fun computeMotes(geometry: CpBarGeometry, timeSeconds: Float): List<Mote> {
        if (activity <= 1e-4f) return emptyList()
        val windowEff = min(window, maxOf(0f, 1f - displayProgress))
        if (windowEff <= 1e-4f) return emptyList()
        val motes = ArrayList<Mote>(moteCount)
        val fillEdgeX = geometry.edgeX(displayProgress)
        for (i in 0 until moteCount) {
            val anchor = (i + 0.5f) / moteCount
            val gap = anchor - displayProgress
            if (gap <= 0f || gap >= windowEff) continue
            val env = bell(gap / windowEff)
            if (env <= 1e-4f) continue
            val shimmer = 0.75f + 0.25f * sin(timeSeconds * shimmerSpeed + i * 2.39996f)
            val alpha = activity * env * shimmer
            if (alpha <= 1e-4f) continue
            val drawnQ = displayProgress + gap * pullFactor
            val jitterX = sin(timeSeconds * shimmerSpeed * 0.7f + i * 1.7f) * 1.2f
            val jitterY = cos(timeSeconds * shimmerSpeed * 0.6f + i * 2.3f) * 1.2f
            val t = fract(i * 0.618033988f)
            val x = min(geometry.edgeX(drawnQ) + jitterX, fillEdgeX - moteSize * 0.5f - 0.5f)
            val y = geometry.topPadding + t * geometry.barHeight + jitterY
            motes.add(Mote(i, x, y, alpha, moteSize))
        }
        return motes
    }

    /**
     * 绘制填充条 + 粒子场。
     *
     * @param baseAlpha 已经乘入 widget alpha 与 accumulatedAlpha 的基础透明度。
     * @param hudAlpha  HUD 淡入淡出系数（沿用旧实现：填充长度随 HUD 淡出缩短）。
     * @param tintR/G/B 条颜色（0..255），粒子与填充条共用同色。
     */
    fun render(
        context: Canvas,
        geometry: CpBarGeometry,
        sampler: GpuSampler,
        view: GpuTextureView,
        tintR: Int,
        tintG: Int,
        tintB: Int,
        baseAlpha: Float,
        hudAlpha: Float,
    ) {
        val progress = (displayProgress * hudAlpha).coerceIn(0f, 1f)
        val edgeX = geometry.edgeX(progress)

        context.submit(object : DrawCommand(
            Render.RenderPipelines.IMAGE,
            listOf(TextureBinding("Sampler0", view, sampler)),
            mutableListOf()
        ) {
            override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose, alphaMul: Float) {
                val matrix = pose.pose()
                val a = (baseAlpha * alphaMul * 255f).toInt()
                val dest = Vector3f()
                val bottomLeftX = edgeX + geometry.offset
                val bottomRightX = geometry.rightX + geometry.offset

                writer.beginVertex()
                matrix.transformPosition(edgeX, geometry.topPadding, 0f, dest)
                writer.putVec3f(dest.x, dest.y, dest.z)
                writer.putVec2f(edgeX / geometry.width, geometry.topU)
                writer.putColor(tintR, tintG, tintB, a)

                writer.beginVertex()
                matrix.transformPosition(bottomLeftX, geometry.bottomY, 0f, dest)
                writer.putVec3f(dest.x, dest.y, dest.z)
                writer.putVec2f(bottomLeftX / geometry.width, geometry.bottomU)
                writer.putColor(tintR, tintG, tintB, a)

                writer.beginVertex()
                matrix.transformPosition(bottomRightX, geometry.bottomY, 0f, dest)
                writer.putVec3f(dest.x, dest.y, dest.z)
                writer.putVec2f(bottomRightX / geometry.width, geometry.bottomU)
                writer.putColor(tintR, tintG, tintB, a)

                writer.beginVertex()
                matrix.transformPosition(geometry.rightX, geometry.topPadding, 0f, dest)
                writer.putVec3f(dest.x, dest.y, dest.z)
                writer.putVec2f(geometry.rightX / geometry.width, geometry.topU)
                writer.putColor(tintR, tintG, tintB, a)
            }
        })

        val motes = computeMotes(geometry, timeSeconds)
        if (motes.isEmpty()) return
        for (mote in motes) {
            context.pose().pushPose()
            run {
                context.pose().translate(mote.x, mote.y)
                context.submit(
                    FillRectDrawCommand(
                        mote.size,
                        mote.size,
                        tintR / 255f,
                        tintG / 255f,
                        tintB / 255f,
                        mote.alpha * baseAlpha
                    )
                )
            }
            context.pose().popPose()
        }
    }

    data class Mote(val index: Int, val x: Float, val y: Float, val alpha: Float, val size: Float)

    companion object {
        private fun fract(value: Float): Float = value - floor(value)

        /** 双端为 0 的连续包络：贴近前沿（归位交接）与窗口远端（平滑出生）都透明。 */
        private fun bell(rel: Float): Float {
            val rise = smoothstep(0f, 0.18f, rel)
            val fall = 1f - smoothstep(0.72f, 1f, rel)
            return rise * fall
        }

        private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}

/**
 * CP 条唯一几何来源：填充条四边面与粒子共用同一组坐标/UV 计算，
 * 保证合并帧粒子与前沿像素级对齐。
 */
class CpBarGeometry(val width: Float, val height: Float) {
    val spacing: Float = 7f / 4f
    val topPadding: Float = 21f / 4f
    val bottomPadding: Float = 56f / 4f
    val leftPadding: Float = 107f / 4f
    val rightPadding: Float = 130f / 4f

    val offset: Float = height - topPadding - bottomPadding
    val barWidth: Float = width - leftPadding - rightPadding - offset - 9 * spacing
    val barHeight: Float = height - topPadding - bottomPadding
    val rightX: Float = width - rightPadding - offset
    val bottomY: Float = height - bottomPadding
    val topU: Float = topPadding / height
    val bottomU: Float = (height - bottomPadding) / height

    fun edgeX(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val i = 10 - Mth.ceil(p / 0.1f)
        return leftPadding + (1 - p) * barWidth + i * spacing
    }
}
