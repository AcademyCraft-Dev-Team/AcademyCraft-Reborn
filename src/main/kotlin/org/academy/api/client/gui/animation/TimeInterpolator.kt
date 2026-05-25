package org.academy.api.client.gui.animation

fun interface TimeInterpolator {
    fun getInterpolation(input: Float): Float
}