package org.academy.desktop.grapheditor.document

/**
 * 节点分组（frame）：编辑器装饰对象，圈住一组节点，随节点/整体移动。
 * 坐标为图坐标（与节点一致），不进入核心 [org.academy.api.client.render.graph.model.Graph]。
 */
class FrameData(
    val id: String,
    var title: String,
    var color: Int,
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float,
)

/** Sticky note：标题 + 正文 + 颜色，浮于节点之上。 */
class NoteData(
    val id: String,
    var title: String,
    var body: String,
    var color: Int,
    var x: Float,
    var y: Float,
    var w: Float,
    var h: Float,
)

/** 编辑器元数据聚合（sidecar 持久化载体）：frame/note + 相机 + 面板显隐。 */
class EditorMetadata {
    val frames = LinkedHashMap<String, FrameData>()
    val notes = LinkedHashMap<String, NoteData>()

    var cameraZoom = 1f
    var cameraPanX = 0f
    var cameraPanY = 0f

    val panelVisibility = mutableMapOf<String, Boolean>()

    /** 黑板参数分组（paramId → group，sidecar 编辑器元数据，不改核心 GraphParameter）。 */
    val paramGroups = mutableMapOf<String, String>()

    companion object {
        const val DEFAULT_FRAME_COLOR = 0x2ECC71AA.toInt()
        const val DEFAULT_NOTE_COLOR = 0xFFE08AEE.toInt()
    }
}
