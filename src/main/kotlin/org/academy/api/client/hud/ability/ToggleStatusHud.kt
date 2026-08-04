package org.academy.api.client.hud.ability

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.render.UiContext
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.ImageWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.gui.widget.WidgetContext
import org.academy.api.client.input.InputSystem
import org.academy.api.client.vanilla.ResizeDisplayEvent
import org.academy.api.common.ability.Skill
import org.academy.api.common.ability.LearningHelper
import org.academy.api.common.registries.Registries
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/** Always-on top-left status list for enabled toggle skills. */
class ToggleStatusHud private constructor() {
    private val context = Context()
    private val uiContext = UiContext()

    fun perform(mouseX: Double, mouseY: Double, deltaPartialTick: Float) {
        uiContext.perform(context.get(), mouseX, mouseY, deltaPartialTick)
    }

    fun render(target: RenderTarget) {
        uiContext.upload(target, false)
        context.get().invalidate()
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
        private val statuses = LinearLayoutWidget()
        private val root = object : FrameLayoutWidget() {
            override fun tick() {
                refresh()
                super.tick()
            }
        }
        private var cachedSignature: String? = null

        init {
            statuses.orientation = Orientation.VERTICAL
            statuses.spacing = 2f
            statuses.visibility = Widget.Visibility.GONE
            statuses.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.WRAP_CONTENT)
                .gravity(Gravity.TOP_LEFT)
                .margin(8f, 8f, 0f, 0f)
            root.addChild("toggle_statuses", statuses)
        }

        override fun get(): WidgetContainer = root

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
            val signature = active.joinToString("|") { "${it.keyString}=${statusText(it)}" }
            if (signature == cachedSignature) return
            cachedSignature = signature
            statuses.clearChildren()
            statuses.visibility = if (active.isEmpty()) Widget.Visibility.GONE else Widget.Visibility.VISIBLE
            active.forEachIndexed { index, skill ->
                statuses.addChild("toggle_$index", createStatusRow(skill))
            }
        }

        private fun createStatusRow(skill: Skill): Widget {
            val row = FrameLayoutWidget()
            row.layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.WRAP_CONTENT)

            val background = FillWidget(0x78000000)
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

            val name = LabelWidget(skill.translatedName)
            name.baseFontSize = 8f
            name.layoutParams = LinearLayoutWidget.LayoutParams().gravity(Gravity.CENTER_VERTICAL)
            content.addChild("name", name)

            val state = LabelWidget(statusText(skill))
            state.baseFontSize = 7f
            state.setRed(0.42f)
            state.setGreen(1f)
            state.setBlue(0.62f)
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

        @JvmStatic
        fun registerStateProvider(skill: Skill, provider: BooleanSupplier) {
            stateProviders[skill] = provider
        }

        @JvmStatic
        fun registerDetailProvider(skill: Skill, provider: Supplier<String>) {
            detailProviders[skill] = provider
        }

        private fun statusText(skill: Skill): String {
            return detailProviders[skill]?.get()
                ?: Language.getInstance().getOrDefault("hud.academy.toggle_status.on")
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
