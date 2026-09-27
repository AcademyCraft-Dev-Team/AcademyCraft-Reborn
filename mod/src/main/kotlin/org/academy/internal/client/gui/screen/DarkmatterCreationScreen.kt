package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.dsl.onClick
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.*
import org.academy.api.common.ability.darkmatter.DarkmatterCreatureRegistries
import org.academy.internal.common.ability.darkmatter.creature.DarkmatterCreatureBlueprint
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterCreation
import org.academy.internal.common.world.entity.EntityTypes
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle
import org.misaka.MisakaNetworkClient
import kotlin.math.roundToInt

class DarkmatterCreationScreen : UiScreen(Component.translatable("screen.academy.darkmatter_creation.title")) {
    private var snapshot: DarkmatterCreation.EditorSnapshotPacket
    private var editing: DarkmatterCreatureBlueprint
    private var selectedSlot: Int = 0
    private var tab: Tab = Tab.BLUEPRINT
    private var dirty: Boolean = false
    private var rosterPage: Int = 0
    private var widgetCounter: Int = 0
    private var nameBox: TextInputWidget? = null
    private lateinit var panel: FrameLayoutWidget
    private var previewEntity: DarkmatterBeetle? = null
    private var panelWidth: Int = PANEL_W
    private var panelHeight: Int = PANEL_H
    private var panelX: Int = 0
    private var panelY: Int = 0
    private var compact: Boolean = false
    private var summonStatus: String? = null
    private val moduleHoverTargets: MutableList<ModuleHoverTarget> = ArrayList()

    private lateinit var moduleTooltip: FrameLayoutWidget
    private lateinit var moduleTooltipLines: LinearLayoutWidget
    private var tooltipModule: String? = null

    init {
        var current = latest
        if (current == null) {
            val defaults = ArrayList<DarkmatterCreatureBlueprint>()
            for (slot in 0 until 4) {
                defaults.add(DarkmatterCreatureBlueprint.defaultFor(slot, 4))
            }
            current = DarkmatterCreation.EditorSnapshotPacket(0, 4, 0, defaults, emptyList())
        }
        snapshot = current
        selectedSlot = snapshot.selectedSlot
        editing = blueprint(selectedSlot)
    }

    override fun onInit() {
        widgetCounter = 0
        moduleHoverTargets.clear()
        nameBox = null
        panelWidth = (width - 12).coerceIn(300, PANEL_W)
        panelHeight = (height - 12).coerceIn(220, PANEL_H)
        panelX = (width - panelWidth) / 2
        panelY = (height - panelHeight) / 2
        compact = panelWidth < 500 || panelHeight < 285

        panel = FrameLayoutWidget()
        panel.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(panelWidth.toFloat(), panelHeight.toFloat())
            .gravity(Gravity.CENTER)
        val background = BlendQuadWidget()
        background.alpha = 0.43f
        background.drawLine = false
        background.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        panel.addChild("background", background)
        addRule(8, 0, panelWidth - 16, 1, 0xE6FFFFFF.toInt())
        addRule(12, 57, panelWidth - 24, 1, 0x60FFFFFF)

        val title = label(Component.translatable("screen.academy.darkmatter_creation.title").string, 13, 9)
        title.textColor = ACCENT
        root.addChild("darkmatter_creation", panel)

        for (slot in 0 until 4) {
            val value = slot
            addButton(
                panelWidth - 182 + slot * 40, 8, 34, 18,
                (slot + 1).toString(), { selectSlot(value) }, slot == selectedSlot, false
            )
        }
        val tabs = Tab.entries
        val tabGap = 3
        val tabWidth = (panelWidth - 24 - tabGap * (tabs.size - 1)) / tabs.size
        for (i in tabs.indices) {
            val value = tabs[i]
            addButton(
                12 + i * (tabWidth + tabGap), 32, tabWidth, 19,
                Component.translatable(value.key).string, {
                    commitName()
                    tab = value
                    rebuild()
                }, tab === value, false
            )
        }

        when (tab) {
            Tab.BLUEPRINT -> buildBlueprintTab()
            Tab.PARTS -> buildPartsTab()
            Tab.PHASE -> buildPhaseTab()
            Tab.MODULES -> buildModulesTab()
            Tab.SUMMONED -> buildSummonedTab()
        }
        if (tab !== Tab.SUMMONED) {
            addButton(
                panelWidth - 210, panelHeight - 29, 92, 20,
                tr("screen.academy.darkmatter_creation.save"), { save() }, false, false
            )
            addButton(
                panelWidth - 112, panelHeight - 29, 100, 20,
                tr("screen.academy.darkmatter_creation.summon"), { saveAndSummon() }, false, false
            )
        }

        buildModuleTooltip()
        root.setFrameUpdate {
            updateModuleTooltip()
            true
        }
    }

