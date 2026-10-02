package org.academy.internal.client.gui.screen

import net.minecraft.network.chat.Component
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.text.model.Ellipsize
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.ScrollPanelWidget
import org.academy.api.client.gui.widget.TextInputWidget
import org.academy.api.client.gui.widget.TextWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.resources.R
import org.academy.internal.common.ability.teleport.skills.lv3.LocationTeleport
import org.academy.internal.common.skilldata.LocationTeleportData.Mark
import org.misaka.MisakaNetworkClient

class LocationTeleportScreen : UiScreen(Component.translatable("skill.academy.location_teleport")) {
    private val marks = ArrayList<Mark>()
    private var quickMarkIndex = -1
    private var defensiveMarkIndex = -1

    private lateinit var nameInput: TextInputWidget
    private lateinit var xInput: TextInputWidget
    private lateinit var yInput: TextInputWidget
    private lateinit var zInput: TextInputWidget
    private lateinit var marksContent: LinearLayoutWidget
    private lateinit var emptyText: TextWidget

    override fun onInit() {
        root.apply {
            clearChildren()

            val titleText = title.string
            val contentWidth = R.ui.location_teleport.content_width
            val controlHeight = R.ui.location_teleport.control_height
            val contentMargin = R.ui.location_teleport.content_margin
            val actionButtonWidth = (contentWidth - R.ui.location_teleport.action_gap) / 2f
            val coordinateWidth =
                (contentWidth - R.ui.location_teleport.coordinate_gap * 2f) / 3f
            val scrollbarReserve = R.ui.location_teleport.scrollbar_gap +
                    R.ui.location_teleport.scrollbar_width
            val inputPaddingHorizontal = 4f
            val inputPaddingVertical = 2f
            val panelInset = 4f
            val dividerInset = 7f

            lateinit var marksScroll: ScrollPanelWidget

            frame("panel") {
                lp {
                    size(R.ui.location_teleport.panel_width, R.ui.location_teleport.panel_height)
                    gravity(Gravity.CENTER)
                }

                blendQuad("panel_background") {
                    lp {
                        matchParent()
                    }

                    alpha = R.ui.location_teleport.panel_alpha
                    marginLeft = panelInset
                    marginTop = panelInset
                    marginRight = panelInset
                    marginBottom = panelInset
                    drawLine = false
                    red = 0f
                    green = 0f
                    blue = 0f
                }

                fill(R.ui.location_teleport.border_color, "border_top") {
                    lp {
                        size(contentWidth + 8f, 1f)
                        gravity(Gravity.TOP)
                        marginLeft(panelInset)
                    }
                }

                fill(R.ui.location_teleport.border_color, "border_bottom") {
                    lp {
                        size(contentWidth + 8f, 1f)
                        gravity(Gravity.BOTTOM)
                        marginLeft(panelInset)
                    }
                }

                fill(R.ui.location_teleport.border_color, "border_left") {
                    lp {
                        size(1f, R.ui.location_teleport.panel_height - 8f)
                        gravity(Gravity.LEFT)
                        marginTop(panelInset)
                    }
                }

                fill(R.ui.location_teleport.border_color, "border_right") {
                    lp {
                        size(1f, R.ui.location_teleport.panel_height - 8f)
                        gravity(Gravity.RIGHT)
                        marginTop(panelInset)
                    }
                }

                fill(R.ui.location_teleport.border_color, "title_divider") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(1f)
                        margin(dividerInset, R.ui.location_teleport.divider_margin_top, dividerInset, 0f)
                    }
                }

                text(titleText, "title") {
                    lp {
                        sizeMode(SizeMode.WRAP_CONTENT)
                        gravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP)
                        marginTop(R.ui.location_teleport.title_margin_top)
                    }

                    textColor = R.ui.location_teleport.text_color
                }

