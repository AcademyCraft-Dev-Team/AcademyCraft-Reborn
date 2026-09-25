package org.academy.internal.client.app.settings.ui

import com.mojang.blaze3d.platform.InputConstants
import org.academy.AcademyCraftClient
import org.academy.api.client.config.KeyBindingConfig
import org.academy.api.client.gui.event.KeyEvent
import org.academy.api.client.gui.widget.TextWidget
import org.academy.api.client.input.InputSystem
import org.academy.api.common.util.L10n

internal interface KeyBindingSection {
    val config: KeyBindingConfig
    val persist: (KeyBindingConfig) -> Unit
}

internal interface CaptureHost {
    val captureTarget: CaptureTarget?
    fun resetCaptureState()
    fun applyCapture(combo: InputSystem.KeyCombination)
}

internal data class CaptureTarget(
    val section: KeyBindingSection,
    val bindingName: String,
    val keyLabel: TextWidget
)

internal fun displayBinding(combo: InputSystem.KeyCombination): String {
    return if (combo.unbound) L10n["app.academy.settings.keybind.format.none"] else combo.displayName()
}

internal fun writeKeyBinding(
    section: KeyBindingSection,
    bindingName: String,
    combo: InputSystem.KeyCombination,
    keyLabel: TextWidget
) {
    section.config.setKeyBinding(bindingName, combo)
    InputSystem.updateKeyBinding(bindingName, combo)
    section.persist(section.config)
    AcademyCraftClient.Config.INSTANCE.save()
    keyLabel.text = displayBinding(combo)
}

internal fun resetKeyBinding(section: KeyBindingSection, bindingName: String, keyLabel: TextWidget) {
    val defaultCombo = InputSystem.getDefaultKeyBinding(bindingName) ?: return
    writeKeyBinding(section, bindingName, defaultCombo, keyLabel)
}

internal fun handleCaptureEscape(event: KeyEvent, host: CaptureHost): Boolean {
    event.consume()
    if (event.keyCode != InputConstants.KEY_ESCAPE) return false
    val target = host.captureTarget ?: return true
    val current = target.section.config.getKeyBinding(target.bindingName) ?: return true
    host.resetCaptureState()
    host.applyCapture(InputSystem.unbound(current))
    return true
}

internal fun isModifierKey(key: Int): Boolean {
    return key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT
            || key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
            || key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT
            || key == InputConstants.KEY_LGUI || key == InputConstants.KEY_RGUI
}

internal fun sidePinLabel(pin: Int): String = when (pin) {
    InputSystem.SIDE_LEFT -> L10n["app.academy.settings.keybind.side.left"]
    InputSystem.SIDE_RIGHT -> L10n["app.academy.settings.keybind.side.right"]
    else -> L10n["app.academy.settings.keybind.side.any"]
}

internal fun nextSidePin(pin: Int): Int = when (pin) {
    InputSystem.SIDE_ANY -> InputSystem.SIDE_LEFT
    InputSystem.SIDE_LEFT -> InputSystem.SIDE_RIGHT
    else -> InputSystem.SIDE_ANY
}
