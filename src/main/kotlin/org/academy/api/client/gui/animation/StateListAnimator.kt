package org.academy.api.client.gui.animation

class StateListAnimator {
    private val tuples: MutableList<StateTuple> = ArrayList<StateTuple>()
    private var runningAnimator: Animator? = null
    private var lastMatch: Animator? = null

    fun addState(stateMask: Int, animator: Animator) {
        tuples.add(StateTuple(stateMask, animator))
    }

    fun setState(stateMask: Int) {
        var match: Animator? = null

        for (tuple in tuples) {
            if ((stateMask and tuple.mask) == tuple.mask) {
                match = tuple.animator
                break
            }
        }

        if (match === lastMatch) {
            return
        }

        if (runningAnimator != null) {
            runningAnimator!!.cancel()
        }

        lastMatch = match
        runningAnimator = match

        match?.start()
    }

    fun jumpToCurrentState() {
        if (runningAnimator != null) {
            runningAnimator!!.end()
        }
    }

    private data class StateTuple(val mask: Int, val animator: Animator)
}