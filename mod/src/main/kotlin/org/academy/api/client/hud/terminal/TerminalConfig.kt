package org.academy.api.client.hud.terminal

import org.academy.api.client.config.KeyBindingConfig
import org.academy.api.common.gson.TypeHandler

class TerminalConfig : KeyBindingConfig() {
    var blurRadius: Float = 20f

    class Action private constructor() : TypeHandler<TerminalConfig> {
        override fun getDefault(): TerminalConfig {
            return TerminalConfig()
        }

        override fun getTypeClass(): Class<TerminalConfig> {
            return TerminalConfig::class.java
        }

        companion object {
            val INSTANCE: TypeHandler<TerminalConfig> = Action()
        }
    }
}
