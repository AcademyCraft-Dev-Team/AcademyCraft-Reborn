package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.academy.api.client.ability.AbilitySystemClient
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.dsl.onClick
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.text.shape.TextMeasurer
import org.academy.api.client.gui.widget.*
import org.academy.api.common.ability.darkmatter.*
import org.academy.internal.common.ability.Skills
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterShaping
import java.util.*
import kotlin.math.roundToInt

class DarkmatterShapingScreen : UiScreen(Component.translatable("screen.academy.darkmatter_shaping.title")) {
    private val modifiers: MutableMap<String, Int> = LinkedHashMap()
    private val shapeButtons: MutableMap<DarkmatterShape, ButtonWidget> = LinkedHashMap()
    private val phaseWidgets: MutableList<Widget> = ArrayList()
    private val blockWidgets: MutableList<Widget> = ArrayList()
    private val modifierRows: MutableMap<FrameLayoutWidget, DarkmatterModifierType> = LinkedHashMap()
    private val modifierRowFills: MutableMap<FrameLayoutWidget, FillWidget> = LinkedHashMap()
    private var selectedShape: DarkmatterShape = DarkmatterShape.TOOL
    private var alphaPercent: Int = 50
    private lateinit var modifierContent: LinearLayoutWidget
    private lateinit var modifierScroll: ScrollPanelWidget
    private var lastModifierMouseX: Double = Double.NaN
    private var lastModifierMouseY: Double = Double.NaN
    private lateinit var alphaLabel: TextWidget
    private lateinit var betaLabel: TextWidget
    private lateinit var budgetLabel: TextWidget
    private lateinit var costLabel: TextWidget
    private lateinit var parametersLabel: TextWidget
    private lateinit var selectionLabel: TextWidget
    private lateinit var statusLabel: TextWidget
    private lateinit var blockHardnessLabel: TextWidget
    private lateinit var blockResistanceLabel: TextWidget
    private lateinit var blockGravityLabel: TextWidget
    private var createButton: ButtonWidget? = null
    private var blockHardness: Float = DarkmatterBlockProfile.DEFAULT.hardness()
    private var blockResistance: Float = DarkmatterBlockProfile.DEFAULT.explosionResistance()
    private var blockGravity: Boolean = DarkmatterBlockProfile.DEFAULT.gravity()
    private var requestPending: Boolean = false

    private lateinit var modifierTooltip: FrameLayoutWidget
    private lateinit var modifierTooltipLines: LinearLayoutWidget
    private var tooltipType: DarkmatterModifierType? = null

    init {
        DarkmatterModifiers.bootstrap()
    }

    override fun onInit() {
        val panel = FrameLayoutWidget()
        panel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(PANEL_W.toFloat(), PANEL_H.toFloat())
            .gravity(Gravity.CENTER)
        val background = BlendQuadWidget()
        background.alpha = 0.48f
        background.drawLine = false
        background.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
        panel.addChild("background", background)
        panel.addChild("top_rule", rule(PANEL_W - 12, 0xE0FFFFFF.toInt(), Gravity.TOP_LEFT, 6, 0))
        panel.addChild("header_rule", rule(PANEL_W - 20, 0x80FFFFFF.toInt(), Gravity.TOP_LEFT, 10, 25))
        panel.addChild("bottom_rule", rule(PANEL_W - 12, 0x70FFFFFF, Gravity.BOTTOM_LEFT, 6, 0))
        panel.addChild("left_separator", verticalRule(0x55FFFFFF, 98, 30, PANEL_H - 40))
        panel.addChild("right_separator", verticalRule(0x55FFFFFF, 246, 30, PANEL_H - 40))

        val title = label("screen.academy.darkmatter_shaping.title", 8.0f)
        title.layoutParams = FrameLayoutWidget.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(12f, 8f, 0f, 0f)
        panel.addChild("title", title)
        root.addChild("darkmatter_shaping", panel)

        buildShapeList(panel)
        buildPhaseEditor(panel)
        buildModifierList(panel)
        buildFooter(panel)
        buildModifierTooltip()
        refreshAll()
    }

