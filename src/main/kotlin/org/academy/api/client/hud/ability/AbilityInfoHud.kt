package org.academy.api.client.hud.ability

import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.AcademyCraftClient
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.ability.AbilitySystemClient.SkillInfo
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.MeasureSpec
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.Canvas
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.render.VertexWriter
import org.academy.api.client.gui.widget.*
import org.academy.api.client.input.InputSystem
import org.academy.api.client.render.Render
import org.academy.api.client.render.TextureBinding
import org.academy.api.client.resources.R
import org.academy.api.client.vanilla.ResizeDisplayEvent
import org.academy.api.common.ability.AbilityCategory
import org.academy.api.common.util.L10n
import org.academy.internal.client.hud.HudLayout
import org.academy.internal.common.ability.AbilityCategories
import org.joml.Vector3f
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

private const val SP_TEXTURE_WIDTH = 960f
private const val SP_TEXTURE_HEIGHT = 108f
private const val SP_SOURCE_LEFT = 497f
private const val SP_SOURCE_TOP = 69f
private const val SP_SOURCE_RIGHT = 883f
private const val SP_SOURCE_BOTTOM = 84f
private const val SP_SOURCE_SCALE = 0.25f

private const val MP_LABEL_WIDTH = 72f
private const val MP_LABEL_HEIGHT = 8f
private const val MP_LABEL_MARGIN_RIGHT = 156f
private const val MP_LABEL_MARGIN_BOTTOM = 1f

private const val DARKMATTER_LABEL_WIDTH = 92f
private const val DARKMATTER_LABEL_HEIGHT = 8f
private const val DARKMATTER_LABEL_OFFSET_Y = 100f

