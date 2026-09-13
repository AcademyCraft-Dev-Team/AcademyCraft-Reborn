package org.academy.api.client.gui.state

class UiState<T>(initial: T) {
    var value: T = initial
        set(v) {
            if (field != v) {
                field = v
                val snapshot = ArrayList(subscribers)
                for (subscriber in snapshot) subscriber(v)
            }
        }

    private val subscribers: MutableList<(T) -> Unit> = ArrayList()

    fun observe(fn: (T) -> Unit, fireImmediately: Boolean = true): () -> Unit {
        subscribers.add(fn)
        if (fireImmediately) fn(value)
        return { subscribers.remove(fn) }
    }

    fun dispose() {
        subscribers.clear()
    }
}
