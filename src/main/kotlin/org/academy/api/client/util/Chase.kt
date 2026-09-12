package org.academy.api.client.util

import kotlin.math.exp

object Chase {
    const val TIME_CONSTANT_MS: Float = 100f

    private const val NANOS_PER_MS: Float = 1_000_000f

    private var lastNanos: Long = 0L
    private var frameDeltaMs: Float = 0f

    fun tick() {
        val now = System.nanoTime()
        frameDeltaMs = if (lastNanos == 0L) 0f else (now - lastNanos) / NANOS_PER_MS
        lastNanos = now
    }

    fun deltaMillis(): Float = frameDeltaMs

    fun factor(dtMs: Float): Float = factor(dtMs, TIME_CONSTANT_MS)

    fun factor(dtMs: Float, timeConstantMs: Float): Float =
        if (dtMs <= 0f || timeConstantMs <= 0f) 0f else 1f - exp(-dtMs / timeConstantMs)

    fun approach(current: Float, target: Float): Float =
        approach(current, target, frameDeltaMs, TIME_CONSTANT_MS)

    fun approach(current: Float, target: Float, dtMs: Float): Float =
        approach(current, target, dtMs, TIME_CONSTANT_MS)

    fun approach(current: Float, target: Float, dtMs: Float, timeConstantMs: Float): Float =
        current + (target - current) * factor(dtMs, timeConstantMs)
}
