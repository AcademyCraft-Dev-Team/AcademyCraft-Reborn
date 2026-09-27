package org.academy.api.client.gui.text.atlas

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object GlyphSignal {
    private val VERSION = AtomicLong()
    private val PENDING = AtomicBoolean()

    val version: Long
        get() {
            if (PENDING.getAndSet(false)) VERSION.incrementAndGet()
            return VERSION.get()
        }

    fun bump() {
        PENDING.set(true)
    }
}
