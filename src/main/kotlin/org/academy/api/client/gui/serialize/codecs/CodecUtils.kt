package org.academy.api.client.gui.serialize.codecs

import net.minecraft.resources.Identifier

internal fun encodeIdentifier(id: Identifier?): String? = id?.toString()

internal fun decodeIdentifier(s: String?): Identifier? {
    if (s == null) return null
    return runCatching { Identifier.parse(s) }.getOrNull()
}

internal fun requireIdentifier(s: String?, typeName: String): Identifier {
    return decodeIdentifier(s)
        ?: throw IllegalArgumentException("'$typeName' requires a valid 'texture' field")
}
