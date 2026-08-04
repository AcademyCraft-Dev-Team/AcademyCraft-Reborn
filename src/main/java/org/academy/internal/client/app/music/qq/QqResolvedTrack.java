package org.academy.internal.client.app.music.qq;

public record QqResolvedTrack(String id, String title, String artist, int durationSeconds, boolean vip, String streamUrl) {
}
