package org.academy.desktop.widgets

import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.OnClickListener
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.AbstractWidget
import org.academy.api.client.gui.widget.ButtonWidget
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.LabelWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.ScrollPanelWidget
import org.academy.api.client.gui.widget.Widget

class MenuItem(
    val label: String,
    val action: () -> Unit = {},
    val shortcut: String? = null,
    val checked: (() -> Boolean)? = null,
    val separatorBefore: Boolean = false,
)

class Menu(
    val label: String,
    val items: List<MenuItem>,
)

/**
 * A horizontal menu bar whose labels open a [MenuPopup] at the label position.
 * The host app owns the popup layer; set [onOpen] to position and show it.
 */
class MenuBar : LinearLayoutWidget() {
    var onOpen: ((anchorX: Float, anchorBottomY: Float, label: String) -> Unit)? = null

    /** Label of the menu currently open, or null. Drives hover-switching. */
    var activeMenu: String? = null

    private val labelButtons: MutableMap<String, ButtonWidget> = LinkedHashMap()

    init {
        orientation = Orientation.HORIZONTAL
        spacing = 2f
        isClickable = false
    }

    fun setMenus(menus: List<Menu>) {
        clearChildren()
        labelButtons.clear()
        for (menu in menus) {
            val button = ButtonWidget(centeredLabel(menu.label))
            button.layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
                .paddingHorizontal(6f)
            applyHoverState(button)
            button.onClickListener = OnClickListener {
                openMenu(menu.label)
            }
            labelButtons[menu.label] = button
            addChild("menu_${menu.label}", button)
        }
    }

    private fun openMenu(label: String) {
        activeMenu = label
        val button = labelButtons[label] ?: return
        onOpen?.invoke(button.getAbsoluteX(), button.getAbsoluteY() + button.height, label)
    }

    override fun onMouseMoved(event: MouseEvent) {
        val active = activeMenu ?: return
        val hovered = labelButtons.entries.firstOrNull { it.value.isMouseOver(event.x, event.y) }?.key ?: return
        if (hovered != active) openMenu(hovered)
    }
}

/**
 * An overlay popup with a full-screen click-catcher backdrop and a menu panel
 * anchored at a position. Mount this as the topmost child of the app root.
 */
class MenuPopup : FrameLayoutWidget() {
    private val panel = FrameLayoutWidget()
    private val itemsContainer = LinearLayoutWidget().apply { orientation = Orientation.VERTICAL }
    private val panelScroll: ScrollPanelWidget
    private var onAction: (() -> Unit)? = null
    private var onHideCallback: (() -> Unit)? = null

    val isOpen: Boolean get() = visibility == Widget.Visibility.VISIBLE

    init {
        layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        visibility = Widget.Visibility.GONE

        addChild(
            "backdrop",
            object : AbstractWidget() {
                init {
                    isClickable = true
                }

                override fun onMousePressed(event: MouseEvent) {
                    event.consume()
                    hide()
                }
            }.apply {
                layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            }
        )

        panel.background = ColorDrawable(0xFF2B2B2E.toInt())
        panelScroll = ScrollPanelWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        }
        panelScroll.setContent(itemsContainer)
        panel.addChild("items", panelScroll)
        addChild("panel", panel)
    }

    fun show(anchorX: Float, anchorY: Float, menu: Menu) {
        buildItems(menu.items)
        val desired = menu.items.size * ITEM_HEIGHT + PANEL_PADDING
        val availableBelow = (height - anchorY - 12f).coerceAtLeast(ITEM_HEIGHT)
        val panelHeight = desired.coerceIn(0f, availableBelow).coerceIn(0f, MAX_PANEL_HEIGHT)
        panel.layoutParams = FrameLayoutWidget.LayoutParams()
            .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.FIXED)
            .height(panelHeight)
            .marginLeft(anchorX)
            .marginTop(anchorY)
        panel.requestLayout()
        visibility = Widget.Visibility.VISIBLE
    }

    fun hide() {
        if (visibility != Widget.Visibility.GONE) {
            visibility = Widget.Visibility.GONE
            onHideCallback?.invoke()
        }
    }

    /** Invoked after any menu item action fires. */
    fun setOnAction(handler: () -> Unit) {
        onAction = handler
    }

    /** Invoked when the popup is hidden. */
    fun setOnHide(handler: () -> Unit) {
        onHideCallback = handler
    }

    private fun buildItems(items: List<MenuItem>) {
        itemsContainer.clearChildren()
        for ((index, item) in items.withIndex()) {
            if (item.separatorBefore && index > 0) {
                itemsContainer.addChild(
                    "sep_$index",
                    FillWidget(0xFF1A1A1B.toInt()).apply {
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED)
                            .height(3f)
                    }
                )
            }
            itemsContainer.addChild("item_${item.label}_$index", buildItem(item))
        }
    }

    private fun buildItem(item: MenuItem): Widget {
        val row = LinearLayoutWidget().apply {
            orientation = Orientation.HORIZONTAL
            layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED)
                .height(ITEM_HEIGHT)
                .paddingHorizontal(8f)
            spacing = 4f
        }
        val checked = item.checked?.invoke() == true
        row.addChild(
            "check",
            LabelWidget(if (checked) "✓" else "").apply {
                baseFontSize = 13f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.FIXED, SizeMode.MATCH_PARENT)
                    .width(16f)
                    .gravity(Gravity.CENTER_VERTICAL)
            }
        )
        row.addChild(
            "label",
            LabelWidget(item.label).apply {
                baseFontSize = 13f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .heightMode(SizeMode.MATCH_PARENT)
                    .gravity(Gravity.CENTER_VERTICAL)
            }
        )
        val shortcut = item.shortcut
        if (shortcut != null) {
            row.addChild(
                "shortcut",
                LabelWidget(shortcut).apply {
                    baseFontSize = 11f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
                        .gravity(Gravity.CENTER_VERTICAL)
                }
            )
        }
        val button = ButtonWidget(row).apply {
            layoutParams = LinearLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.FIXED).height(ITEM_HEIGHT)
        }
        applyHoverState(button)
        button.onClickListener = OnClickListener {
            hide()
            item.action()
            onAction?.invoke()
        }
        return button
    }

    private companion object {
        const val ITEM_HEIGHT = 26f
        const val PANEL_PADDING = 4f
        const val MAX_PANEL_HEIGHT = 600f
    }
}
