package org.academy.internal.client.app.music.data

import com.google.gson.annotations.SerializedName

data class MusicData(
    val icon: String,
    @SerializedName(value = "source_type", alternate = ["sourceType"])
    val sourceType: String,
    val source: String,
    val subtitle: String
)
