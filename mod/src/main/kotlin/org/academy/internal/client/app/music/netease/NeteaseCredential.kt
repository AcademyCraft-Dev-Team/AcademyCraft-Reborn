package org.academy.internal.client.app.music.netease

class NeteaseCredential(val uid: String, val nickname: String, val avatarUrl: String) {
    fun isValid(): Boolean = uid.isNotBlank()
}
