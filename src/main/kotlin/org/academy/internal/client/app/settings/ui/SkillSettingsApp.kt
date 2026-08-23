package org.academy.internal.client.app.settings.ui

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.textures.FilterMode
import net.minecraft.client.Minecraft
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
import org.academy.api.client.gui.widget.*
import org.academy.api.client.hud.ability.AbilityInfoHud
import org.academy.api.client.hud.terminal.TerminalHud
import org.academy.api.client.input.InputSystem
import org.academy.api.client.resources.R
import org.academy.api.common.ability.Skill
import org.academy.api.common.util.L10n
import org.academy.internal.common.world.damagesource.DestroyBlocksSetting
import org.lwjgl.glfw.GLFW
import org.misaka.MisakaNetworkClient
import kotlin.math.roundToInt

object SkillSettingsApp : App {
    override fun createContext(): WidgetContext = Context()

    override fun name(): String {
        return translate("app.academy.skill_settings.name")
    }

    override fun icon(): Identifier {
        return R.textures.gui.app.abilitysettings.ability_settings
    }

    private fun translate(key: String): String {
        return L10n[key]
    }

    private class Context : WidgetContext {
        companion object {
            private const val TOOLTIP_WIDTH = 190f
            private const val TOOLTIP_PADDING = 5f
            private const val TOOLTIP_FONT_SIZE = 6.2f
            private const val TOOLTIP_GAP = 4f
        }

        private val pageContainer = FrameLayoutWidget()
        private val skillIcons = linkedMapOf<Skill, Widget>()
        private var skillTooltipHeight = 1f
        private var tooltipSkill: Skill? = null
        private var tooltipBindingRevision = -1L
        private var capturing: CaptureTarget? = null
        private var pendingType: InputSystem.InputType? = null
        private val pendingKeys: MutableSet<Int> = linkedSetOf()
        private var pendingMouseButton: Int = -1
        private var pendingModifiers: Int = 0

        private val captureHint = object : LabelWidget("") {
            override fun tick() {
                super.tick()
                updateCaptureHint()
            }
        }
        private val skillTooltipText = LabelWidget("").apply {
            baseFontSize = TOOLTIP_FONT_SIZE
            setRed(0.9f)
            setGreen(0.95f)
            setBlue(1f)
        }
        private val skillTooltip = createSkillTooltip()
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
            val bindingName: String,
            val keyLabel: LabelWidget
        )

        private fun createRoot(): FrameLayoutWidget {
            val root = object : FrameLayoutWidget() {
                override fun tick() {
                    super.tick()
                    updateSkillTooltip()
                }
            }
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
            root.addChild("skill_tooltip", skillTooltip)
            return root
        }

        private fun refreshPage() {
            skillIcons.clear()
            tooltipSkill = null
            skillTooltip.visibility = Widget.Visibility.INVISIBLE
            pageContainer.clearChildren()
            pageContainer.addChild("skills", createSkillPage())
        }

        private fun createSkillPage(): LinearLayoutWidget {
            val page = LinearLayoutWidget()
            page.orientation = Orientation.VERTICAL
            page.spacing = 1f
            page.layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)

