package org.academy.internal.client.hud

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.Drawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.event.MouseEvent
import org.academy.api.client.gui.event.ScrollEvent
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.FillWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.TextWidget
import org.academy.api.client.gui.widget.Widget
import kotlin.math.roundToInt

class HudLayoutEditorScreen(
    private val previousScreen: Screen?
) : UiScreen(Component.translatable("hud.academy.layout.title")) {

    override fun isPauseScreen(): Boolean = false

    /**
     * 不绘制原版背景, 移除全屏模糊; 遮罩由下方的暗色 [FillWidget] 提供.
     */
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
    }

    override fun onInit() {
        root.fill(DIM, "dim") {
            matchParent()
        }

        root.text(title.string, "title") {
            matchWidth()
            height(12f)
            marginTop(8f)
            gravity = Gravity.CENTER
            textSize = 8f
            textColor = TEXT
        }

        root.text(Component.translatable("hud.academy.layout.hint").string, "hint") {
            matchWidth()
            height(12f)
            marginTop(20f)
            gravity = Gravity.CENTER
            textSize = 8f
            textColor = TEXT_DIM
        }

        HudLayout.Region.entries.forEach { region ->
            root.addChild("region_${region.configKey}", HudRegionBox(region))
        }

        root.row("buttons", 6f) {
            widthMode(SizeMode.WRAP_CONTENT)
            height(BUTTON_HEIGHT)
            lp {
                gravity = Gravity.CENTER_BOTTOM
            }
            margin(0f, 0f, 0f, 8f)

            button("reset") {
                size(BUTTON_WIDTH, BUTTON_HEIGHT)
                background = buttonBackground()
                onClick {
                    HudLayout.resetAll()
                    saveAndRebuild()
                }
                text(Component.translatable("hud.academy.layout.reset").string, "label") {
                    matchParent()
                    gravity = Gravity.CENTER
                    textSize = 8f
                    textColor = TEXT
                }
            }

            button("done") {
                size(BUTTON_WIDTH, BUTTON_HEIGHT)
                background = buttonBackground()
                onClick { onClose() }
                text(Component.translatable("hud.academy.layout.done").string, "label") {
                    matchParent()
                    gravity = Gravity.CENTER
                    textSize = 8f
                    textColor = TEXT
                }
            }
        }
    }

    override fun onClose() {
        saveAndRebuild()
        Minecraft.getInstance().gui.setScreen(previousScreen)
    }

    private fun saveAndRebuild() {
        HudLayoutConfig.save()
        HudManager.rebuildHudLayouts()
    }

    private fun buttonBackground(): Drawable {
        val normal = ColorDrawable(BUTTON)
        val active = ColorDrawable(BUTTON_HOVER)
        return StateListDrawable().apply {
            setDefault(normal)
            addState(Widget.HOVERED, active)
            addState(Widget.FOCUSED, active)
            addState(Widget.PRESSED, active)
        }
    }

    private inner class HudRegionBox(val region: HudLayout.Region) : FrameLayoutWidget() {
        private val backgroundFill: FillWidget
        private val borderTop: FillWidget
        private val borderBottom: FillWidget
        private val borderLeft: FillWidget
        private val borderRight: FillWidget
        private val label: TextWidget

        private var grabbed = false
        private var grabStartX = 0.0
        private var grabStartY = 0.0
        private var initialTranslateX = 0f
        private var initialTranslateY = 0f

        init {
            isClickable = true
            layoutParams = LayoutParams().sizeMode(SizeMode.FIXED)

            backgroundFill = FillWidget(FILL)
            backgroundFill.layoutParams = LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
            addChild("background", backgroundFill)

            borderTop = horizontalBorder("border_top", Gravity.TOP)
            borderBottom = horizontalBorder("border_bottom", Gravity.BOTTOM)
            borderLeft = verticalBorder("border_left", Gravity.LEFT)
            borderRight = verticalBorder("border_right", Gravity.RIGHT)

            label = text("", "label") {
                matchWidth()
                height(10f)
                margin(2f, 2f)
                textSize = 8f
                textColor = TEXT
            }

            setFrameUpdate {
                syncFromRegion()
                true
            }
        }

        private fun horizontalBorder(name: String, boxGravity: Int): FillWidget {
            val widget = FillWidget(BOX)
            widget.layoutParams = LayoutParams().apply {
                widthMode = SizeMode.MATCH_PARENT
                heightMode = SizeMode.FIXED
                height = BORDER_THICKNESS
                gravity = boxGravity
            }
            addChild(name, widget)
            return widget
        }

        private fun verticalBorder(name: String, boxGravity: Int): FillWidget {
            val widget = FillWidget(BOX)
            widget.layoutParams = LayoutParams().apply {
                widthMode = SizeMode.FIXED
                width = BORDER_THICKNESS
                heightMode = SizeMode.MATCH_PARENT
                gravity = boxGravity
            }
            addChild(name, widget)
            return widget
        }

        private fun syncFromRegion() {
            val minecraft = Minecraft.getInstance()
            val rect = region.rect(minecraft)

            translationX = rect.x
            translationY = rect.y

            val boxWidth = rect.width.coerceAtLeast(1f)
            val boxHeight = rect.height.coerceAtLeast(1f)
            if (width != boxWidth) width = boxWidth
            if (height != boxHeight) height = boxHeight

            val hidden = region.hidden
            val active = grabbed

            backgroundFill.setColor(if (hidden) HIDDEN_FILL else if (active) FILL_ACTIVE else FILL)
            val borderColor = if (hidden) HIDDEN_BOX else if (active) BOX_ACTIVE else BOX
            borderTop.setColor(borderColor)
            borderBottom.setColor(borderColor)
            borderLeft.setColor(borderColor)
            borderRight.setColor(borderColor)

            label.text = Component.translatable(region.nameKey).string +
                    "  " + (region.scaleXY * 100f).roundToInt() + "%" +
                    (if (hidden) {
                        "  " + Component.translatable("hud.academy.layout.hidden").string
                    } else {
                        ""
                    })
            label.textColor = if (hidden) TEXT_DIM else if (active) BOX_ACTIVE else TEXT
        }

        override fun onMousePressed(event: MouseEvent) {
            if (!isMouseOver(event.x, event.y)) return
            when (event.button) {
                InputConstants.MOUSE_BUTTON_LEFT -> {
                    grabbed = true
                    grabStartX = event.x
                    grabStartY = event.y
                    initialTranslateX = region.translateX
                    initialTranslateY = region.translateY
                    event.consume()
                }

                InputConstants.MOUSE_BUTTON_RIGHT -> {
                    region.toggleHidden()
                    saveAndRebuild()
                    event.consume()
                }
            }
        }

        override fun onMouseDragged(event: MouseEvent) {
            if (!grabbed || event.button != InputConstants.MOUSE_BUTTON_LEFT) return
            val deltaX = (event.x - grabStartX).toFloat()
            val deltaY = (event.y - grabStartY).toFloat()
            region.setTranslate(
                initialTranslateX + deltaX,
                initialTranslateY + deltaY,
                Minecraft.getInstance()
            )
            event.consume()
        }

        override fun onMouseReleased(event: MouseEvent) {
            if (!grabbed) return
            saveAndRebuild()
            grabbed = false
            event.consume()
        }

        override fun onMouseScrolled(event: ScrollEvent) {
            if (!isMouseOver(event.x, event.y) || event.delta == 0.0) return
            region.scaleXY += if (event.delta > 0.0) SCALE_STEP else -SCALE_STEP
            saveAndRebuild()
            event.consume()
        }
    }

    companion object {
        private const val SCALE_STEP = 0.05f
        private const val BUTTON_WIDTH = 100f
        private const val BUTTON_HEIGHT = 20f
        private const val BORDER_THICKNESS = 1f

        private const val DIM = 0x66000000
        private const val FILL = 0x18FFFFFF
        private const val FILL_ACTIVE = 0x30FFFFFF
        private const val HIDDEN_FILL = 0x0CFFFFFF
        private const val BOX = 0xFFFFFFFF.toInt()
        private const val BOX_ACTIVE = 0xFFFFFFFF.toInt()
        private const val HIDDEN_BOX = 0x66FFFFFF
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val TEXT_DIM = 0x99FFFFFF.toInt()
        private const val BUTTON = 0x20FFFFFF
        private const val BUTTON_HOVER = 0x40FFFFFF
    }
}
