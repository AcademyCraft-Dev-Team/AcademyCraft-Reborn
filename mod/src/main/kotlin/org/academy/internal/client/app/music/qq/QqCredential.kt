package org.academy.internal.client.app.music.qq

import com.google.gson.annotations.SerializedName

class QqCredential(
    @SerializedName("musicid") var musicId: String = "",
    @SerializedName("musickey") var musicKey: String = "",
    @SerializedName("keyExpiresIn") var keyExpiresIn: Long = 0,
    @SerializedName("musickeyCreateTime") var musicKeyCreateTime: Long = 0,
    @SerializedName("refresh_key") var refreshKey: String = "",
    @SerializedName("refresh_token") var refreshToken: String = "",
) {
    fun isExpired(): Boolean =
        keyExpiresIn > 0 && System.currentTimeMillis() / 1000 >= musicKeyCreateTime + keyExpiresIn

    fun isValid(): Boolean = musicId.isNotBlank() && musicKey.isNotBlank() && !isExpired()
}
