package org.academy.api.client.gui.glyph

import java.util.concurrent.atomic.AtomicLong

/**
 * 拉取式 MSDF 就绪信号。
 *
 * [MsdfAtlas] 每完成一个字形（READY/FAILED）就 bump [version]；控件记录上次布局时的版本，
 * 下一帧仅在版本变化且仍有占位时才重新展开。取代回调/弱引用失效注册表。
 */
object GlyphReadiness {
    private val VERSION = AtomicLong()

    /** 单调计数，每完成一个字形 +1。 */
    val version: Long get() = VERSION.get()

    fun bump() {
        VERSION.incrementAndGet()
    }
}