            val category = AbilitySystemClient.getCategory()
            page.addChild(
                "category", LabelWidget(
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
            columnHeader.addChild(
                "toggle_title", LabelWidget(
                    translate("app.academy.settings.keybind.toggle")
                ).apply {
                    scale = 0.8f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .width(16f)
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
            val icon = ImageWidget(resolveSkillIcon(info)).apply {
                setSampler(FilterMode.LINEAR, false)
                layoutParams = LinearLayoutWidget.LayoutParams().size(16f, 16f)
            }
            skillIcons[skill] = icon
            header.addChild("icon", icon)
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
            section.addChild(
                "advanced_title", LabelWidget(
                    translate("app.academy.skill_settings.advanced.title")
                ).apply {
                    scale = 0.8f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(10f)
                        .gravity(Gravity.CENTER_LEFT)
                })
            val hasBlockDestructionSetting = DestroyBlocksSetting.supportsSkillBlockDestruction(skill)
            if (hasBlockDestructionSetting) {
                section.addChild("block_destruction", createSkillDestroyBlocksRow(skill))
            }

            val modules = SkillSettingsRegistry.getModules(skill)
            if (modules.isEmpty() && !hasBlockDestructionSetting) {
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

        private fun createSkillTooltip(): FrameLayoutWidget {
            val tooltip = FrameLayoutWidget()
            tooltip.layoutParams = FrameLayoutWidget.LayoutParams().size(TOOLTIP_WIDTH, 1f)
            tooltip.isEnabled = false
            tooltip.visibility = Widget.Visibility.INVISIBLE
            tooltip.addChild("background", FillWidget(0xF00B1115.toInt()).apply {
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            })
            tooltip.addChild("accent", FillWidget(0xFF55CFE1.toInt()).apply {
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .width(2f)
                    .heightMode(SizeMode.MATCH_PARENT)
            })
            skillTooltipText.layoutParams = FrameLayoutWidget.LayoutParams()
                .size(TOOLTIP_WIDTH - TOOLTIP_PADDING * 2, 1f)
                .margin(TOOLTIP_PADDING, TOOLTIP_PADDING, TOOLTIP_PADDING, TOOLTIP_PADDING)
            tooltip.addChild("text", skillTooltipText)
            return tooltip
        }

        private fun updateSkillTooltip() {
            if (capturing != null || root.width <= 0f || root.height <= 0f) {
                skillTooltip.visibility = Widget.Visibility.INVISIBLE
                return
            }
            val hovered = skillIcons.entries.firstOrNull { it.value.isHovered }
            if (hovered == null) {
                skillTooltip.visibility = Widget.Visibility.INVISIBLE
                return
            }

            val bindingRevision = InputSystem.getBindingRevision()
            if (tooltipSkill !== hovered.key || tooltipBindingRevision != bindingRevision) {
                tooltipSkill = hovered.key
                tooltipBindingRevision = bindingRevision
                val text = wrapTooltipText(buildSkillTooltipText(hovered.key))
                skillTooltipText.text = text
                val textHeight = LabelWidget.getTextHeight(text, TOOLTIP_FONT_SIZE)
                skillTooltipHeight = textHeight + TOOLTIP_PADDING * 2
                skillTooltip.layoutParams = FrameLayoutWidget.LayoutParams()
                    .size(TOOLTIP_WIDTH, skillTooltipHeight)
                skillTooltipText.layoutParams = FrameLayoutWidget.LayoutParams()
                    .size(TOOLTIP_WIDTH - TOOLTIP_PADDING * 2, textHeight)
                    .margin(TOOLTIP_PADDING, TOOLTIP_PADDING, TOOLTIP_PADDING, TOOLTIP_PADDING)
            }

            val icon = hovered.value
            val anchorX = icon.getAbsoluteX() - root.getAbsoluteX()
            val anchorY = icon.getAbsoluteY() - root.getAbsoluteY()
            val maxX = maxOf(2f, root.width - TOOLTIP_WIDTH - 2f)
            val preferredX = anchorX + icon.width + TOOLTIP_GAP
            val fallbackX = anchorX - TOOLTIP_WIDTH - TOOLTIP_GAP
            skillTooltip.translationX = (if (preferredX <= maxX) preferredX else fallbackX).coerceIn(2f, maxX)
            val maxY = maxOf(2f, root.height - skillTooltipHeight - 2f)
            skillTooltip.translationY = anchorY.coerceIn(2f, maxY)
            skillTooltip.visibility = Widget.Visibility.VISIBLE
        }

        private fun buildSkillTooltipText(skill: Skill): String {
            val descriptionKey = "skill.${skill.key.namespace}.${skill.key.path}.desc"
            val localizedDescription = L10n[descriptionKey]
            val description = if (localizedDescription == descriptionKey) {
                translate("app.academy.skill_settings.tooltip.description_missing")
            } else localizedDescription

            val lines = mutableListOf(
                skill.translatedName,
                translate("app.academy.skill_settings.tooltip.description"),
                description,
                translate("app.academy.skill_settings.tooltip.usage")
            )
            val bindings = InputSystem.getKeyBindings()
                .filter { InputSystem.isBindingForSkill(it.name(), skill) }
            if (bindings.isEmpty()) {
                lines += translate("app.academy.skill_settings.tooltip.passive")
            } else {
                bindings.forEach { binding ->
                    val actionKey = "key.academy.${binding.name()}"
                    val localizedAction = L10n[actionKey]
                    val actionName = if (localizedAction == actionKey) {
                        binding.name().replace('_', ' ')
                    } else localizedAction
                    val phase = translate(
                        when (binding.combo().action) {
                            InputConstants.PRESS -> "app.academy.skill_settings.tooltip.phase.press"
                            InputConstants.RELEASE -> "app.academy.skill_settings.tooltip.phase.release"
                            InputConstants.REPEAT -> "app.academy.skill_settings.tooltip.phase.repeat"
                            else -> "app.academy.skill_settings.tooltip.phase.press_release"
                        }
                    )
                    val disabled = if (InputSystem.isKeyBindingEnabled(binding.name())) "" else
                        " ${translate("app.academy.skill_settings.tooltip.disabled")}"
                    lines += "- $actionName: ${displayBinding(binding.combo())} ($phase)$disabled"
                }
                val selectedKey = InputSystem.getKeyBinding(AbilityInfoHud.KEY_NAME_RELEASE_SELECTED)
                    ?.let(::displayBinding)
                    ?: translate("app.academy.settings.keybind.format.none")
                lines += translate("app.academy.skill_settings.tooltip.selected_cast")
                    .replace("%s", selectedKey)
            }
            return lines.joinToString("\n")
        }

        private fun wrapTooltipText(text: String): String {
            val maxWidth = TOOLTIP_WIDTH - TOOLTIP_PADDING * 2
            return text.lines().flatMap { wrapTooltipLine(it, maxWidth) }.joinToString("\n")
        }

        private fun wrapTooltipLine(line: String, maxWidth: Float): List<String> {
            if (line.isEmpty() || LabelWidget.getTextWidth(line, TOOLTIP_FONT_SIZE) <= maxWidth) return listOf(line)
            val output = mutableListOf<String>()
            var remaining = line
            while (remaining.isNotEmpty()) {
                var end = 1
                var lastSpace = -1
                while (end <= remaining.length
                    && LabelWidget.getTextWidth(remaining.substring(0, end), TOOLTIP_FONT_SIZE) <= maxWidth
                ) {
                    if (remaining[end - 1].isWhitespace()) lastSpace = end - 1
                    end++
                }
                var split = (end - 1).coerceAtLeast(1)
                if (split < remaining.length && lastSpace > 0) split = lastSpace
                output += remaining.substring(0, split).trimEnd()
                remaining = remaining.substring(split).trimStart()
            }
            return output
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

            row.addChild(
                "name", LabelWidget(
                    L10n["key.academy.$bindingName"]
                ).apply {
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .weight(1f)
                        .height(10f)
                        .gravity(Gravity.CENTER_LEFT)
                })
            val keyLabel = LabelWidget(displayBinding(combo)).apply {
                scale = 0.7f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .width(44f)
                    .height(10f)
                    .gravity(Gravity.CENTER)
            }
            row.addChild("key", keyLabel)
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
            rebind.onClickListener = { startCapture(section, bindingName, keyLabel) }
            rebind.addChild("text", LabelWidget(translate("app.academy.settings.keybind.rebind")).apply {
                scale = 0.7f
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .gravity(Gravity.CENTER)
            })
            row.addChild("rebind", rebind)

            val reset = ButtonWidget()
            reset.layoutParams = LinearLayoutWidget.LayoutParams()
                .size(26f, 12f)
                .gravity(Gravity.CENTER)
            reset.onClickListener = { resetBinding(section, bindingName, keyLabel) }
            reset.addChild("text", LabelWidget(translate("app.academy.settings.keybind.reset")).apply {
                scale = 0.7f
                layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                    .gravity(Gravity.CENTER)
            })
            row.addChild("reset", reset)
            return row
        }

        private fun createSkillDestroyBlocksRow(skill: Skill): LinearLayoutWidget {
            val row = LinearLayoutWidget()
            row.orientation = Orientation.HORIZONTAL
            row.spacing = 2f
            row.layoutParams = WidgetContainer.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .paddingLeft(3f)
            row.addChild(
                "label", LabelWidget(
                    translate(
                        if (DestroyBlocksSetting.usesIndependentBlockDestructionSetting(skill)) {
                            "app.academy.skill_settings.advanced.block_destruction_independent"
                        } else {
                            "app.academy.skill_settings.advanced.block_destruction"
                        }
                    )
                ).apply {
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .weight(1f)
                        .height(11f)
                        .gravity(Gravity.CENTER_LEFT)
                })
            row.addChild("control", ToggleButtonWidget().apply {
                val player = Minecraft.getInstance().player
                setChecked(player == null || DestroyBlocksSetting.isSkillDestroyBlocksEnabled(player, skill))
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(16f, 9f)
                    .gravity(Gravity.CENTER)
                setOnCheckedChangeListener(object : ToggleButtonWidget.OnCheckedChangeListener {
                    override fun onCheckedChanged(toggle: ToggleButtonWidget, isChecked: Boolean) {
                        val currentPlayer = Minecraft.getInstance().player ?: return
                        DestroyBlocksSetting.setSkillDestroyBlocksEnabled(currentPlayer, skill, isChecked)
                        MisakaNetworkClient.send(DestroyBlocksSetting.SetSkillPacket(skill, isChecked))
                    }
                })
            })
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

                is SkillSettingsRegistry.Choice -> {
                    var tracking = false
                    var displayedIndex = entry.clampIndex(entry.getter.asInt)
                    val value = object : LabelWidget(translate(entry.optionKeys[displayedIndex])) {
                        override fun tick() {
                            super.tick()
                            val available = entry.available.asBoolean
                            if (!tracking) displayedIndex = entry.clampIndex(entry.getter.asInt)
                            text = if (available) {
                                translate(entry.optionKeys[displayedIndex])
                            } else {
                                translate(entry.unavailableKey)
                            }
                            alpha = if (available) 0.85f else 0.35f
                        }
                    }.apply {
                        scale = 0.7f
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .width(64f)
                            .height(10f)
                            .gravity(Gravity.CENTER)
                    }
                    val slider = object : SeekBarWidget() {
                        override fun tick() {
                            super.tick()
                            val available = entry.available.asBoolean
                            isEnabled = available
                            alpha = if (available) 1f else 0.2f
                            if (!tracking) {
                                val selected = entry.clampIndex(entry.getter.asInt)
                                displayedIndex = selected
                                if (progress.roundToInt() != selected) setProgress(selected.toFloat())
                            }
                        }
                    }
                    slider.setMin(0f)
                    slider.setMax(entry.optionKeys.lastIndex.toFloat())
                    slider.setProgress(displayedIndex.toFloat())
                    slider.setKeyProgressIncrement(1)
                    slider.layoutParams = LinearLayoutWidget.LayoutParams()
                        .size(48f, 5f)
                        .gravity(Gravity.CENTER)
                    slider.setOnSeekBarChangeListener(object : SeekBarWidget.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBarWidget,
                            progress: Float,
                            fromUser: Boolean
                        ) {
                            if (!fromUser || !entry.available.asBoolean) return
                            displayedIndex = entry.clampIndex(progress.roundToInt())
                            value.text = translate(entry.optionKeys[displayedIndex])
                            if (!tracking) entry.setter.accept(displayedIndex)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBarWidget) {
                            tracking = true
                        }

                        override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
                            if (tracking && entry.available.asBoolean) {
                                entry.setter.accept(displayedIndex)
                                seekBar.setProgress(displayedIndex.toFloat())
                            }
                            tracking = false
                        }
                    })
                    row.addChild("slider", slider)
                    row.addChild("value", value)
                }

                is SkillSettingsRegistry.FloatRange -> {
                    fun displayedValue(): String {
                        val range = entry.max - entry.min
                        val normalized = if (range > 0f) {
                            ((entry.getter.getAsFloat() - entry.min) / range).coerceIn(0f, 1f)
                        } else 0f
                        return "${(normalized * 100f).roundToInt()}%"
                    }

                    val value = LabelWidget(displayedValue()).apply {
                        scale = 0.7f
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .width(26f)
                            .height(10f)
                            .gravity(Gravity.CENTER)
                    }
                    val stepCount = (((entry.max - entry.min) / entry.step).roundToInt()).coerceAtLeast(1)
                    val currentStep = (((entry.getter.getAsFloat() - entry.min) / entry.step).roundToInt())
                        .coerceIn(0, stepCount)
                    val slider = SeekBarWidget()
                    slider.setMin(0f)
                    slider.setMax(stepCount.toFloat())
                    slider.setProgress(currentStep.toFloat())
                    slider.setKeyProgressIncrement(1)
                    slider.layoutParams = LinearLayoutWidget.LayoutParams()
                        .size(48f, 5f)
                        .gravity(Gravity.CENTER)
                    var tracking = false
                    slider.setOnSeekBarChangeListener(object : SeekBarWidget.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBarWidget,
                            progress: Float,
                            fromUser: Boolean
                        ) {
                            if (!fromUser) return
                            val index = progress.roundToInt().coerceIn(0, stepCount)
                            val updated = entry.quantize(entry.min + index * entry.step)
                            entry.setter.accept(updated)
                            value.text = displayedValue()
                            if (!tracking) entry.commit.run()
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBarWidget) {
                            tracking = true
                        }

                        override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
                            tracking = false
                            entry.commit.run()
                        }
                    })
                    row.addChild("slider", slider)
                    row.addChild("value", value)
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
                        val target = capturing ?: return
                        val current = target.section.config.getKeyBinding(target.bindingName) ?: return
                        resetCaptureState()
                        applyCapture(InputSystem.unbound(current))
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
                    if (pendingType == InputSystem.InputType.KEYBOARD) finishCapture()
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
                    if (pendingType == InputSystem.InputType.MOUSE) finishCapture()
                }

