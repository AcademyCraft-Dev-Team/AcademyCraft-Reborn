package org.academy.internal.common.music.provider;

import java.util.List;

/**
 * 曲目解析结果：同一路径下可能有多个 CDN 直链（不同码率/格式），按顺序尝试直到下载成功喵。
 */
public record TrackCandidate(List<String> streamUrls, String diagnoseMessage) {
    public TrackCandidate {
        streamUrls = List.copyOf(streamUrls);
        diagnoseMessage = diagnoseMessage == null ? "" : diagnoseMessage;
    }
}
