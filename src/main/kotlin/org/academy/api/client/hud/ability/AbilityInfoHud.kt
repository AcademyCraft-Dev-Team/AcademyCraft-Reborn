package org.academy.api.client.hud.ability

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraft
import org.academy.AcademyCraftClient
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.ability.AbilitySystemClient.SkillInfo
import org.academy.api.client.gui.animation.Animator
import org.academy.api.client.gui.animation.AnimatorListener
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.RenderContext
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.gui.widget.*
import org.academy.api.client.input.InputSystem
import org.academy.api.client.render.Render
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.resources.R
import org.academy.api.client.vanilla.ResizeDisplayEvent
import org.academy.api.common.util.L10n
import org.academy.internal.client.gui.SerializedUiLayout
import org.academy.internal.client.hud.HudLayout
import org.academy.internal.common.ability.AbilityCategories
import org.academy.internal.common.ability.Skills
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class Color(val r: Int, val g: Int, val b: Int)

private data class ProgColor(val prog: Float, val color: Color)

private val cpColors = listOf(
    ProgColor(0.0f, Color(240, 103, 103)),
    ProgColor(0.35f, Color(255, 174, 68)),
    ProgColor(1.0f, Color(255, 255, 255)),
)

private const val WHEEL_ITEM_ENTRANCE_DURATION = 200L
private const val WHEEL_ITEM_ENTRANCE_STAGGER = 30L
private const val WHEEL_ITEM_ENTRANCE_MAX_DELAY = 240L

private fun autoLerpColor(progress: Float): Color {
    val p = progress.coerceIn(0f, 1f)
    for (i in cpColors.indices) {
        val cur = cpColors[i]
        if (cur.prog >= p) {
            if (i == 0) return cur.color
            val last = cpColors[i - 1]
            val factor = (p - last.prog) / (cur.prog - last.prog)
            return Color(
                (last.color.r + (cur.color.r - last.color.r) * factor).roundToInt(),
                (last.color.g + (cur.color.g - last.color.g) * factor).roundToInt(),
                (last.color.b + (cur.color.b - last.color.b) * factor).roundToInt(),
            )
        }
    }
    return cpColors.last().color
}

class AbilityInfoHud private constructor() {
    private val context = Context()
    private val uiContext = UiContext()
    private var activeSelectedSkill: SkillInfo? = null

    fun perform(mouseX: Double, mouseY: Double, deltaPartialTick: Float) {
        if (context.get().alpha == 0f) return
        uiContext.perform(context.get(), mouseX, mouseY, deltaPartialTick)
    }

    fun render(target: RenderTarget) {
        if (context.get().alpha == 0f) return
        uiContext.upload(target, false)
    }

    fun toggleActive() {
        context.get().cancelAnimations()
        context.get().startAnimation(
            ObjectAnimator.ofFloat(
                {
                    context.get().alpha = it
                },
                context.get().alpha, if (AbilitySystemClient.isActiveHUD()) 1f else 0f
            ).setDuration(200L).setInterpolator(EasingFunctions.EASE_IN_OUT_CUBIC)
        )
    }

    fun scrollWheel(direction: Int) {
        context.skillWheel.scrollByItems(direction)
    }

    val selectedSkill: SkillInfo?
        get() = context.skillWheel.selectedSkillInfo

    private fun triggerSelectedSkill(binding: InputSystem.BindingContext) {
        val info = when (binding.action()) {
            InputConstants.PRESS -> selectedSkill.also { activeSelectedSkill = it }
            InputConstants.RELEASE -> (activeSelectedSkill ?: selectedSkill).also { activeSelectedSkill = null }
            else -> null
        } ?: return
        InputSystem.triggerPrimaryBindingForSkill(info.skill, binding)
    }

    @SubscribeEvent
    fun onTick(@Suppress("unused") event: ClientTickEvent.Post) {
        context.get().tick()
    }

    @SubscribeEvent
    fun onResizeDisplay(@Suppress("unused") event: ResizeDisplayEvent) {
        context.get().requestLayout()
    }

    private class Context : WidgetContext {
        val skillWheel: SkillWheelWidget = SkillWheelWidget()
        private val cp = FrameLayoutWidget()
        private lateinit var cpLayout: FrameLayoutWidget
        private lateinit var cpMount: FrameLayoutWidget
        private lateinit var skillWheelLayout: FrameLayoutWidget
        private lateinit var skillWheelMount: FrameLayoutWidget

