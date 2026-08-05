package org.academy.internal.client.app.music.netease;

public final class NeteaseCredential {
    private String uid;
    private String nickname;
    private String avatarUrl;
    private long loginTime;

    public NeteaseCredential() {
    }

    public NeteaseCredential(String uid, String nickname, String avatarUrl) {
        this.uid = uid;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        loginTime = System.currentTimeMillis();
    }

    public String getUid() {
        return uid;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public long getLoginTime() {
        return loginTime;
    }

    public boolean isValid() {
        return uid != null && !uid.isBlank();
    }
}
