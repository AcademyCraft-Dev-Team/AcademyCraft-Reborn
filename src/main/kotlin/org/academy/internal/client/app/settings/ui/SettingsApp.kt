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
import org.academy.api.client.gui.dsl.*
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
import org.academy.internal.client.ability.program.AbilityProgramEditorClient
import org.academy.internal.client.hud.HudLayoutEditorScreen
import org.academy.internal.common.ability.level0.skills.OutputControl
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting
import org.academy.internal.common.world.damagesource.FriendlyFireSetting
import org.academy.internal.common.world.damagesource.PvpSetting
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
        private lateinit var panelContainer: FrameLayoutWidget
        private var capturing: CaptureTarget? = null
        private var pendingType: InputSystem.InputType? = null
        private val pendingKeys: MutableSet<Int> = linkedSetOf()
        private var pendingMouseButton: Int = -1
        private var pendingModifiers: Int = 0

        private val captureHint: LabelWidget = LabelWidget("").apply {
            setFrameUpdate {
                updateHint()
                true
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
            val bindingName: String,
            val keyLabel: LabelWidget
        )

        private fun createRoot(): FrameLayoutWidget {
            return standaloneFrame {
                matchParent()

                column("content") {
                    spacing = 1f
                    sizeMode(SizeMode.MATCH_PARENT)

                    row("top_bar") {
                        sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)

                        button("back_button") {
                            margin(2f, 2f, 2f, 0f)
                            size(16f, 16f)
                            onClick {
                                TerminalHud.INSTANCE.closeApp()
                            }
                            image(R.textures.gui.icon.arrow_back, "arrow") {
                                sampler(FilterMode.LINEAR, false)
                                sizeMode(SizeMode.MATCH_PARENT)
                            }
                        }

                        label(name(), "title") {
                            weight(1f)
                            height(0f)
                            gravity(Gravity.CENTER)
                        }
                    }

                    fill(-0x1, "split_line") {
                        height(1f)
                        widthMode(SizeMode.MATCH_PARENT)
                        padding(2f, 0f)
                    }

                    createTabBar()

                    add("capture_hint", captureHint) {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(10f)
                        gravity(Gravity.CENTER)
                    }

                    panelContainer = frame("panel") {
                        weight(1f)
                        widthMode(SizeMode.MATCH_PARENT)
                        padding(2f)
                    }

                    showPage(PAGE_GENERAL)
                }

                add("capture_layer", captureLayer) {
                    matchParent()
                    isEnabled = false
                    visibility = Widget.Visibility.INVISIBLE
                }
            }
        }

        private fun WidgetContainer.createTabBar(): RadioGroupWidget {
            return radioGroup("tab_bar") {
                orientation = Orientation.HORIZONTAL
                spacing = 2f
                widthMode(SizeMode.MATCH_PARENT)

                val general = add(
                    "general", createTabButton(
                        L10n["app.academy.settings.tab.general"],
                        PAGE_GENERAL
                    )
                ) {
                    weight(1f)
                    height(14f)
                }
                add(
                    "keybindings", createTabButton(
                        L10n["app.academy.settings.tab.keybindings"],
                        PAGE_KEYBINDINGS
                    )
                ) {
                    weight(1f)
                    height(14f)
                }
                add("about", createTabButton("About", PAGE_ABOUT)) {
                    weight(1f)
                    height(14f)
                }
                selectButton(general)
            }
        }

        private fun createTabButton(text: String, page: Int): RadioButtonWidget {
            return RadioButtonWidget().apply {
                onClick {
                    showPage(page)
                }
                val back = fill(0, "back") {
                    sizeMode(SizeMode.MATCH_PARENT)
                }

                label(text, "text") {
                    sizeMode(SizeMode.MATCH_PARENT)
                    gravity(Gravity.CENTER)
                }

                val progressState = AtomicReference(0f)
                val updateState = Consumer<Float> { progress ->
                    progressState.set(progress)
                    val alpha = (progress * 0.5f * 255).toInt()
                    back.setColor(ARGB.color(alpha, 0, 0, 0))
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
                stateListAnimator = animator
            }
        }

        private fun showPage(page: Int) {
            panelContainer.clearChildren()
            when (page) {
                PAGE_GENERAL -> panelContainer.add("general", createGeneralPage())
                PAGE_KEYBINDINGS -> panelContainer.add("keybindings", createKeybindPage())
                PAGE_ABOUT -> panelContainer.add("about", createAboutPage())
            }
        }

        private fun createGeneralPage(): LinearLayoutWidget {
            return standaloneColumn(3f) {
                sizeMode(SizeMode.MATCH_PARENT)

                val player = Minecraft.getInstance().player
                val pvpEnabled = player?.let(PvpSetting::isPvpEnabled) ?: true
                add(
                    "pvp", createSettingToggle(
                        L10n["app.academy.settings.general.pvp"],
                        pvpEnabled,
                        authoritativeState = { PvpSetting.clientPvpEnabled(pvpEnabled) }
                    ) { enabled ->
                        PvpSetting.expectClientPvpEnabled(enabled)
                        MisakaNetworkClient.send(PvpSetting.SetPacket(enabled))
                    })
                add(
                    "friendly_fire", createSettingToggle(
                        L10n["app.academy.settings.general.friendly_fire"],
                        player?.let(FriendlyFireSetting::isFriendlyFireEnabled) ?: true
                    ) { enabled ->
                        MisakaNetworkClient.send(FriendlyFireSetting.SetPacket(enabled))
                    })
                add(
                    "destroy_blocks", createSettingToggle(
                        L10n["app.academy.settings.general.destroy_blocks"],
                        player?.let(DestroyBlocksSetting::isDestroyBlocksEnabled) ?: true
                    ) { enabled ->
                        MisakaNetworkClient.send(DestroyBlocksSetting.SetPacket(enabled))
                    })
                add("hud_layout", createHudLayoutRow())
            }
        }

        private fun createHudLayoutRow(): LinearLayoutWidget {
            return standaloneRow(4f) {
                widthMode(SizeMode.MATCH_PARENT)
                height(18f)

                label(L10n["app.academy.settings.general.hud_layout"], "label") {
                    weight(1f)
                    height(0f)
                    gravity(Gravity.CENTER_LEFT)
                }
                button("open") {
                    size(72f, 14f)
                    gravity(Gravity.CENTER)
                    onClick {
                        val minecraft = Minecraft.getInstance()
                        minecraft.gui.setScreen(HudLayoutEditorScreen(minecraft.gui.screen()))
                    }
                    label(L10n["app.academy.settings.general.hud_layout.open"], "text") {
                        scale = 0.65f
                        sizeMode(SizeMode.MATCH_PARENT)
                        gravity(Gravity.CENTER)
                    }
                }
            }
        }

        private fun createSettingToggle(
            text: String,
            checked: Boolean,
            authoritativeState: (() -> Boolean)? = null,
            onChanged: (Boolean) -> Unit
        ): LinearLayoutWidget {
            return standaloneRow(4f) {
                widthMode(SizeMode.MATCH_PARENT)
                height(18f)

                label(text, "label") {
                    weight(1f)
                    height(0f)
                    gravity(Gravity.CENTER_LEFT)
                }
                var applyingAuthoritativeState = false
                toggle(checked, "toggle") {
                    setFrameUpdate {
                        val expected = authoritativeState?.invoke()
                        if (expected != null && isChecked != expected) {
                            applyingAuthoritativeState = true
                            setChecked(expected)
                            applyingAuthoritativeState = false
                        }
                        true
                    }
                    size(20f, 10f)
                    gravity(Gravity.CENTER)
                    onCheckedChange { isChecked ->
                        if (!applyingAuthoritativeState) onChanged(isChecked)
                    }
                }
            }
        }

        private fun createKeybindPage(): LinearLayoutWidget {
            return standaloneColumn(1f) {
                sizeMode(SizeMode.MATCH_PARENT)

                row("column_header") {
                    spacing = 2f
                    widthMode(SizeMode.MATCH_PARENT)
                    height(10f)

                    fill(0, "spacer") {
                        weight(1f)
                        height(0f)
                    }
                    fill(0, "key_spacer") {
                        size(44f, 0f)
                    }
                    label(
                        L10n["app.academy.settings.keybind.toggle"],
                        "toggle_title"
                    ) {
                        scale = 0.65f
                        size(22f, 10f)
                        gravity(Gravity.CENTER)
                    }
                    fill(0, "rebind_spacer") {
                        size(26f, 0f)
                    }
                    fill(0, "reset_spacer") {
                        size(26f, 0f)
                    }
                }

                val list = standaloneColumn(2f) {
                    sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)

                    for (section in createGeneralSections()) {
                        if (section.config.keyBindings.keys.all(section.hiddenBindings::contains)) continue
                        add(section.id, createBindingSection(section))
                    }
                }

                scrollPanel(Orientation.VERTICAL, "bindings", list) {
                    weight(1f)
                    widthMode(SizeMode.MATCH_PARENT)
                }
            }
        }

        private fun createGeneralSections(): List<BindingSection> {
            val terminalConfig = AcademyCraftClient.Config.INSTANCE
                .getConfig<TerminalConfig>(TerminalHud.CONFIG_KEY)
            val abilityConfig = AcademyCraftClient.Config.INSTANCE
                .getConfig<AbilitySystemClient.Config>(AbilitySystemClient.CONFIG_KEY_ABILITY_SYSTEM)
            val outputControlConfig = AcademyCraftClient.Config.INSTANCE
                .getConfig<OutputControl.Client.Config>(OutputControl.Client.CONFIG_KEY)
            val sections = mutableListOf(
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
                },
                BindingSection(
                    "general_output_control",
                    L10n["app.academy.settings.keybind.group.output_control"],
                    R.textures.ability.level0.skill.output_control.icon,
                    outputControlConfig
                ) { updated ->
                    AcademyCraftClient.Config.INSTANCE.setConfig(
                        OutputControl.Client.CONFIG_KEY,
                        updated
                    )
                }
            )
            if (AbilityProgramEditorClient.canUsePrecisionOperation()) {
                val precisionConfig = AcademyCraftClient.Config.INSTANCE
                    .getConfig<AbilityProgramEditorClient.Config>(AbilityProgramEditorClient.CONFIG_KEY)
                sections += BindingSection(
                    "general_precision_operation",
                    L10n["app.academy.settings.keybind.group.precision_operation"],
                    AbilitySystemClient.getCategory().developerIcon,
                    precisionConfig
                ) { updated ->
                    AcademyCraftClient.Config.INSTANCE.setConfig(
                        AbilityProgramEditorClient.CONFIG_KEY,
                        updated
                    )
                }
            }
            return sections
        }

        private fun createBindingSection(sectionInfo: BindingSection): LinearLayoutWidget {
            return standaloneColumn(1f) {
                sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)

                row("header") {
                    spacing = 2f
                    widthMode(SizeMode.MATCH_PARENT)

                    image(sectionInfo.icon, "icon") {
                        sampler(FilterMode.LINEAR, false)
                        size(16f, 16f)
                    }

                    label(sectionInfo.title, "name") {
                        weight(1f)
                        height(0f)
                        gravity(Gravity.CENTER_LEFT)
                    }
                }

                for ((bindingName, combo) in sectionInfo.config.keyBindings) {
                    if (bindingName in sectionInfo.hiddenBindings) continue
                    add(bindingName, createBindingRow(sectionInfo, bindingName, combo))
                }
            }
        }

        private fun createBindingRow(
            section: BindingSection,
            bindingName: String,
            combo: InputSystem.KeyCombination
        ): LinearLayoutWidget {
            return standaloneRow(2f) {
                widthMode(SizeMode.MATCH_PARENT)

                label(
                    L10n["key.academy.$bindingName"],
                    "name"
                ) {
                    weight(1f)
                    height(10f)
                    gravity(Gravity.CENTER_LEFT)
                }

                val keyLabel = label(displayBinding(combo), "key") {
                    scale = 0.7f
                    width(44f)
                    height(10f)
                    gravity(Gravity.CENTER)
                }

                toggle(section.config.isKeyBindingEnabled(bindingName), "toggle") {
                    size(16f, 9f)
                    gravity(Gravity.CENTER)
                    onCheckedChange { isChecked ->
                        section.config.setKeyBindingEnabled(bindingName, isChecked)
                        InputSystem.setKeyBindingEnabled(bindingName, isChecked)
                        section.persist(section.config)
                        AcademyCraftClient.Config.INSTANCE.save()
                    }
                }

                button("rebind") {
                    size(26f, 12f)
                    gravity(Gravity.CENTER)
                    onClick {
                        startCapture(section, bindingName, keyLabel)
                    }
                    label(
                        L10n["app.academy.settings.keybind.rebind"],
                        "text"
                    ) {
                        scale = 0.7f
                        sizeMode(SizeMode.MATCH_PARENT)
                        gravity(Gravity.CENTER)
                    }
                }

                button("reset") {
                    size(26f, 12f)
                    gravity(Gravity.CENTER)
                    onClick {
                        resetBinding(section, bindingName, keyLabel)
                    }
                    label(
                        L10n["app.academy.settings.keybind.reset"],
                        "text"
                    ) {
                        scale = 0.7f
                        sizeMode(SizeMode.MATCH_PARENT)
                        gravity(Gravity.CENTER)
                    }
                }
            }
        }

        private fun createAboutPage(): LinearLayoutWidget {
            return standaloneColumn(2f) {
                sizeMode(SizeMode.MATCH_PARENT)

                image(R.textures.gui.icon.icon_settings, "icon") {
                    size(48f, 48f)
                    gravity(Gravity.CENTER)
                    marginTop(12f)
                }

                label(AcademyCraft.MOD_NAME, "title") {
                    height(12f)
                    gravity(Gravity.CENTER)
                }

                label("Version " + getModVersion(), "version") {
                    scale = 0.7f
                    height(10f)
                    gravity(Gravity.CENTER)
                }
            }
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
                        applyCapture(InputSystem.unbound(current))
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
                        current.action, pendingModifiers, current.availableWhenScreen
                    )
                }

                InputSystem.InputType.MOUSE -> {
                    if (pendingMouseButton < 0) return null
                    InputSystem.combo(
                        type, pendingMouseButton,
                        current.action, pendingModifiers, current.availableWhenScreen
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

        private fun startCapture(
            section: BindingSection,
            bindingName: String,
            keyLabel: LabelWidget
        ) {
            if (bindingName in section.hiddenBindings || bindingName == TerminalHud.KEY_NAME_TOGGLE) return
            resetCaptureState()
            capturing = CaptureTarget(section, bindingName, keyLabel)
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
            target.keyLabel.text = displayBinding(combo)
            exitCapture()
        }

        private fun resetBinding(
            section: BindingSection,
            bindingName: String,
            keyLabel: LabelWidget
        ) {
            val defaultCombo = InputSystem.getDefaultKeyBinding(bindingName) ?: return
            section.config.setKeyBinding(bindingName, defaultCombo)
            InputSystem.updateKeyBinding(bindingName, defaultCombo)
            section.persist(section.config)
            AcademyCraftClient.Config.INSTANCE.save()
            keyLabel.text = displayBinding(defaultCombo)
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
