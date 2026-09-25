package org.academy.internal.client.app.props

import net.minecraft.resources.Identifier
import org.academy.AcademyCraft

object PropsIcon {
    val LOCATION: Identifier = AcademyCraft.academy("textures/gui/app/props/icon.png")

    /** Kept for the existing client bootstrap; the icon is now a normal resource texture. */
    fun init() = Unit
}
