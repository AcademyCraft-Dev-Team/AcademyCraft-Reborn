package org.academy.desktop.grapheditor.command

import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.academy.desktop.grapheditor.document.FrameData
import org.academy.desktop.grapheditor.document.NoteData

/** 添加分组 frame。undo 移除，redo 复用同一对象（id 稳定）。 */
class AddFrameCommand(
    model: GraphEditorModel,
    private val frame: FrameData,
) : ModelCommand(model) {
    override fun execute() {
        model.frames[frame.id] = frame
    }

    override fun undo() {
        model.frames.remove(frame.id)
    }

    override fun label(): String = "Add frame"
}

/** 删除分组 frame。记录快照，undo 还原。 */
class RemoveFrameCommand(
    model: GraphEditorModel,
    private val frameId: String,
) : ModelCommand(model) {
    private var frame: FrameData? = null

    override fun execute() {
        frame = model.frames.remove(frameId)
    }

    override fun undo() {
        frame?.let { model.frames[it.id] = it }
    }

    override fun label(): String = "Delete frame"
}

/** 移动分组 frame。拖拽合并（mergeKey = frame_move:id）。 */
class MoveFrameCommand(
    model: GraphEditorModel,
    private val frameId: String,
    private val oldX: Float,
    private val oldY: Float,
    private val newX: Float,
    private val newY: Float,
) : ModelCommand(model) {
    override fun execute() {
        model.frames[frameId]?.let { f ->
            f.x = newX
            f.y = newY
        }
    }

    override fun undo() {
        model.frames[frameId]?.let { f ->
            f.x = oldX
            f.y = oldY
        }
    }

    override fun mergeKey(): String = "frame_move:$frameId"

    override fun mergeWith(next: Command): Command? {
        if (next !is MoveFrameCommand || next.frameId != frameId) return null
        return MoveFrameCommand(model, frameId, oldX, oldY, next.newX, next.newY)
    }

    override fun label(): String = "Move frame"
}

/** 缩放分组 frame（右下角拖拽）。拖拽合并。 */
class ResizeFrameCommand(
    model: GraphEditorModel,
    private val frameId: String,
    private val oldW: Float,
    private val oldH: Float,
    private val newW: Float,
    private val newH: Float,
) : ModelCommand(model) {
    override fun execute() {
        model.frames[frameId]?.let { f ->
            f.w = newW
            f.h = newH
        }
    }

    override fun undo() {
        model.frames[frameId]?.let { f ->
            f.w = oldW
            f.h = oldH
        }
    }

    override fun mergeKey(): String = "frame_resize:$frameId"

    override fun mergeWith(next: Command): Command? {
        if (next !is ResizeFrameCommand || next.frameId != frameId) return null
        return ResizeFrameCommand(model, frameId, oldW, oldH, next.newW, next.newH)
    }

    override fun label(): String = "Resize frame"
}

/** 重命名分组 frame。 */
class RenameFrameCommand(
    model: GraphEditorModel,
    private val frameId: String,
    private val oldTitle: String,
    private val newTitle: String,
) : ModelCommand(model) {
    override fun execute() {
        model.frames[frameId]?.title = newTitle
    }

    override fun undo() {
        model.frames[frameId]?.title = oldTitle
    }

    override fun label(): String = "Rename frame"
}

/** 添加 sticky note。 */
class AddNoteCommand(
    model: GraphEditorModel,
    private val note: NoteData,
) : ModelCommand(model) {
    override fun execute() {
        model.notes[note.id] = note
    }

    override fun undo() {
        model.notes.remove(note.id)
    }

    override fun label(): String = "Add note"
}

/** 删除 sticky note。记录快照。 */
class RemoveNoteCommand(
    model: GraphEditorModel,
    private val noteId: String,
) : ModelCommand(model) {
    private var note: NoteData? = null

    override fun execute() {
        note = model.notes.remove(noteId)
    }

    override fun undo() {
        note?.let { model.notes[it.id] = it }
    }

    override fun label(): String = "Delete note"
}

/** 移动 sticky note。拖拽合并。 */
class MoveNoteCommand(
    model: GraphEditorModel,
    private val noteId: String,
    private val oldX: Float,
    private val oldY: Float,
    private val newX: Float,
    private val newY: Float,
) : ModelCommand(model) {
    override fun execute() {
        model.notes[noteId]?.let { n ->
            n.x = newX
            n.y = newY
        }
    }

    override fun undo() {
        model.notes[noteId]?.let { n ->
            n.x = oldX
            n.y = oldY
        }
    }

    override fun mergeKey(): String = "note_move:$noteId"

    override fun mergeWith(next: Command): Command? {
        if (next !is MoveNoteCommand || next.noteId != noteId) return null
        return MoveNoteCommand(model, noteId, oldX, oldY, next.newX, next.newY)
    }

    override fun label(): String = "Move note"
}

/** 修改 sticky note 内容（标题/正文/颜色）。 */
class SetNoteContentCommand(
    model: GraphEditorModel,
    private val noteId: String,
    private val oldTitle: String,
    private val newTitle: String,
    private val oldBody: String,
    private val newBody: String,
    private val oldColor: Int,
    private val newColor: Int,
) : ModelCommand(model) {
    override fun execute() {
        model.notes[noteId]?.let { n ->
            n.title = newTitle
            n.body = newBody
            n.color = newColor
        }
    }

    override fun undo() {
        model.notes[noteId]?.let { n ->
            n.title = oldTitle
            n.body = oldBody
            n.color = oldColor
        }
    }

    override fun label(): String = "Edit note"
}
