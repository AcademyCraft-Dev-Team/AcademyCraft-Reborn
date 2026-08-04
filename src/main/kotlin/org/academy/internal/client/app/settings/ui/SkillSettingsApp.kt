package org.academy.internal.client.app.settings.ui

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import net.minecraft.resources.Identifier
import org.academy.AcademyCraftClient
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.app.App
import org.academy.api.client.config.KeyBindingConfig
import org.academy.api.client.config.SkillSettingsRegistry
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
import org.academy.api.client.gui.widget.ScrollPanelWidget
import org.academy.api.client.gui.widget.ToggleButtonWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.gui.widget.WidgetContext
import org.academy.api.client.hud.terminal.TerminalHud
import org.academy.api.client.input.InputSystem
import org.academy.api.client.resources.R
import org.academy.api.common.ability.Skill
import org.lwjgl.glfw.GLFW

object SkillSettingsApp : App {
    override fun createContext(): WidgetContext = Context()

    override fun name(): String {
        return translate("app.academy.skill_settings.name")
    }

    override fun icon(): Identifier {
        return R.textures.gui.app.abilitysettings.ability_settings
    }

    private fun translate(key: String): String {
        return Language.getInstance().getOrDefault(key)
    }

    private class Context : WidgetContext {
        private val pageContainer = FrameLayoutWidget()
        private var capturing: CaptureTarget? = null
        private var pendingType: InputSystem.InputType? = null
        private val pendingKeys: MutableSet<Int> = linkedSetOf()
        private var pendingMouseButton: Int = -1
        private var pendingModifiers: Int = 0
        private var pendingCancel: Boolean = false

        private val captureHint = object : LabelWidget("") {
            override fun tick() {
                super.tick()
                updateCaptureHint()
            }
        }
        private val captureLayer = createCaptureLayer()
        private val root = createRoot()

        override fun get(): Widget = root

        private data class BindingSection(
            val skill: Skill,
            val config: KeyBindingConfig,
            val persist: (KeyBindingConfig) -> Unit
        )

        private data class CaptureTarget(
            val section: BindingSection,
            val bindingName: String
        )

        private fun createRoot(): FrameLayoutWidget {
            val root = FrameLayoutWidget()
            root.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)

            val content = LinearLayoutWidget()
            content.orientation = Orientation.VERTICAL
            content.spacing = 1f
            content.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            root.addChild("content", content)

            val topBar = LinearLayoutWidget()
            topBar.orientation = Orientation.HORIZONTAL
            topBar.layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
            content.addChild("top_bar", topBar)

            val backButton = ButtonWidget()
            backButton.layoutParams = LinearLayoutWidget.LayoutParams()
                .margin(2f, 2f, 2f, 0f)
                .size(16f, 16f)
            backButton.onClickListener = { TerminalHud.INSTANCE.closeApp() }
            backButton.addChild("arrow", ImageWidget(R.textures.gui.icon.arrow_back).apply {
                setSampler(FilterMode.LINEAR, false)
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            })
            topBar.addChild("back_button", backButton)

            topBar.addChild("title", LabelWidget(name()).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(0f)
                    .gravity(Gravity.CENTER)
            })

