package org.academy.api.client.gui.frame

import java.util.concurrent.CopyOnWriteArrayList

/**
 * UI 线程逐帧回调调度器 (Choreographer 类比喵).
 *
 * 与 Android 的 `Choreographer.postFrameCallback` 对齐: 每渲染帧在 UI 线程执行一次,
 * 用于动画推进、光标闪烁、HUD 数值刷新等持续逻辑. 返回的取消函数可在任意线程调用.
 *
 * 线程契约: [post] 在 UI 线程调用; 回调体只允许触碰 UI 线程亲和的对象 (widget/invalidate).
 */
object UiFrame {
    private val callbacks: MutableList<() -> Unit> = CopyOnWriteArrayList()

    /** 注册一个逐帧回调, 返回取消函数. */
    fun post(callback: () -> Unit): () -> Unit {
        callbacks.add(callback)
        return { callbacks.remove(callback) }
    }

    /** 每帧由渲染循环调用一次 (MC: MixinGameRenderer; Desktop: DesktopUiHost.frame). */
    fun onFrame() {
        for (callback in callbacks) callback()
    }

    fun clear() {
        callbacks.clear()
    }
}