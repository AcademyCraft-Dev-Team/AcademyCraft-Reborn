package org.academy.api.client.gui.animation

interface AnimatorListener {
    fun onAnimationStart(animation: Animator) {
    }

    fun onAnimationEnd(animation: Animator) {
    }

    fun onAnimationCancel(animation: Animator) {
    }
}