package org.academy.internal.client.app.settings.ui

import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.neoforged.fml.ModList
import org.academy.AcademyCraft
import org.academy.AcademyCraftClient
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.app.App
import org.academy.api.client.config.KeyBindingConfig
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator.Companion.ofFloat
import org.academy.api.client.gui.animation.StateListAnimator
import org.academy.api.client.gui.event.KeyEvent
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.api.client.hud.terminal.TerminalConfig
import org.academy.api.client.hud.terminal.TerminalHud
import org.academy.api.client.input.InputSystem
import org.academy.api.client.resources.R
import org.academy.api.common.util.L10n
import org.academy.internal.client.hud.HudLayoutEditorScreen
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting
import org.academy.internal.common.world.damagesource.FriendlyFireSetting
import org.lwjgl.glfw.GLFW
import org.misaka.MisakaNetworkClient
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

object SettingsApp : App {
    private const val PAGE_GENERAL = 0
    private const val PAGE_KEYBINDINGS = 1
    private const val PAGE_ABOUT = 2

    override fun createContext(): WidgetContext {
        return Context()
    }

    override fun name(): String {
        return L10n["app.academy.settings.name"]
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
            val hiddenBindings: Set<String> = emptySet(),
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

                    showPage(PAGE_GENERAL)
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
            tabBar.addChild(
                "general", createTabButton(
                    L10n["app.academy.settings.tab.general"],
                    PAGE_GENERAL
                )
            )
            tabBar.addChild(
                "keybindings", createTabButton(
                    L10n["app.academy.settings.tab.keybindings"],
                    PAGE_KEYBINDINGS
                )
            )
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
                PAGE_GENERAL -> panelContainer.addChild("general", createGeneralPage())
                PAGE_KEYBINDINGS -> panelContainer.addChild("keybindings", createKeybindPage())
                PAGE_ABOUT -> panelContainer.addChild("about", createAboutPage())
            }
        }

        private fun createGeneralPage(): LinearLayoutWidget {
            val page = LinearLayoutWidget()
            page.orientation = Orientation.VERTICAL
            page.spacing = 3f
            page.layoutParams = WidgetContainer.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)

            val player = Minecraft.getInstance().player
            page.addChild(
                "friendly_fire", createSettingToggle(
                    L10n["app.academy.settings.general.friendly_fire"],
                    player?.let(FriendlyFireSetting::isFriendlyFireEnabled) ?: true
                ) { enabled ->
                    MisakaNetworkClient.send(FriendlyFireSetting.SetPacket(enabled))
                })
            page.addChild(
                "destroy_blocks", createSettingToggle(
                    L10n["app.academy.settings.general.destroy_blocks"],
                    player?.let(DestroyBlocksSetting::isDestroyBlocksEnabled) ?: true
                ) { enabled ->
                    MisakaNetworkClient.send(DestroyBlocksSetting.SetPacket(enabled))
                })
            page.addChild("hud_layout", createHudLayoutRow())
            return page
        }

        private fun createHudLayoutRow(): LinearLayoutWidget {
            val row = LinearLayoutWidget()
            row.orientation = Orientation.HORIZONTAL
            row.spacing = 4f
            row.layoutParams = WidgetContainer.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(18f)
            row.addChild(
                "label", LabelWidget(
                    L10n["app.academy.settings.general.hud_layout"]
                ).apply {
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .weight(1f)
                        .height(0f)
                        .gravity(Gravity.CENTER_LEFT)
                })
            row.addChild("open", ButtonWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(72f, 14f)
                    .gravity(Gravity.CENTER)
                onClickListener = {
                    val minecraft = Minecraft.getInstance()
                    minecraft.gui.setScreen(HudLayoutEditorScreen(minecraft.gui.screen()))
                }
                addChild(
                    "text", LabelWidget(
                        L10n["app.academy.settings.general.hud_layout.open"]
                    ).apply {
                        scale = 0.65f
                        layoutParams = FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                            .gravity(Gravity.CENTER)
                    })
            })
            return row
        }

        private fun createSettingToggle(
            text: String,
            checked: Boolean,
            onChanged: (Boolean) -> Unit
        ): LinearLayoutWidget {
            val row = LinearLayoutWidget()
            row.orientation = Orientation.HORIZONTAL
            row.spacing = 4f
            row.layoutParams = WidgetContainer.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(18f)

            row.addChild("label", LabelWidget(text).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(0f)
                    .gravity(Gravity.CENTER_LEFT)
            })
            row.addChild("toggle", ToggleButtonWidget().apply {
                setChecked(checked)
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(20f, 10f)
                    .gravity(Gravity.CENTER)
                setOnCheckedChangeListener(object : ToggleButtonWidget.OnCheckedChangeListener {
                    override fun onCheckedChanged(toggle: ToggleButtonWidget, isChecked: Boolean) {
                        onChanged(isChecked)
                    }
                })
            })
            return row
        }

        private fun createKeybindPage(): LinearLayoutWidget {
            val page = LinearLayoutWidget()
            page.orientation = Orientation.VERTICAL
            page.spacing = 1f
            page.layoutParams = WidgetContainer.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)

            val columnHeader = LinearLayoutWidget()
            columnHeader.orientation = Orientation.HORIZONTAL
            columnHeader.spacing = 2f
            columnHeader.layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
            columnHeader.addChild("spacer", FillWidget(0).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(0f)
            })
            columnHeader.addChild("key_spacer", FillWidget(0).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().size(44f, 0f)
            })
            columnHeader.addChild(
                "toggle_title", LabelWidget(
                    L10n["app.academy.settings.keybind.toggle"]
                ).apply {
                    scale = 0.65f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .width(22f)
                        .height(10f)
                        .gravity(Gravity.CENTER)
                })
            columnHeader.addChild("rebind_spacer", FillWidget(0).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().size(26f, 0f)
            })
            columnHeader.addChild("reset_spacer", FillWidget(0).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().size(26f, 0f)
            })
            page.addChild("column_header", columnHeader)

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
                if (section.config.keyBindings.keys.all(section.hiddenBindings::contains)) continue
                list.addChild(section.id, createBindingSection(section))
            }

            panel.setContent(list)
            page.addChild("bindings", panel)
            return page
        }

        private fun createGeneralSections(): List<BindingSection> {
            val terminalConfig = AcademyCraftClient.Config.INSTANCE
                .getConfig<TerminalConfig>(TerminalHud.CONFIG_KEY)
            val abilityConfig = AcademyCraftClient.Config.INSTANCE
                .getConfig<AbilitySystemClient.Config>(AbilitySystemClient.CONFIG_KEY_ABILITY_SYSTEM)
            return listOf(
                BindingSection(
                    "general_terminal",
                    L10n["app.academy.settings.keybind.group.terminal"],
                    R.textures.gui.terminal.icon,
                    terminalConfig
                ) { updated ->
                    AcademyCraftClient.Config.INSTANCE.setConfig(TerminalHud.CONFIG_KEY, updated)
                }.copy(hiddenBindings = setOf(TerminalHud.KEY_NAME_TOGGLE)),
                BindingSection(
                    "general_ability_hud",
                    L10n["app.academy.settings.keybind.group.ability_hud"],
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
                if (bindingName in sectionInfo.hiddenBindings) continue
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
                L10n["key.academy.$bindingName"]
            )
            name.layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .height(10f)
                .gravity(Gravity.CENTER_LEFT)
            row.addChild("name", name)

            val keyLabel = LabelWidget(displayBinding(combo))
            keyLabel.scale = 0.7f
            keyLabel.layoutParams = LinearLayoutWidget.LayoutParams()
                .width(44f)
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
            rebindButton.addChild(
                "text", LabelWidget(
                    L10n["app.academy.settings.keybind.rebind"]
                ).apply {
                    scale = 0.7f
                    layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
                        .gravity(Gravity.CENTER)
                })
            row.addChild("rebind", rebindButton)

            val resetButton = ButtonWidget()
            resetButton.layoutParams = LinearLayoutWidget.LayoutParams()
                .size(26f, 12f)
                .gravity(Gravity.CENTER)
            resetButton.onClickListener = { _: Widget? ->
                resetBinding(section, bindingName)
            }
            resetButton.addChild(
                "text", LabelWidget(
                    L10n["app.academy.settings.keybind.reset"]
                ).apply {
                    scale = 0.7f
                    layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
                        .gravity(Gravity.CENTER)
                })
            row.addChild("reset", resetButton)

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
                        val target = capturing ?: return
                        val current = target.section.config.getKeyBinding(target.bindingName) ?: return
                        resetCaptureState()
                        if (current != null) {
                            applyCapture(InputSystem.unbound(current))
                        } else {
                            exitCapture()
                            showPage(PAGE_KEYBINDINGS)
                        }
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
                    if (pendingType == InputSystem.InputType.KEYBOARD) {
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
                    if (pendingType == InputSystem.InputType.MOUSE) {
                        finishCapture()
                    }
                }

                private fun finishCapture() {
                    val combo = buildPendingCombo() ?: return
                    resetCaptureState()
                    applyCapture(combo)
                }
            }
        }

        private fun buildPendingCombo(): InputSystem.KeyCombination? {
            val type = pendingType ?: return null
            val target = capturing ?: return null
            val current = target.section.config.getKeyBinding(target.bindingName) ?: return null
            return when (type) {
                InputSystem.InputType.KEYBOARD -> {
                    if (pendingKeys.isEmpty()) return null
                    InputSystem.combo(
                        type, pendingKeys.toSet(),
                        current?.action ?: 1, pendingModifiers, current?.availableWhenScreen ?: true
                    )
                }

                InputSystem.InputType.MOUSE -> {
                    if (pendingMouseButton < 0) return null
                    InputSystem.combo(
                        type, pendingMouseButton,
                        current?.action ?: 1, pendingModifiers, current?.availableWhenScreen ?: true
                    )
                }
            }
        }

        private fun resetCaptureState() {
            pendingType = null
            pendingKeys.clear()
            pendingMouseButton = -1
            pendingModifiers = 0
        }

        private fun startCapture(section: BindingSection, bindingName: String) {
            if (bindingName in section.hiddenBindings || bindingName == TerminalHud.KEY_NAME_TOGGLE) return
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

        private fun resetBinding(section: BindingSection, bindingName: String) {
            val defaultCombo = InputSystem.getDefaultKeyBinding(bindingName) ?: return
            section.config.setKeyBinding(bindingName, defaultCombo)
            InputSystem.updateKeyBinding(bindingName, defaultCombo)
            section.persist(section.config)
            AcademyCraftClient.Config.INSTANCE.save()
            showPage(PAGE_KEYBINDINGS)
        }

        private fun displayBinding(combo: InputSystem.KeyCombination): String {
            return if (combo.unbound) {
                L10n["app.academy.settings.keybind.format.none"]
            } else {
                combo.displayName()
            }
        }

        private fun updateHint() {
            val target = capturing
            captureHint.text = if (target != null) {
                val preview = buildPendingCombo()?.let(::displayBinding)
                    ?: L10n["app.academy.skill_settings.capture.key"]
                L10n["app.academy.skill_settings.capture.hint"]
                    .replace($$"%1$s", L10n["key.academy.${target.bindingName}"])
                    .replace($$"%2$s", preview)
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
