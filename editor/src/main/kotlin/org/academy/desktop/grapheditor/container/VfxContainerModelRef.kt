package org.academy.desktop.grapheditor.container

/**
 * 容器模型共享引用（M26）：多文档切换时由宿主替换 [model] 指向（与扁平
 * [org.academy.desktop.grapheditor.canvas.GraphEditorModelRef] 同角色，VFX 容器文档配对使用）。
 */
class VfxContainerModelRef(initial: VfxContainerModel) {
    var model: VfxContainerModel = initial
}