    /**
     * 唯一依赖原版通道的绘制: 实时 3D 实体预览没有对应的 widget 实现.
     */
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, a)
        extractCreaturePreview(graphics, mouseX, mouseY)
    }

    private fun extractCreaturePreview(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        if (compact || tab === Tab.MODULES || tab === Tab.SUMMONED || level == null) return
        var entity = previewEntity
        if (entity == null || entity.level() !== level) {
            entity = DarkmatterBeetle(EntityTypes.DARKMATTER_BEETLE.get(), level)
            entity.setId(Integer.MIN_VALUE + 1)
            previewEntity = entity
        }
        entity.applyBlueprint(editing, selectedSlot, snapshot.abilityLevel, 0, false)
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics,
            panelX + panelWidth - 184, panelY + 66,
            panelX + panelWidth - 18, panelY + panelHeight - 42,
            62, 0.0f, mouseX.toFloat(), mouseY.toFloat(), entity
        )
    }

    private fun buildModuleTooltip() {
        moduleTooltip = FrameLayoutWidget()
        moduleTooltip.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.FIXED)
        moduleTooltip.visibility = Widget.Visibility.INVISIBLE

        val background = FillWidget(0xD9101010.toInt())
        background.layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        moduleTooltip.addChild("background", background)

        moduleTooltipLines = LinearLayoutWidget()
        moduleTooltipLines.orientation = Orientation.VERTICAL
        moduleTooltipLines.spacing = 0f
        moduleTooltipLines.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
            .padding(5f, 4f, 5f, 4f)
        moduleTooltip.addChild("lines", moduleTooltipLines)

        root.addChild("module_tooltip", moduleTooltip)
    }

    private fun updateModuleTooltip() {
        if (!::moduleTooltip.isInitialized) return
        if (tab !== Tab.MODULES) {
            moduleTooltip.visibility = Widget.Visibility.INVISIBLE
            tooltipModule = null
            return
        }
        val minecraft = Minecraft.getInstance()
        val window = minecraft.window
        val mouseX = minecraft.mouseHandler.getScaledXPos(window)
        val mouseY = minecraft.mouseHandler.getScaledYPos(window)
        val target = moduleHoverTargets.firstOrNull {
            mouseX >= panelX + it.x && mouseX < panelX + it.x + it.width &&
                    mouseY >= panelY + it.y && mouseY < panelY + it.y + it.height
        }
        if (target == null) {
            moduleTooltip.visibility = Widget.Visibility.INVISIBLE
            tooltipModule = null
            return
        }
        if (target.module != tooltipModule) {
            tooltipModule = target.module
            rebuildModuleTooltip(target.module)
        }
        val widest = moduleTooltipLines.children.values.maxOfOrNull { it.measuredWidth } ?: 0f
        val boxWidth = (widest + 12f).coerceIn(60f, 240f)
        val boxHeight = moduleTooltipLines.measuredHeight.coerceAtLeast(1f)
        moduleTooltip.width = boxWidth
        moduleTooltip.height = boxHeight
        moduleTooltip.translationX = (mouseX + 8.0).toFloat().coerceIn(2f, maxOf(2f, width - boxWidth - 2f))
        moduleTooltip.translationY = (mouseY + 10.0).toFloat().coerceIn(2f, maxOf(2f, height - boxHeight - 2f))
        moduleTooltip.visibility = Widget.Visibility.VISIBLE
    }

    private fun rebuildModuleTooltip(module: String) {
        moduleTooltipLines.clearChildren()
        addTooltipLine(moduleName(module), 0xFFFFFFFF.toInt(), 230)
        addTooltipLine(moduleDescription(module), 0xFF9AA4AA.toInt(), 230)
    }

    private fun addTooltipLine(text: String, color: Int, maxWidth: Int) {
        val widget = TextWidget(text)
        widget.textSize = 7.5f
        widget.textColor = color
        widget.singleLine = false
        widget.layoutParams = LinearLayoutWidget.LayoutParams()
            .width(maxWidth.toFloat())
            .heightMode(SizeMode.WRAP_CONTENT)
        moduleTooltipLines.addChild("line_${moduleTooltipLines.children.size}", widget)
    }

    private fun buildBlueprintTab() {
        val inputY = if (compact) 70 else 82
        val box = TextInputWidget(32)
        box.text = editing.name()
        box.hint = tr("screen.academy.darkmatter_creation.name")
        box.setClearWhenEnter(false)
        box.setWhenEnter { commitName() }
        box.setOnFocusLost { commitName() }
        box.background = ColorDrawable(CONTROL)
        box.layoutParams = FrameLayoutWidget.LayoutParams()
            .size((if (compact) panelWidth - 52 else 238).toFloat(), 20f)
            .gravity(Gravity.TOP_LEFT)
            .margin(26f, inputY.toFloat(), 0f, 0f)
            .paddingLeft(5f)
        panel.addChild("name", box)
        nameBox = box

        val investmentY = if (compact) 104 else 124
        addButton(26, investmentY, 76, 20, "− 5 MP", { changeInvestment(-5) }, false, false)
        addButton(188, investmentY, 76, 20, "+ 5 MP", { changeInvestment(5) }, false, false)
        addLabel(
            "screen.academy.darkmatter_creation.investment",
            114,
            investmentY + 6,
            "  " + editing.investment() + " MP"
        )
        val strength = editing.effectiveInvestment(0) / 5.0
        addLabelLiteral(
            tr(
                "screen.academy.darkmatter_creation.stats",
                String.format("%.0f", 8 + 2 * strength),
                String.format("%.1f", 2 + 0.4 * strength),
                String.format("%.1f", Math.min(20.0, 0.5 * strength)),
                String.format("%.3f", 0.20 + 0.004 * strength)
            ),
            26, if (compact) 137 else 170
        )
        addLabelLiteral(
            tr(
                "screen.academy.darkmatter_creation.module_usage",
                editing.moduleCost(), editing.moduleBudget()
            ),
            26, if (compact) 157 else 191
        )
        val errors = editing.validate(snapshot.abilityLevel)
        addLabelLiteral(
            fit(
                summonStatus ?: if (errors.isEmpty()) {
                    tr("screen.academy.darkmatter_creation.valid")
                } else {
                    tr(
                        "screen.academy.darkmatter_creation.invalid",
                        errors.joinToString(", ") { validationError(it) }
                    )
                },
                if (compact) panelWidth - 52 else 306
            ),
            26, if (compact) 177 else 220
        )
    }

    private fun buildPartsTab() {
        val firstY = if (compact) 68 else 78
        val gap = if (compact) 28 else 35
        addCycleButton(24, firstY, "screen.academy.darkmatter_creation.part.head", editing.head(), HEADS) { value ->
            replaceParts(value, editing.torso(), editing.limbs(), editing.additional())
        }
        addCycleButton(
            24,
            firstY + gap,
            "screen.academy.darkmatter_creation.part.torso",
            editing.torso(),
            TORSOS
        ) { value ->
            replaceParts(editing.head(), value, editing.limbs(), editing.additional())
        }
        addCycleButton(
            24,
            firstY + gap * 2,
            "screen.academy.darkmatter_creation.part.limbs",
            editing.limbs(),
            LIMBS
        ) { value ->
            replaceParts(editing.head(), editing.torso(), value, editing.additional())
        }
        addCycleButton(
            24,
            firstY + gap * 3,
            "screen.academy.darkmatter_creation.part.additional",
            editing.additional(),
            ADDITIONAL
        ) { value ->
            replaceParts(editing.head(), editing.torso(), editing.limbs(), value)
        }
    }

    private fun buildPhaseTab() {
        val firstY = if (compact) 68 else 75
        val gap = if (compact) 31 else 40
        addPhaseSlider(24, firstY, tr("screen.academy.darkmatter_creation.part.head"), editing.headAlpha(), 0)
        addPhaseSlider(24, firstY + gap, tr("screen.academy.darkmatter_creation.part.torso"), editing.torsoAlpha(), 1)
        addPhaseSlider(
            24,
            firstY + gap * 2,
            tr("screen.academy.darkmatter_creation.part.limbs"),
            editing.limbsAlpha(),
            2
        )
        addPhaseSlider(
            24, firstY + gap * 3,
            tr("screen.academy.darkmatter_creation.part.additional"), editing.additionalAlpha(), 3
        )
        addLabelLiteral(
            tr("screen.academy.darkmatter_creation.phase_pool", snapshot.abilityLevel * 50),
            24, if (compact) 192 else 238
        )
    }

    private fun buildModulesTab() {
        val columns = if (compact) 3 else 2
        val columnWidth = (panelWidth - 48 - (columns - 1) * 8) / columns
        val rowGap = if (compact) 28 else 35
        for (i in MODULES.indices) {
            val module = MODULES[i]
            val enabled = editing.modules().contains(module)
            val x = 24 + (i % columns) * (columnWidth + 8)
            val y = 73 + (i / columns) * rowGap
            addButton(
                x, y, columnWidth, 21,
                fit(
                    tr("screen.academy.darkmatter_creation.module_entry", moduleName(module), moduleCost(module)),
                    columnWidth - 8
                ),
                { toggleModule(module) }, enabled, false
            )
            moduleHoverTargets.add(ModuleHoverTarget(x, y, columnWidth, 21, module))
        }
        addLabelLiteral(
            tr("screen.academy.darkmatter_creation.module_budget", editing.moduleCost(), editing.moduleBudget()),
            24, if (compact) panelHeight - 58 else 222
        )
    }

    private fun buildSummonedTab() {
        val roster = snapshot.roster
        val rowsPerPage = if (compact) ((panelHeight - 110) / 32).coerceIn(1, 4) else 6
        val from = minOf(roster.size, rosterPage * rowsPerPage)
        val to = minOf(roster.size, from + rowsPerPage)
        if (roster.isEmpty()) addLabel("screen.academy.darkmatter_creation.empty", 24, 86, "")
        for (i in from until to) {
            val entry = roster[i]
            val rowY = 69 + (i - from) * 32
            addRule(20, rowY + 25, panelWidth - 40, 1, 0x28FFFFFF)
            val dimension = dimensionName(entry.dimension())
            val position = if (entry.loaded()) {
                if (entry.distance() < 0) dimension
                else tr(
                    "screen.academy.darkmatter_creation.roster.distance",
                    String.format("%.1f", entry.distance())
                )
            } else {
                tr("screen.academy.darkmatter_creation.roster.unloaded", dimension)
            }
            val rowText = tr(
                "screen.academy.darkmatter_creation.roster.row",
                entry.name(), Math.round(entry.health()), Math.round(entry.maxHealth()),
                position, entry.investment(), entry.slot() + 1
            )
            addLabelLiteral(font.plainSubstrByWidth(rowText, panelWidth - 150), 24, rowY + 6)
            addButton(
                panelWidth - 108, rowY, 82, 20,
                tr("screen.academy.darkmatter_creation.dismantle"),
                { MisakaNetworkClient.send(DarkmatterCreation.DismantlePacket(false, entry.uuid())) }, false, true
            )
        }
        val controlsY = panelHeight - 29
        addButton(24, controlsY, 30, 18, "<", {
            rosterPage = maxOf(0, rosterPage - 1)
            rebuild()
        }, false, false)
        addButton(58, controlsY, 30, 18, ">", {
            rosterPage = ((roster.size - 1) / rowsPerPage).coerceIn(0, rosterPage + 1)
            rebuild()
        }, false, false)
        addButton(
            panelWidth / 2 - 66, controlsY, 132, 18,
            tr("screen.academy.darkmatter_creation.dismantle_all"),
            { MisakaNetworkClient.send(DarkmatterCreation.DismantlePacket(true, null)) }, false, true
        )
    }

    private fun addCycleButton(
        x: Int,
        y: Int,
        labelKey: String,
        current: String,
        values: Array<String>,
        setter: (String) -> Unit
    ) {
        addButton(
            x, y, if (compact) panelWidth - 48 else 300, 24,
            tr(labelKey) + "  ·  " + partName(current),
            {
                setter(values[(indexOf(values, current) + 1) % values.size])
                dirty = true
                rebuild()
            }, false, false
        )
        if (!compact) {
            addLabelLiteral(fit(partDescription(current), 292), x + 4, y + 25)
        }
    }

    private fun addPhaseSlider(x: Int, y: Int, text: String, alpha: Int, part: Int) {
        val total = maxOf(1, snapshot.abilityLevel * 50)
        val label = addLabelLiteral(phaseLabel(text, alpha, total), x, y)
        val slider = SeekBarWidget()
        slider.setMin(0f)
        slider.setMax(total.toFloat())
        slider.setBarColors(0x50101820, ACCENT)
        slider.layoutParams = FrameLayoutWidget.LayoutParams()
            .size((if (compact) panelWidth - 48 else 300).toFloat(), 7f)
            .gravity(Gravity.TOP_LEFT)
            .margin(x.toFloat(), (y + 13).toFloat(), 0f, 0f)
        slider.setOnSeekBarChangeListener(object : SeekBarWidget.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                if (!fromUser) return
                val points = progress.roundToInt()
                label.text = phaseLabel(text, points, total)
                updatePartPhase(part, points)
            }

            override fun onStartTrackingTouch(seekBar: SeekBarWidget) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
            }
        })
        slider.setProgress(alpha.toFloat())
        panel.addChild("phase_" + widgetCounter++, slider)
    }

    private fun updatePartPhase(part: Int, points: Int) {
        editing = when (part) {
            0 -> copy(
                editing.head(), editing.torso(), editing.limbs(), editing.additional(),
                points, editing.torsoAlpha(), editing.limbsAlpha(), editing.additionalAlpha(), editing.modules()
            )

            1 -> copy(
                editing.head(), editing.torso(), editing.limbs(), editing.additional(),
                editing.headAlpha(), points, editing.limbsAlpha(), editing.additionalAlpha(), editing.modules()
            )

            2 -> copy(
                editing.head(), editing.torso(), editing.limbs(), editing.additional(),
                editing.headAlpha(), editing.torsoAlpha(), points, editing.additionalAlpha(), editing.modules()
            )

            else -> copy(
                editing.head(), editing.torso(), editing.limbs(), editing.additional(),
                editing.headAlpha(), editing.torsoAlpha(), editing.limbsAlpha(), points, editing.modules()
            )
        }
        dirty = true
    }

    private fun addButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        text: String,
        action: () -> Unit,
        selected: Boolean,
        danger: Boolean
    ): ButtonWidget {
        val button = ButtonWidget()
        button.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(width.toFloat(), height.toFloat())
            .gravity(Gravity.TOP_LEFT)
            .margin(x.toFloat(), y.toFloat(), 0f, 0f)
        val background = StateListDrawable()
        background.setDefault(ColorDrawable(if (danger) 0x60402028 else CONTROL))
        background.addState(Widget.SELECTED, ColorDrawable(CONTROL_ACTIVE))
        background.addState(Widget.HOVERED, ColorDrawable(if (danger) DANGER else CONTROL_HOVER))
        background.addState(Widget.PRESSED, ColorDrawable(if (danger) 0xD0783038.toInt() else CONTROL_ACTIVE))
        button.background = background
        button.isSelected = selected
        button.onClick { action() }
        val rail = FillWidget(if (selected) ACCENT else 0x55FFFFFF)
        rail.layoutParams = FrameLayoutWidget.LayoutParams()
            .size((if (selected) 2 else 1).toFloat(), maxOf(1, height - 6).toFloat())
            .gravity(Gravity.CENTER_LEFT)
            .marginLeft(2f)
        button.addChild("rail", rail)
        val label = TextWidget(text)
        label.textSize = 7.5f
        label.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.MATCH_PARENT)
            .gravity(Gravity.CENTER)
        button.addChild("label", label)
        panel.addChild("button_" + widgetCounter++, button)
        return button
    }

    private fun addRule(x: Int, y: Int, width: Int, height: Int, color: Int) {
        val rule = FillWidget(color)
        rule.layoutParams = FrameLayoutWidget.LayoutParams()
            .size(width.toFloat(), height.toFloat())
            .gravity(Gravity.TOP_LEFT)
            .margin(x.toFloat(), y.toFloat(), 0f, 0f)
        panel.addChild("rule_" + widgetCounter++, rule)
    }

    private fun label(value: String, x: Int, y: Int): TextWidget {
        val label = TextWidget(value)
        label.textSize = 8.0f
        label.layoutParams = FrameLayoutWidget.LayoutParams()
            .gravity(Gravity.TOP_LEFT)
            .margin(x.toFloat(), y.toFloat(), 0f, 0f)
        panel.addChild("label_" + widgetCounter++, label)
        return label
    }

    private fun addLabel(key: String, x: Int, y: Int, suffix: String) {
        addLabelLiteral(tr(key) + suffix, x, y)
    }

    private fun addLabelLiteral(value: String, x: Int, y: Int): TextWidget {
        return label(value, x, y)
    }

    private fun fit(value: String, width: Int): String {
        return font.plainSubstrByWidth(value, maxOf(1, width))
    }

    private fun selectSlot(slot: Int) {
        commitName()
        selectedSlot = slot.coerceIn(0, 3)
        editing = blueprint(selectedSlot)
        dirty = false
        summonStatus = null
        rebuild()
    }

    private fun blueprint(slot: Int): DarkmatterCreatureBlueprint {
        if (snapshot.blueprints.size > slot) return snapshot.blueprints[slot].copy()
        return DarkmatterCreatureBlueprint.defaultFor(slot, snapshot.abilityLevel)
    }

    private fun commitName() {
        val box = nameBox ?: return
        if (box.text != editing.name()) {
            editing = DarkmatterCreatureBlueprint(
                box.text, editing.investment(),
                editing.head(), editing.torso(), editing.limbs(), editing.additional(),
                editing.headAlpha(), editing.torsoAlpha(), editing.limbsAlpha(),
                editing.additionalAlpha(), editing.modules()
            )
            dirty = true
        }
    }

    private fun changeInvestment(delta: Int) {
        commitName()
        val value = (editing.investment() + delta).coerceIn(5, snapshot.abilityLevel * 25)
        editing = DarkmatterCreatureBlueprint(
            editing.name(), value, editing.head(),
            editing.torso(), editing.limbs(), editing.additional(), editing.headAlpha(),
            editing.torsoAlpha(), editing.limbsAlpha(), editing.additionalAlpha(), editing.modules()
        )
        dirty = true
        summonStatus = null
        rebuild()
    }

    private fun replaceParts(head: String, torso: String, limbs: String, additional: String) {
        editing = copy(
            head, torso, limbs, additional, editing.headAlpha(), editing.torsoAlpha(),
            editing.limbsAlpha(), editing.additionalAlpha(), editing.modules()
        )
    }

    private fun toggleModule(module: String) {
        val values = ArrayList(editing.modules())
        if (!values.remove(module)) values.add(module)
        editing = copy(
            editing.head(), editing.torso(), editing.limbs(), editing.additional(),
            editing.headAlpha(), editing.torsoAlpha(), editing.limbsAlpha(),
            editing.additionalAlpha(), values
        )
        dirty = true
        rebuild()
    }

    private fun copy(
        head: String, torso: String, limbs: String, additional: String,
        headA: Int, torsoA: Int, limbsA: Int, additionalA: Int,
        modules: List<String>
    ): DarkmatterCreatureBlueprint {
        return DarkmatterCreatureBlueprint(
            editing.name(), editing.investment(), head, torso,
            limbs, additional, headA, torsoA, limbsA, additionalA, modules
        )
    }

    private fun save() {
        commitName()
        if (editing.validate(snapshot.abilityLevel).isNotEmpty()) return
        MisakaNetworkClient.send(DarkmatterCreation.SaveBlueprintPacket(selectedSlot, editing))
        dirty = false
    }

    private fun saveAndSummon() {
        commitName()
        if (editing.validate(snapshot.abilityLevel).isNotEmpty()) return
        MisakaNetworkClient.send(DarkmatterCreation.SummonPacket(selectedSlot, editing))
        summonStatus = tr("screen.academy.darkmatter_creation.status.waiting")
        dirty = false
        rebuild()
    }

    private fun validationError(error: String): String {
        if (error.startsWith("module_budget:")) {
            val values = error.substring("module_budget:".length).split("/", limit = 2)
            return tr(
                "screen.academy.darkmatter_creation.error.module_budget",
                if (values.isNotEmpty()) values[0] else "?",
                if (values.size > 1) values[1] else "?"
            )
        }
        if (error.startsWith("module:")) {
            return tr("screen.academy.darkmatter_creation.error.module", moduleName(error.substring("module:".length)))
        }
        val key = "screen.academy.darkmatter_creation.error." + error
        return localizedOrFallback(key, error)
    }

    private fun applySnapshot(packet: DarkmatterCreation.EditorSnapshotPacket) {
        snapshot = packet
        if (!dirty) {
            selectedSlot = packet.selectedSlot
            editing = blueprint(selectedSlot)
            rebuild()
        } else if (tab === Tab.SUMMONED) {
            rebuild()
        }
    }

    private fun rebuild() {
        clearWidgets()
        init()
    }

    private data class ModuleHoverTarget(val x: Int, val y: Int, val width: Int, val height: Int, val module: String)

    private enum class Tab(val key: String) {
        BLUEPRINT("screen.academy.darkmatter_creation.tab.blueprint"),
        PARTS("screen.academy.darkmatter_creation.tab.parts"),
        PHASE("screen.academy.darkmatter_creation.tab.phase"),
        MODULES("screen.academy.darkmatter_creation.tab.modules"),
        SUMMONED("screen.academy.darkmatter_creation.tab.summoned")
    }

    companion object {
        private const val PANEL_W = 540
        private const val PANEL_H = 320
        private const val ACCENT = 0xFF55C8E8.toInt()
        private const val CONTROL = 0x45101820
        private const val CONTROL_HOVER = 0x70465A64
        private const val CONTROL_ACTIVE = 0x9855C8E8.toInt()
        private const val DANGER = 0xA0502028.toInt()

        private val HEADS = arrayOf(
            DarkmatterCreatureRegistries.HEAD_JAW.toString(),
            DarkmatterCreatureRegistries.HEAD_CANNON.toString(),
            DarkmatterCreatureRegistries.HEAD_HOMING.toString()
        )
        private val TORSOS = arrayOf(
            DarkmatterCreatureRegistries.TORSO_WALK.toString(),
            DarkmatterCreatureRegistries.TORSO_FLY.toString(),
            DarkmatterCreatureRegistries.TORSO_SWIM.toString()
        )
        private val LIMBS = arrayOf(
            DarkmatterCreatureRegistries.LIMBS_GUARD.toString(),
            DarkmatterCreatureRegistries.LIMBS_MINER.toString(),
            DarkmatterCreatureRegistries.LIMBS_CARRIER.toString()
        )
        private val ADDITIONAL = arrayOf(
            DarkmatterCreatureRegistries.ADDITIONAL_NONE.toString(),
            DarkmatterCreatureRegistries.ADDITIONAL_CARAPACE.toString(),
            DarkmatterCreatureRegistries.ADDITIONAL_SENSOR.toString(),
            DarkmatterCreatureRegistries.ADDITIONAL_WEAPON.toString()
        )
        private val MODULES = arrayOf(
            DarkmatterCreatureRegistries.MODULE_GUARD.toString(),
            DarkmatterCreatureRegistries.MODULE_FOCUS.toString(),
            DarkmatterCreatureRegistries.MODULE_PICKUP.toString(),
            DarkmatterCreatureRegistries.MODULE_EXCAVATION.toString(),
            DarkmatterCreatureRegistries.MODULE_SCOUT.toString(),
            DarkmatterCreatureRegistries.MODULE_SELF_REPAIR.toString(),
            DarkmatterCreatureRegistries.MODULE_FORMATION.toString()
        )

        private var latest: DarkmatterCreation.EditorSnapshotPacket? = null

        @JvmStatic
        fun acceptSnapshot(packet: DarkmatterCreation.EditorSnapshotPacket) {
            latest = packet
            Minecraft.getInstance().execute {
                val screen = Minecraft.getInstance().gui.screen()
                if (screen is DarkmatterCreationScreen) {
                    screen.applySnapshot(packet)
                }
            }
        }

        @JvmStatic
        fun acceptRosterDelta(packet: DarkmatterCreation.RosterDeltaPacket) {
            Minecraft.getInstance().execute {
                val current = latest
                if (current == null || packet.baseRevision != current.revision
                    || packet.revision <= packet.baseRevision
                ) {
                    MisakaNetworkClient.send(DarkmatterCreation.EditorRequestPacket.INSTANCE)
                    return@execute
                }
                val next = DarkmatterCreation.EditorSnapshotPacket(
                    packet.revision, current.abilityLevel, current.selectedSlot, current.blueprints, packet.roster
                )
                latest = next
                val screen = Minecraft.getInstance().gui.screen()
                if (screen is DarkmatterCreationScreen) {
                    screen.applySnapshot(next)
                }
            }
        }

        @JvmStatic
        fun acceptSummonResult(packet: DarkmatterCreation.SummonResultPacket) {
            Minecraft.getInstance().execute {
                val minecraft = Minecraft.getInstance()
                val message = Component.translatable(packet.result.translationKey())
                minecraft.player?.sendOverlayMessage(message)
                val screen = minecraft.gui.screen()
                if (screen is DarkmatterCreationScreen) {
                    screen.summonStatus = message.string
                    if (packet.result === DarkmatterCreation.SummonResult.SUMMONED) {
                        screen.tab = Tab.SUMMONED
                        screen.rosterPage = 0
                    }
                    screen.rebuild()
                }
            }
        }

        private fun tr(key: String, vararg arguments: Any): String {
            return Component.translatable(key, *arguments).string
        }

        private fun phaseLabel(label: String, alpha: Int, total: Int): String {
            val alphaPercent = (alpha * 100.0f / total).roundToInt()
            return tr(
                "screen.academy.darkmatter_creation.phase_value",
                label, alphaPercent, 100 - alphaPercent
            )
        }

        private fun partName(raw: String): String {
            val id = Identifier.tryParse(raw) ?: return shortId(raw)
            return DarkmatterCreatureRegistries.part(id)
                .map { type -> localizedOrFallback(type.translationKey(), shortId(raw)) }
                .orElseGet { shortId(raw) }
        }

        private fun partDescription(raw: String): String {
            val id =
                Identifier.tryParse(raw) ?: return tr("screen.academy.darkmatter_creation.missing_part", shortId(raw))
            return DarkmatterCreatureRegistries.part(id)
                .map { type ->
                    localizedOrFallback(
                        type.descriptionTranslationKey(),
                        tr("screen.academy.darkmatter_creation.missing_description")
                    )
                }
                .orElseGet { tr("screen.academy.darkmatter_creation.missing_part", shortId(raw)) }
        }

        private fun moduleName(raw: String): String {
            val id = Identifier.tryParse(raw) ?: return shortId(raw)
            return DarkmatterCreatureRegistries.module(id)
                .map { type -> localizedOrFallback(type.translationKey(), shortId(raw)) }
                .orElseGet { shortId(raw) }
        }

        private fun moduleDescription(raw: String): String {
            val id =
                Identifier.tryParse(raw) ?: return tr("screen.academy.darkmatter_creation.missing_part", shortId(raw))
            return DarkmatterCreatureRegistries.module(id)
                .map { type ->
                    localizedOrFallback(
                        type.descriptionTranslationKey(),
                        tr("screen.academy.darkmatter_creation.missing_description")
                    )
                }
                .orElseGet { tr("screen.academy.darkmatter_creation.error.module", shortId(raw)) }
        }

        private fun moduleCost(raw: String): Int {
            val id = Identifier.tryParse(raw) ?: return 0
            return DarkmatterCreatureRegistries.module(id).map { it.budgetCost() }.orElse(0)
        }

        private fun localizedOrFallback(key: String, fallback: String): String {
            val localized = I18n.get(key)
            return if (localized == key) fallback else localized
        }

        private fun dimensionName(raw: String): String {
            val id = Identifier.tryParse(raw) ?: return raw
            return localizedOrFallback("dimension." + id.namespace + "." + id.path, raw)
        }

        private fun indexOf(values: Array<String>, value: String): Int {
            for (i in values.indices) if (values[i] == value) return i
            return 0
        }

        private fun shortId(value: String?): String {
            if (value == null) return "missing"
            val split = value.indexOf(':')
            return if (split >= 0) value.substring(split + 1) else value
        }
    }
}