    private fun buildShapeList(panel: FrameLayoutWidget) {
        val section = label("screen.academy.darkmatter_shaping.shapes", 7.0f)
        section.alpha = 0.72f
        section.layoutParams = FrameLayoutWidget.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(10f, 31f, 0f, 0f)
        panel.addChild("shape_header", section)

        val scroll = ScrollPanelWidget()
        scroll.setScrollSpeed(18.0f)
        scroll.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(82f, 140f)
            .gravity(Gravity.TOP_LEFT)
            .margin(10f, 43f, 0f, 0f)
        val content = LinearLayoutWidget()
        content.orientation = Orientation.VERTICAL
        content.spacing = 2.0f
        content.layoutParams = LinearLayoutWidget.LayoutParams()
            .width(77f)
            .heightMode(SizeMode.WRAP_CONTENT)
        var index = 0
        for (shape in DarkmatterShape.values()) {
            val unlocked = shape.isUnlockedAt(currentAbilityLevel())
            val name = Component.translatable(shape.translationKey()).string
            val button = textButton(
                if (unlocked) name else Component.translatable(
                    "screen.academy.darkmatter_shaping.locked.entry", name,
                    shape.requiredAbilityLevel()
                ).string,
                77,
                18
            )
            button.onClick { selectShape(shape) }
            button.isEnabled = unlocked
            button.alpha = if (unlocked) 1.0f else 0.34f
            button.tooltipText = (if (unlocked) {
                Component.translatable(
                    "screen.academy.darkmatter_shaping.shape.tooltip",
                    shape.baseMatterCost()
                )
            } else {
                Component.translatable(
                    "screen.academy.darkmatter_shaping.shape.tooltip.locked",
                    shape.baseMatterCost(), shape.requiredAbilityLevel()
                )
            }).string
            shapeButtons[shape] = button
            content.addChild("shape_" + index++, button)
        }
        scroll.setContent(content)
        panel.addChild("shape_scroll", scroll)
        val bar = ScrollBarWidget(scroll, Orientation.VERTICAL)
        bar.setShowBackground(true)
        bar.setTrackColor(0x28000000)
        bar.setThumbColor(0xB0FFFFFF.toInt())
        bar.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(4f, 140f)
            .gravity(Gravity.TOP_LEFT)
            .margin(91f, 43f, 0f, 0f)
        panel.addChild("shape_scrollbar", bar)
    }

    private fun buildPhaseEditor(panel: FrameLayoutWidget) {
        val section = label("screen.academy.darkmatter_shaping.phase", 7.0f)
        section.alpha = 0.72f
        section.layoutParams = FrameLayoutWidget.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 31f, 0f, 0f)
        panel.addChild("phase_header", section)

