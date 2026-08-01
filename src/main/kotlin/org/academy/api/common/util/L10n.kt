package org.academy.api.common.util

import net.minecraft.locale.Language

object L10n {
    operator fun get(key: String): String = Language.getInstance().getOrDefault(key)
}
