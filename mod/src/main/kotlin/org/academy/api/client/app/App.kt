package org.academy.api.client.app

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.widget.WidgetContext

interface App {
    fun createContext(): WidgetContext

    fun name(): String

    fun icon(): Identifier
}
