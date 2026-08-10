package org.academy.internal.client.app.music.netease;

public record NeteaseSearchResult(String id, String title, String artist, int durationSeconds, String albumName,
                                  String picUrl, int fee) {
    public NeteaseSearchResult {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        artist = artist == null ? "" : artist;
        albumName = albumName == null ? "" : albumName;
        picUrl = picUrl == null ? "" : picUrl;
    }

    public boolean isVip() {
        return fee >= 1;
    }

    public String displayText() {
        if (title.isBlank()) {
            return artist.isBlank() ? "" : artist;
        }
        return artist.isBlank() ? title : title + " - " + artist;
    }
}
