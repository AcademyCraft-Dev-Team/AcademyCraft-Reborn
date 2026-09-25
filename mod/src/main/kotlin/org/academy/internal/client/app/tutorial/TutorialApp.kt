package org.academy.internal.client.app.tutorial

import net.minecraft.resources.Identifier
import org.academy.api.client.app.App
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContext
import org.academy.api.client.hud.terminal.TerminalHud
import org.academy.api.client.resources.R
import org.academy.api.common.util.L10n

object TutorialApp : App {
    override fun createContext(): WidgetContext = object : WidgetContext {
        private val root = TutorialUi.create { TerminalHud.INSTANCE.closeApp() }

        override fun get(): Widget = root
    }

    override fun name(): String = L10n["app.academy.tutorial.name"]

    override fun icon(): Identifier = R.textures.gui.app.tutorial.icon
}