        private val root: FrameLayoutWidget = createRoot()

        override fun get(): WidgetContainer {
            return root
        }

        fun createRoot(): FrameLayoutWidget {
            val root = object : FrameLayoutWidget() {
                override fun tick() {
                    applyHudLayout()
                    super.tick()
                }
            }
            root.alpha = 0f
            cpLayout = SerializedUiLayout.load(
                AcademyCraft.academy("ui/layout/ability_cp_hud.json"),
                listOf("cp")
            ) { hudFallback("cp", 240f, 27f) }
            cpMount = SerializedUiLayout.require(cpLayout, "cp") as FrameLayoutWidget
            root.addChild("cp_layout", cpLayout)

            skillWheelLayout = SerializedUiLayout.load(
                AcademyCraft.academy("ui/layout/ability_skill_wheel_hud.json"),
                listOf("skill_wheel")
            ) { hudFallback("skill_wheel", 104f, 119f) }
            skillWheelMount = SerializedUiLayout.require(skillWheelLayout, "skill_wheel") as FrameLayoutWidget
            root.addChild("skill_wheel_layout", skillWheelLayout)
            run {
                cp.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)

                cpMount.addChild("runtime_content", cp)
                run {
                    val sampler: GpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                    val back = ImageWidget(R.textures.hud.cp_bar_background)
                    back.layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
                    back.setSampler(sampler)
                    cp.addChild("back", back)

                    val content: AbstractWidget = object : AbstractWidget() {
                        var textureView: GpuTextureView? = null
                        val particles = mutableListOf<Particle>()
                        var lastCp: Float = AbilitySystemClient.getAvailableCP()
                        var visualCp: Float = AbilitySystemClient.getAvailableCP()

                        override fun renderInternal(context: RenderContext) {
                            super.renderInternal(context)
                            val tint = autoLerpColor(root.alpha)
                            back.setColor(tint.r / 255f, tint.g / 255f, tint.b / 255f)
                            var view = textureView
                            if (view == null || view.isClosed) {
                                try {
                                    val tex = Minecraft.getInstance().textureManager
                                        .getTexture(R.textures.hud.cp_bar_value)
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
                            val progress = visualCp / AbilitySystemClient.getMaxCP() * root.alpha
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
                                override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose) {
                                    val matrix = pose.pose()
                                    val a = (alpha * 255.0f).toInt()
                                    val dest = Vector3f()

                                    writer.beginVertex()
                                    matrix.transformPosition(topLeft, topPadding, 0f, dest)
                                    writer.putVec3f(dest.x, dest.y, dest.z)
                                    writer.putVec2f(topLeftU, topPaddingU)
                                    writer.putColor(tint.r, tint.g, tint.b, a)

                                    writer.beginVertex()
                                    matrix.transformPosition(bottomLeft, height - bottomPadding, 0f, dest)
                                    writer.putVec3f(dest.x, dest.y, dest.z)
                                    writer.putVec2f(bottomLeftU, 1.0f - bottomPaddingU)
                                    writer.putColor(tint.r, tint.g, tint.b, a)

                                    writer.beginVertex()
                                    matrix.transformPosition(bottomRight, height - bottomPadding, 0f, dest)
                                    writer.putVec3f(dest.x, dest.y, dest.z)
                                    writer.putVec2f(bottomRightU, 1.0f - bottomPaddingU)
                                    writer.putColor(tint.r, tint.g, tint.b, a)

                                    writer.beginVertex()
                                    matrix.transformPosition(topRight, topPadding, 0f, dest)
                                    writer.putVec3f(dest.x, dest.y, dest.z)
                                    writer.putVec2f(topRightU, topPaddingU)
                                    writer.putColor(tint.r, tint.g, tint.b, a)
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
                                    context.pose().translate(particle.posOffset, particle.posOffset)
                                    context.submit(object : DrawCommand(
                                        Render.RenderPipelines.IMAGE,
                                        listOf(TextureBinding("Sampler0", view, sampler)),
                                        mutableListOf()
                                    ) {
                                        override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose) {
                                            val matrix = pose.pose()
                                            val a = (particle.alpha * 255.0f).toInt()
                                            val dest = Vector3f()

                                            writer.beginVertex()
                                            matrix.transformPosition(particleTopLeft, topPadding, 0f, dest)
                                            writer.putVec3f(dest.x, dest.y, dest.z)
                                            writer.putVec2f(particleTopLeftU, topPaddingU)
                                            writer.putColor(tint.r, tint.g, tint.b, a)

                                            writer.beginVertex()
                                            matrix.transformPosition(
                                                particleBottomLeft,
                                                height - bottomPadding,
                                                0f,
                                                dest
                                            )
                                            writer.putVec3f(dest.x, dest.y, dest.z)
                                            writer.putVec2f(particleBottomLeftU, 1.0f - bottomPaddingU)
                                            writer.putColor(tint.r, tint.g, tint.b, a)

                                            writer.beginVertex()
                                            matrix.transformPosition(
                                                particleBottomRight,
                                                height - bottomPadding,
                                                0f,
                                                dest
                                            )
                                            writer.putVec3f(dest.x, dest.y, dest.z)
                                            writer.putVec2f(particleBottomRightU, 1.0f - bottomPaddingU)
                                            writer.putColor(tint.r, tint.g, tint.b, a)

                                            writer.beginVertex()
                                            matrix.transformPosition(particleTopRight, topPadding, 0f, dest)
                                            writer.putVec3f(dest.x, dest.y, dest.z)
                                            writer.putVec2f(particleTopRightU, topPaddingU)
                                            writer.putColor(tint.r, tint.g, tint.b, a)
                                        }
                                    })
                                }
                                context.pose().popPose()
                            }
                        }

                        override fun tick() {
                            val currentCp = AbilitySystemClient.getAvailableCP()
                            if (currentCp == lastCp) return

                            val maxCp = AbilitySystemClient.getMaxCP()
                            val increase = currentCp > lastCp

                            if (increase) {
                                cancelShrinkParticles()
                                visualCp = lastCp
                                spawnFillParticles(lastCp / maxCp, currentCp / maxCp, maxCp)
                            } else {
                                cancelIncreaseParticles()
                                visualCp = currentCp
                                spawnShrinkParticles(lastCp / maxCp, currentCp / maxCp, maxCp)
                            }
                            lastCp = currentCp
                        }

                        private fun spawnFillParticles(lastProgress: Float, currentProgress: Float, maxCp: Float) {
                            val animationTime = 750L
                            var progressTracker = lastProgress
                            var i = 0
                            while (true) {
                                val start = progressTracker
                                val nextBoundary = (Mth.floor(start / 0.1f) + 1).toFloat() * 0.1f
                                val end = min(nextBoundary, currentProgress)
                                val progressChanged = abs(start - end) > 0
                                if (!progressChanged) break

                                val particle = Particle(start, end, true)
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
                        }

                        private fun cancelIncreaseParticles() {
                            val iterator = particles.iterator()
                            while (iterator.hasNext()) {
                                val particle = iterator.next()
                                if (particle.increase) {
                                    iterator.remove()
                                    particle.animator?.cancel()
                                }
                            }
                        }

                        private fun spawnShrinkParticles(lastProgress: Float, currentProgress: Float, maxCp: Float) {
                            val animationTime = 750L
                            var progressTracker = lastProgress
                            var i = 0
                            while (true) {
                                val start = progressTracker
                                val nextBoundary = (Mth.ceil(start / 0.1f) - 1).toFloat() * 0.1f
                                val end = max(nextBoundary, currentProgress)
                                val progressChanged = abs(start - end) > 0
                                if (!progressChanged) break

                                val particle = Particle(start, end, false)
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
                                    }
                                })
                                animator.start()
                                particle.animator = animator
                                particles.add(particle)

                                progressTracker = end
                                i++

                                if (progressTracker == currentProgress) break
                            }
                        }

