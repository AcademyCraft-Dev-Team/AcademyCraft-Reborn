package org.academy.desktop.grapheditor.canvas

import org.academy.desktop.grapheditor.EditorTestFixtures
import org.academy.desktop.grapheditor.document.EditorMetadata
import org.academy.desktop.grapheditor.document.FrameData
import org.academy.desktop.grapheditor.document.NoteData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GraphEditorModelFrameNoteTest {

    @Test
    fun frameAddMoveMergesIntoSingleUndo() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val frame = model.addFrame("Group", 0f, 0f, 200f, 150f)
        assertEquals(1, model.frames.size)

        model.moveFrame(frame.id, 50f, 50f)
        model.moveFrame(frame.id, 100f, 100f)
        assertEquals(100f, model.frames[frame.id]!!.x)

        model.undo()
        assertEquals(0f, model.frames[frame.id]!!.x)
        model.undo()
        assertTrue(model.frames.isEmpty())
        model.redo()
        assertEquals(1, model.frames.size)
        assertEquals("Group", model.frames[frame.id]!!.title)
    }

    @Test
    fun frameResizeRenameRemoveUndo() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val frame = model.addFrame("G", 0f, 0f, 100f, 100f)

        model.resizeFrame(frame.id, 300f, 200f)
        model.renameFrame(frame.id, "Renamed")
        assertEquals(300f, model.frames[frame.id]!!.w)
        assertEquals("Renamed", model.frames[frame.id]!!.title)

        model.undo() // rename
        assertEquals("G", model.frames[frame.id]!!.title)
        model.undo() // resize
        assertEquals(100f, model.frames[frame.id]!!.w)

        model.removeFrame(frame.id)
        assertTrue(model.frames.isEmpty())
        model.undo()
        assertEquals("G", model.frames[frame.id]!!.title)
    }

    @Test
    fun noteAddMoveEditRemoveUndo() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        val note = model.addNote("TODO", 10f, 10f)
        assertEquals(1, model.notes.size)

        model.moveNote(note.id, 50f, 60f)
        model.setNoteContent(note.id, "Fixed", "new body", EditorMetadata.DEFAULT_NOTE_COLOR)
        assertEquals(50f, model.notes[note.id]!!.x)
        assertEquals("Fixed", model.notes[note.id]!!.title)
        assertEquals("new body", model.notes[note.id]!!.body)

        model.undo() // content
        assertEquals("TODO", model.notes[note.id]!!.title)
        model.undo() // move
        assertEquals(10f, model.notes[note.id]!!.x)

        model.removeNote(note.id)
        assertTrue(model.notes.isEmpty())
        model.undo()
        assertEquals("TODO", model.notes[note.id]!!.title)
    }

    @Test
    fun loadMetadataReplacesAndClearsHistory() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        model.addNode("input.constant", 0f, 0f)
        model.addFrame("A", 0f, 0f, 100f, 100f)
        assertTrue(model.canUndo)

        val meta = EditorMetadata()
        meta.frames["f9"] = FrameData("f9", "Loaded", EditorMetadata.DEFAULT_FRAME_COLOR, 5f, 6f, 200f, 150f)
        meta.notes["n9"] = NoteData("n9", "Loaded Note", "hi", EditorMetadata.DEFAULT_NOTE_COLOR, 1f, 2f, 180f, 120f)
        model.loadMetadata(meta)

        assertEquals(1, model.frames.size)
        assertEquals("Loaded", model.frames["f9"]!!.title)
        assertEquals(1, model.notes.size)
        assertFalse(model.canUndo)
        assertFalse(model.canRedo)
    }

    @Test
    fun resetClearsDecorations() {
        val model = GraphEditorModel(EditorTestFixtures.registry())
        model.addFrame("A", 0f, 0f, 100f, 100f)
        model.addNote("N", 0f, 0f)
        model.reset()
        assertTrue(model.frames.isEmpty())
        assertTrue(model.notes.isEmpty())
    }
}
