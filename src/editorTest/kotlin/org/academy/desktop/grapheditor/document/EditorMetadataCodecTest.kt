package org.academy.desktop.grapheditor.document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class EditorMetadataCodecTest {

    @Test
    fun roundTripPreservesFramesNotesCameraPanels() {
        val meta = EditorMetadata()
        meta.frames["f0"] = FrameData("f0", "My Group", EditorMetadata.DEFAULT_FRAME_COLOR, 10f, 20f, 200f, 150f)
        meta.frames["f1"] = FrameData("f1", "Render", 0xFF0000AA.toInt(), -5f, 0f, 120f, 80f)
        meta.notes["n0"] = NoteData("n0", "TODO", "fix the shader", EditorMetadata.DEFAULT_NOTE_COLOR, 1f, 2f, 180f, 120f)
        meta.cameraZoom = 1.5f
        meta.cameraPanX = -10f
        meta.cameraPanY = 5f
        meta.panelVisibility["Palette"] = false
        meta.panelVisibility["Project"] = true
        meta.paramGroups["speed"] = "Movement"
        meta.paramGroups["color"] = "Appearance"

        val back = EditorMetadataCodec.decode(EditorMetadataCodec.encode(meta))

        assertEquals(2, back.frames.size)
        assertEquals("My Group", back.frames["f0"]!!.title)
        assertEquals(200f, back.frames["f0"]!!.w)
        assertEquals(-5f, back.frames["f1"]!!.x)
        assertEquals(1, back.notes.size)
        assertEquals("fix the shader", back.notes["n0"]!!.body)
        assertEquals(1.5f, back.cameraZoom)
        assertEquals(-10f, back.cameraPanX)
        assertEquals(5f, back.cameraPanY)
        assertFalse(back.panelVisibility["Palette"]!!)
        assertEquals(true, back.panelVisibility["Project"])
        assertEquals("Movement", back.paramGroups["speed"])
        assertEquals("Appearance", back.paramGroups["color"])
    }

    @Test
    fun decodeEmptyJsonReturnsDefaults() {
        val meta = EditorMetadataCodec.decode(com.google.gson.JsonObject())
        assertTrue(meta.frames.isEmpty())
        assertTrue(meta.notes.isEmpty())
        assertEquals(1f, meta.cameraZoom)
    }

    private fun assertTrue(value: Boolean) = org.junit.jupiter.api.Assertions.assertTrue(value)
}
