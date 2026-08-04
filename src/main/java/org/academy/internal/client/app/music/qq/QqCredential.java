package org.academy.internal.client.app.music.qq;

import com.google.gson.annotations.SerializedName;

public final class QqCredential {
    @SerializedName("musicid")
    private String musicId = "";
    @SerializedName("musickey")
    private String musicKey = "";
    @SerializedName("keyExpiresIn")
    private long keyExpiresIn;
    @SerializedName("musickeyCreateTime")
    private long musicKeyCreateTime;
    @SerializedName("refresh_key")
    private String refreshKey = "";
    @SerializedName("refresh_token")
    private String refreshToken = "";

    public QqCredential() {
    }

    public QqCredential(String musicId, String musicKey, long keyExpiresIn, long musicKeyCreateTime, String refreshKey, String refreshToken) {
        this.musicId = musicId;
        this.musicKey = musicKey;
        this.keyExpiresIn = keyExpiresIn;
        this.musicKeyCreateTime = musicKeyCreateTime;
        this.refreshKey = refreshKey;
        this.refreshToken = refreshToken;
    }

    public boolean isExpired() {
        return keyExpiresIn > 0 && System.currentTimeMillis() / 1000 >= musicKeyCreateTime + keyExpiresIn;
    }

    public boolean isValid() {
        return musicId != null && !musicId.isBlank()
                && musicKey != null && !musicKey.isBlank()
                && !isExpired();
    }

    public String toCookieString() {
        if (!isValid()) {
            return "";
        }
        return "uin=" + musicId + "; qm_keyst=" + musicKey;
    }

    public String getMusicId() {
        return musicId;
    }

    public String getMusicKey() {
        return musicKey;
    }

    public long getKeyExpiresIn() {
        return keyExpiresIn;
    }

    public long getMusicKeyCreateTime() {
        return musicKeyCreateTime;
    }

    public String getRefreshKey() {
        return refreshKey;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