                        private fun cancelShrinkParticles() {
                            val iterator = particles.iterator()
                            while (iterator.hasNext()) {
                                val particle = iterator.next()
                                if (!particle.increase) {
                                    iterator.remove()
                                    particle.animator?.cancel()
                                }
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

                    val sp = object : AbstractWidget() {
                        private var textureView: GpuTextureView? = null

                        override fun renderInternal(context: RenderContext) {
                            super.renderInternal(context)

                            val maximum = AbilitySystemClient.getMaxSP()
                            if (maximum <= 0) return
                            val progress = (AbilitySystemClient.getCurrSP().toFloat() / maximum)
                                .coerceIn(0f, 1f)
                            if (progress <= 0f) return

                            var view = textureView
                            if (view == null || view.isClosed) {
                                try {
                                    val texture = Minecraft.getInstance().textureManager
                                        .getTexture(R.textures.SP_BAR_VALUE)
                                    view = texture.getTextureView()
                                    textureView = view
                                } catch (_: Exception) {
                                    return
                                }
                            }

                            val textureWidth = 960f
                            val textureHeight = 108f
                            val sourceLeft = 497f
                            val sourceTop = 69f
                            val sourceRight = 883f
                            val sourceBottom = 84f
                            val scale = 0.25f
                            val destLeft = sourceLeft * scale
                            val destTop = sourceTop * scale
                            val fullWidth = (sourceRight - sourceLeft) * scale
                            val destHeight = (sourceBottom - sourceTop) * scale
                            val fillWidth = fullWidth * progress
                            val destFillLeft = fullWidth - fillWidth
                            val sourceFillLeft = sourceRight - (sourceRight - sourceLeft) * progress
                            val finalAlpha = alpha * context.accumulatedAlpha

                            context.pose().pushPose()
                            context.pose().translate(destLeft + destFillLeft, destTop)
                            context.submit(object : DrawCommand(
                                Render.RenderPipelines.IMAGE,
                                listOf(TextureBinding("Sampler0", view, sampler)),
                                mutableListOf()
                            ) {
                                override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose) {
                                    val matrix = pose.pose()
                                    val dest = Vector3f()
                                    val u0 = sourceFillLeft / textureWidth
                                    val u1 = sourceRight / textureWidth
                                    val v0 = sourceTop / textureHeight
                                    val v1 = sourceBottom / textureHeight
                                    val a = (finalAlpha * 255f).toInt()

                                    writer.beginVertex()
                                    matrix.transformPosition(0f, 0f, 0f, dest)
                                    writer.putVec3f(dest.x, dest.y, dest.z)
                                    writer.putVec2f(u0, v0)
                                    writer.putColor(255, 255, 255, a)

                                    writer.beginVertex()
                                    matrix.transformPosition(0f, destHeight, 0f, dest)
                                    writer.putVec3f(dest.x, dest.y, dest.z)
                                    writer.putVec2f(u0, v1)
                                    writer.putColor(255, 255, 255, a)

                                    writer.beginVertex()
                                    matrix.transformPosition(fillWidth, destHeight, 0f, dest)
                                    writer.putVec3f(dest.x, dest.y, dest.z)
                                    writer.putVec2f(u1, v1)
                                    writer.putColor(255, 255, 255, a)

                                    writer.beginVertex()
                                    matrix.transformPosition(fillWidth, 0f, 0f, dest)
                                    writer.putVec3f(dest.x, dest.y, dest.z)
                                    writer.putVec2f(u1, v0)
                                    writer.putColor(255, 255, 255, a)
                                }
                            })
                            context.pose().popPose()
                        }
                    }
                    sp.layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
                    cp.addChild("sp", sp)

                    val matter = object : LabelWidget("") {
                        override fun tick() {
                            val isDarkmatter = AbilitySystemClient.getCategory() == AbilityCategories.DARKMATTER.get()
                            val maximum = AbilitySystemClient.getMaxMP()
                            visibility = if (isDarkmatter && maximum > 0f) {
                                Widget.Visibility.VISIBLE
                            } else {
                                Widget.Visibility.GONE
                            }
                            if (!isDarkmatter || maximum <= 0f) return
                            val current = AbilitySystemClient.getCurrMP().roundToInt()
                            val maxValue = maximum.roundToInt()
                            text = "MP $current/$maxValue"
                            super.tick()
                        }
                    }.apply {
                        baseFontSize = 7f
                        setRed(0.84f)
                        setGreen(0.80f)
                        setBlue(1.0f)
                        setDropShadow(true)
                        layoutParams = FrameLayoutWidget.LayoutParams().apply {
                            size(72f, 8f)
                            gravity(Gravity.BOTTOM_RIGHT)
                            marginRight = 156f
                            marginBottom = 1f
                        }
                    }
                    cp.addChild("matter", matter)
                }
            }

