package org.academy.internal.client.app.settings.ui

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.neoforged.fml.ModList
import org.academy.AcademyCraft
import org.academy.AcademyCraftClient
import org.academy.api.client.app.App
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.config.KeyBindingConfig
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator.Companion.ofFloat
import org.academy.api.client.gui.animation.StateListAnimator
import org.academy.api.client.gui.event.KeyEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.AbstractWidget
import org.academy.api.client.gui.widget.ButtonWidget
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.ImageWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.RadioButtonWidget
import org.academy.api.client.gui.widget.RadioGroupWidget
import org.academy.api.client.gui.widget.ScrollPanelWidget
import org.academy.api.client.gui.widget.ToggleButtonWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.gui.widget.WidgetContext
import org.academy.api.client.hud.terminal.TerminalConfig
import org.academy.api.client.hud.terminal.TerminalHud
import org.academy.api.client.input.InputSystem
import org.academy.api.client.resources.R
import org.academy.api.common.ability.Skill
import org.lwjgl.glfw.GLFW
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

object SettingsApp : App {
    private const val PAGE_KEYBINDINGS = 0
    private const val PAGE_ABOUT = 1

    override fun createContext(): WidgetContext {
        return Context()
    }

    override fun name(): String {
        return "Settings"
    }

    override fun icon(): Identifier {
        return R.textures.gui.icon.icon_settings
    }

    private class Context : WidgetContext {
        private val panelContainer = FrameLayoutWidget()
        private var capturing: CaptureTarget? = null
        private var pendingType: InputSystem.InputType? = null
        private val pendingKeys: MutableSet<Int> = linkedSetOf()
        private var pendingMouseButton: Int = -1
        private var pendingModifiers: Int = 0
        private var pendingCancel: Boolean = false

        private val captureHint: LabelWidget = object : LabelWidget("") {
            override fun tick() {
                super.tick()
                updateHint()
            }
        }
        private val captureLayer = createCaptureLayer()

        private val root: FrameLayoutWidget = createRoot()

        override fun get(): Widget {
            return root
        }

        private data class BindingSection(
            val id: String,
            val title: String,
            val icon: Identifier,
            val config: KeyBindingConfig,
            val persist: (KeyBindingConfig) -> Unit
        )

        private data class CaptureTarget(
            val section: BindingSection,
            val bindingName: String
        )

        private fun createRoot(): FrameLayoutWidget {
            val root = FrameLayoutWidget()
            root.layoutParams = WidgetContainer.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
            run {
                val content = LinearLayoutWidget()
                content.orientation = Orientation.VERTICAL
                content.spacing = 1f
                content.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                root.addChild("content", content)
                run {
                    val topBar = LinearLayoutWidget()
                    topBar.orientation = Orientation.HORIZONTAL
                    topBar.layoutParams = LinearLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    content.addChild("top_bar", topBar)
                    run {
                        val backButton = ButtonWidget()
                        backButton.layoutParams = LinearLayoutWidget.LayoutParams()
                            .margin(2f, 2f, 2f, 0f)
                            .size(16f, 16f)
                        backButton.onClickListener = {
                            TerminalHud.INSTANCE.closeApp()
                        }
                        topBar.addChild("back_button", backButton)
                        run {
                            val arrow = ImageWidget(R.textures.gui.icon.arrow_back)
                            arrow.setSampler(FilterMode.LINEAR, false)
                            arrow.layoutParams = FrameLayoutWidget.LayoutParams()
                                .sizeMode(SizeMode.MATCH_PARENT)
                            backButton.addChild("arrow", arrow)
                        }

                        val title = LabelWidget(name())
                        title.layoutParams = LinearLayoutWidget.LayoutParams()
                            .weight(1f)
                            .height(0f)
                            .gravity(Gravity.CENTER)
                        topBar.addChild("title", title)
                    }

                    val splitLine = FillWidget(-0x1)
                    splitLine.layoutParams = LinearLayoutWidget.LayoutParams()
                        .height(1f)
                        .widthMode(SizeMode.MATCH_PARENT)
                        .padding(2f, 0f)
                    content.addChild("split_line", splitLine)

                    content.addChild("tab_bar", createTabBar())
                    captureHint.layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(10f)
                        .gravity(Gravity.CENTER)
                    content.addChild("capture_hint", captureHint)
                    panelContainer.layoutParams = LinearLayoutWidget.LayoutParams()
                        .weight(1f)
                        .widthMode(SizeMode.MATCH_PARENT)
                        .padding(2f)
                    content.addChild("panel", panelContainer)

                    showPage(PAGE_KEYBINDINGS)
                }
            }

            captureLayer.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
            captureLayer.isEnabled = false
            captureLayer.visibility = Widget.Visibility.INVISIBLE
            root.addChild("capture_layer", captureLayer)

            return root
        }

