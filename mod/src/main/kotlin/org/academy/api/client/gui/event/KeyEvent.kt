package org.academy.api.client.gui.event

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.InputQuirks

class KeyEvent(type: EventType, keyCode: Int, scanCode: Int, modifiers: Int) : InputEvent(type) {
    val keyCode: Int
    val scanCode: Int
    val modifiers: Int

    init {
        require(!(type != EventType.KEY_PRESSED && type != EventType.KEY_RELEASED)) { "This constructor is for KEY_PRESSED or KEY_RELEASED events." }
        this.keyCode = keyCode
        this.scanCode = scanCode
        this.modifiers = modifiers
    }

    fun hasAltDown(): Boolean {
        return (this.modifiers and InputConstants.MOD_ALT) != 0
    }

    fun hasShiftDown(): Boolean {
        return (this.modifiers and InputConstants.MOD_SHIFT) != 0
    }

    fun hasControlDown(): Boolean {
        return (this.modifiers and InputConstants.MOD_CONTROL) != 0
    }

    fun hasControlDownWithQuirk(): Boolean {
        return (this.modifiers and InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER) != 0
    }
}