            val phase = object : LabelWidget("") {
                override fun tick() {
                    val isDarkmatter = AbilitySystemClient.getCategory() == AbilityCategories.DARKMATTER.get()
                    visibility = if (isDarkmatter && AbilitySystemClient.getDarkmatterLevel() > 0) {
                        Widget.Visibility.VISIBLE
                    } else {
                        Widget.Visibility.GONE
                    }
                    if (visibility == Widget.Visibility.VISIBLE) {
                        val alphaValue = (AbilitySystemClient.getDarkmatterAlpha() * 100f).roundToInt()
                        val betaValue = (AbilitySystemClient.getDarkmatterBeta() * 100f).roundToInt()
                        text = "α$alphaValue%  β$betaValue%"
                    }
                    super.tick()
                }
            }.apply {
                baseFontSize = 7f
                setRed(0.84f)
                setGreen(0.80f)
                setBlue(1.0f)
                setDropShadow(true)
                layoutParams = FrameLayoutWidget.LayoutParams().apply {
                    size(92f, 8f)
                    gravity(Gravity.CENTER)
                }
                translationY = 100f
            }
            root.addChild("darkmatter_phase", phase)

            skillWheel.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
            skillWheel.setVisibleItemCount(7)
                .setCyclic(true)
                .setCurtain(true)
                .setAtmospheric(true)
            skillWheelMount.addChild("runtime_content", skillWheel)