            content.addChild("split_line", FillWidget(-0x1).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .height(1f)
                    .widthMode(SizeMode.MATCH_PARENT)
                    .padding(2f, 0f)
            })

            content.addChild("page_title", LabelWidget(translate("app.academy.skill_settings.tab.skills")).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(14f)
                    .gravity(Gravity.CENTER)
            })

            captureHint.layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
                .gravity(Gravity.CENTER)
            content.addChild("capture_hint", captureHint)

            pageContainer.layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)
                .padding(2f)
            content.addChild("page", pageContainer)
            refreshPage()

            captureLayer.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            captureLayer.isEnabled = false
            captureLayer.visibility = Widget.Visibility.INVISIBLE
            root.addChild("capture_layer", captureLayer)
            return root
        }

        private fun refreshPage() {
            pageContainer.clearChildren()
            pageContainer.addChild("skills", createSkillPage())
        }

        private fun createSkillPage(): LinearLayoutWidget {
            val page = LinearLayoutWidget()
            page.orientation = Orientation.VERTICAL
            page.spacing = 1f
            page.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)

            val category = AbilitySystemClient.getCategory()
            page.addChild("category", LabelWidget(
                translate("app.academy.skill_settings.current_category") + category.displayName
            ).apply {
                scale = 0.8f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(10f)
                    .gravity(Gravity.CENTER_LEFT)
            })

            val columnHeader = LinearLayoutWidget()
            columnHeader.orientation = Orientation.HORIZONTAL
            columnHeader.spacing = 2f
            columnHeader.layoutParams = LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(10f)
            columnHeader.addChild("spacer", FillWidget(0).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).height(0f)
            })
            columnHeader.addChild("key_spacer", FillWidget(0).apply {
                layoutParams = LinearLayoutWidget.LayoutParams().size(44f, 0f)
            })
            columnHeader.addChild("toggle_title", LabelWidget(
                translate("app.academy.settings.keybind.toggle")
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
            page.addChild("column_header", columnHeader)

            val panel = ScrollPanelWidget()
            panel.layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .widthMode(SizeMode.MATCH_PARENT)

            val list = LinearLayoutWidget()
            list.orientation = Orientation.VERTICAL
            list.spacing = 3f
            list.layoutParams = WidgetContainer.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)

            val skillInfos = AbilitySystemClient.getSkillInfosForCategory(category)
            if (skillInfos.isEmpty()) {
                list.addChild("empty", emptyLabel("app.academy.skill_settings.empty"))
            } else {
                skillInfos.forEachIndexed { index, info ->
                    list.addChild("skill_$index", createSkillSection(info))
                }
            }
            panel.setContent(list)
            page.addChild("skills", panel)
            return page
        }

        private fun createSkillSection(info: AbilitySystemClient.SkillInfo): LinearLayoutWidget {
            val skill = info.skill
            val config = tryGetConfig(skill)
            val section = LinearLayoutWidget()
            section.orientation = Orientation.VERTICAL
            section.spacing = 1f
            section.layoutParams = WidgetContainer.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                .padding(2f)

            val header = LinearLayoutWidget()
            header.orientation = Orientation.HORIZONTAL
            header.spacing = 2f
            header.layoutParams = WidgetContainer.LayoutParams().widthMode(SizeMode.MATCH_PARENT)
            header.addChild("icon", ImageWidget(resolveSkillIcon(info)).apply {
                setSampler(FilterMode.LINEAR, false)
                layoutParams = LinearLayoutWidget.LayoutParams().size(16f, 16f)
            })
            header.addChild("name", LabelWidget(skill.translatedName).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(0f)
                    .gravity(Gravity.CENTER_LEFT)
            })
            section.addChild("header", header)

            if (config == null || config.keyBindings.isEmpty()) {
                section.addChild("no_keys", emptyLabel("app.academy.skill_settings.keys.empty"))
            } else {
                val bindingSection = BindingSection(skill, config) { updated ->
                    AcademyCraftClient.Config.INSTANCE.setConfig(skill.key, updated)
                }
                for ((bindingName, combo) in config.keyBindings) {
                    section.addChild("key_$bindingName", createBindingRow(bindingSection, bindingName, combo))
                }
            }

            section.addChild("advanced_separator", FillWidget(0x66FFFFFF).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(1f)
                    .marginTop(1f)
            })
            section.addChild("advanced_title", LabelWidget(
                translate("app.academy.skill_settings.advanced.title")
            ).apply {
                scale = 0.8f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(10f)
                    .gravity(Gravity.CENTER_LEFT)
            })

            val modules = SkillSettingsRegistry.getModules(skill)
            if (modules.isEmpty()) {
                section.addChild("advanced_empty", emptyLabel("app.academy.skill_settings.advanced.empty"))
            } else {
                modules.forEachIndexed { moduleIndex, module ->
                    if (module.titleKey.isNotBlank()) {
                        section.addChild("module_${moduleIndex}_title", LabelWidget(translate(module.titleKey)).apply {
                            scale = 0.72f
                            layoutParams = LinearLayoutWidget.LayoutParams()
                                .widthMode(SizeMode.MATCH_PARENT)
                                .height(9f)
                                .gravity(Gravity.CENTER_LEFT)
                                .paddingLeft(3f)
                        })
                    }
                    module.entries.forEachIndexed { entryIndex, entry ->
                        section.addChild(
                            "module_${moduleIndex}_entry_$entryIndex",
                            createAdvancedEntry(entry)
                        )
                    }
                }
            }

            section.addChild("section_separator", FillWidget(0x88FFFFFF.toInt()).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(1f)
                    .marginTop(2f)
            })
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
            row.layoutParams = WidgetContainer.LayoutParams().widthMode(SizeMode.MATCH_PARENT)

            row.addChild("name", LabelWidget(
                Language.getInstance().getOrDefault("key.academy.$bindingName")
            ).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(10f)
                    .gravity(Gravity.CENTER_LEFT)
            })
            row.addChild("key", LabelWidget(combo.displayName()).apply {
                scale = 0.7f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .width(44f)
                    .height(10f)
                    .gravity(Gravity.CENTER)
            })
            row.addChild("toggle", ToggleButtonWidget().apply {
                setChecked(section.config.isKeyBindingEnabled(bindingName))
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(16f, 9f)
                    .gravity(Gravity.CENTER)
                setOnCheckedChangeListener(object : ToggleButtonWidget.OnCheckedChangeListener {
                    override fun onCheckedChanged(toggle: ToggleButtonWidget, isChecked: Boolean) {
                        section.config.setKeyBindingEnabled(bindingName, isChecked)
                        InputSystem.setKeyBindingEnabled(bindingName, isChecked)
                        section.persist(section.config)
                        AcademyCraftClient.Config.INSTANCE.save()
                    }
                })
            })

            val rebind = ButtonWidget()
            rebind.layoutParams = LinearLayoutWidget.LayoutParams()
                .size(26f, 12f)
                .gravity(Gravity.CENTER)
            rebind.onClickListener = { startCapture(section, bindingName) }
            rebind.addChild("text", LabelWidget(translate("app.academy.settings.keybind.rebind")).apply {
                scale = 0.7f
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .gravity(Gravity.CENTER)
            })
            row.addChild("rebind", rebind)
            return row
        }

        private fun createAdvancedEntry(entry: SkillSettingsRegistry.Entry): LinearLayoutWidget {
            val row = LinearLayoutWidget()
            row.orientation = Orientation.HORIZONTAL
            row.spacing = 2f
            row.layoutParams = WidgetContainer.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .paddingLeft(3f)
            row.addChild("label", LabelWidget(translate(entry.labelKey)).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(11f)
                    .gravity(Gravity.CENTER_LEFT)
            })

            when (entry) {
                is SkillSettingsRegistry.Toggle -> {
                    row.addChild("control", ToggleButtonWidget().apply {
                        setChecked(entry.getter.asBoolean)
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .size(16f, 9f)
                            .gravity(Gravity.CENTER)
                        setOnCheckedChangeListener(object : ToggleButtonWidget.OnCheckedChangeListener {
                            override fun onCheckedChanged(toggle: ToggleButtonWidget, isChecked: Boolean) {
                                entry.setter.accept(isChecked)
                            }
                        })
                    })
                }

                is SkillSettingsRegistry.IntegerRange -> {
                    val value = LabelWidget(entry.getter.asInt.toString()).apply {
                        scale = 0.75f
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .width(20f)
                            .height(10f)
                            .gravity(Gravity.CENTER)
                    }
                    fun change(delta: Int) {
                        val updated = (entry.getter.asInt + delta).coerceIn(entry.min, entry.max)
                        entry.setter.accept(updated)
                        value.text = entry.getter.asInt.toString()
                    }
                    row.addChild("decrease", smallButton("-") { change(-entry.step) })
                    row.addChild("value", value)
                    row.addChild("increase", smallButton("+") { change(entry.step) })
                }

                is SkillSettingsRegistry.Action -> {
                    row.addChild("control", ButtonWidget().apply {
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .size(34f, 12f)
                            .gravity(Gravity.CENTER)
                        onClickListener = { entry.action.run() }
                        addChild("text", LabelWidget(translate(entry.buttonKey)).apply {
                            scale = 0.7f
                            layoutParams = FrameLayoutWidget.LayoutParams()
                                .sizeMode(SizeMode.MATCH_PARENT)
                                .gravity(Gravity.CENTER)
                        })
                    })
                }
            }
            return row
        }

        private fun smallButton(text: String, onClick: () -> Unit): ButtonWidget {
            return ButtonWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(12f, 12f)
                    .gravity(Gravity.CENTER)
                onClickListener = { onClick() }
                addChild("text", LabelWidget(text).apply {
                    layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
                        .gravity(Gravity.CENTER)
                })
            }
        }

        private fun emptyLabel(key: String): LabelWidget {
            return LabelWidget(translate(key)).apply {
                scale = 0.7f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(9f)
                    .gravity(Gravity.CENTER_LEFT)
                    .paddingLeft(3f)
            }
        }

        private fun tryGetConfig(skill: Skill): KeyBindingConfig? {
            if (!AcademyCraftClient.Config.INSTANCE.hasTypeHandler(skill.key)) return null
            return runCatching {
                AcademyCraftClient.Config.INSTANCE.getConfig<KeyBindingConfig>(skill.key)
            }.getOrNull()
        }

        private fun resolveSkillIcon(info: AbilitySystemClient.SkillInfo): Identifier {
            val skill = info.skill
            val placeholder = R.textures.gui.icon.close
            val categoryIcon = skill.category.developerIcon
            val inferred = Identifier.fromNamespaceAndPath(
                skill.key.namespace,
                "textures/ability/${skill.category.key.path}/skill/${skill.key.path}/icon.png"
            )
            val resourceManager = Minecraft.getInstance().resourceManager
            return sequenceOf(info.texture, skill.icon, inferred)
                .distinct()
                .firstOrNull { icon ->
                    icon != placeholder && icon != categoryIcon && resourceManager.getResource(icon).isPresent
                }
                ?: placeholder
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
                    if (isModifierKey(key) || pendingType == InputSystem.InputType.MOUSE) return
                    pendingType = InputSystem.InputType.KEYBOARD
                    pendingKeys.add(key)
                    pendingModifiers = event.modifiers
                    updateCaptureHint()
                }

                override fun onKeyReleased(event: KeyEvent) {
                    event.consume()
                    if (isModifierKey(event.keyCode)) return
                    if (pendingCancel || pendingType == InputSystem.InputType.KEYBOARD) finishCapture()
                }

                override fun onMousePressed(event: MouseEvent) {
                    event.consume()
                    if (pendingType == InputSystem.InputType.KEYBOARD) return
                    pendingType = InputSystem.InputType.MOUSE
                    pendingMouseButton = event.button
                    pendingModifiers = InputSystem.currentMouseModifier
                    updateCaptureHint()
                }

                override fun onMouseReleased(event: MouseEvent) {
                    event.consume()
                    if (pendingCancel || pendingType == InputSystem.InputType.MOUSE) finishCapture()
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
            return when (pendingType ?: return null) {
                InputSystem.InputType.KEYBOARD -> {
                    if (pendingKeys.isEmpty()) return null
                    InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        pendingKeys.toSet(),
                        InputConstants.PRESS,
                        pendingModifiers,
                        false
                    )
                }

                InputSystem.InputType.MOUSE -> {
                    if (pendingMouseButton < 0) return null
                    InputSystem.combo(
                        InputSystem.InputType.MOUSE,
                        pendingMouseButton,
                        InputConstants.PRESS,
                        pendingModifiers
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
            updateCaptureHint()
        }

        private fun exitCapture() {
            capturing = null
            captureLayer.isEnabled = false
            captureLayer.visibility = Widget.Visibility.INVISIBLE
            updateCaptureHint()
        }

        private fun applyCapture(combo: InputSystem.KeyCombination) {
            val target = capturing ?: return
            target.section.config.setKeyBinding(target.bindingName, combo)
            InputSystem.updateKeyBinding(target.bindingName, combo)
            target.section.persist(target.section.config)
            AcademyCraftClient.Config.INSTANCE.save()
            exitCapture()
            refreshPage()
        }

        private fun updateCaptureHint() {
            val target = capturing
            captureHint.text = if (target == null) {
                ""
            } else {
                val preview = buildPendingCombo()?.displayName()
                    ?: translate("app.academy.skill_settings.capture.key")
                translate("app.academy.skill_settings.capture.hint")
                    .replace("%1\$s", target.bindingName)
                    .replace("%2\$s", preview)
            }
            captureHint.visibility = if (target == null) Widget.Visibility.INVISIBLE else Widget.Visibility.VISIBLE
        }

        private fun isModifierKey(key: Int): Boolean {
            return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                    || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                    || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                    || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER
        }
    }
}
