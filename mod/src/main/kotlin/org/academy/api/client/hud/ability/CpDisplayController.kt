package org.academy.api.client.hud.ability

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.util.Mth
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.FillRectDrawCommand
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.render.Render
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.util.Chase
import org.joml.Vector3f
import kotlin.math.*

class CpDisplayController {
    var displayProgress: Float = 0f
        private set

    var timeSeconds: Float = 0f
        private set

    private var targetProgress: Float = 0f
    private var activity: Float = 0f
    private var hasInitialized: Boolean = false

    var window: Float = 0.3f

    var activityTimeConstantMs: Float = 200f

    var activityReferenceSpeed: Float = 0.6f

    var moteCount: Int = 60

    var moteSize: Float = 2.5f

    var shimmerSpeed: Float = 1.6f

    var pullFactor: Float = 0.6f

    fun update(actualCp: Float, maxCp: Float) {
        updateProgress((actualCp / maxCp).coerceIn(0f, 1f), Chase.deltaMillis())
    }

    fun updateProgress(target: Float, dtMs: Float) {
        targetProgress = target.coerceIn(0f, 1f)
        if (!hasInitialized) {
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
            val t = fract(i * 0.618034f)
            val x = min(geometry.edgeX(drawnQ) + jitterX, fillEdgeX - moteSize * 0.5f - 0.5f)
            val y = geometry.topPadding + t * geometry.barHeight + jitterY
            motes.add(Mote(i, x, y, alpha, moteSize))
        }
        return motes
    }

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

                fun vertex(x: Float, y: Float, u: Float) {
                    writer.beginVertex()
                    matrix.transformPosition(x, y, 0f, dest)
                    writer.putVec3f(dest.x, dest.y, dest.z)
                    writer.putVec2f(x / geometry.width, u)
                    writer.putColor(tintR, tintG, tintB, a)
                }

                vertex(edgeX, geometry.topPadding, geometry.topU)
                vertex(bottomLeftX, geometry.bottomY, geometry.bottomU)
                vertex(bottomRightX, geometry.bottomY, geometry.bottomU)
                vertex(geometry.rightX, geometry.topPadding, geometry.topU)
            }
        })

        val motes = computeMotes(geometry, timeSeconds)
        if (motes.isEmpty()) return
        for ((_, x, y, alpha, size) in motes) {
            context.pose().pushPose()
            run {
                context.pose().translate(x, y)
                context.submit(
                    FillRectDrawCommand(
                        size,
                        size,
                        tintR / 255f,
                        tintG / 255f,
                        tintB / 255f,
                        alpha * baseAlpha
                    )
                )
            }
            context.pose().popPose()
        }
    }

    data class Mote(val index: Int, val x: Float, val y: Float, val alpha: Float, val size: Float)

    companion object {
        private fun fract(value: Float): Float = value - floor(value)

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
