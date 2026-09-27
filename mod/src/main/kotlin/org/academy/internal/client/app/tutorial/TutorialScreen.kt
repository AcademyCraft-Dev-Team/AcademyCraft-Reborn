package org.academy.internal.client.app.tutorial

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.gui.widget.BlendQuadWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.WidgetContainer

class TutorialScreen private constructor() : UiScreen(Component.translatable("screen.academy.tutorial")) {
    override fun onInit() {
        val panel = FrameLayoutWidget().apply {
            layoutParams = FrameLayoutWidget.LayoutParams()
                .gravity(Gravity.CENTER)
                .size(TutorialUi.WIDTH, TutorialUi.HEIGHT)
        }
        root.addChild("tutorial_panel", panel)

        panel.addChild("background", BlendQuadWidget().apply {
            alpha = 0.78f
            layoutParams = WidgetContainer.LayoutParams().sizeMode(SizeMode.MATCH_PARENT)
        })
        panel.addChild("content", TutorialUi.create { onClose() })
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        @JvmStatic
        fun open() {
            Minecraft.getInstance().gui.setScreen(TutorialScreen())
        }
    }
}
