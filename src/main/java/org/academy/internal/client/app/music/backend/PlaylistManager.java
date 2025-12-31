package org.academy.internal.client.app.music.backend;

import java.util.*;

public final class PlaylistManager {
    public enum PlaybackMode {
        REPEAT_LIST, REPEAT_ONE, SHUFFLE
    }

    private final List<MusicInfo> playlist = new ArrayList<>();
    private final List<Integer> shuffledPlaylist = new ArrayList<>();

    private int currentTrackIndex = -1;
    private int shuffleIndex = -1;
    private PlaybackMode playbackMode = PlaybackMode.REPEAT_LIST;

    public void updatePlaylist(List<MusicInfo> newPlaylist) {
        playlist.clear();
        playlist.addAll(newPlaylist);
        shuffledPlaylist.clear();
        currentTrackIndex = -1;
        shuffleIndex = -1;
    }

    public int nextTrackIndex() {
        if (playlist.isEmpty()) return -1;

        return switch (playbackMode) {
            case REPEAT_ONE -> currentTrackIndex;
            case SHUFFLE -> {
                if (shuffledPlaylist.isEmpty() || ++shuffleIndex >= shuffledPlaylist.size()) generateShuffledPlaylist();
                yield shuffledPlaylist.isEmpty() ? -1 : shuffledPlaylist.get(shuffleIndex);
            }
            case REPEAT_LIST -> (currentTrackIndex + 1) % playlist.size();
        };
    }

    public int previousTrackIndex() {
        if (playlist.isEmpty()) return -1;

        return switch (playbackMode) {
            case REPEAT_ONE -> currentTrackIndex;
            case SHUFFLE -> {
                if (shuffledPlaylist.isEmpty()) generateShuffledPlaylist();
                if (shuffledPlaylist.isEmpty()) yield -1;
                shuffleIndex--;
                if (shuffleIndex < 0) shuffleIndex = shuffledPlaylist.size() - 1;
                yield shuffledPlaylist.get(shuffleIndex);
            }
            case REPEAT_LIST -> (currentTrackIndex - 1 + playlist.size()) % playlist.size();
        };
    }

    private void generateShuffledPlaylist() {
        shuffledPlaylist.clear();
        if (playlist.isEmpty()) return;
        for (var i = 0; i < playlist.size(); i++) shuffledPlaylist.add(i);
        Collections.shuffle(shuffledPlaylist, new Random());
        if (currentTrackIndex != -1) {
            var currentItemPos = shuffledPlaylist.indexOf(currentTrackIndex);
            if (currentItemPos != -1) Collections.swap(shuffledPlaylist, 0, currentItemPos);
        }
        shuffleIndex = 0;
    }

    public void cyclePlaybackMode() {
        playbackMode = PlaybackMode.values()[(playbackMode.ordinal() + 1) % PlaybackMode.values().length];
        if (playbackMode == PlaybackMode.SHUFFLE && (shuffledPlaylist.isEmpty() || currentTrackIndex == -1)) {
            generateShuffledPlaylist();
        }
    }

    public Optional<MusicInfo> getTrack(int index) {
        if (index < 0 || index >= playlist.size()) return Optional.empty();
        return Optional.of(playlist.get(index));
    }

    public List<MusicInfo> getPlaylist() {
        return Collections.unmodifiableList(playlist);
    }

    public int getCurrentTrackIndex() {
        return currentTrackIndex;
    }

    public void setCurrentTrackIndex(int currentTrackIndex) {
        this.currentTrackIndex = currentTrackIndex;
        if (playbackMode == PlaybackMode.SHUFFLE) {
            var newShuffleIndex = shuffledPlaylist.indexOf(currentTrackIndex);
            if (newShuffleIndex != -1) {
                shuffleIndex = newShuffleIndex;
            } else if (!shuffledPlaylist.isEmpty()) generateShuffledPlaylist();
        }
    }

    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }
}