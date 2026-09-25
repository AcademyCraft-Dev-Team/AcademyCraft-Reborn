package org.academy.desktop.grapheditor.canvas

/**
 * 可变 [GraphEditorModel] 持有者（M19，ADR-022）：多文档编辑器在标签页切换时替换
 * 指向的活动模型，共享同一 ref 的画布/预览/检查器随之切换到新文档，无需重建组件。
 */
class GraphEditorModelRef(initial: GraphEditorModel) {
    var model: GraphEditorModel = initial
}
