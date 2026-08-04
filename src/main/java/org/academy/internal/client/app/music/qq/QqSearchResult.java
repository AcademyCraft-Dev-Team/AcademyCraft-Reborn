package org.academy.internal.client.app.music.qq;

public record QqSearchResult(String id, String title, String singer, boolean vip) {
    public QqSearchResult {
        id = id == null ? "" : id;
        title = title == null ? "" : title;
        singer = singer == null ? "" : singer;
    }

    public String displayText() {
        if (title.isBlank()) {
            return singer.isBlank() ? "" : singer;
        }
        return singer.isBlank() ? title : title + " - " + singer;
    }
}
