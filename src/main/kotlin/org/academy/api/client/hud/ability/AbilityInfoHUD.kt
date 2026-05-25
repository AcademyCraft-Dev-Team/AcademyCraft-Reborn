package org.academy.api.client.hud.ability

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.api.client.Render
import org.academy.api.client.Resource
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.gui.animation.Animator
import org.academy.api.client.gui.animation.AnimatorListener
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.imgui.ImGuiUIDebugger
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.widget.*
import org.academy.api.client.render.TextureBinding
import org.joml.Matrix4f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AbilityInfoHUD private constructor() {
    private val context = Context()
    private val uiContext = UiContext()

    fun perform(mouseX: Double, mouseY: Double, deltaPartialTick: Float) {
        uiContext.perform(context.get(), mouseX, mouseY, deltaPartialTick)
    }

    fun render(target: RenderTarget) {
        if (!AbilitySystemClient.isActiveHUD()) return
        uiContext.upload(target, false)
        context.get().requestLayout()
        ImGuiUIDebugger.render(target, context.get())
    }

    @SubscribeEvent
    fun onTick(@Suppress("unused") event: ClientTickEvent.Post) {
        context.get().tick()
    }

    private class Context : WidgetContext {
        private val root: FrameLayoutWidget = createRoot()

        override fun get(): WidgetContainer {
            return root
        }

        fun createRoot(): FrameLayoutWidget {
            val root = FrameLayoutWidget()
            run {
                val cp = FrameLayoutWidget()
                cp.layoutParams = FrameLayoutWidget.LayoutParams()
                    .size(240f, 27f)
                    .margin(0f, 4f, 4f, 0f)
                    .gravity(Gravity.TOP_RIGHT)

                root.addChild("cp", cp)
                run {
                    val back = ImageWidget(Resource.Textures.CP_BAR_BACKGROUND)
                    back.layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)

                    cp.addChild("back", back)

                    val content: AbstractWidget = object : AbstractWidget() {
                        var textureView: GpuTextureView? = null
                        val sampler: GpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                        val particles: MutableList<Particle> = ArrayList<Particle>()
                        var lastCp: Float = AbilitySystemClient.getAvailableCP()
                        var visualCp: Float = AbilitySystemClient.getAvailableCP()

                        override fun renderInternal(context: RenderContext) {
                            super.renderInternal(context)
                            var view = textureView
                            if (view == null || view.isClosed) {
                                try {
                                    val tex = Minecraft.getInstance().textureManager
                                        .getTexture(Resource.Textures.CP_BAR_VALUE)
                                    view = tex.getTextureView()
                                    textureView = view
                                } catch (_: Exception) {
                                    return
                                }
                            }

                            val spacing = 7f / 4f
                            val topPadding = 21f / 4f
                            val bottomPadding = 56f / 4f
                            val leftPadding = 107f / 4f
                            val rightPadding = 130f / 4f

                            // because tan(45°) = 1
                            val progress = visualCp / AbilitySystemClient.getMaxCP()
                            val offset = height - topPadding - bottomPadding
                            val i = 10 - Mth.ceil(progress / 0.1f)
                            val barWidth = width - leftPadding - rightPadding - offset - 9 * spacing
                            val progressOffsetX = (1 - progress) * barWidth + i * spacing

                            val topPaddingU = topPadding / height
                            val bottomPaddingU = bottomPadding / height

                            val topLeft = leftPadding + progressOffsetX
                            val bottomLeft = topLeft + offset
                            val topRight = width - rightPadding - offset
                            val bottomRight = topRight + offset

                            val topLeftU = topLeft / width
                            val bottomLeftU = bottomLeft / width
                            val topRightU = topRight / width
                            val bottomRightU = bottomRight / width

                            val alpha = alpha * context.accumulatedAlpha

                            context.submit(object : DrawCommand(
                                Render.RenderPipelines.IMAGE,
                                listOf(TextureBinding("Sampler0", view, sampler)),
                                mutableListOf()
                            ) {
                                override fun generateVertices(consumer: VertexConsumer, pose: Matrix4f) {
                                    consumer.addVertex(pose, topLeft, topPadding, 0f).setUv(topLeftU, topPaddingU)
                                        .setColor(1f, 1f, 1f, alpha)
                                    consumer.addVertex(pose, bottomLeft, height - bottomPadding, 0f)
                                        .setUv(bottomLeftU, 1 - bottomPaddingU).setColor(1f, 1f, 1f, alpha)
                                    consumer.addVertex(pose, bottomRight, height - bottomPadding, 0f)
                                        .setUv(bottomRightU, 1 - bottomPaddingU).setColor(1f, 1f, 1f, alpha)
                                    consumer.addVertex(pose, topRight, topPadding, 0f).setUv(topRightU, topPaddingU)
                                        .setColor(1f, 1f, 1f, alpha)
                                }
                            })

                            for (particle in particles) {
                                val currentI = 10 - Mth.ceil(particle.current / 0.1f)
                                val lastI = 10 - Mth.ceil(particle.last / 0.1f)
                                val currentOffsetX = (1 - particle.current) * barWidth + currentI * spacing
                                val lastOffsetX = (1 - particle.last) * barWidth + lastI * spacing

                                val leftX = if (particle.increase) currentOffsetX else lastOffsetX
                                val rightX = if (particle.increase) lastOffsetX else currentOffsetX

                                val particleTopLeft = leftPadding + leftX
                                val particleTopRight = leftPadding + rightX
                                val particleBottomLeft = particleTopLeft + offset
                                val particleBottomRight = particleTopRight + offset

                                val particleTopLeftU = particleTopLeft / width
                                val particleTopRightU = particleTopRight / width
                                val particleBottomLeftU = particleBottomLeft / width
                                val particleBottomRightU = particleBottomRight / width

                                context.pose().pushPose()
                                run {
                                    context.pose().translate(particle.posOffset, particle.posOffset, 0f)
                                    context.submit(object : DrawCommand(
                                        Render.RenderPipelines.IMAGE,
                                        listOf(TextureBinding("Sampler0", view, sampler)),
                                        mutableListOf()
                                    ) {
                                        override fun generateVertices(consumer: VertexConsumer, pose: Matrix4f) {
                                            consumer.addVertex(pose, particleTopLeft, topPadding, 0f)
                                                .setUv(particleTopLeftU, topPaddingU)
                                                .setColor(1f, 1f, 1f, particle.alpha)
                                            consumer.addVertex(pose, particleBottomLeft, height - bottomPadding, 0f)
                                                .setUv(particleBottomLeftU, 1 - bottomPaddingU)
                                                .setColor(1f, 1f, 1f, particle.alpha)
                                            consumer.addVertex(pose, particleBottomRight, height - bottomPadding, 0f)
                                                .setUv(particleBottomRightU, 1 - bottomPaddingU)
                                                .setColor(1f, 1f, 1f, particle.alpha)
                                            consumer.addVertex(pose, particleTopRight, topPadding, 0f)
                                                .setUv(particleTopRightU, topPaddingU)
                                                .setColor(1f, 1f, 1f, particle.alpha)
                                        }
                                    })
                                }
                                context.pose().popPose()
                            }
                        }

                        override fun tick() {
                            val animationTime = 750L
                            val currentCp = AbilitySystemClient.getAvailableCP()

                            if (currentCp != lastCp) {
                                val maxCp = AbilitySystemClient.getMaxCP()
                                val lastProgress = lastCp / maxCp
                                val currentProgress = currentCp / maxCp
                                val deltaProgress = currentProgress - lastProgress
                                val increase = deltaProgress > 0

                                if (!increase) {
                                    visualCp = currentCp
                                    val iterator = particles.iterator()
                                    while (iterator.hasNext()) {
                                        val particle = iterator.next()
                                        if (particle.increase) {
                                            iterator.remove()
                                            if (particle.animator != null) particle.animator!!.cancel()
                                        }
                                    }
                                }

                                var progressTracker = lastProgress
                                var i = 0
                                while (true) {
                                    val start = progressTracker
                                    val end: Float

                                    if (increase) {
                                        val nextBoundary = (Mth.floor(start / 0.1f) + 1).toFloat() * 0.1f
                                        end = min(nextBoundary, currentProgress)
                                    } else {
                                        val nextBoundary = Mth.ceil(start / 0.1f - 1).toFloat() * 0.1f
                                        end = max(nextBoundary, currentProgress)
                                    }

                                    val progressChanged = abs(start - end) > 0
                                    if (!progressChanged) break

                                    val particle = Particle(start, end, increase)
                                    val animator = ObjectAnimator.ofFloat(
                                        { progress: Float? -> particle.setProgress(progress!!) },
                                        0f,
                                        1f
                                    )
                                        .setDuration(animationTime).setInterpolator(EasingFunctions.EASE_OUT_EXPO)
                                        .setStartDelay(i * animationTime / 10)

                                    animator.addListener(object : AnimatorListener {
                                        override fun onAnimationEnd(animation: Animator) {
                                            particles.remove(particle)
                                            if (particle.increase) visualCp = end * maxCp
                                        }
                                    })
                                    animator.start()
                                    particle.animator = animator
                                    particles.add(particle)

                                    progressTracker = end
                                    i++

                                    if (progressTracker == currentProgress) break
                                }
                                lastCp = currentCp
                            }
                        }

                        inner class Particle(val last: Float, val current: Float, val increase: Boolean) {
                            var animator: Animator? = null
                            var posOffset: Float = 0f
                            var alpha: Float = 1f

                            fun setProgress(progress: Float) {
                                posOffset = if (increase) -10 + progress * 10 else progress * -10
                                alpha = if (increase) progress else 1 - progress
                            }
                        }
                    }
                    content.layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
                    cp.addChild("content", content)
                }
            }
            return root
        }
    }

    companion object {
        private var INSTANCE: AbilityInfoHUD? = null

        val instance: AbilityInfoHUD
            get() = checkNotNull(INSTANCE) { "AbilityInfoHUD has not been initialized." }

        fun initMain() {
            INSTANCE = AbilityInfoHUD()
            NeoForge.EVENT_BUS.register(INSTANCE)
        }
    }
}