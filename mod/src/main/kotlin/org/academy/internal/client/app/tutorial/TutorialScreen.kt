package org.academy.internal.client.app.tutorial

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.screen.UiScreen
import org.academy.api.client.resources.R

class TutorialScreen private constructor() : UiScreen(Component.translatable("screen.academy.tutorial")) {
    override fun onInit() {
        root.apply {
            frame("panel_tutorial") {
                lp {
                    gravity(Gravity.CENTER)
                    size(R.ui.tutorial.width, R.ui.tutorial.height)
                }

                blendQuad("background") {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT)
                    }

                    alpha = 0.78f
                }

                add("content", TutorialUi.create { onClose() })
            }
        }
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        @JvmStatic
        fun open() {
            Minecraft.getInstance().gui.setScreen(TutorialScreen())
        }
    }
}
