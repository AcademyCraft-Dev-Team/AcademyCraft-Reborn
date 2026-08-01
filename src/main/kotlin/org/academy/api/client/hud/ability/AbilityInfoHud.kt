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
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.ability.AbilitySystemClient.SkillInfo
import org.academy.api.client.gui.animation.Animator
import org.academy.api.client.gui.animation.AnimatorListener
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.imgui.ImGuiUIDebugger
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
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
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private data class Color(val r: Int, val g: Int, val b: Int)

private data class ProgColor(val prog: Float, val color: Color)

private val cpColors = listOf(
    ProgColor(0.0f, Color(240, 103, 103)),
    ProgColor(0.35f, Color(255, 174, 68)),
    ProgColor(1.0f, Color(255, 255, 255)),
)

/** How long a CP bar shrink transition takes. */
private const val CP_SHRINK_DURATION = 260L

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

    fun perform(mouseX: Double, mouseY: Double, deltaPartialTick: Float) {
        if (context.get().alpha == 0f) return
        uiContext.perform(context.get(), mouseX, mouseY, deltaPartialTick)
    }

    fun render(target: RenderTarget) {
        if (context.get().alpha == 0f) return
        uiContext.upload(target, false)
        context.get().invalidate()
        ImGuiUIDebugger.render(target, context.get())
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

        private val root: FrameLayoutWidget = createRoot()

        override fun get(): WidgetContainer {
            return root
        }

        fun createRoot(): FrameLayoutWidget {
            val root = FrameLayoutWidget()
            root.alpha = 0f
            run {
                val cp = FrameLayoutWidget()
                cp.layoutParams = FrameLayoutWidget.LayoutParams()
                    .size(240f, 27f)
                    .margin(0f, 4f, 4f, 0f)
                    .gravity(Gravity.TOP_RIGHT)

                root.addChild("cp", cp)
                run {
                    val sampler: GpuSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                    val back = ImageWidget(R.textures.CP_BAR_BACKGROUND)
                    back.layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
                    back.setSampler(sampler)
                    cp.addChild("back", back)

                    val content: AbstractWidget = object : AbstractWidget() {
                        var textureView: GpuTextureView? = null
                        val particles = mutableListOf<Particle>()
                        var lastCp: Float = AbilitySystemClient.getAvailableCP()
                        var visualCp: Float = AbilitySystemClient.getAvailableCP()
                        var visualCpAnimator: ObjectAnimator? = null

                        override fun renderInternal(context: RenderContext) {
                            super.renderInternal(context)
                            val tint = autoLerpColor(root.alpha)
                            back.setColor(tint.r / 255f, tint.g / 255f, tint.b / 255f)
                            var view = textureView
                            if (view == null || view.isClosed) {
                                try {
                                    val tex = Minecraft.getInstance().textureManager
                                        .getTexture(R.textures.CP_BAR_VALUE)
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
                                visualCpAnimator?.cancel()
                                visualCpAnimator = null
                                visualCp = lastCp
                                spawnFillParticles(lastCp / maxCp, currentCp / maxCp, maxCp)
                            } else {
                                cancelIncreaseParticles()
                                animateShrink(currentCp)
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

                        private fun animateShrink(targetCp: Float) {
                            visualCpAnimator?.cancel()
                            visualCpAnimator = null
                            val from = visualCp
                            val animator = ObjectAnimator.ofFloat(
                                { value: Float -> visualCp = value },
                                from,
                                targetCp
                            ).setDuration(CP_SHRINK_DURATION).setInterpolator(EasingFunctions.EASE_OUT_QUAD)
                            animator.addListener(object : AnimatorListener {
                                override fun onAnimationEnd(animation: Animator) {
                                    visualCp = targetCp
                                    visualCpAnimator = null
                                }

                                override fun onAnimationCancel(animation: Animator) {
                                    visualCpAnimator = null
                                }
                            })
                            visualCpAnimator = animator
                            animator.start()
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

            skillWheel.layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                .sizeMode(SizeMode.WRAP_CONTENT)
            skillWheel.setVisibleItemCount(7)
                .setCyclic(true)
                .setCurtain(true)
                .setAtmospheric(true)
            root.addChild("skill_wheel", skillWheel)

            return root
        }
    }

    private class SkillWheelWidget : WheelPickerWidget() {
        private var cachedSignature: String? = null
        private var currentSkills: List<SkillInfo> = emptyList()

        val selectedSkillInfo: SkillInfo?
            get() = currentSkills.getOrNull(selectedPosition)

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
            return AbilitySystemClient.getSkillInfos()[category]
                ?.filter { AbilitySystemClient.isSkillLearned(it.skill) }
                ?: emptyList()
        }

        private fun createSkillItem(info: SkillInfo): Widget {
            val row = LinearLayoutWidget()
            row.orientation = Orientation.HORIZONTAL
            row.spacing = 2f
            val icon = ImageWidget(info.texture)
            icon.layoutParams = WidgetContainer.LayoutParams().size(14f, 14f)
            row.addChild("icon", icon)
            val name = LabelWidget(info.skill.translatedName)
            name.baseFontSize = 8f
            name.layoutParams = WidgetContainer.LayoutParams().gravity(Gravity.CENTER_VERTICAL)
            row.addChild("name", name)
            return row
        }
    }

    companion object {
        private lateinit var INSTANCE: AbilityInfoHud

        val instance: AbilityInfoHud get() = INSTANCE

        private const val KEY_NAME_WHEEL_UP = "academy_ability_hud_wheel_up"
        private const val KEY_NAME_WHEEL_DOWN = "academy_ability_hud_wheel_down"

        fun initMain() {
            INSTANCE = AbilityInfoHud()
            NeoForge.EVENT_BUS.register(INSTANCE)
            InputSystem.addKeyBinding(
                KEY_NAME_WHEEL_UP,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_UP, InputConstants.PRESS)
            ) { if (AbilitySystemClient.isActiveHUD()) INSTANCE.scrollWheel(-1) }
            InputSystem.addKeyBinding(
                KEY_NAME_WHEEL_DOWN,
                InputSystem.combo(InputSystem.InputType.KEYBOARD, InputConstants.KEY_DOWN, InputConstants.PRESS)
            ) { if (AbilitySystemClient.isActiveHUD()) INSTANCE.scrollWheel(1) }
        }
    }
}
