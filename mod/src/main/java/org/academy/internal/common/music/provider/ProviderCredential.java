package org.academy.internal.common.music.provider;

import java.util.function.Supplier;

/**
 * 提供者服务所需的登录上下文：COOKIE 模式直接携带 Cookie 串（网易），
 * QQ 模式携带 uin/musickey 及 key 过期时间点（epoch 秒，0 表示未知），凭证为空表示匿名访问喵。
 */
public record ProviderCredential(
        String cookie,
        String qqMusicId,
        String qqMusicKey,
        long qqKeyExpiresAtEpochSeconds
) {
    public static final ProviderCredential ANONYMOUS = new ProviderCredential("", "", "", 0L);

    public ProviderCredential {
        cookie = cookie == null ? "" : cookie;
        qqMusicId = qqMusicId == null ? "" : qqMusicId;
        qqMusicKey = qqMusicKey == null ? "" : qqMusicKey;
        qqKeyExpiresAtEpochSeconds = Math.max(0L, qqKeyExpiresAtEpochSeconds);
    }

    public static ProviderCredential ofCookie(String cookie) {
        return new ProviderCredential(cookie, "", "", 0L);
    }

    public static ProviderCredential ofQq(String musicId, String musicKey) {
        return new ProviderCredential("", musicId, musicKey, 0L);
    }

    public static ProviderCredential ofQq(String musicId, String musicKey, long keyExpiresAtEpochSeconds) {
        return new ProviderCredential("", musicId, musicKey, keyExpiresAtEpochSeconds);
    }

    public static ProviderCredential fromCookieSupplier(Supplier<String> supplier) {
        return ofCookie(supplier.get());
    }

    public boolean hasCookie() {
        return !cookie.isBlank();
    }

    public boolean hasQqCredential() {
        return !qqMusicId.isBlank() && !qqMusicKey.isBlank();
    }

    public boolean isQqExpired() {
        return qqKeyExpiresAtEpochSeconds > 0
                && System.currentTimeMillis() / 1000 >= qqKeyExpiresAtEpochSeconds;
    }

    public String qqCookieString() {
        if (!hasQqCredential()) return "";
        return "uin=" + qqMusicId + "; qm_keyst=" + qqMusicKey;
    }
}