        private fun createTabBar(): RadioGroupWidget {
            val tabBar = RadioGroupWidget()
            tabBar.orientation = Orientation.HORIZONTAL
            tabBar.spacing = 2f
            tabBar.layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
            tabBar.addChild("keybindings", createTabButton(name(), PAGE_KEYBINDINGS))
            tabBar.addChild("about", createTabButton("About", PAGE_ABOUT))
            tabBar.selectButton(tabBar.children.values.first() as RadioButtonWidget)
            return tabBar
        }

        private fun createTabButton(text: String, page: Int): RadioButtonWidget {
            val button = RadioButtonWidget()
            button.layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(14f)
            button.onClickListener = { _: Widget? ->
                showPage(page)
            }
            run {
                val back = FillWidget(0)
                back.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                button.addChild("back", back)

                button.addChild("text", LabelWidget(text).apply {
                    layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
                        .gravity(Gravity.CENTER)
                })

                val progressState = AtomicReference(0f)
                val updateState = Consumer<Float> { progress ->
                    progressState.set(progress)
                    val alpha = (progress * 0.5f * 255).toInt()
                    back.setColor(ARGB.color(alpha, 255, 255, 255))
                }
                val animator = StateListAnimator()
                animator.addState(
                    Widget.SELECTED,
                    ofFloat({ progressState.get() }, updateState, 1.0f)
                        .setDuration(100)
                        .setInterpolator(EasingFunctions.EASE_OUT_SINE)
                )
                animator.addState(
                    Widget.NONE,
                    ofFloat({ progressState.get() }, updateState, 0.0f)
                        .setDuration(100)
                        .setInterpolator(EasingFunctions.EASE_OUT_SINE)
                )
                button.stateListAnimator = animator
            }
            return button
        }

        private fun showPage(page: Int) {
            panelContainer.clearChildren()
            when (page) {
                PAGE_KEYBINDINGS -> panelContainer.addChild("keybindings", createKeybindPage())
                PAGE_ABOUT -> panelContainer.addChild("about", createAboutPage())
            }
        }

        private fun createKeybindPage(): ScrollPanelWidget {
            val panel = ScrollPanelWidget()
            panel.layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)

            val list = LinearLayoutWidget()
            list.orientation = Orientation.VERTICAL
            list.spacing = 2f
            list.layoutParams = WidgetContainer.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)

            for (section in createGeneralSections()) {
                if (section.config.keyBindings.isEmpty()) continue
                list.addChild(section.id, createBindingSection(section))
            }

