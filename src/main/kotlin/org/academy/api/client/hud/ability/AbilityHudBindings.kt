package org.academy.api.client.hud.ability

import org.academy.api.client.config.KeyBindingConfig
import org.academy.api.client.input.InputSystem

internal fun getHudBindingMigratingDefaults(
    config: KeyBindingConfig,
    name: String,
    key: Int,
    action: Int,
    vararg obsoleteDefaultKeys: Int
): InputSystem.KeyCombination {
    val defaultBinding = InputSystem.combo(
        InputSystem.InputType.KEYBOARD,
        key,
        action,
        0
    )
    val obsoleteDefaults = buildList {
        add(
            InputSystem.combo(
                InputSystem.InputType.KEYBOARD,
                key,
                action,
                InputSystem.ANY_MODIFIER
            )
        )
        obsoleteDefaultKeys.forEach { obsoleteKey ->
            add(
                InputSystem.combo(
                    InputSystem.InputType.KEYBOARD,
                    obsoleteKey,
                    action,
                    0
                )
            )
            add(
                InputSystem.combo(
                    InputSystem.InputType.KEYBOARD,
                    obsoleteKey,
                    action,
                    InputSystem.ANY_MODIFIER
                )
            )
        }
    }
    val configured = config.getKeyBindingMigratingDefaults(
        name,
        defaultBinding,
        *obsoleteDefaults.toTypedArray()
    )
    val normalized = InputSystem.withAction(configured, action)
    config.setKeyBinding(name, normalized)
    return normalized
}
