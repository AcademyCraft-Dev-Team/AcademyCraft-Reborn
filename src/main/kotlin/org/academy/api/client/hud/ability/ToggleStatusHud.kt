package org.academy.api.client.hud.ability

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.widget.*
import org.academy.api.client.input.InputSystem
import org.academy.api.client.vanilla.ResizeDisplayEvent
import org.academy.api.common.ability.LearningHelper
import org.academy.api.common.ability.Skill
import org.academy.api.common.registries.Registries
import org.academy.api.common.util.L10n
import org.academy.internal.client.hud.HudLayout
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/** Always-on top-left status list for enabled toggle skills. */
class ToggleStatusHud private constructor() {
    private val context = Context()
    private val uiContext = UiContext()

    val root: WidgetContainer
        get() = context.get()

    fun perform(mouseX: Double, mouseY: Double, deltaPartialTick: Float) {
        uiContext.perform(context.get(), mouseX, mouseY, deltaPartialTick)
    }

    fun render(target: RenderTarget) {
        uiContext.upload(target, false)
        context.get().invalidate()
    }

    fun rebuildLayout() {
        context.rebuildLayout()
    }

    @SubscribeEvent
    fun onResizeDisplay(@Suppress("unused") event: ResizeDisplayEvent) {
        context.rebuildLayout()
    }

    private class Context : WidgetContext {
        private lateinit var statuses: LinearLayoutWidget
        private var cachedSignature: String? = null
        private val root = FrameLayoutWidget().apply {
            setFrameUpdate {
                refresh()
                true
            }
        }

        init {
            build()
            root.dispatchAttached()
        }

        override fun get(): WidgetContainer = root

        fun rebuildLayout() {
            root.clearChildren()
            build()
            root.requestLayout()
        }

        private fun build() {
            cachedSignature = null
            val region = HudLayout.Region.TOGGLE_STATUS
            val base = region.baseRect(Minecraft.getInstance())
            val layout = FrameLayoutWidget()
            layout.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            val mount = LinearLayoutWidget()
            mount.orientation = Orientation.VERTICAL
            mount.spacing = 2f
            mount.layoutParams = FrameLayoutWidget.LayoutParams().apply {
                size(region.nominalWidth, region.nominalHeight)
                gravity(Gravity.TOP_LEFT)
                marginLeft = base.x
                marginTop = base.y
            }
            mount.origin = 0f
            mount.translationX = region.translateX
            mount.translationY = region.translateY
            mount.scaleX = region.scaleXY
            mount.scaleY = region.scaleXY
            mount.visibility = Widget.Visibility.GONE
            statuses = mount
            layout.addChild("toggle_statuses", mount)
            root.addChild("layout", layout)
        }

        private fun activeSkills(): List<Skill> {
            if (Minecraft.getInstance().player == null) return emptyList()
            val category = AbilitySystemClient.getCategory()
            return Registries.SKILLS.asSequence()
                .filter { skill -> LearningHelper.isSkillAvailableForCategory(category, skill) }
                .filter(AbilitySystemClient::isSkillLearned)
                .filter { skill ->
                    InputSystem.hasToggleBindingForSkill(skill) || hasStateProvider(skill)
                }
                .filter(::isToggleActive)
                .sortedWith(compareBy<Skill> { it.recommendedLevel.levelCode }.thenBy { it.keyString })
                .toList()
        }

        private fun refresh() {
            val active = activeSkills()
            val hidden = HudLayout.Region.TOGGLE_STATUS.hidden
            val signature = (if (hidden) "1" else "0") + "|" +
                    active.joinToString("|") { "${it.keyString}=${statusText(it)}" }
            if (signature == cachedSignature) return
            cachedSignature = signature
            statuses.clearChildren()
            statuses.visibility = if (hidden || active.isEmpty()) {
                Widget.Visibility.GONE
            } else {
                Widget.Visibility.VISIBLE
            }
            active.forEachIndexed { index, skill ->
                statuses.addChild("toggle_$index", createStatusRow(skill))
            }
        }

        private fun createStatusRow(skill: Skill): Widget {
            val row = FrameLayoutWidget()
            row.layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.WRAP_CONTENT)

            val background = FillWidget(0x48343434)
            background.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            row.addChild("background", background)

            val content = LinearLayoutWidget()
            content.orientation = Orientation.HORIZONTAL
            content.spacing = 3f
            content.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.WRAP_CONTENT)
                .padding(4f, 2f, 5f, 2f)
            row.addChild("content", content)

            val info = AbilitySystemClient.getSkillInfosForCategory(AbilitySystemClient.getCategory())
                .firstOrNull { it.skill === skill }
            if (info != null) {
                val icon = ImageWidget(info.texture)
                icon.layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(11f, 11f)
                    .gravity(Gravity.CENTER_VERTICAL)
                content.addChild("icon", icon)
            }

            val name = TextWidget(skill.translatedName)
            name.textSize = 8f
            name.layoutParams = LinearLayoutWidget.LayoutParams().gravity(Gravity.CENTER_VERTICAL)
            content.addChild("name", name)

            val state = TextWidget(statusText(skill))
            state.textSize = 7f
            state.rgb(37f / 255f, 196f / 255f, 1f)
            state.layoutParams = LinearLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER_VERTICAL)
                .margin(2f, 0f)
            content.addChild("state", state)
            return row
        }
    }

    companion object {
        private val stateProviders = ConcurrentHashMap<Skill, BooleanSupplier>()
        private val detailProviders = ConcurrentHashMap<Skill, Supplier<String>>()
        private lateinit var INSTANCE: ToggleStatusHud

        val instance: ToggleStatusHud
            get() = INSTANCE

        fun registerStateProvider(skill: Skill, provider: BooleanSupplier) {
            stateProviders[skill] = provider
        }

        fun registerDetailProvider(skill: Skill, provider: Supplier<String>) {
            detailProviders[skill] = provider
        }

        private fun statusText(skill: Skill): String {
            return detailProviders[skill]?.get()
                ?: L10n["hud.academy.toggle_status.on"]
        }

        private fun isToggleActive(skill: Skill): Boolean {
            val provider = stateProviders[skill]
            return provider?.asBoolean
                ?: AbilitySystemClient.getSkillData(skill).map { it.isEnabled }.orElse(false)
        }

        private fun hasStateProvider(skill: Skill): Boolean = stateProviders.containsKey(skill)

        fun initMain() {
            INSTANCE = ToggleStatusHud()
            NeoForge.EVENT_BUS.register(INSTANCE)
        }
    }
}
