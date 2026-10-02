package org.academy.internal.client.gui.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.*
import org.academy.api.client.resources.R
import org.academy.api.common.ability.darkmatter.DarkmatterCreatureRegistries
import org.academy.internal.common.ability.darkmatter.creature.DarkmatterCreatureBlueprint
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterCreation
import org.academy.internal.common.world.entity.EntityTypes
import org.academy.internal.common.world.entity.ability.DarkmatterBeetle
import org.misaka.MisakaNetworkClient
import kotlin.jvm.optionals.getOrDefault
import kotlin.math.roundToInt

class DarkmatterCreationScreen : UiScreen(Component.translatable("screen.academy.darkmatter_creation.title")) {
    private var snapshot: DarkmatterCreation.EditorSnapshotPacket
    private var editing: DarkmatterCreatureBlueprint
    private var selectedSlot: Int = 0
    private var tab: Tab = Tab.BLUEPRINT
    private var dirty: Boolean = false
    private var rosterPage: Int = 0
    private var nameBox: TextInputWidget? = null
    private var previewEntity: DarkmatterBeetle? = null
    private var panelWidth: Int = 0
    private var panelHeight: Int = 0
    private var compact: Boolean = false
    private var summonStatus: String? = null
    private lateinit var previewArea: FrameLayoutWidget
    private lateinit var tooltip: ModuleTooltipWidget
    private val moduleButtons: MutableList<Pair<String, ButtonWidget>> = ArrayList()

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
        nameBox = null
        moduleButtons.clear()

        panelWidth = (width - R.ui.darkmatter_creation.panel_margin)
            .coerceIn(R.ui.darkmatter_creation.panel_min_width, R.ui.darkmatter_creation.panel_width).toInt()
        panelHeight = (height - R.ui.darkmatter_creation.panel_margin)
            .coerceIn(R.ui.darkmatter_creation.panel_min_height, R.ui.darkmatter_creation.panel_height).toInt()
        compact = panelWidth < 500 || panelHeight < 285

        root.apply {
            lateinit var slotGroup: RadioGroupWidget
            lateinit var tabGroup: RadioGroupWidget

            frame("panel_darkmatter_creation") {
                lp {
                    gravity(Gravity.CENTER)
                    size(panelWidth.toFloat(), panelHeight.toFloat())
                }

                blendQuad("back") {
                    lp {
                        matchParent()
                    }

                    alpha = R.ui.darkmatter_creation.panel_alpha
                    drawLine = false
                }

                column("content_main") {
                    lp {
                        matchParent()
                        padding(12f, 8f, 12f, 8f)
                    }

                    spacing = 4f

                    row("bar_title") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(R.ui.darkmatter_creation.title_height)
                        }

                        text(
                            Component.translatable("screen.academy.darkmatter_creation.title").string,
                            "title_darkmatter_creation"
                        ) {
                            lp {
                                gravity(Gravity.CENTER_LEFT)
                            }

                            textSize = 8f
                            textColor = R.ui.darkmatter_creation.accent
                        }

                        empty("spacer_title") {
                            weight(1f)
                        }

                        slotGroup = radioGroup("bar_slot") {
                            lp {
                                height(R.ui.darkmatter_creation.slot_button_height)
                                gravity(Gravity.CENTER_RIGHT)
                            }

                            orientation = Orientation.HORIZONTAL
                            spacing = 6f

                            for (slot in 0 until 4) {
                                add("button_slot_$slot", createRadioButton((slot + 1).toString(), slot == selectedSlot)) {
                                    lp {
                                        size(R.ui.darkmatter_creation.slot_button_width, R.ui.darkmatter_creation.slot_button_height)
                                    }

                                    onClick {
                                        selectSlot(slot)
                                    }
                                }
                            }
                        }
                    }

                    tabGroup = radioGroup("bar_tab") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(R.ui.darkmatter_creation.tab_height)
                        }

                        orientation = Orientation.HORIZONTAL
                        spacing = R.ui.darkmatter_creation.tab_gap

