package org.academy.api.client.gui.msdf.core

object Constants {
    const val CORNER_DOT_EPSILON = 0.000001
    const val CUBIC_SEARCH_STARTS = 4
    const val CUBIC_SEARCH_STEPS = 4
    const val DECONVERGE_OVERSHOOT = 1.111111111111111
    const val DISTANCE_DELTA_FACTOR = 1.001
    const val DEFAULT_ATLAS_SIZE = 2048
    const val DEFAULT_GLYPH_SIZE = 64
    const val DEFAULT_PX_RANGE = 4.0

    const val SOLVE_QUADRATIC_LARGE_B_THRESHOLD = 1e12
    const val SOLVE_CUBIC_DOUBLE_ROOT_EPSILON = 1e-12
    const val INV_9 = 0.1111111111111111
    const val INV_54 = 0.01851851851851852
    const val INV_3 = 0.3333333333333333
}

enum class YAxisOrientation {
    Y_UPWARD, Y_DOWNWARD
}