        selectionLabel = TextWidget("")
        selectionLabel.textSize = 8.0f
        selectionLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, 12f)
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 46f, 0f, 0f)
        panel.addChild("selection", selectionLabel)

        alphaLabel = TextWidget("")
        alphaLabel.textSize = 7.0f
        alphaLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(60f, 10f)
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 67f, 0f, 0f)
        panel.addChild("alpha", alphaLabel)
        phaseWidgets.add(alphaLabel)

        betaLabel = TextWidget("")
        betaLabel.textSize = 7.0f
        betaLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(60f, 10f)
            .gravity(Gravity.TOP_RIGHT)
            .margin(0f, 67f, 157f, 0f)
        panel.addChild("beta", betaLabel)
        phaseWidgets.add(betaLabel)

        val slider = SeekBarWidget()
        slider.setMin(0.0f)
        slider.setMax(100.0f)
        slider.setProgress(alphaPercent.toFloat())
        slider.setKeyProgressIncrement(1)
        slider.setBarColors(0x402A2A2A, ACCENT)
        slider.tooltipText = Component.translatable(
            "screen.academy.darkmatter_shaping.phase_hint"
        ).string
        slider.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, 6f)
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 81f, 0f, 0f)
        slider.setOnSeekBarChangeListener(object : SeekBarWidget.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                alphaPercent = progress.roundToInt()
                refreshSummary()
            }

            override fun onStartTrackingTouch(seekBar: SeekBarWidget) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
            }
        })
        panel.addChild("phase_slider", slider)
        phaseWidgets.add(slider)

        budgetLabel = TextWidget("")
        budgetLabel.textSize = 7.0f
        budgetLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, 10f)
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 100f, 0f, 0f)
        panel.addChild("budget", budgetLabel)
        phaseWidgets.add(budgetLabel)

        costLabel = TextWidget("")
        costLabel.textSize = 7.0f
        costLabel.alpha = 0.72f
        costLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, 10f)
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 114f, 0f, 0f)
        panel.addChild("cost", costLabel)

        parametersLabel = TextWidget("")
        parametersLabel.textSize = 6.25f
        parametersLabel.alpha = 0.82f
        parametersLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, 48f)
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 130f, 0f, 0f)
        panel.addChild("parameters", parametersLabel)

        buildBlockEditor(panel)
    }

    private fun buildBlockEditor(panel: FrameLayoutWidget) {
        blockHardnessLabel = TextWidget("")
        blockHardnessLabel.textSize = 7.0f
        blockHardnessLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, 10f)
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 67f, 0f, 0f)
        panel.addChild("block_hardness_label", blockHardnessLabel)
        blockWidgets.add(blockHardnessLabel)

        val hardnessSlider = SeekBarWidget()
        hardnessSlider.setMin(DarkmatterBlockProfile.MIN_HARDNESS)
        hardnessSlider.setMax(DarkmatterBlockProfile.MAX_HARDNESS)
        hardnessSlider.setProgress(blockHardness)
        hardnessSlider.setKeyProgressIncrement(1)
        hardnessSlider.setBarColors(0x402A2A2A, ACCENT)
        hardnessSlider.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, 6f)
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 80f, 0f, 0f)
        hardnessSlider.setOnSeekBarChangeListener(object : SeekBarWidget.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                blockHardness = (progress * 2.0f).roundToInt() / 2.0f
                refreshSummary()
            }

            override fun onStartTrackingTouch(seekBar: SeekBarWidget) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
            }
        })
        panel.addChild("block_hardness_slider", hardnessSlider)
        blockWidgets.add(hardnessSlider)

        blockResistanceLabel = TextWidget("")
        blockResistanceLabel.textSize = 7.0f
        blockResistanceLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, 10f)
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 92f, 0f, 0f)
        panel.addChild("block_resistance_label", blockResistanceLabel)
        blockWidgets.add(blockResistanceLabel)

        val resistanceSlider = SeekBarWidget()
        resistanceSlider.setMin(DarkmatterBlockProfile.MIN_EXPLOSION_RESISTANCE)
        resistanceSlider.setMax(DarkmatterBlockProfile.MAX_EXPLOSION_RESISTANCE)
        resistanceSlider.setProgress(blockResistance)
        resistanceSlider.setKeyProgressIncrement(10)
        resistanceSlider.setBarColors(0x402A2A2A, ACCENT)
        resistanceSlider.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, 6f)
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 105f, 0f, 0f)
        resistanceSlider.setOnSeekBarChangeListener(object : SeekBarWidget.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                blockResistance = (progress / 10.0f).roundToInt() * 10.0f
                refreshSummary()
            }

            override fun onStartTrackingTouch(seekBar: SeekBarWidget) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
            }
        })
        panel.addChild("block_resistance_slider", resistanceSlider)
        blockWidgets.add(resistanceSlider)

        blockGravityLabel = TextWidget("")
        blockGravityLabel.textSize = 7.0f
        blockGravityLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(96f, 12f)
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, 132f, 0f, 0f)
        panel.addChild("block_gravity_label", blockGravityLabel)
        blockWidgets.add(blockGravityLabel)

        val gravityToggle = ToggleButtonWidget()
        gravityToggle.updateChecked(blockGravity)
        gravityToggle.updateTrackColors(0x50FFFFFF, ACCENT)
        gravityToggle.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(28f, 10f)
            .gravity(Gravity.TOP_LEFT)
            .margin(208f, 130f, 0f, 0f)
        gravityToggle.updateOnCheckedChangeListener(object : ToggleButtonWidget.OnCheckedChangeListener {
            override fun onCheckedChanged(toggle: ToggleButtonWidget, isChecked: Boolean) {
                blockGravity = isChecked
                refreshSummary()
            }
        })
        panel.addChild("block_gravity_toggle", gravityToggle)
        blockWidgets.add(gravityToggle)
    }

    private fun buildModifierList(panel: FrameLayoutWidget) {
        val section = label("screen.academy.darkmatter_shaping.modifiers", 7.0f)
        section.alpha = 0.72f
        section.layoutParams = FrameLayoutWidget.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(256f, 31f, 0f, 0f)
        panel.addChild("modifier_header", section)

        modifierScroll = ScrollPanelWidget()
        modifierScroll.setScrollSpeed(18.0f)
        modifierScroll.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(119f, 140f)
            .gravity(Gravity.TOP_LEFT)
            .margin(256f, 43f, 0f, 0f)
        modifierContent = LinearLayoutWidget()
        modifierContent.orientation = Orientation.VERTICAL
        modifierContent.spacing = 2.0f
        modifierContent.layoutParams = LinearLayoutWidget.LayoutParams()
            .width(114f)
            .heightMode(SizeMode.WRAP_CONTENT)
        modifierScroll.setContent(modifierContent)
        panel.addChild("modifier_scroll", modifierScroll)
        val bar = ScrollBarWidget(modifierScroll, Orientation.VERTICAL)
        bar.setShowBackground(true)
        bar.setTrackColor(0x28000000)
        bar.setThumbColor(0xB0FFFFFF.toInt())
        bar.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(4f, 140f)
            .gravity(Gravity.TOP_LEFT)
            .margin(378f, 43f, 0f, 0f)
        panel.addChild("modifier_scrollbar", bar)
    }

    private fun buildFooter(panel: FrameLayoutWidget) {
        statusLabel = TextWidget("")
        statusLabel.textSize = 6.5f
        statusLabel.alpha = 0.75f
        statusLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, 10f)
            .gravity(Gravity.BOTTOM_LEFT)
            .margin(256f, 0f, 0f, 12f)
        panel.addChild("status", statusLabel)

        val button = textButton(
            Component.translatable("screen.academy.darkmatter_shaping.create").string, 128, 18
        )
        button.onClick { submit() }
        button.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, 18f)
            .gravity(Gravity.BOTTOM_LEFT)
            .margin(108f, 0f, 0f, 8f)
        panel.addChild("create", button)
        createButton = button
    }

    private fun buildModifierTooltip() {
        modifierTooltip = FrameLayoutWidget()
        modifierTooltip.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED)
        modifierTooltip.visibility = Widget.Visibility.INVISIBLE

        val background = FillWidget(0xD9101010.toInt())
        background.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        modifierTooltip.addChild("background", background)

        modifierTooltipLines = LinearLayoutWidget()
        modifierTooltipLines.orientation = Orientation.VERTICAL
        modifierTooltipLines.spacing = 0f
        modifierTooltipLines.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
            .padding(4f, 3f, 4f, 3f)
        modifierTooltip.addChild("lines", modifierTooltipLines)

        root.addChild("modifier_tooltip", modifierTooltip)
    }

    private fun selectShape(shape: DarkmatterShape) {
        if (!shape.isUnlockedAt(currentAbilityLevel())) {
            showLevelRequirement(shape.requiredAbilityLevel())
            return
        }
        selectedShape = shape
        modifiers.entries.removeIf { entry ->
            DarkmatterShapingRegistries.modifier(entry.key).map { type -> !type.supports(shape) }
                .orElse(true)
        }
        refreshAll()
    }

    private fun refreshAll() {
        shapeButtons.forEach { (shape, button) ->
            val unlocked = shape.isUnlockedAt(currentAbilityLevel())
            button.isSelected = unlocked && shape === selectedShape
            button.isEnabled = unlocked
            button.alpha = if (unlocked) 1.0f else 0.34f
        }
        rebuildModifierRows()
        refreshSummary()
    }

    private fun rebuildModifierRows() {
        modifierRows.clear()
        modifierRowFills.clear()
        modifierContent.clearChildren()
        var index = 0
        for (type in DarkmatterShapingRegistries.modifiers()) {
            if (!type.supports(selectedShape)) continue
            modifierContent.addChild("modifier_" + index++, modifierRow(type))
        }
        if (index == 0) {
            val empty = TextWidget(
                Component.translatable("screen.academy.darkmatter_shaping.modifiers.none").string
            )
            empty.textSize = 6.5f
            empty.alpha = 0.6f
            empty.layoutParams = LinearLayoutWidget.LayoutParams().size(114f, 18f)
            modifierContent.addChild("modifier_empty", empty)
        }
    }

    private fun modifierRow(type: DarkmatterModifierType): FrameLayoutWidget {
        val row = FrameLayoutWidget()
        row.layoutParams = LinearLayoutWidget.LayoutParams().size(114f, 18f)

        val fill = FillWidget(0x28000000)
        fill.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        row.addChild("fill", fill)

        val minus = textButton("−", 16, 16)
        minus.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(16f, 16f)
            .gravity(Gravity.CENTER_LEFT)
            .marginLeft(1f)
        minus.onClick { changeModifier(type, -1) }
        row.addChild("minus", minus)

        val level = modifiers.getOrDefault(type.id(), 0)
        val name = Component.translatable(type.nameKey()).string
        val unlocked = type.isUnlockedAt(currentAbilityLevel())
        val displayName = if (unlocked) name else Component.translatable(
            "screen.academy.darkmatter_shaping.locked.entry", name,
            type.requiredAbilityLevel()
        ).string
        val text = TextWidget(displayName + if (level > 0) "  " + level else "")
        text.textSize = 6.5f
        text.alpha = if (unlocked) (if (level > 0) 1.0f else 0.68f) else 0.34f
        text.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(78f, 16f)
            .gravity(Gravity.CENTER)
        row.addChild("name", text)

        val plus = textButton("+", 16, 16)
        plus.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(16f, 16f)
            .gravity(Gravity.CENTER_RIGHT)
            .marginRight(1f)
        plus.onClick { changeModifier(type, 1) }

        minus.isEnabled = unlocked
        plus.isEnabled = unlocked
        minus.alpha = if (unlocked) 1.0f else 0.34f
        plus.alpha = if (unlocked) 1.0f else 0.34f
        row.addChild("plus", plus)

        modifierRows[row] = type
        modifierRowFills[row] = fill
        return row
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        super.mouseMoved(mouseX, mouseY)
        lastModifierMouseX = mouseX
        lastModifierMouseY = mouseY
        updateModifierHover(mouseX, mouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val handled = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        lastModifierMouseX = mouseX
        lastModifierMouseY = mouseY
        updateModifierHover(mouseX, mouseY)
        return handled
    }

    override fun tick() {
        super.tick()
        if (lastModifierMouseX.isFinite() && lastModifierMouseY.isFinite()) {
            updateModifierHover(lastModifierMouseX, lastModifierMouseY)
        }
    }

    private fun updateModifierHover(mouseX: Double, mouseY: Double) {
        val selected = modifierAt(mouseX, mouseY)
        val selectedRow = rowFor(selected)
        modifierRows.forEach { (row, _) ->
            modifierRowFills[row]?.setColor(if (row === selectedRow) 0x507680DE else 0x28000000)
        }
        updateModifierTooltip(selected)
    }

    private fun updateModifierTooltip(type: DarkmatterModifierType?) {
        if (type == null) {
            modifierTooltip.visibility = Widget.Visibility.INVISIBLE
            tooltipType = null
            return
        }
        val maxWidth = modifierTooltipTextWidth(width) - 8
        if (type !== tooltipType) {
            tooltipType = type
            rebuildTooltipLines(type, maxWidth)
        }
        val widest = modifierTooltipLines.children.values.maxOfOrNull { it.measuredWidth } ?: 0f
        val boxWidth = (widest + 8f).coerceIn(40f, (maxWidth + 8).toFloat())
        val boxHeight = modifierTooltipLines.measuredHeight
        modifierTooltip.width = boxWidth
        modifierTooltip.height = boxHeight.coerceAtLeast(1f)
        val x = (lastModifierMouseX + 10.0).toFloat()
        val y = (lastModifierMouseY + 6.0).toFloat()
        modifierTooltip.translationX = x.coerceIn(2f, maxOf(2f, width - boxWidth - 2f))
        modifierTooltip.translationY = y.coerceIn(2f, maxOf(2f, height - boxHeight - 2f))
        modifierTooltip.visibility = Widget.Visibility.VISIBLE
    }

    private fun rebuildTooltipLines(type: DarkmatterModifierType, maxWidth: Int) {
        modifierTooltipLines.clearChildren()
        val level = modifiers.getOrDefault(type.id(), 0)
        addTooltipText(Component.translatable(type.nameKey()).string, maxWidth, ACCENT)
        addTooltipText(
            Component.translatable(
                "screen.academy.darkmatter_shaping.modifier.detail.stats",
                level, type.maxLevel(), type.pointCost()
            ).string, maxWidth, 0xAEB7C5
        )
        addTooltipText(
            Component.translatable("screen.academy.darkmatter_shaping.modifier.detail.effect").string,
            maxWidth, 0xD8DCE5
        )
        addTooltipText(Component.translatable(type.descriptionKey()).string, maxWidth, 0xFFFFFF)
        if (!type.isUnlockedAt(currentAbilityLevel())) {
            addTooltipText(
                Component.translatable(
                    "screen.academy.darkmatter_shaping.modifier.detail.locked",
                    type.requiredAbilityLevel()
                ).string, maxWidth, 0xFF966C
            )
        }
    }

    private fun addTooltipText(text: String, maxWidth: Int, color: Int) {
        for (paragraph in text.split(Regex("\\R"))) {
            if (paragraph.isEmpty()) continue
            val lines = TextMeasurer.wrapLines(paragraph, TOOLTIP_FONT_SIZE, maxWidth.toFloat())
            for (line in lines) {
                if (line.isBlank()) continue
                val widget = TextWidget(line.trim())
                widget.textSize = TOOLTIP_FONT_SIZE
                widget.textColor = color
                widget.layoutParams = LinearLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.WRAP_CONTENT)
                modifierTooltipLines.addChild("line_${modifierTooltipLines.children.size}", widget)
            }
        }
    }

    private fun modifierAt(mouseX: Double, mouseY: Double): DarkmatterModifierType? {
        if (!::modifierScroll.isInitialized || !modifierScroll.isMouseOver(mouseX, mouseY)) return null
        for (entry in modifierRows.entries) {
            if (entry.key.isMouseOver(mouseX, mouseY)) return entry.value
        }
        return null
    }

    private fun rowFor(type: DarkmatterModifierType?): FrameLayoutWidget? {
        if (type == null) return null
        for (entry in modifierRows.entries) {
            if (entry.value === type) return entry.key
        }
        return null
    }

    private fun changeModifier(type: DarkmatterModifierType, delta: Int) {
        if (!type.isUnlockedAt(currentAbilityLevel())) {
            showLevelRequirement(type.requiredAbilityLevel())
            return
        }
        val current = modifiers.getOrDefault(type.id(), 0)
        val next = (current + delta).coerceIn(0, type.maxLevel())
        if (next > 0) {
            for (conflict in type.conflicts()) modifiers.remove(conflict)
            val candidate = LinkedHashMap(modifiers)
            candidate[type.id()] = next
            if (!clientValidation(candidate).valid()) return
            modifiers[type.id()] = next
        } else {
            modifiers.remove(type.id())
        }
        rebuildModifierRows()
        refreshSummary()
    }

    private fun clientValidation(candidate: Map<String, Int>): DarkmatterShaping.Server.ModifierValidation {
        return DarkmatterShaping.Server.validateModifiers(
            selectedShape, candidate,
            AbilitySystemClient.getDarkmatterLevel().coerceIn(1, 5),
            AbilitySystemClient.getSkillProficiencyMilestone(Skills.DARKMATTER_SHAPING.get())
        )
    }

    private fun refreshSummary() {
        val validation = clientValidation(modifiers)
        val blockMode = selectedShape === DarkmatterShape.BLOCK
        setGroupVisible(phaseWidgets, !blockMode)
        setGroupVisible(blockWidgets, blockMode)
        parametersLabel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(128f, (if (blockMode) 30 else 48).toFloat())
            .gravity(Gravity.TOP_LEFT)
            .margin(108f, (if (blockMode) 148 else 130).toFloat(), 0f, 0f)
        selectionLabel.text = Component.translatable(selectedShape.translationKey()).string
        alphaLabel.text = Component.translatable(
            "screen.academy.darkmatter_shaping.alpha", alphaPercent
        ).string
        betaLabel.text = Component.translatable(
            "screen.academy.darkmatter_shaping.beta", 100 - alphaPercent
        ).string
        budgetLabel.text = Component.translatable(
            "screen.academy.darkmatter_shaping.budget",
            validation.usedPoints(), validation.budget()
        ).string
        val milestone = AbilitySystemClient.getSkillProficiencyMilestone(Skills.DARKMATTER_SHAPING.get())
        val rawCost = selectedShape.baseMatterCost() + validation.usedPoints() * 0.5f
        val cost = rawCost * (if (milestone >= 1) 0.9f else 1.0f)
        costLabel.text = Component.translatable(
            "screen.academy.darkmatter_shaping.cost", cost
        ).string
        blockHardnessLabel.text = Component.translatable(
            "screen.academy.darkmatter_shaping.block.hardness", decimal(blockHardness)
        ).string
        blockResistanceLabel.text = Component.translatable(
            "screen.academy.darkmatter_shaping.block.explosion_resistance", decimal(blockResistance)
        ).string
        blockGravityLabel.text = Component.translatable(
            "screen.academy.darkmatter_shaping.block.gravity",
            Component.translatable(
                if (blockGravity) "screen.academy.darkmatter_shaping.block.gravity.enabled"
                else "screen.academy.darkmatter_shaping.block.gravity.disabled"
            )
        ).string
        parametersLabel.text = parameterText(previewProfile())
        createButton?.let { button ->
            val enabled = !requestPending && validation.valid()
            button.isEnabled = enabled
            button.alpha = if (enabled) 1.0f else 0.34f
        }
        if (!requestPending) {
            val requiredLevel = firstLockedRequirement()
            if (requiredLevel > 0) {
                showLevelRequirement(requiredLevel)
            } else if (!validation.valid()) {
                statusLabel.text = Component.translatable(
                    DarkmatterShaping.Result.INVALID_PROFILE.translationKey()
                ).string
            } else {
                statusLabel.text = ""
            }
        }
    }

    private fun previewProfile(): DarkmatterShapingProfile {
        val level = AbilitySystemClient.getDarkmatterLevel().coerceIn(1, 5)
        val total = level * 50
        val alpha = (total * alphaPercent / 100.0f).roundToInt()
        return DarkmatterShapingProfile(level, alpha, total - alpha, modifiers)
    }

    private fun parameterText(profile: DarkmatterShapingProfile): String {
        val alpha = profile.alphaPower()
        val beta = profile.betaPower()
        val penetration = DarkmatterShaping.Server.penetration(selectedShape, beta) * 100.0f
        return when (selectedShape) {
            DarkmatterShape.TOOL -> Component.translatable(
                "screen.academy.darkmatter_shaping.parameters.tool",
                DarkmatterShaping.Server.toolEfficiency(alpha),
                DarkmatterShaping.Server.toolFortune(beta),
                decimal(DarkmatterShaping.Server.directDamage(selectedShape, alpha)),
                decimal(penetration)
            ).string

            DarkmatterShape.SPEAR -> Component.translatable(
                "screen.academy.darkmatter_shaping.parameters.spear",
                decimal(DarkmatterShaping.Server.spearDamage(alpha)),
                decimal(DarkmatterShaping.Server.spearRange(alpha)),
                decimal(DarkmatterShaping.Server.spearSpeed(beta)),
                decimal(penetration)
            ).string

            DarkmatterShape.SWORD, DarkmatterShape.TRIDENT -> Component.translatable(
                "screen.academy.darkmatter_shaping.parameters.weapon",
                decimal(DarkmatterShaping.Server.directDamage(selectedShape, alpha)),
                decimal(penetration)
            ).string

            DarkmatterShape.MACE -> Component.translatable(
                "screen.academy.darkmatter_shaping.parameters.mace",
                decimal(DarkmatterShaping.Server.phaseDamageBonus(alpha)),
                decimal(penetration)
            ).string

            DarkmatterShape.BOW, DarkmatterShape.CROSSBOW, DarkmatterShape.ARROW -> Component.translatable(
                "screen.academy.darkmatter_shaping.parameters.ranged",
                decimal(DarkmatterShaping.Server.phaseDamageBonus(alpha)),
                decimal(penetration),
                if (selectedShape === DarkmatterShape.CROSSBOW) 8 else 15
            ).string

            DarkmatterShape.ARMOR -> Component.translatable(
                "screen.academy.darkmatter_shaping.parameters.armor",
                decimal(DarkmatterShaping.Server.armorReduction(alpha) * 100.0f),
                DarkmatterShaping.Server.armorWeaknessTicks(beta)
            ).string

            DarkmatterShape.COATING -> Component.translatable(
                "screen.academy.darkmatter_shaping.parameters.coating",
                decimal(DarkmatterShaping.Server.phaseDamageBonus(alpha)),
                DarkmatterShaping.Server.toolEfficiency(alpha),
                DarkmatterShaping.Server.toolFortune(beta),
                decimal(DarkmatterShaping.Server.penetration(DarkmatterShape.TOOL, beta) * 100.0f)
            ).string

            DarkmatterShape.BLOCK -> Component.translatable(
                "screen.academy.darkmatter_shaping.parameters.block",
                decimal(blockHardness), decimal(blockResistance)
            ).string
        }
    }

    private fun blockProfile(): DarkmatterBlockProfile {
        return DarkmatterBlockProfile(blockHardness, blockResistance, blockGravity)
    }

    private fun submit() {
        if (requestPending) return
        val validation = clientValidation(modifiers)
        if (!validation.valid()) return
        requestPending = true
        statusLabel.text = Component.translatable(
            "screen.academy.darkmatter_shaping.status.waiting"
        ).string
        createButton?.let { button ->
            button.isEnabled = false
            button.alpha = 0.34f
        }
        DarkmatterShaping.Client.shape(selectedShape, alphaPercent, modifiers, blockProfile())
    }

    private fun firstLockedRequirement(): Int {
        if (!selectedShape.isUnlockedAt(currentAbilityLevel())) {
            return selectedShape.requiredAbilityLevel()
        }
        for (entry in modifiers.entries) {
            if (entry.value <= 0) continue
            val type = DarkmatterShapingRegistries.modifier(entry.key).orElse(null) ?: continue
            if (!type.isUnlockedAt(currentAbilityLevel())) {
                return type.requiredAbilityLevel()
            }
        }
        return 0
    }

    private fun showLevelRequirement(requiredLevel: Int) {
        statusLabel.text = Component.translatable(
            "screen.academy.darkmatter_shaping.locked.level", requiredLevel
        ).string
    }

    fun acceptServerResult(result: DarkmatterShaping.Result) {
        requestPending = false
        if (result === DarkmatterShaping.Result.SHAPED) {
            Minecraft.getInstance().player?.sendOverlayMessage(
                Component.translatable(result.translationKey())
            )
            Minecraft.getInstance().gui.setScreen(null)
            return
        }
        refreshSummary()
        statusLabel.text = Component.translatable(result.translationKey()).string
    }

    companion object {
        private const val PANEL_W = 392
        private const val PANEL_H = 210
        private const val MODIFIER_TOOLTIP_MAX_WIDTH = 180
        private const val MODIFIER_TOOLTIP_SCREEN_MARGIN = 24
        private const val ACCENT = 0xFF7680DE.toInt()
        private const val TOOLTIP_FONT_SIZE = 6.5f

        private fun currentAbilityLevel(): Int {
            return AbilitySystemClient.getDarkmatterLevel().coerceIn(1, 5)
        }

        private fun setGroupVisible(widgets: List<Widget>, visible: Boolean) {
            for (widget in widgets) {
                widget.visibility = if (visible) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
                widget.isEnabled = visible
            }
        }

        private fun decimal(value: Float): String {
            return String.format(Locale.ROOT, "%.1f", value)
        }

        @JvmStatic
        fun modifierTooltipTextWidth(screenWidth: Int): Int {
            return (screenWidth - MODIFIER_TOOLTIP_SCREEN_MARGIN).coerceIn(1, MODIFIER_TOOLTIP_MAX_WIDTH)
        }

        private fun label(key: String, size: Float): TextWidget {
            val label = TextWidget(Component.translatable(key).string)
            label.textSize = size
            return label
        }

        private fun rule(width: Int, color: Int, gravity: Int, x: Int, y: Int): FillWidget {
            val widget = FillWidget(color)
            widget.layoutParams = FrameLayoutWidget.LayoutParams()
                .size(width.toFloat(), 1f)
                .gravity(gravity)
                .margin(x.toFloat(), y.toFloat(), 0f, 0f)
            return widget
        }

        private fun verticalRule(color: Int, x: Int, y: Int, height: Int): FillWidget {
            val widget = FillWidget(color)
            widget.layoutParams = FrameLayoutWidget.LayoutParams()
                .size(1f, height.toFloat())
                .gravity(Gravity.TOP_LEFT)
                .margin(x.toFloat(), y.toFloat(), 0f, 0f)
            return widget
        }

        private fun textButton(text: String, width: Int, height: Int): ButtonWidget {
            val button = ButtonWidget()
            button.layoutParams = FrameLayoutWidget.LayoutParams().size(width.toFloat(), height.toFloat())
            val states = StateListDrawable()
            states.setDefault(ColorDrawable(0x28101010))
            states.addState(Widget.SELECTED, ColorDrawable(0x707680DE))
            states.addState(Widget.HOVERED, ColorDrawable(0x50FFFFFF))
            states.addState(Widget.PRESSED, ColorDrawable(0x907680DE.toInt()))
            button.background = states
            val label = TextWidget(text)
            label.textSize = 7.0f
            label.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
            button.addChild("label", label)
            return button
        }
    }
}