            for (info in AbilitySystemClient.getSkillInfosForCategory(AbilitySystemClient.getCategory())) {
                val skill = info.skill
                val config = tryGetConfig(skill) ?: continue
                if (config.keyBindings.isEmpty()) continue
                val section = BindingSection(
                    skill.getKeyString(),
                    skill.translatedName,
                    resolveSkillIcon(info),
                    config
                ) { updated ->
                    AcademyCraftClient.Config.INSTANCE.setConfig(skill.getKey(), updated)
                }
                list.addChild(section.id, createBindingSection(section))
            }
            panel.setContent(list)
            return panel
        }

        private fun createGeneralSections(): List<BindingSection> {
            val terminalConfig = AcademyCraftClient.Config.INSTANCE
                .getConfig<TerminalConfig>(TerminalHud.CONFIG_KEY)
            val abilityConfig = AcademyCraftClient.Config.INSTANCE
                .getConfig<AbilitySystemClient.Config>(AbilitySystemClient.CONFIG_KEY_ABILITY_SYSTEM)
            return listOf(
                BindingSection(
                    "general_terminal",
                    Language.getInstance().getOrDefault("app.academy.settings.keybind.group.terminal"),
                    R.textures.gui.terminal.icon,
                    terminalConfig
                ) { updated ->
                    AcademyCraftClient.Config.INSTANCE.setConfig(TerminalHud.CONFIG_KEY, updated)
                },
                BindingSection(
                    "general_ability_hud",
                    Language.getInstance().getOrDefault("app.academy.settings.keybind.group.ability_hud"),
                    AbilitySystemClient.getCategory().developerIcon,
                    abilityConfig
                ) { updated ->
                    AcademyCraftClient.Config.INSTANCE.setConfig(
                        AbilitySystemClient.CONFIG_KEY_ABILITY_SYSTEM,
                        updated
                    )
                }
            )
        }

        private fun tryGetConfig(skill: Skill): KeyBindingConfig? {
            if (!AcademyCraftClient.Config.INSTANCE.hasTypeHandler(skill.getKey())) return null
            return runCatching {
                AcademyCraftClient.Config.INSTANCE.getConfig<KeyBindingConfig>(skill.getKey())
            }.getOrNull()
        }

        private fun resolveSkillIcon(info: AbilitySystemClient.SkillInfo): Identifier {
            val skill = info.skill
            val placeholder = R.textures.gui.icon.close
            val categoryIcon = skill.category.developerIcon
            val inferred = Identifier.fromNamespaceAndPath(
                skill.getKey().namespace,
                "textures/ability/${skill.category.getKey().path}/skill/${skill.getKey().path}/icon.png"
            )
            val resourceManager = Minecraft.getInstance().resourceManager
            return sequenceOf(info.texture, skill.icon, inferred)
                .distinct()
                .firstOrNull { icon ->
                    icon != placeholder
                            && icon != categoryIcon
                            && resourceManager.getResource(icon).isPresent
                }
                ?: placeholder
        }

        private fun createBindingSection(sectionInfo: BindingSection): LinearLayoutWidget {
            val section = LinearLayoutWidget()
            section.orientation = Orientation.VERTICAL
            section.spacing = 1f
            section.layoutParams = WidgetContainer.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)

            val header = LinearLayoutWidget()
            header.orientation = Orientation.HORIZONTAL
            header.spacing = 2f
            header.layoutParams = WidgetContainer.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
            section.addChild("header", header)
            run {
                val icon = ImageWidget(sectionInfo.icon)
                icon.setSampler(FilterMode.LINEAR, false)
                icon.layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(16f, 16f)
                header.addChild("icon", icon)

                val name = LabelWidget(sectionInfo.title)
                name.layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(0f)
                    .gravity(Gravity.CENTER_LEFT)
                header.addChild("name", name)
            }

            for ((bindingName, combo) in sectionInfo.config.keyBindings) {
                section.addChild(bindingName, createBindingRow(sectionInfo, bindingName, combo))
            }
            return section
        }

        private fun createBindingRow(
            section: BindingSection,
            bindingName: String,
            combo: InputSystem.KeyCombination
        ): LinearLayoutWidget {
            val row = LinearLayoutWidget()
            row.orientation = Orientation.HORIZONTAL
            row.spacing = 2f
            row.layoutParams = WidgetContainer.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)

            val name = LabelWidget(
                Language.getInstance().getOrDefault("key.academy.$bindingName")
            )
            name.layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_LEFT)
            row.addChild("name", name)

            val keyLabel = LabelWidget(combo.displayName())
            keyLabel.scale = 0.7f
            keyLabel.layoutParams = LinearLayoutWidget.LayoutParams()
                .height(10f)
                .gravity(Gravity.CENTER)
            row.addChild("key", keyLabel)

            val toggle = ToggleButtonWidget()
            toggle.setChecked(section.config.isKeyBindingEnabled(bindingName))
            toggle.layoutParams = LinearLayoutWidget.LayoutParams()
                .size(16f, 9f)
                .gravity(Gravity.CENTER)
            toggle.setOnCheckedChangeListener(object : ToggleButtonWidget.OnCheckedChangeListener {
                override fun onCheckedChanged(toggle: ToggleButtonWidget, isChecked: Boolean) {
                    section.config.setKeyBindingEnabled(bindingName, isChecked)
                    InputSystem.setKeyBindingEnabled(bindingName, isChecked)
                    section.persist(section.config)
                    AcademyCraftClient.Config.INSTANCE.save()
                }
            })
            row.addChild("toggle", toggle)

            val rebindButton = ButtonWidget()
            rebindButton.layoutParams = LinearLayoutWidget.LayoutParams()
                .size(26f, 12f)
                .gravity(Gravity.CENTER)
            rebindButton.onClickListener = { _: Widget? ->
                startCapture(section, bindingName)
            }
            rebindButton.addChild("text", LabelWidget("改键").apply {
                scale = 0.7f
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .gravity(Gravity.CENTER)
            })
            row.addChild("rebind", rebindButton)

            return row
        }

        private fun createAboutPage(): LinearLayoutWidget {
            val page = LinearLayoutWidget()
            page.orientation = Orientation.VERTICAL
            page.spacing = 2f
            page.layoutParams = WidgetContainer.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)

            val icon = ImageWidget(R.textures.gui.icon.icon_settings)
            icon.setSampler(FilterMode.LINEAR, false)
            icon.layoutParams = LinearLayoutWidget.LayoutParams()
                .size(48f, 48f)
                .gravity(Gravity.CENTER)
                .marginTop(12f)
            page.addChild("icon", icon)

            val title = LabelWidget(AcademyCraft.MOD_NAME)
            title.layoutParams = LinearLayoutWidget.LayoutParams()
                .height(12f)
                .gravity(Gravity.CENTER)
            page.addChild("title", title)

            val version = LabelWidget("Version " + getModVersion())
            version.scale = 0.7f
            version.layoutParams = LinearLayoutWidget.LayoutParams()
                .height(10f)
                .gravity(Gravity.CENTER)
            page.addChild("version", version)

            val desc = LabelWidget("A superpower academy mod. Reborn.")
            desc.layoutParams = LinearLayoutWidget.LayoutParams()
                .height(10f)
                .gravity(Gravity.CENTER)
            page.addChild("desc", desc)

            return page
        }

        private fun getModVersion(): String {
            return runCatching {
                ModList.get().getModContainerById(AcademyCraft.MOD_ID)
                    .map { it.modInfo.version.toString() }
                    .orElse("unknown")
            }.getOrNull() ?: "unknown"
        }

        private fun createCaptureLayer(): AbstractWidget {
            return object : AbstractWidget() {
                override fun onKeyPressed(event: KeyEvent) {
                    event.consume()
                    val key = event.keyCode
                    if (key == GLFW.GLFW_KEY_ESCAPE) {
                        pendingCancel = true
                        return
                    }
                    if (isModifierKey(key)) return
                    if (pendingType == InputSystem.InputType.MOUSE) return
                    pendingType = InputSystem.InputType.KEYBOARD
                    pendingKeys.add(key)
                    pendingModifiers = event.modifiers
                    updateHint()
                }

                override fun onKeyReleased(event: KeyEvent) {
                    event.consume()
                    if (isModifierKey(event.keyCode)) return
                    if (pendingCancel || pendingType == InputSystem.InputType.KEYBOARD) {
                        finishCapture()
                    }
                }

                override fun onMousePressed(event: MouseEvent) {
                    event.consume()
                    if (pendingType == InputSystem.InputType.KEYBOARD) return
                    pendingType = InputSystem.InputType.MOUSE
                    pendingMouseButton = event.button
                    pendingModifiers = InputSystem.currentMouseModifier
                    updateHint()
                }

                override fun onMouseReleased(event: MouseEvent) {
                    event.consume()
                    if (pendingCancel || pendingType == InputSystem.InputType.MOUSE) {
                        finishCapture()
                    }
                }

                private fun finishCapture() {
                    if (pendingCancel) {
                        resetCaptureState()
                        exitCapture()
                        return
                    }
                    val combo = buildPendingCombo() ?: return
                    resetCaptureState()
                    applyCapture(combo)
                }
            }
        }

        private fun buildPendingCombo(): InputSystem.KeyCombination? {
            val type = pendingType ?: return null
            return when (type) {
                InputSystem.InputType.KEYBOARD -> {
                    if (pendingKeys.isEmpty()) return null
                    InputSystem.combo(
                        type, pendingKeys.toSet(),
                        InputConstants.PRESS, pendingModifiers, false
                    )
                }
                InputSystem.InputType.MOUSE -> {
                    if (pendingMouseButton < 0) return null
                    InputSystem.combo(
                        type, pendingMouseButton,
                        InputConstants.PRESS, pendingModifiers
                    )
                }
            }
        }

        private fun resetCaptureState() {
            pendingType = null
            pendingKeys.clear()
            pendingMouseButton = -1
            pendingModifiers = 0
            pendingCancel = false
        }

        private fun startCapture(section: BindingSection, bindingName: String) {
            resetCaptureState()
            capturing = CaptureTarget(section, bindingName)
            captureLayer.isEnabled = true
            captureLayer.visibility = Widget.Visibility.VISIBLE
            updateHint()
        }

        private fun exitCapture() {
            capturing = null
            captureLayer.isEnabled = false
            captureLayer.visibility = Widget.Visibility.INVISIBLE
            updateHint()
        }

        private fun applyCapture(combo: InputSystem.KeyCombination) {
            val target = capturing ?: return
            target.section.config.setKeyBinding(target.bindingName, combo)
            InputSystem.updateKeyBinding(target.bindingName, combo)
            target.section.persist(target.section.config)
            AcademyCraftClient.Config.INSTANCE.save()
            exitCapture()
            showPage(PAGE_KEYBINDINGS)
        }

        private fun updateHint() {
            val target = capturing
            captureHint.text = if (target != null) {
                val preview = buildPendingCombo()?.displayName() ?: "按键"
                "正在为 \"${target.bindingName}\" 设置按键：$preview，按 ESC 取消..."
            } else {
                ""
            }
            captureHint.visibility = if (target != null) Widget.Visibility.VISIBLE else Widget.Visibility.INVISIBLE
        }

        private fun isModifierKey(key: Int): Boolean {
            return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                    || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                    || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                    || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER
        }
    }
}
