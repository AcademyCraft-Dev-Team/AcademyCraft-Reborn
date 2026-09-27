package org.academy.desktop.grapheditor.app

import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.academy.desktop.grapheditor.container.VfxContainerModel
import org.academy.desktop.grapheditor.document.EditorMetadata
import java.nio.file.Path

/**
 * 编辑器打开的单个图文档（M19，ADR-022）：每文档持有独立的
 * [GraphEditorModel]（含各自的 undo 栈）与 [EditorMetadata]、磁盘路径与模式/相机状态。
 * VFX 容器模式（M26）另持 [VfxContainerModel]（contexts/blocks/operators，独立 undo）。
 */
class EditorDocument(
    var name: String,
    var path: Path?,
    val model: GraphEditorModel,
    val containerModel: VfxContainerModel,
    val metadata: EditorMetadata,
    var mode: GraphMode,
    var cameraZoom: Float,
    var cameraPanX: Float,
    var cameraPanY: Float,
)