                private fun finishCapture() {
                    val combo = buildPendingCombo() ?: return
                    resetCaptureState()
                    applyCapture(combo)
                }
            }
        }

        private fun buildPendingCombo(): InputSystem.KeyCombination? {
            val target = capturing ?: return null
            val current = target.section.config.getKeyBinding(target.bindingName) ?: return null
            return when (pendingType ?: return null) {
                InputSystem.InputType.KEYBOARD -> {
                    if (pendingKeys.isEmpty()) return null
                    InputSystem.combo(
                        InputSystem.InputType.KEYBOARD,
                        pendingKeys.toSet(),
                        current.action,
                        pendingModifiers,
                        current.availableWhenScreen
                    )
                }

                InputSystem.InputType.MOUSE -> {
                    if (pendingMouseButton < 0) return null
                    InputSystem.combo(
                        InputSystem.InputType.MOUSE,
                        pendingMouseButton,
                        current.action,
                        pendingModifiers,
                        current.availableWhenScreen
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
            resetCaptureState()
            capturing = CaptureTarget(section, bindingName, keyLabel)
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
            return if (combo.unbound) translate("app.academy.settings.keybind.format.none") else combo.displayName()
        }

        private fun updateCaptureHint() {
            val target = capturing
            captureHint.text = if (target == null) {
                ""
            } else {
                val preview = buildPendingCombo()?.let(::displayBinding)
                    ?: translate("app.academy.skill_settings.capture.key")
                translate("app.academy.skill_settings.capture.hint")
                    .replace($$"%1$s", target.bindingName)
                    .replace($$"%2$s", preview)
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