internal fun shouldShowAbilityResource(category: AbilityCategory, maximum: Float): Boolean {
    return category.resourceSpec.isPresent && maximum.isFinite() && maximum > 0f
}

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

    val root: WidgetContainer
        get() = context.get()

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

    fun rebuildLayout() {
        context.rebuildLayout()
    }

    @SubscribeEvent
    fun onResizeDisplay(@Suppress("unused") event: ResizeDisplayEvent) {
        context.rebuildLayout()
    }

    private class CpBarWidget(
        private val barBackground: ImageWidget,
        private val sampler: GpuSampler,
        private val hudAlpha: () -> Float,
    ) : AbstractWidget() {
        private val controller = CpDisplayController()
        private var textureView: GpuTextureView? = null

        init {
            setFrameUpdate {
                controller.update(
                    AbilitySystemClient.getAvailableCP(),
                    AbilitySystemClient.getMaxCP()
                )
                true
            }
        }

        override fun renderInternal(context: Canvas) {
            super.renderInternal(context)
            val tint = autoLerpColor(hudAlpha())
            barBackground.setColor(tint.r / 255f, tint.g / 255f, tint.b / 255f)
            val view = resolveTexture() ?: return
            controller.render(
                context,
                CpBarGeometry(width, height),
                sampler,
                view,
                tint.r,
                tint.g,
                tint.b,
                alpha * context.accumulatedAlpha,
                hudAlpha()
            )
        }

        private fun resolveTexture(): GpuTextureView? {
            val current = textureView
            if (current != null && !current.isClosed) return current
            return try {
                Minecraft.getInstance().textureManager
                    .getTexture(R.textures.hud.cp_bar_value)
                    .getTextureView()
                    .also { textureView = it }
            } catch (_: Exception) {
                null
            }
        }
    }

    private class SpBarWidget(private val sampler: GpuSampler) : AbstractWidget() {
        private var textureView: GpuTextureView? = null

        override fun renderInternal(context: Canvas) {
            super.renderInternal(context)
            val maximum = AbilitySystemClient.getMaxSP()
            if (maximum <= 0) return
            val progress = (AbilitySystemClient.getCurrSP().toFloat() / maximum).coerceIn(0f, 1f)
            if (progress <= 0f) return
            val view = resolveTexture() ?: return

            val destLeft = SP_SOURCE_LEFT * SP_SOURCE_SCALE
            val destTop = SP_SOURCE_TOP * SP_SOURCE_SCALE
            val fullWidth = (SP_SOURCE_RIGHT - SP_SOURCE_LEFT) * SP_SOURCE_SCALE
            val destHeight = (SP_SOURCE_BOTTOM - SP_SOURCE_TOP) * SP_SOURCE_SCALE
            val fillWidth = fullWidth * progress
            val destFillLeft = fullWidth - fillWidth
            val sourceFillLeft = SP_SOURCE_RIGHT - (SP_SOURCE_RIGHT - SP_SOURCE_LEFT) * progress
            val finalAlpha = alpha * context.accumulatedAlpha

            context.pose().pushPose()
            context.pose().translate(destLeft + destFillLeft, destTop)
            context.submit(object : DrawCommand(
                Render.RenderPipelines.IMAGE,
                listOf(TextureBinding("Sampler0", view, sampler)),
                mutableListOf()
            ) {
                override fun generateVertices(writer: VertexWriter, pose: PoseStack.Pose, alphaMul: Float) {
                    val matrix = pose.pose()
                    val dest = Vector3f()
                    val u0 = sourceFillLeft / SP_TEXTURE_WIDTH
                    val u1 = SP_SOURCE_RIGHT / SP_TEXTURE_WIDTH
                    val v0 = SP_SOURCE_TOP / SP_TEXTURE_HEIGHT
                    val v1 = SP_SOURCE_BOTTOM / SP_TEXTURE_HEIGHT
                    val a = (finalAlpha * alphaMul * 255f).toInt()

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

        private fun resolveTexture(): GpuTextureView? {
            val current = textureView
            if (current != null && !current.isClosed) return current
            return try {
                Minecraft.getInstance().textureManager
                    .getTexture(R.textures.SP_BAR_VALUE)
                    .getTextureView()
                    .also { textureView = it }
            } catch (_: Exception) {
                null
            }
        }
    }

    private class Context : WidgetContext {
        private val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        private lateinit var cp: FrameLayoutWidget
        lateinit var skillWheel: SkillWheelWidget
        private val root: FrameLayoutWidget = standaloneFrame { alpha = 0f }

        init {
            buildContent()
            root.dispatchAttached()
        }

        override fun get(): WidgetContainer {
            return root
        }

        fun rebuildLayout() {
            root.clearChildren()
            buildContent()
            root.requestLayout()
        }

        private fun buildContent() {
            HudLayout.registerLiveSize(HudLayout.Region.SKILL_WHEEL.configKey, null)
            val minecraft = Minecraft.getInstance()
            val cpBase = HudLayout.Region.CP.baseRect(minecraft)
            cp = root.frame("cp") {
                layoutParams = FrameLayoutWidget.LayoutParams().apply {
                    size(HudLayout.Region.CP.nominalWidth, HudLayout.Region.CP.nominalHeight)
                    gravity(Gravity.TOP_LEFT)
                    marginLeft = cpBase.x
                    marginTop = cpBase.y
                }
                origin = 0f
                translationX = HudLayout.Region.CP.translateX
                translationY = HudLayout.Region.CP.translateY
                scaleX = HudLayout.Region.CP.scaleXY
                scaleY = HudLayout.Region.CP.scaleXY
                visibility = if (HudLayout.Region.CP.hidden) {
                    Widget.Visibility.GONE
                } else {
                    Widget.Visibility.VISIBLE
                }

                val background = image(R.textures.hud.cp_bar_background) {
                    sizeMode(SizeMode.MATCH_PARENT)
                    sampler(sampler)
                }

                add("content", CpBarWidget(background, sampler) { root.alpha })
                add("sp", SpBarWidget(sampler))

                text("") {
                    setFrameUpdate {
                        val category = AbilitySystemClient.getCategory()
                        val maximum = AbilitySystemClient.getMaxMP()
                        val shouldShow = shouldShowAbilityResource(category, maximum)
                        visibility = if (shouldShow) {
                            Widget.Visibility.VISIBLE
                        } else {
                            Widget.Visibility.GONE
                        }
                        text = if (shouldShow) {
                            val current = AbilitySystemClient.getCurrMP().roundToInt()
                            val maxValue = maximum.roundToInt()
                            "MP $current/$maxValue"
                        } else {
                            ""
                        }
                        true
                    }

                    textSize = 7f
                    rgb(0.84f, 0.80f, 1.0f)
                    layoutParams = FrameLayoutWidget.LayoutParams().apply {
                        size(MP_LABEL_WIDTH, MP_LABEL_HEIGHT)
                        gravity(Gravity.BOTTOM_RIGHT)
                        marginRight = MP_LABEL_MARGIN_RIGHT
                        marginBottom = MP_LABEL_MARGIN_BOTTOM
                    }
                    gravity = Gravity.BOTTOM_RIGHT
                }

                text("") {
                    setFrameUpdate {
                        val isDarkmatter = AbilitySystemClient.getCategory() == AbilityCategories.DARKMATTER.get()
                        visibility = if (isDarkmatter) {
                            Widget.Visibility.VISIBLE
                        } else {
                            Widget.Visibility.GONE
                        }
                        text = if (isDarkmatter) {
                            val alphaValue = (AbilitySystemClient.getDarkmatterAlpha() * 100f).roundToInt()
                            val betaValue = (AbilitySystemClient.getDarkmatterBeta() * 100f).roundToInt()
                            "α$alphaValue%  β$betaValue%"
                        } else {
                            ""
                        }
                        true
                    }

                    textSize = 7f
                    rgb(0.84f, 0.80f, 1.0f)
                    layoutParams = FrameLayoutWidget.LayoutParams().apply {
                        size(DARKMATTER_LABEL_WIDTH, DARKMATTER_LABEL_HEIGHT)
                        gravity(Gravity.CENTER)
                    }
                    gravity = Gravity.CENTER
                    translationY = DARKMATTER_LABEL_OFFSET_Y
                }
            }

            val wheelBase = HudLayout.Region.SKILL_WHEEL.baseRect(minecraft)
            skillWheel = root.add("skill_wheel", SkillWheelWidget()) {
                widthMode(SizeMode.WRAP_CONTENT)
                height(HudLayout.Region.SKILL_WHEEL.nominalHeight)
                gravity(Gravity.TOP_RIGHT)
                marginTop(wheelBase.y)

                originX = 1f
                originY = 0f
                translationX = HudLayout.Region.SKILL_WHEEL.translateX
                translationY = HudLayout.Region.SKILL_WHEEL.translateY
                scaleX = HudLayout.Region.SKILL_WHEEL.scaleXY
                scaleY = HudLayout.Region.SKILL_WHEEL.scaleXY

                visibleItemCount = 7
                isCyclic = true
                isCurtain = true
                isAtmospheric = true
            }
            HudLayout.registerLiveSize(HudLayout.Region.SKILL_WHEEL.configKey) {
                val minecraft = Minecraft.getInstance()
                val region = HudLayout.Region.SKILL_WHEEL
                skillWheel.measure(
                    MeasureSpec(MeasureSpec.Mode.AT_MOST, minecraft.window.guiScaledWidth.toFloat()),
                    MeasureSpec(MeasureSpec.Mode.EXACTLY, region.nominalHeight)
                )
                HudLayout.Rect(0f, 0f, skillWheel.measuredWidth, skillWheel.measuredHeight)
            }
        }
    }

    private class SkillWheelWidget : WheelPickerWidget() {
        private var cachedSignature: String? = null
        private var currentSkills: List<SkillInfo> = emptyList()

        val selectedSkillInfo: SkillInfo?
            get() = currentSkills.getOrNull(targetSelectedPosition)

        init {
            setFrameUpdate {
                refreshItems()
                true
            }
        }

        override fun computeItemScale(distanceRatio: Float): Float {
            return 1f - distanceRatio * 0.07f
        }

        private fun refreshItems() {
            val skills = buildSkills()
            val hidden = HudLayout.Region.SKILL_WHEEL.hidden
            val signature = (if (hidden) "1" else "0") + "|" + skills.joinToString("|") { it.skill.getKeyString() }
            if (signature == cachedSignature) return
            cachedSignature = signature
            currentSkills = skills
            clearChildren()
            if (skills.isEmpty() || hidden) {
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
                        .setStartDelay(
                            (index * WHEEL_ITEM_ENTRANCE_STAGGER).coerceAtMost(WHEEL_ITEM_ENTRANCE_MAX_DELAY)
                        )
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
            return standaloneRow(spacing = 2f) {
                height(16f)
                widthMode(SizeMode.WRAP_CONTENT)

                image(info.texture) {
                    size(16f, 16f)
                    gravity(Gravity.CENTER_VERTICAL)
                }

                column {
                    weight(1f)
                    marginRight(2f)

                    text(info.skill.translatedName) {
                        textSize = 10f
                    }

                    text("") {
                        setFrameUpdate {
                            text = InputSystem.formatBindingsForSkill(info.skill).ifBlank {
                                L10n["app.academy.settings.keybind.format.none"]
                            }
                            true
                        }

                        textSize = 6f
                        rgb(0.72f, 0.82f, 0.9f)
                    }
                }
            }
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

            InputSystem.addKeyBinding(
                KEY_NAME_WHEEL_UP,
                getHudBindingMigratingDefaults(
                    config,
                    KEY_NAME_WHEEL_UP,
                    InputConstants.KEY_Z,
                    InputConstants.PRESS,
                    InputConstants.KEY_UP
                )
            ) { if (AbilitySystemClient.isActiveHUD()) INSTANCE.scrollWheel(-1) }
            InputSystem.addKeyBinding(
                KEY_NAME_WHEEL_DOWN,
                getHudBindingMigratingDefaults(
                    config,
                    KEY_NAME_WHEEL_DOWN,
                    InputConstants.KEY_X,
                    InputConstants.PRESS,
                    InputConstants.KEY_DOWN
                )
            ) { if (AbilitySystemClient.isActiveHUD()) INSTANCE.scrollWheel(1) }
            InputSystem.addExclusiveKeyBinding(
                KEY_NAME_RELEASE_SELECTED,
                getHudBindingMigratingDefaults(
                    config,
                    KEY_NAME_RELEASE_SELECTED,
                    InputConstants.KEY_C,
                    InputSystem.ANY_ACTION
                ),
                { binding -> INSTANCE.triggerSelectedSkill(binding) },
                { AbilitySystemClient.isActiveHUD() }
            )
            InputSystem.setKeyBindingEnabled(
                KEY_NAME_RELEASE_SELECTED, config.isKeyBindingEnabled(KEY_NAME_RELEASE_SELECTED)
            )
            AcademyCraftClient.Config.INSTANCE.save()
        }
    }
}
