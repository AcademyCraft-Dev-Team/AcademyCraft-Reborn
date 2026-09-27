package org.academy.api.client.gui.frame

import org.academy.api.client.util.Chase
import java.util.concurrent.CopyOnWriteArrayList

object UiFrame {
    private val callbacks: MutableList<() -> Unit> = CopyOnWriteArrayList()

    fun post(callback: () -> Unit): () -> Unit {
        callbacks.add(callback)
        return { callbacks.remove(callback) }
    }

    fun onFrame() {
        Chase.tick()
        for (callback in callbacks) callback()
    }

    fun clear() {
        callbacks.clear()
    }
}
