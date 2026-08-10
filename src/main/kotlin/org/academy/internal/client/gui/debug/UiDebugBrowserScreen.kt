package org.academy.internal.client.gui.debug

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.editor.UiLayoutEditorScreen

class UiDebugBrowserScreen : Screen(Component.translatable("screen.academy.ui_debug.browser.title")) {
    private val entries = UiDebugLayoutRegistry.gui()

    override fun isPauseScreen(): Boolean = false

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xB0101418.toInt())
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val panelWidth = minOf(420, width - 24)
        val panelX = (width - panelWidth) / 2
        val panelY = 18
        val rowHeight = 27
        val panelHeight = 42 + entries.size * rowHeight + 32
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0182028.toInt())
        border(graphics, panelX, panelY, panelWidth, panelHeight, 0xFF4AA9C8.toInt())
        graphics.centeredText(font, title, width / 2, panelY + 10, 0xFFE8F5F8.toInt())
        entries.forEachIndexed { index, definition ->
            val y = panelY + 34 + index * rowHeight
            val hovered = inside(mouseX.toDouble(), mouseY.toDouble(), panelX + 8, y, panelWidth - 16, 23)
            val state = UiDebugSession.status(definition.id)
            graphics.fill(
                panelX + 8, y, panelX + panelWidth - 8, y + 23,
                if (hovered) 0x554AA9C8 else 0x22FFFFFF
            )
            val suffix = when {
                state.error != null -> "  [${tr("screen.academy.ui_debug.status.invalid")}]"
                state.dirty -> "  [${tr("screen.academy.ui_debug.status.modified")}]"
                else -> ""
            }
            graphics.text(
                font, displayName(definition.id) + " [${variant(definition.id)}]" + suffix, panelX + 13, y + 3,
                if (state.error != null) 0xFFFF7777.toInt() else 0xFFE8F5F8.toInt(), false
            )
            graphics.text(
                font,
                definition.id + " - " + tr(UiDebugSession.sourceTranslationKey(definition.id)),
                panelX + 13,
                y + 14,
                0xFF91A6AE.toInt(),
                false
            )
        }
        val bottomY = panelY + panelHeight - 25
        drawButton(
            graphics, panelX + 8, bottomY, 96, 17,
            tr("screen.academy.ui_debug.action.publish"), mouseX, mouseY
        )
        drawButton(
            graphics, panelX + panelWidth - 104, bottomY, 96, 17,
            tr("screen.academy.ui_debug.action.close"), mouseX, mouseY
        )
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick)
        val panelWidth = minOf(420, width - 24)
        val panelX = (width - panelWidth) / 2
        val panelY = 18
        val rowHeight = 27
        entries.forEachIndexed { index, definition ->
            val y = panelY + 34 + index * rowHeight
            if (inside(event.x(), event.y(), panelX + 8, y, panelWidth - 16, 23)) {
                UiLayoutEditorScreen.openDebug(definition.id)
                return true
            }
        }
        val panelHeight = 42 + entries.size * rowHeight + 32
        val bottomY = panelY + panelHeight - 25
        if (inside(event.x(), event.y(), panelX + 8, bottomY, 96, 17)) {
            notifyPublish(UiDebugSession.publish())
            return true
        }
        if (inside(event.x(), event.y(), panelX + panelWidth - 104, bottomY, 96, 17)) {
            onClose()
            return true
        }
        return true
    }

    companion object {
        fun open() {
            Minecraft.getInstance().execute { Minecraft.getInstance().gui.setScreen(UiDebugBrowserScreen()) }
        }

        fun notifyPublish(result: UiDebugSession.PublishResult) {
            val message = when {
                result.successful -> Component.translatable(
                    "message.academy.ui_debug.publish.success",
                    result.saved,
                    (result.sourceRoot ?: result.workingDirectory).toString()
                )

                result.saved > 0 -> Component.translatable(
                    "message.academy.ui_debug.publish.pending",
                    result.saved,
                    result.workingDirectory.toString(),
                    result.error ?: ""
                )

                else -> Component.translatable(
                    "message.academy.ui_debug.publish.failed",
                    result.error ?: ""
                )
            }
            Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(message)
        }

        private fun inside(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, height: Int): Boolean {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
        }

        private fun displayName(id: String): String = when {
            id == "location_teleport" -> tr("screen.academy.ui_debug.layout.location_teleport")
            id.startsWith("precision_operation_") -> tr("screen.academy.ui_debug.layout.precision_operation")
            id.startsWith("reflection_filter_") -> tr("screen.academy.ui_debug.layout.reflection_filter")
            else -> id
        }

        private fun variant(id: String): String = when {
            id.endsWith("_wide") -> tr("screen.academy.ui_debug.variant.wide")
            id.endsWith("_medium") -> tr("screen.academy.ui_debug.variant.medium")
            id.endsWith("_compact") -> tr("screen.academy.ui_debug.variant.compact")
            else -> tr("screen.academy.ui_debug.variant.default")
        }

        private fun tr(key: String, vararg args: Any): String = Component.translatable(key, *args).string

        private fun border(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Int) {
            graphics.fill(x, y, x + width, y + 1, color)
            graphics.fill(x, y + height - 1, x + width, y + height, color)
            graphics.fill(x, y, x + 1, y + height, color)
            graphics.fill(x + width - 1, y, x + width, y + height, color)
        }

        private fun drawButton(
            graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int,
            label: String, mouseX: Int, mouseY: Int
        ) {
            val hovered = inside(mouseX.toDouble(), mouseY.toDouble(), x, y, width, height)
            graphics.fill(x, y, x + width, y + height, if (hovered) 0x554AA9C8 else 0x22FFFFFF)
            border(graphics, x, y, width, height, if (hovered) 0xFF65D5F2.toInt() else 0x884AA9C8.toInt())
            graphics.centeredText(Minecraft.getInstance().font, label, x + width / 2, y + 5, 0xFFE8F5F8.toInt())
        }
    }
}