                nameInput = textBox(R.ui.location_teleport.name_max_length, "name_input") {
                    lp {
                        size(contentWidth, controlHeight)
                        margin(contentMargin, R.ui.location_teleport.name_margin_top, 0f, 0f)
                        padding(
                            inputPaddingHorizontal, inputPaddingVertical,
                            inputPaddingHorizontal, inputPaddingVertical
                        )
                    }

                    background = inputBackground()
                    hint = Component.translatable("academy.location_teleport.name").string
                    textColor = R.ui.location_teleport.text_color
                    hintTextColor = R.ui.location_teleport.dim_color
                    gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL

                    clearOnEnter(false)
                }

                row("coordinates") {
                    lp {
                        size(contentWidth, controlHeight)
                        margin(contentMargin, R.ui.location_teleport.coordinates_margin_top, 0f, 0f)
                    }

                    spacing = R.ui.location_teleport.coordinate_gap

                    xInput = textBox(R.ui.location_teleport.coordinate_max_length, "x_input") {
                        lp {
                            width(coordinateWidth)
                            heightMode(SizeMode.MATCH_PARENT)
                            padding(
                                inputPaddingHorizontal, inputPaddingVertical,
                                inputPaddingHorizontal, inputPaddingVertical
                            )
                        }

                        background = inputBackground()
                        hint = "X"
                        textColor = R.ui.location_teleport.text_color
                        hintTextColor = R.ui.location_teleport.dim_color
                        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL

                        clearOnEnter(false)
                    }

                    yInput = textBox(R.ui.location_teleport.coordinate_max_length, "y_input") {
                        lp {
                            width(coordinateWidth)
                            heightMode(SizeMode.MATCH_PARENT)
                            padding(
                                inputPaddingHorizontal, inputPaddingVertical,
                                inputPaddingHorizontal, inputPaddingVertical
                            )
                        }

                        background = inputBackground()
                        hint = "Y"
                        textColor = R.ui.location_teleport.text_color
                        hintTextColor = R.ui.location_teleport.dim_color
                        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL

                        clearOnEnter(false)
                    }

                    zInput = textBox(R.ui.location_teleport.coordinate_max_length, "z_input") {
                        lp {
                            width(coordinateWidth)
                            heightMode(SizeMode.MATCH_PARENT)
                            padding(
                                inputPaddingHorizontal, inputPaddingVertical,
                                inputPaddingHorizontal, inputPaddingVertical
                            )
                        }

                        background = inputBackground()
                        hint = "Z"
                        textColor = R.ui.location_teleport.text_color
                        hintTextColor = R.ui.location_teleport.dim_color
                        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL

                        clearOnEnter(false)
                    }
                }

                row("mark_actions") {
                    lp {
                        margin(contentMargin, R.ui.location_teleport.mark_actions_margin_top, 0f, 0f)
                    }

                    spacing = R.ui.location_teleport.action_gap

                    button("mark_current") {
                        lp {
                            size(actionButtonWidth, controlHeight)
                        }

                        background = controlBackground()

                        text(
                            Component.translatable("academy.location_teleport.mark_current").string,
                            "label"
                        ) {
                            lp {
                                matchParent()
                            }

                            gravity = Gravity.CENTER
                            textColor = R.ui.location_teleport.text_color
                        }

                        onClick {
                            MisakaNetworkClient.send(
                                LocationTeleport.SaveMarkPacket(true, nameInput.text, 0, 0, 0)
                            )
                        }
                    }

                    button("add_mark") {
                        lp {
                            size(actionButtonWidth, controlHeight)
                        }

                        background = controlBackground()

                        text(
                            Component.translatable("academy.location_teleport.add_mark").string,
                            "label"
                        ) {
                            lp {
                                matchParent()
                            }

                            gravity = Gravity.CENTER
                            textColor = R.ui.location_teleport.text_color
                        }

                        onClick {
                            MisakaNetworkClient.send(
                                LocationTeleport.SaveMarkPacket(
                                    false, nameInput.text,
                                    integer(xInput.text),
                                    integer(yInput.text),
                                    integer(zInput.text)
                                )
                            )
                        }
                    }
                }

