package org.academy.internal.client.app.settings.ui

import org.academy.AcademyCraftClient
import org.academy.api.client.config.KeyBindingConfig
import org.academy.api.client.gui.event.KeyEvent
import org.academy.api.client.gui.widget.TextWidget
import org.academy.api.client.input.InputSystem
import org.academy.api.common.util.L10n
import org.lwjgl.glfw.GLFW

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
    if (event.keyCode != GLFW.GLFW_KEY_ESCAPE) return false
    val target = host.captureTarget ?: return true
    val current = target.section.config.getKeyBinding(target.bindingName) ?: return true
    host.resetCaptureState()
    host.applyCapture(InputSystem.unbound(current))
    return true
}

internal fun isModifierKey(key: Int): Boolean {
    return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
            || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
            || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
            || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER
}