                        for (value in Tab.entries) {
                            add("button_tab_" + value.name.lowercase(), createRadioButton(tr(value.key), value === tab)) {
                                lp {
                                    width(0f)
                                    heightMode(SizeMode.MATCH_PARENT)
                                }

                                weight(1f)

                                onClick {
                                    commitName()
                                    tab = value
                                    rebuild()
                                }
                            }
                        }
                    }

                    fill(R.ui.darkmatter_creation.rule_soft, "rule_tab") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(1f)
                        }
                    }

                    row("area_content") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(0f)
                        }

                        weight(1f)

                        frame("area_page") {
                            lp {
                                width(0f)
                                heightMode(SizeMode.MATCH_PARENT)
                            }

                            weight(1f)

                            add("page_" + tab.name.lowercase(), buildTabContent())
                        }

                        previewArea = frame("area_preview") {
                            lp {
                                width(R.ui.darkmatter_creation.preview_width)
                                heightMode(SizeMode.MATCH_PARENT)
                            }

                            visibility = if (showPreview()) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
                        }
                    }

                    if (tab !== Tab.SUMMONED) {
                        row("bar_action", spacing = 8f) {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                height(R.ui.darkmatter_creation.action_button_height)
                            }

                            empty("spacer_action") {
                                weight(1f)
                            }

                            add(
                                "button_save",
                                createControlButton(tr("screen.academy.darkmatter_creation.save"), false,
                                    danger = false
                                ) { save() }
                            ) {
                                lp {
                                    width(R.ui.darkmatter_creation.save_button_width)
                                    heightMode(SizeMode.MATCH_PARENT)
                                }
                            }

                            add(
                                "button_summon",
                                createControlButton(
                                    tr("screen.academy.darkmatter_creation.summon"), selected = false, danger = false
                                ) { saveAndSummon() }
                            ) {
                                lp {
                                    width(R.ui.darkmatter_creation.summon_button_width)
                                    heightMode(SizeMode.MATCH_PARENT)
                                }
                            }
                        }
                    }
                }
            }

            tooltip = add("tooltip_module", ModuleTooltipWidget()) {
                lp {
                    gravity(Gravity.TOP_LEFT)
                }
            }

            slotGroup.selectButton(slotGroup.children["button_slot_$selectedSlot"] as RadioButtonWidget)
            tabGroup.selectButton(tabGroup.children["button_tab_" + tab.name.lowercase()] as RadioButtonWidget)
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
        if (!showPreview() || !::previewArea.isInitialized) return
        val level = Minecraft.getInstance().level ?: return
        var entity = previewEntity
        if (entity == null || entity.level() !== level) {
            entity = DarkmatterBeetle(EntityTypes.DARKMATTER_BEETLE.get(), level)
            entity.id = Integer.MIN_VALUE + 1
            previewEntity = entity
        }
        entity.applyBlueprint(editing, selectedSlot, snapshot.abilityLevel, 0, false)
        val left = previewArea.getAbsoluteX().toInt()
        val top = previewArea.getAbsoluteY().toInt()
        InventoryScreen.extractEntityInInventoryFollowsMouse(
            graphics,
            left, top,
            left + previewArea.width.toInt(), top + previewArea.height.toInt(),
            62, 0.0f, mouseX.toFloat(), mouseY.toFloat(), entity
        )
    }

    private fun showPreview(): Boolean = !compact && tab !== Tab.MODULES && tab !== Tab.SUMMONED

    private fun createRadioButton(text: String, selected: Boolean): RadioButtonWidget {
        val button = RadioButtonWidget()
        applyButtonStyle(button, selected, false)
        button.add("label", TextWidget(text)) {
            lp {
                matchParent()
                gravity(Gravity.CENTER)
            }

            textSize = 7.5f
            gravity = Gravity.CENTER
        }
        return button
    }

    private fun createControlButton(
        text: String,
        selected: Boolean,
        danger: Boolean,
        onClick: () -> Unit
    ): ButtonWidget {
        val button = ButtonWidget()
        applyButtonStyle(button, selected, danger)
        button.onClick { onClick() }
        button.add("label", TextWidget(text)) {
            lp {
                matchParent()
                gravity(Gravity.CENTER)
            }

            textSize = 7.5f
            gravity = Gravity.CENTER
        }
        return button
    }

    private fun applyButtonStyle(button: ButtonWidget, selected: Boolean, danger: Boolean) {
        val background = StateListDrawable()
        background.setDefault(ColorDrawable(if (danger) R.ui.darkmatter_creation.danger_base else R.ui.darkmatter_creation.control))
        background.addState(Widget.SELECTED, ColorDrawable(R.ui.darkmatter_creation.control_active))
        background.addState(Widget.HOVERED, ColorDrawable(if (danger) R.ui.darkmatter_creation.danger else R.ui.darkmatter_creation.control_hover))
        background.addState(Widget.PRESSED, ColorDrawable(if (danger) R.ui.darkmatter_creation.danger_pressed else R.ui.darkmatter_creation.control_active))
        button.background = background
        button.isSelected = selected
        button.add("rail", FillWidget(if (selected) R.ui.darkmatter_creation.accent else R.ui.darkmatter_creation.rail_idle)) {
            lp {
                width(if (selected) 2f else 1f)
                heightMode(SizeMode.MATCH_PARENT)
                gravity(Gravity.CENTER_LEFT)
                margin(2f, 3f, 0f, 3f)
            }
        }
    }

    private fun buildTabContent(): Widget = when (tab) {
        Tab.BLUEPRINT -> buildBlueprintTab()
        Tab.PARTS -> buildPartsTab()
        Tab.PHASE -> buildPhaseTab()
        Tab.MODULES -> buildModulesTab()
        Tab.SUMMONED -> buildSummonedTab()
    }

    private fun buildBlueprintTab(): Widget = standaloneColumn(spacing = 6f) {
        lp {
            widthMode(SizeMode.MATCH_PARENT)
        }

        row("row_name") {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
                height(20f)
            }

            nameBox = textBox(32, "box_name") {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    heightMode(SizeMode.MATCH_PARENT)
                }

                text = editing.name()
                hint = tr("screen.academy.darkmatter_creation.name")
                background = ColorDrawable(R.ui.darkmatter_creation.control)
                clearOnEnter(false)
                paddingLeft(5f)

                enter { commitName() }
                onLostFocus { commitName() }
            }
        }

        row("row_investment", spacing = 8f) {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
                height(20f)
            }

            add(
                "button_investment_decrease",
                createControlButton("− 5 MP", selected = false, danger = false) { changeInvestment(-5) }
            ) {
                lp {
                    width(76f)
                    heightMode(SizeMode.MATCH_PARENT)
                }
            }

            text("  " + editing.investment() + " MP", "text_investment") {
                lp {
                    width(0f)
                    heightMode(SizeMode.MATCH_PARENT)
                    gravity(Gravity.CENTER)
                }

                weight(1f)
                gravity = Gravity.CENTER
            }

            add(
                "button_investment_increase",
                createControlButton("+ 5 MP", selected = false, danger = false) { changeInvestment(5) }
            ) {
                lp {
                    width(76f)
                    heightMode(SizeMode.MATCH_PARENT)
                }
            }
        }

        val strength = editing.effectiveInvestment(0) / 5.0
        val stats = tr(
            "screen.academy.darkmatter_creation.stats",
            String.format("%.0f", 8 + 2 * strength),
            String.format("%.1f", 2 + 0.4 * strength),
            String.format("%.1f", 20.0.coerceAtMost(0.5 * strength)),
            String.format("%.3f", 0.20 + 0.004 * strength)
        )
        val errors = editing.validate(snapshot.abilityLevel)
        val status = summonStatus ?: if (errors.isEmpty()) {
            tr("screen.academy.darkmatter_creation.valid")
        } else {
            tr("screen.academy.darkmatter_creation.invalid", errors.joinToString(", ") { validationError(it) })
        }

        text(stats, "text_stats") {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
                heightMode(SizeMode.WRAP_CONTENT)
            }

            singleLine = false
        }

        text(
            tr("screen.academy.darkmatter_creation.module_usage", editing.moduleCost(), editing.moduleBudget()),
            "text_module_usage"
        ) {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
                heightMode(SizeMode.WRAP_CONTENT)
            }

            singleLine = false
        }

        text(fit(status, if (compact) panelWidth - 52 else 306), "text_status") {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
                heightMode(SizeMode.WRAP_CONTENT)
            }

            singleLine = false
        }
    }

    private fun buildPartsTab(): Widget = standaloneColumn(spacing = 8f) {
        lp {
            widthMode(SizeMode.MATCH_PARENT)
        }

        addCycleRow("head", "screen.academy.darkmatter_creation.part.head", editing.head(), HEADS) {
            replaceParts(it, editing.torso(), editing.limbs(), editing.additional())
        }
        addCycleRow("torso", "screen.academy.darkmatter_creation.part.torso", editing.torso(), TORSOS) {
            replaceParts(editing.head(), it, editing.limbs(), editing.additional())
        }
        addCycleRow("limbs", "screen.academy.darkmatter_creation.part.limbs", editing.limbs(), LIMBS) {
            replaceParts(editing.head(), editing.torso(), it, editing.additional())
        }
        addCycleRow("additional", "screen.academy.darkmatter_creation.part.additional", editing.additional(), ADDITIONAL) {
            replaceParts(editing.head(), editing.torso(), editing.limbs(), it)
        }
    }

    private fun WidgetContainer.addCycleRow(
        key: String,
        labelKey: String,
        current: String,
        values: Array<String>,
        setter: (String) -> Unit
    ) {
        column("row_part_$key", spacing = 2f) {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
            }

            add(
                "button_part_$key",
                createControlButton(tr(labelKey) + "  ·  " + partName(current), selected = false, danger = false) {
                    setter(values[(indexOf(values, current) + 1) % values.size])
                    dirty = true
                    rebuild()
                }
            ) {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(24f)
                }
            }

            if (!compact) {
                text(fit(partDescription(current), 292), "text_part_$key") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        heightMode(SizeMode.WRAP_CONTENT)
                    }

                    singleLine = false
                }
            }
        }
    }

    private fun buildPhaseTab(): Widget = standaloneColumn(spacing = 10f) {
        lp {
            widthMode(SizeMode.MATCH_PARENT)
        }

        addPhaseRow("head", tr("screen.academy.darkmatter_creation.part.head"), editing.headAlpha(), 0)
        addPhaseRow("torso", tr("screen.academy.darkmatter_creation.part.torso"), editing.torsoAlpha(), 1)
        addPhaseRow("limbs", tr("screen.academy.darkmatter_creation.part.limbs"), editing.limbsAlpha(), 2)
        addPhaseRow("additional", tr("screen.academy.darkmatter_creation.part.additional"), editing.additionalAlpha(), 3)

        text(tr("screen.academy.darkmatter_creation.phase_pool", snapshot.abilityLevel * 50), "text_phase_pool") {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
                heightMode(SizeMode.WRAP_CONTENT)
            }
        }
    }

    private fun WidgetContainer.addPhaseRow(key: String, label: String, alpha: Int, part: Int) {
        val total = maxOf(1, snapshot.abilityLevel * 50)
        column("row_phase_$key", spacing = 2f) {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
            }

            val valueLabel = text(phaseLabel(label, alpha, total), "text_phase_$key") {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                }
            }

            seekBar("bar_phase_$key") {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(7f)
                }

                setMin(0f)
                setMax(total.toFloat())
                setBarColors(R.ui.darkmatter_creation.seek_track, R.ui.darkmatter_creation.accent)
                setProgress(alpha.toFloat())
                seekListener(object : SeekBarWidget.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                        if (!fromUser) return
                        val points = progress.roundToInt()
                        valueLabel.text = phaseLabel(label, points, total)
                        updatePartPhase(part, points)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBarWidget) {
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
                    }
                })
            }
        }
    }

    private fun buildModulesTab(): Widget {
        val columns = if (compact) 3 else 2
        return standaloneColumn(spacing = 6f) {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
            }

            var index = 0
            while (index < MODULES.size) {
                row("row_modules_${index / columns}", spacing = 6f) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                    }

                    for (column in 0 until columns) {
                        val position = index + column
                        if (position >= MODULES.size) {
                            empty("spacer_module_$position") {
                                weight(1f)
                            }
                            continue
                        }
                        val module = MODULES[position]
                        val enabled = editing.modules().contains(module)
                        val button = add(
                            "button_module_$position",
                            createControlButton(
                                tr(
                                    "screen.academy.darkmatter_creation.module_entry",
                                    moduleName(module), moduleCost(module)
                                ),
                                enabled, false
                            ) { toggleModule(module) }
                        ) {
                            lp {
                                width(0f)
                                height(21f)
                            }

                            weight(1f)
                        }
                        moduleButtons.add(module to button)
                    }
                }
                index += columns
            }

            text(
                tr("screen.academy.darkmatter_creation.module_budget", editing.moduleCost(), editing.moduleBudget()),
                "text_budget_module"
            ) {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    heightMode(SizeMode.WRAP_CONTENT)
                }

                singleLine = false
            }
        }
    }

    private fun buildSummonedTab(): Widget {
        val roster = snapshot.roster
        val rowsPerPage = if (compact) ((panelHeight - 110) / 32).coerceIn(1, 4) else 6
        val from = minOf(roster.size, rosterPage * rowsPerPage)
        val to = minOf(roster.size, from + rowsPerPage)

        return standaloneColumn(spacing = 4f) {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
                heightMode(SizeMode.MATCH_PARENT)
            }

            if (roster.isEmpty()) {
                text(tr("screen.academy.darkmatter_creation.empty"), "text_empty") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                    }
                }
            }

            val rosterContent = standaloneColumn(spacing = 0f) {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    heightMode(SizeMode.WRAP_CONTENT)
                }

                for (position in from until to) {
                    addRosterRow(roster[position], position)
                }
            }

            scrollPanel(Orientation.VERTICAL, "area_roster", rosterContent) {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(0f)
                }

                weight(1f)
            }

            row("bar_pager", spacing = 4f) {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(18f)
                }

                add(
                    "button_page_previous",
                    createControlButton("<", selected = false, danger = false) {
                        rosterPage = maxOf(0, rosterPage - 1)
                        rebuild()
                    }
                ) {
                    lp {
                        size(30f, 18f)
                    }
                }

                add(
                    "button_page_next",
                    createControlButton(">", selected = false, danger = false) {
                        rosterPage = ((roster.size - 1) / rowsPerPage).coerceIn(0, rosterPage + 1)
                        rebuild()
                    }
                ) {
                    lp {
                        size(30f, 18f)
                    }
                }

                empty("spacer_pager") {
                    weight(1f)
                }

                add(
                    "button_dismantle_all",
                    createControlButton(
                        tr("screen.academy.darkmatter_creation.dismantle_all"), selected = false, danger = true
                    ) {
                        MisakaNetworkClient.send(DarkmatterCreation.DismantlePacket(true, null))
                    }
                ) {
                    lp {
                        width(132f)
                        heightMode(SizeMode.MATCH_PARENT)
                    }
                }
            }
        }
    }

    private fun WidgetContainer.addRosterRow(entry: DarkmatterCreation.RosterEntry, position: Int) {
        column("row_roster_$position", spacing = 0f) {
            lp {
                widthMode(SizeMode.MATCH_PARENT)
            }

            val dimension = dimensionName(entry.dimension())
            val place = if (entry.loaded()) {
                if (entry.distance() < 0) {
                    dimension
                } else {
                    tr("screen.academy.darkmatter_creation.roster.distance", String.format("%.1f", entry.distance()))
                }
            } else {
                tr("screen.academy.darkmatter_creation.roster.unloaded", dimension)
            }
            val rowText = tr(
                "screen.academy.darkmatter_creation.roster.row",
                entry.name(), entry.health().roundToInt(), entry.maxHealth().roundToInt(),
                place, entry.investment(), entry.slot() + 1
            )

            row("content_roster_$position", spacing = 6f) {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(26f)
                }

                text(fit(rowText, panelWidth - 150), "text_roster_$position") {
                    lp {
                        width(0f)
                        heightMode(SizeMode.MATCH_PARENT)
                        gravity(Gravity.CENTER_LEFT)
                    }

                    weight(1f)
                    gravity = Gravity.CENTER_LEFT
                }

                add(
                    "button_dismantle_$position",
                    createControlButton(tr("screen.academy.darkmatter_creation.dismantle"),
                        selected = false,
                        danger = true
                    ) {
                        MisakaNetworkClient.send(DarkmatterCreation.DismantlePacket(false, entry.uuid()))
                    }
                ) {
                    lp {
                        width(82f)
                        heightMode(SizeMode.MATCH_PARENT)
                    }
                }
            }

            fill(R.ui.darkmatter_creation.divider, "rule_roster_$position") {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(1f)
                }
            }
        }
    }

    /**
     * 界面专用的模块悬浮提示: 跟随鼠标显示模块名称与描述.
     */
    private inner class ModuleTooltipWidget : FrameLayoutWidget() {
        private val lines: LinearLayoutWidget
        private var current: String? = null

        init {
            isClickable = false
            background = ColorDrawable(R.ui.darkmatter_creation.tooltip_bg)
            visibility = Widget.Visibility.INVISIBLE

            lines = column("lines", spacing = 0f) {
                lp {
                    matchParent()
                    padding(5f, 4f, 5f, 4f)
                }
            }

            setFrameUpdate {
                update()
                true
            }
        }

        private fun update() {
            if (tab !== Tab.MODULES) {
                hide()
                return
            }

            val minecraft = Minecraft.getInstance()
            val window = minecraft.window
            val mouseX = minecraft.mouseHandler.getScaledXPos(window)
            val mouseY = minecraft.mouseHandler.getScaledYPos(window)
            val hovered = moduleButtons.firstOrNull { (_, button) ->
                val x = button.getAbsoluteX()
                val y = button.getAbsoluteY()
                mouseX >= x && mouseX < x + button.width && mouseY >= y && mouseY < y + button.height
            }
            if (hovered == null) {
                hide()
                return
            }

            val module = hovered.first
            if (module != current) {
                current = module
                lines.clearChildren()
                lines.addTooltipLine(moduleName(module), R.ui.darkmatter_creation.tooltip_text, 230)
                lines.addTooltipLine(moduleDescription(module), R.ui.darkmatter_creation.tooltip_description, 230)
            }

            val widest = lines.children.values.maxOfOrNull { it.measuredWidth } ?: 0f
            val boxWidth = (widest + 12f).coerceIn(60f, 240f)
            val boxHeight = lines.measuredHeight.coerceAtLeast(1f)
            width = boxWidth
            height = boxHeight
            val screenWidth = this@DarkmatterCreationScreen.width
            val screenHeight = this@DarkmatterCreationScreen.height
            translationX = (mouseX + 8.0).toFloat().coerceIn(2f, maxOf(2f, screenWidth - boxWidth - 2f))
            translationY = (mouseY + 10.0).toFloat().coerceIn(2f, maxOf(2f, screenHeight - boxHeight - 2f))
            visibility = Widget.Visibility.VISIBLE
        }

        private fun hide() {
            if (visibility !== Widget.Visibility.INVISIBLE) {
                visibility = Widget.Visibility.INVISIBLE
            }
            current = null
        }

        private fun LinearLayoutWidget.addTooltipLine(text: String, color: Int, maxWidth: Int) {
            add("line_${children.size}", TextWidget(text)) {
                lp {
                    width(maxWidth.toFloat())
                    heightMode(SizeMode.WRAP_CONTENT)
                }

                textSize = 7.5f
                textColor = color
                singleLine = false
            }
        }
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
        val key = "screen.academy.darkmatter_creation.error.$error"
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

    private enum class Tab(val key: String) {
        BLUEPRINT("screen.academy.darkmatter_creation.tab.blueprint"),
        PARTS("screen.academy.darkmatter_creation.tab.parts"),
        PHASE("screen.academy.darkmatter_creation.tab.phase"),
        MODULES("screen.academy.darkmatter_creation.tab.modules"),
        SUMMONED("screen.academy.darkmatter_creation.tab.summoned")
    }

    companion object {
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
            return DarkmatterCreatureRegistries.module(id).map { it.budgetCost() }.getOrDefault(0)
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