                frame("marks") {
                    lp {
                        size(contentWidth, R.ui.location_teleport.marks_height)
                        margin(contentMargin, R.ui.location_teleport.marks_margin_top, 0f, 0f)
                    }

                    fill(R.ui.location_teleport.section_color, "marks_background") {
                        lp {
                            matchParent()
                        }
                    }

                    fill(R.ui.location_teleport.border_dim_color, "marks_border_top") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(1f)
                        }
                    }

                    fill(R.ui.location_teleport.border_dim_color, "marks_border_bottom") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(1f)
                            gravity(Gravity.BOTTOM)
                        }
                    }

                    fill(R.ui.location_teleport.border_dim_color, "marks_border_left") {
                        lp {
                            width(1f)
                            heightMode(SizeMode.MATCH_PARENT)
                        }
                    }

                    fill(R.ui.location_teleport.border_dim_color, "marks_border_right") {
                        lp {
                            width(1f)
                            heightMode(SizeMode.MATCH_PARENT)
                            gravity(Gravity.RIGHT)
                        }
                    }

                    marksScroll = scrollPanel(Orientation.VERTICAL, "marks_scroll") {
                        lp {
                            matchParent()
                            marginRight(scrollbarReserve)
                        }

                        scrollSpeed(R.ui.location_teleport.scroll_speed)

                        marksContent = column("marks_content") {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                heightMode(SizeMode.WRAP_CONTENT)
                            }
                        }
                    }

                    scrollBar(marksScroll, Orientation.VERTICAL, "marks_scroll_bar") {
                        lp {
                            width(R.ui.location_teleport.scrollbar_width)
                            heightMode(SizeMode.MATCH_PARENT)
                            gravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                        }

                        trackColor(R.ui.location_teleport.scroll_track_color)
                        thumbColor(R.ui.location_teleport.dim_color)
                    }

                    emptyText = text(
                        Component.translatable("academy.location_teleport.empty").string,
                        "empty"
                    ) {
                        lp {
                            sizeMode(SizeMode.WRAP_CONTENT)
                            gravity(Gravity.CENTER)
                        }

                        textColor = R.ui.location_teleport.dim_color
                    }
                }

                row("bottom_actions") {
                    lp {
                        margin(contentMargin, R.ui.location_teleport.bottom_actions_margin_top, 0f, 0f)
                    }

                    spacing = R.ui.location_teleport.action_gap

                    button("refresh") {
                        lp {
                            size(actionButtonWidth, controlHeight)
                        }

                        background = controlBackground()

                        text(
                            Component.translatable("academy.location_teleport.refresh").string,
                            "label"
                        ) {
                            lp {
                                matchParent()
                            }

                            gravity = Gravity.CENTER
                            textColor = R.ui.location_teleport.text_color
                        }

                        onClick {
                            MisakaNetworkClient.send(LocationTeleport.RequestMarksPacket.INSTANCE)
                        }
                    }

                    button("done") {
                        lp {
                            size(actionButtonWidth, controlHeight)
                        }

                        background = controlBackground()

                        text(
                            Component.translatable("gui.done").string,
                            "label"
                        ) {
                            lp {
                                matchParent()
                            }

                            gravity = Gravity.CENTER
                            textColor = R.ui.location_teleport.text_color
                        }

                        onClick {
                            onClose()
                        }
                    }
                }
            }
        }

        rebuildMarks()

        MisakaNetworkClient.send(LocationTeleport.RequestMarksPacket.INSTANCE)
    }

    fun setMarks(marks: List<Mark>, quickMarkIndex: Int, defensiveMarkIndex: Int) {
        this.marks.clear()
        this.marks.addAll(marks)
        this.quickMarkIndex = quickMarkIndex
        this.defensiveMarkIndex = defensiveMarkIndex
        rebuildMarks()
    }

    private fun rebuildMarks() {
        if (!::marksContent.isInitialized) return

        val content = marksContent
        content.clearChildren()

        val actionsWidth = R.ui.location_teleport.quick_action_width +
                R.ui.location_teleport.defensive_action_width +
                R.ui.location_teleport.teleport_action_width +
                R.ui.location_teleport.remove_action_width
        val textAreaWidth = R.ui.location_teleport.content_width -
                R.ui.location_teleport.scrollbar_gap -
                R.ui.location_teleport.scrollbar_width -
                R.ui.location_teleport.text_inset * 2f -
                actionsWidth
        val nameWidth = textAreaWidth * 3f / 5f
        val coordinateWidth = textAreaWidth - nameWidth
        val actionsRightMargin = actionsWidth + R.ui.location_teleport.text_inset

        marks.forEachIndexed { index, mark ->
            val quickSelected = index == quickMarkIndex
            val defensiveSelected = index == defensiveMarkIndex

            content.button("mark_$index") {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(R.ui.location_teleport.row_height)
                }

                background = rowBackground(index % 2 != 0)
                isSelected = quickSelected || defensiveSelected

                if (quickSelected) {
                    fill(R.ui.location_teleport.text_color, "quick_rail") {
                        lp {
                            width(R.ui.location_teleport.quick_rail_width)
                            heightMode(SizeMode.MATCH_PARENT)
                        }
                    }
                }

                if (defensiveSelected) {
                    val bracketLeft =
                        if (quickSelected) R.ui.location_teleport.quick_rail_width else 0f
                    fill(R.ui.location_teleport.text_color, "defensive_top") {
                        lp {
                            width(R.ui.location_teleport.defensive_bracket_width)
                            height(1f)
                            marginLeft(bracketLeft)
                        }
                    }
                    fill(R.ui.location_teleport.text_color, "defensive_bottom") {
                        lp {
                            width(R.ui.location_teleport.defensive_bracket_width)
                            height(1f)
                            marginLeft(bracketLeft)
                            gravity(Gravity.BOTTOM)
                        }
                    }
                }

                row("text_row") {
                    lp {
                        heightMode(SizeMode.MATCH_PARENT)
                        margin(
                            R.ui.location_teleport.text_inset, 0f,
                            actionsRightMargin, 0f
                        )
                    }

                    text(markName(mark, index), "name") {
                        lp {
                            width(nameWidth)
                            heightMode(SizeMode.MATCH_PARENT)
                        }

                        ellipsize = Ellipsize.END
                        gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                        textColor = R.ui.location_teleport.text_color
                    }

                    text(coordinates(mark), "coordinates") {
                        lp {
                            width(coordinateWidth)
                            heightMode(SizeMode.MATCH_PARENT)
                        }

                        gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                        textColor = R.ui.location_teleport.dim_color
                    }
                }

                row("actions") {
                    lp {
                        heightMode(SizeMode.MATCH_PARENT)
                        gravity(Gravity.RIGHT or Gravity.CENTER_VERTICAL)
                    }

                    button("quick") {
                        lp {
                            width(R.ui.location_teleport.quick_action_width)
                            heightMode(SizeMode.MATCH_PARENT)
                        }

                        background = controlBackground(quickSelected)
                        isSelected = quickSelected

                        text(
                            Component.translatable("academy.location_teleport.quick_point").string,
                            "label"
                        ) {
                            lp {
                                matchParent()
                            }

                            ellipsize = Ellipsize.END
                            gravity = Gravity.CENTER
                            textColor = R.ui.location_teleport.text_color
                        }

                        onClick {
                            quickMarkIndex = if (quickMarkIndex == index) -1 else index
                            MisakaNetworkClient.send(
                                LocationTeleport.SelectMarkPacket(quickMarkIndex, false)
                            )
                            rebuildMarks()
                        }
                    }

                    button("defensive") {
                        lp {
                            width(R.ui.location_teleport.defensive_action_width)
                            heightMode(SizeMode.MATCH_PARENT)
                        }

                        background = controlBackground(defensiveSelected)
                        isSelected = defensiveSelected

                        text(
                            Component.translatable("academy.location_teleport.defensive_point").string,
                            "label"
                        ) {
                            lp {
                                matchParent()
                            }

                            ellipsize = Ellipsize.END
                            gravity = Gravity.CENTER
                            textColor = R.ui.location_teleport.text_color
                        }

                        onClick {
                            defensiveMarkIndex = if (defensiveMarkIndex == index) -1 else index
                            MisakaNetworkClient.send(
                                LocationTeleport.SelectMarkPacket(defensiveMarkIndex, true)
                            )
                            rebuildMarks()
                        }
                    }

                    button("teleport") {
                        lp {
                            width(R.ui.location_teleport.teleport_action_width)
                            heightMode(SizeMode.MATCH_PARENT)
                        }

                        background = controlBackground()

                        text(">", "label") {
                            lp {
                                matchParent()
                            }

                            gravity = Gravity.CENTER
                            textColor = R.ui.location_teleport.teleport_color
                        }

                        onClick {
                            MisakaNetworkClient.send(LocationTeleport.TeleportToMarkPacket(index))
                            onClose()
                        }
                    }

                    button("remove") {
                        lp {
                            width(R.ui.location_teleport.remove_action_width)
                            heightMode(SizeMode.MATCH_PARENT)
                        }

                        background = controlBackground()

                        text("x", "label") {
                            lp {
                                matchParent()
                            }

                            gravity = Gravity.CENTER
                            textColor = R.ui.location_teleport.danger_color
                        }

                        onClick {
                            MisakaNetworkClient.send(LocationTeleport.RemoveMarkPacket(index))
                        }
                    }
                }

                onClick {
                    nameInput.text = mark.name()
                    xInput.text = mark.x().toString()
                    yInput.text = mark.y().toString()
                    zInput.text = mark.z().toString()
                }
            }
        }

        emptyText.visibility =
            if (marks.isEmpty()) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
    }

    private fun markName(mark: Mark, index: Int): String =
        mark.name().ifBlank { "Mark ${index + 1}" }

    private fun coordinates(mark: Mark): String =
        "${mark.x()}, ${mark.y()}, ${mark.z()}"

    private fun integer(value: String): Int {
        return try {
            value.trim().toInt()
        } catch (_: RuntimeException) {
            0
        }
    }

    private companion object {
        private fun inputBackground(): StateListDrawable =
            StateListDrawable().apply {
                setDefault(ColorDrawable(R.ui.location_teleport.input_color))
                addState(Widget.FOCUSED, ColorDrawable(R.ui.location_teleport.input_focused_color))
            }

        private fun controlBackground(selected: Boolean = false): StateListDrawable =
            StateListDrawable().apply {
                setDefault(ColorDrawable(R.ui.location_teleport.control_color))
                if (selected) {
                    addState(Widget.SELECTED, ColorDrawable(R.ui.location_teleport.row_selected_color))
                }
                addState(Widget.HOVERED, ColorDrawable(R.ui.location_teleport.row_hover_color))
                addState(Widget.PRESSED, ColorDrawable(R.ui.location_teleport.row_hover_color))
            }

        private fun rowBackground(alternate: Boolean): StateListDrawable =
            StateListDrawable().apply {
                setDefault(
                    ColorDrawable(
                        if (alternate) R.ui.location_teleport.row_alternate_color
                        else R.ui.location_teleport.row_color
                    )
                )
                addState(Widget.SELECTED, ColorDrawable(R.ui.location_teleport.row_selected_color))
                addState(Widget.HOVERED, ColorDrawable(R.ui.location_teleport.row_hover_color))
            }
    }
}