            return root
        }

        private fun applyHudLayout() {
            val minecraft = Minecraft.getInstance()
            val cpRect = HudLayout.Region.CP.rect(minecraft)
            cpMount.translationX = cpRect.x()
            cpMount.translationY = cpRect.y()
            cpMount.scale = HudLayout.Region.CP.scale()
            val wheelRect = HudLayout.Region.SKILL_WHEEL.rect(minecraft)
            skillWheelMount.translationX = wheelRect.x()
            skillWheelMount.translationY = wheelRect.y()
            skillWheelMount.scale = HudLayout.Region.SKILL_WHEEL.scale()
        }

        private fun hudFallback(name: String, width: Float, height: Float): FrameLayoutWidget {
            val layout = FrameLayoutWidget()
            layout.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            val mount = FrameLayoutWidget()
            mount.layoutParams = FrameLayoutWidget.LayoutParams().size(width, height)
            layout.addChild(name, mount)
            return layout
        }
    }

    private class SkillWheelWidget : WheelPickerWidget() {
        private var cachedSignature: String? = null
        private var currentSkills: List<SkillInfo> = emptyList()

        val selectedSkillInfo: SkillInfo?
            get() = currentSkills.getOrNull(targetSelectedPosition)

        override fun tick() {
            refreshItems()
            super.tick()
        }

        override fun computeItemScale(distanceRatio: Float): Float {
            return 1f - distanceRatio * 0.07f
        }

        private fun refreshItems() {
            val skills = buildSkills()
            val signature = skills.joinToString("|") { it.skill.getKeyString() }
            if (signature == cachedSignature) return
            cachedSignature = signature
            currentSkills = skills
            clearChildren()
            if (skills.isEmpty()) {
                visibility = Widget.Visibility.GONE
                return
            }
            visibility = Widget.Visibility.VISIBLE
            skills.forEachIndexed { index, info ->
                val item = createSkillItem(info)
                item.alpha = 0f
                addChild("skill_$index", item)
                item.startAnimation(
                    ObjectAnimator.ofFloat(
                        { value: Float -> item.alpha = value },
                        0f,
                        1f
                    )
                        .setDuration(WHEEL_ITEM_ENTRANCE_DURATION)
                        .setStartDelay((index * WHEEL_ITEM_ENTRANCE_STAGGER).coerceAtMost(WHEEL_ITEM_ENTRANCE_MAX_DELAY))
                        .setInterpolator(EasingFunctions.EASE_OUT_QUAD)
                )
            }
            setSelectedPosition(0)
        }

        private fun buildSkills(): List<SkillInfo> {
            val category = AbilitySystemClient.getCategory()
            return AbilitySystemClient.getSkillInfosForCategory(category)
                .filter { AbilitySystemClient.isSkillLearned(it.skill) }
                .filter { InputSystem.hasActiveBindingForSkill(it.skill) }
        }

        private fun createSkillItem(info: SkillInfo): Widget {
            val row = FrameLayoutWidget()
            row.layoutParams = WidgetContainer.LayoutParams().size(104f, 15f)

            val icon = ImageWidget(info.texture)
            icon.layoutParams = FrameLayoutWidget.LayoutParams()
                .size(14f, 14f)
                .gravity(Gravity.CENTER_VERTICAL)
            row.addChild("icon", icon)

            val name = LabelWidget(info.skill.translatedName)
            name.baseFontSize = 7f
            name.layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.TOP_LEFT)
                .margin(16f, 0f, 1f, 0f)
            row.addChild("name", name)

            val binding = object : LabelWidget("") {
                override fun tick() {
                    val current = InputSystem.formatBindingsForSkill(info.skill).ifBlank {
                        L10n["app.academy.settings.keybind.format.none"]
                    }
                    text = current
                }
            }
            binding.baseFontSize = 5f
            binding.setRed(0.72f)
            binding.setGreen(0.82f)
            binding.setBlue(0.9f)
            binding.layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.BOTTOM_LEFT)
                .margin(16f, 0f, 0f, 0f)
            row.addChild("binding", binding)
            return row
        }
    }

    companion object {
        private lateinit var INSTANCE: AbilityInfoHud

        val instance: AbilityInfoHud get() = INSTANCE

        private const val KEY_NAME_WHEEL_UP = "academy_ability_hud_wheel_up"
        private const val KEY_NAME_WHEEL_DOWN = "academy_ability_hud_wheel_down"
        const val KEY_NAME_RELEASE_SELECTED = "academy_ability_hud_release_selected"

        fun initMain() {
            INSTANCE = AbilityInfoHud()
            NeoForge.EVENT_BUS.register(INSTANCE)
            val config = AcademyCraftClient.Config.INSTANCE
                .getConfig<AbilitySystemClient.Config>(AbilitySystemClient.CONFIG_KEY_ABILITY_SYSTEM)
            var migratedLegacyBindings = false

            fun hudBinding(
                name: String,
                key: Int,
                action: Int,
                vararg obsoleteDefaultKeys: Int
            ): InputSystem.KeyCombination {
                val defaultBinding = InputSystem.combo(
                    InputSystem.InputType.KEYBOARD,
                    key,
                    action,
                    0
                )
                val configured = config.getKeyBinding(name, defaultBinding)
                val defaultKeys = obsoleteDefaultKeys.toSet() + key
                val isLegacyDefault = configured.type == InputSystem.InputType.KEYBOARD
                        && configured.keys.size == 1
                        && configured.keys.first() in defaultKeys
                        && configured.action == action
                        && (configured.modifiers == 0 || configured.modifiers == InputSystem.ANY_MODIFIER)
                        && !configured.availableWhenScreen
                        && !configured.unbound
                if (!isLegacyDefault || configured == defaultBinding) return configured
                config.setKeyBinding(name, defaultBinding)
                migratedLegacyBindings = true
                return defaultBinding
            }

            InputSystem.addKeyBinding(
                KEY_NAME_WHEEL_UP,
                hudBinding(
                    KEY_NAME_WHEEL_UP,
                    InputConstants.KEY_Z,
                    InputConstants.PRESS,
                    InputConstants.KEY_UP
                )
            ) { if (AbilitySystemClient.isActiveHUD()) INSTANCE.scrollWheel(-1) }
            InputSystem.addKeyBinding(
                KEY_NAME_WHEEL_DOWN,
                hudBinding(
                    KEY_NAME_WHEEL_DOWN,
                    InputConstants.KEY_X,
                    InputConstants.PRESS,
                    InputConstants.KEY_DOWN
                )
            ) { if (AbilitySystemClient.isActiveHUD()) INSTANCE.scrollWheel(1) }
            InputSystem.addKeyBinding(
                KEY_NAME_RELEASE_SELECTED,
                hudBinding(KEY_NAME_RELEASE_SELECTED, InputConstants.KEY_C, InputSystem.ANY_ACTION)
            ) { binding ->
                if (AbilitySystemClient.isActiveHUD()) INSTANCE.triggerSelectedSkill(binding)
            }
            if (migratedLegacyBindings) AcademyCraftClient.Config.INSTANCE.save()
        }
    }
}
