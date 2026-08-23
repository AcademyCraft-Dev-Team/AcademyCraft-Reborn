package org.academy.desktop.grapheditor.document

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * 编辑器元数据 sidecar 编解码（`<name>.editor.json`）。
 * 与核心图编解码（JsonGraphCodec）完全隔离：运行时 GraphAssets 不受影响。
 */
object EditorMetadataCodec {
    const val VERSION_FIELD = "version"
    const val CURRENT_VERSION = 1

    fun encode(metadata: EditorMetadata): JsonObject {
        val root = JsonObject()
        root.addProperty(VERSION_FIELD, CURRENT_VERSION)

        val camera = JsonObject()
        camera.addProperty("zoom", metadata.cameraZoom.toDouble())
        camera.addProperty("panX", metadata.cameraPanX.toDouble())
        camera.addProperty("panY", metadata.cameraPanY.toDouble())
        root.add("camera", camera)

        val frames = JsonArray()
        for (frame in metadata.frames.values) {
            val o = JsonObject()
            o.addProperty("id", frame.id)
            o.addProperty("title", frame.title)
            o.addProperty("color", frame.color)
            o.addProperty("x", frame.x.toDouble())
            o.addProperty("y", frame.y.toDouble())
            o.addProperty("w", frame.w.toDouble())
            o.addProperty("h", frame.h.toDouble())
            frames.add(o)
        }
        root.add("frames", frames)

        val notes = JsonArray()
        for (note in metadata.notes.values) {
            val o = JsonObject()
            o.addProperty("id", note.id)
            o.addProperty("title", note.title)
            o.addProperty("body", note.body)
            o.addProperty("color", note.color)
            o.addProperty("x", note.x.toDouble())
            o.addProperty("y", note.y.toDouble())
            o.addProperty("w", note.w.toDouble())
            o.addProperty("h", note.h.toDouble())
            notes.add(o)
        }
        root.add("notes", notes)

        val panels = JsonObject()
        metadata.panelVisibility.forEach { (name, visible) -> panels.addProperty(name, visible) }
        root.add("panels", panels)

        val groups = JsonObject()
        metadata.paramGroups.forEach { (id, group) -> groups.addProperty(id, group) }
        root.add("paramGroups", groups)

        return root
    }

    fun decode(json: JsonObject): EditorMetadata {
        val metadata = EditorMetadata()

        json.getAsJsonObject("camera")?.let { camera ->
            metadata.cameraZoom = camera.get("zoom")?.asFloat ?: 1f
            metadata.cameraPanX = camera.get("panX")?.asFloat ?: 0f
            metadata.cameraPanY = camera.get("panY")?.asFloat ?: 0f
        }

        json.getAsJsonArray("frames")?.forEach { el ->
            val o = el.asJsonObject
            metadata.frames[o.get("id").asString] = FrameData(
                o.get("id").asString,
                o.get("title")?.asString ?: "",
                o.get("color")?.asInt ?: EditorMetadata.DEFAULT_FRAME_COLOR,
                o.get("x")?.asFloat ?: 0f,
                o.get("y")?.asFloat ?: 0f,
                o.get("w")?.asFloat ?: 200f,
                o.get("h")?.asFloat ?: 150f,
            )
        }

        json.getAsJsonArray("notes")?.forEach { el ->
            val o = el.asJsonObject
            metadata.notes[o.get("id").asString] = NoteData(
                o.get("id").asString,
                o.get("title")?.asString ?: "Note",
                o.get("body")?.asString ?: "",
                o.get("color")?.asInt ?: EditorMetadata.DEFAULT_NOTE_COLOR,
                o.get("x")?.asFloat ?: 0f,
                o.get("y")?.asFloat ?: 0f,
                o.get("w")?.asFloat ?: 180f,
                o.get("h")?.asFloat ?: 120f,
            )
        }

        json.getAsJsonObject("panels")?.let { panels ->
            panels.entrySet().forEach { (name, value) ->
                metadata.panelVisibility[name] = value.asBoolean
            }
        }

        json.getAsJsonObject("paramGroups")?.let { groups ->
            groups.entrySet().forEach { (id, value) ->
                metadata.paramGroups[id] = value.asString
            }
        }

        return metadata
    }
}
