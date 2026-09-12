package org.academy.api.client.gui.glyph

/** 图集中 MSDF 字形的生命周期状态。 */
enum class GlyphStatus {
    /** 槽位已分配但像素尚未上传：消费者应渲染位图占位。 */
    PENDING,

    /** MSDF 像素已上传，可渲染。 */
    READY,

    /** 异步生成失败：消费者须永久回退到 bitmap 路径。 */
    FAILED
}
