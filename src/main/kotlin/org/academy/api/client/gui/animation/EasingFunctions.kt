package org.academy.api.client.gui.animation

import net.minecraft.util.Mth
import kotlin.math.pow

@Suppress("unused")
object EasingFunctions {
    val LINEAR: TimeInterpolator = TimeInterpolator { input: Float -> input }

    val EASE_IN_SINE: TimeInterpolator = TimeInterpolator { input: Float -> 1 - Mth.cos((input * Math.PI) / 2) }
    val EASE_IN_QUAD: TimeInterpolator = TimeInterpolator { input: Float -> input * input }
    val EASE_IN_EXPO: TimeInterpolator =
        TimeInterpolator { input: Float -> if (input == 0f) 0f else 2.0.pow((10 * input - 10).toDouble()).toFloat() }
    val EASE_IN_BACK: TimeInterpolator = TimeInterpolator { input: Float ->
        val c1 = 1.70158f
        val c3 = c1 + 1
        c3 * input * input * input - c1 * input * input
    }
    val EASE_IN_CUBIC: TimeInterpolator = TimeInterpolator { input: Float -> input * input * input }
    val EASE_IN_ELASTIC: TimeInterpolator = TimeInterpolator { input: Float ->
        val c4 = (2 * Math.PI).toFloat() / 3
        when (input) {
            0f -> 0f
            1f -> 1f
            else -> -2.0.pow((10 * input - 10).toDouble()).toFloat() * Mth.sin((input * 10 - 10.75) * c4)
        }
    }
    val EASE_IN_OUT_SINE: TimeInterpolator = TimeInterpolator { input: Float -> -0.5f * (Mth.cos(Math.PI * input) - 1) }
    val EASE_IN_OUT_QUAD: TimeInterpolator = TimeInterpolator { input: Float ->
        if (input < 0.5f) 2 * input * input else 1 - (-2 * input + 2).toDouble().pow(2.0).toFloat() / 2
    }
    val EASE_IN_OUT_BACK: TimeInterpolator = TimeInterpolator { input: Float ->
        val c1 = 1.70158f
        val c2 = c1 * 1.525f
        if (input < 0.5f) ((2 * input).toDouble().pow(2.0)
            .toFloat() * ((c2 + 1) * 2 * input - c2)) / 2 else ((2 * input - 2).toDouble().pow(2.0)
            .toFloat() * ((c2 + 1) * (input * 2 - 2) + c2) + 2) / 2
    }
    val EASE_IN_OUT_EXPO: TimeInterpolator = TimeInterpolator { input: Float ->
        if (input == 0f) 0f else if (input == 1f) 1f else if (input < 0.5f) 2.0.pow((20 * input - 10).toDouble())
            .toFloat() / 2 else (2 - 2.0.pow((-20 * input + 10).toDouble()).toFloat()) / 2
    }
    val EASE_IN_OUT_CUBIC: TimeInterpolator = TimeInterpolator { input: Float ->
        if (input < 0.5f) 4 * input * input * input else 1 - (-2 * input + 2).toDouble().pow(3.0).toFloat() / 2
    }

    val EASE_IN_OUT_ELASTIC: TimeInterpolator = TimeInterpolator { input: Float ->
        val c5 = (2 * Mth.PI) / 4.5f
        val sin = Mth.sin((20 * input - 11.125) * c5)
        if (input == 0f) 0f else if (input == 1f) 1f else if (input < 0.5f) -(2.0.pow((20 * input - 10).toDouble())
            .toFloat() * sin) / 2 else (2.0.pow((-20 * input + 10).toDouble()).toFloat() * sin) / 2 + 1
    }

    val EASE_OUT_SINE: TimeInterpolator = TimeInterpolator { input: Float -> Mth.sin((input * Math.PI) / 2) }

    val EASE_OUT_QUAD: TimeInterpolator = TimeInterpolator { input: Float -> 1 - (1 - input) * (1 - input) }

    val EASE_OUT_CUBIC: TimeInterpolator =
        TimeInterpolator { input: Float -> 1 - (1 - input).toDouble().pow(3.0).toFloat() }

    val EASE_OUT_EXPO: TimeInterpolator =
        TimeInterpolator { input: Float -> if (input == 1f) 1f else 1 - 2.0.pow((-10 * input).toDouble()).toFloat() }

    val EASE_OUT_BACK: TimeInterpolator = TimeInterpolator { input: Float ->
        val c1 = 1.70158f
        val c3 = c1 + 1
        1 + c3 * (input - 1).toDouble().pow(3.0).toFloat() + c1 * (input - 1).toDouble().pow(2.0).toFloat()
    }
    val EASE_OUT_ELASTIC: TimeInterpolator = TimeInterpolator { input: Float ->
        val c4 = (2 * Math.PI).toFloat() / 3
        when (input) {
            0f -> 0f
            1f -> 1f
            else -> 2.0.pow((-10 * input).toDouble()).toFloat() * Mth.sin((input * 10 - 0.75) * c4) + 1
        }
    }

    fun createExpoOut(strength: Float): TimeInterpolator {
        return TimeInterpolator { input: Float ->
            if (input == 1f) 1f else 1 - 2.0.pow((-strength * input).toDouble()).toFloat()
        }
    }

    val EASE_OUT_AC: TimeInterpolator = createExpoOut(6.0f)
}