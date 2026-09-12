package org.academy.api.client.gui.text

import com.mojang.blaze3d.platform.InputConstants

/** 文本编辑命令，由 [TextInputKeymap] 从按键解析，由 [TextInputWidget] 执行。 */
enum class TextEditCommand {
    BACKSPACE,
    DELETE,
    MOVE_LEFT,
    MOVE_RIGHT,
    MOVE_HOME,
    MOVE_END,
    NEWLINE,
    SELECT_ALL,
    COPY,
    CUT,
    PASTE
}

/**
 * 按键 → 编辑命令映射。把快捷键表从控件事件逻辑中分离，方便调整/测试。
 * 复制/粘贴等需要 Ctrl 的命令仅在 [ctrl] 为 true 时解析。
 */
object TextInputKeymap {
    fun resolve(keyCode: Int, ctrl: Boolean): TextEditCommand? = when (keyCode) {
        InputConstants.KEY_BACKSPACE -> TextEditCommand.BACKSPACE
        InputConstants.KEY_DELETE -> TextEditCommand.DELETE
        InputConstants.KEY_LEFT -> TextEditCommand.MOVE_LEFT
        InputConstants.KEY_RIGHT -> TextEditCommand.MOVE_RIGHT
        InputConstants.KEY_HOME -> TextEditCommand.MOVE_HOME
        InputConstants.KEY_END -> TextEditCommand.MOVE_END
        InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> TextEditCommand.NEWLINE
        InputConstants.KEY_A -> if (ctrl) TextEditCommand.SELECT_ALL else null
        InputConstants.KEY_C -> if (ctrl) TextEditCommand.COPY else null
        InputConstants.KEY_X -> if (ctrl) TextEditCommand.CUT else null
        InputConstants.KEY_V -> if (ctrl) TextEditCommand.PASTE else null
        else -> null
    }
}
