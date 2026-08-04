package org.academy.internal.client.app.music.data

import net.minecraft.resources.Identifier

data class MusicInfo(
    val icon: Identifier,
    val source: MusicSource,
    val name: String,
    val subtitle: String,
    val provider: String = "local",
    val externalId: String = "",
    val durationSeconds: Int = 0,
    val vip: Boolean = false,
    val artworkUrl: String = ""
)
