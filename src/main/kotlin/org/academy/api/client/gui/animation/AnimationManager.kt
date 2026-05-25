package org.academy.api.client.gui.animation

import java.util.concurrent.CopyOnWriteArrayList

object AnimationManager {
    private val runningAnimations: MutableList<Animator> = CopyOnWriteArrayList()
    private val pendingAdditions: MutableList<Animator> = CopyOnWriteArrayList()

    fun startAnimation(animator: Animator) {
        pendingAdditions.add(animator)
    }

    fun remove(animator: Animator) {
        runningAnimations.remove(animator)
        pendingAdditions.remove(animator)
    }

    fun onFrameUpdate() {
        val currentTime = System.nanoTime() / 1000000

        if (!pendingAdditions.isEmpty()) {
            for (anim in pendingAdditions) {
                anim.startTime = currentTime + anim.startDelay
                anim.onStartInternal()
            }
            runningAnimations.addAll(pendingAdditions)
            pendingAdditions.clear()
        }

        if (runningAnimations.isEmpty()) return

        val toRemove = ArrayList<Animator>()
        for (anim in runningAnimations) {
            if (anim.tick(currentTime)) {
                toRemove.add(anim)
            }
        }

        if (!toRemove.isEmpty()) runningAnimations.removeAll(toRemove)
    }
}