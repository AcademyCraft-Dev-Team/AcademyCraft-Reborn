package org.academy.internal.client.app.music.backend;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.AcademyCraft;
import org.academy.api.client.vanilla.MainLoopEvent;
import org.academy.internal.client.app.music.backend.engine.AudioPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class MusicPlayerBackend {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    @Nullable
    private static MusicPlayerBackend instance;

    private final PlaylistManager playlistManager = new PlaylistManager();
    private final AudioPlayer audioPlayer = new AudioPlayer();

    @Nullable
    private ByteBuffer currentTrackData;

    private final AtomicBoolean isSessionActive = new AtomicBoolean(false);

    private MusicPlayerBackend() {
    }

    public static void init() {
        if (instance == null) {
            instance = new MusicPlayerBackend();
            NeoForge.EVENT_BUS.register(instance);
        } else LOGGER.warn("MusicPlayerBackend has already been initialized.");
    }

    public static MusicPlayerBackend getInstance() {
        if (instance == null) throw new IllegalStateException("MusicPlayerBackend has not been initialized.");
        return instance;
    }

    private Executor getSoundExecutor() {
        return Minecraft.getInstance().getSoundManager().soundEngine.executor;
    }

    private void runOnSoundEngine(Runnable task) {
        getSoundExecutor().execute(task);
    }

    @SubscribeEvent
    public void onClientPauseChangePost(ClientPauseChangeEvent.Post event) {
        if (event.isPaused()) stop();
    }

    @SubscribeEvent
    public void onMainLoop(MainLoopEvent event) {
        runOnSoundEngine(this::update);
    }

    public void update() {
        audioPlayer.update();
        if (audioPlayer.isFinished() && isSessionActive.get()) {
            if (getPlaybackMode() == PlaylistManager.PlaybackMode.REPEAT_ONE) performPlay(getCurrentTrackIndex());
            else performPlayNext();
        }
    }

    public void handleContextReset() {
        runOnSoundEngine(this::performHandleContextReset);
    }

    private void performHandleContextReset() {
        isSessionActive.set(false);
        audioPlayer.handleContextReset();
        currentTrackData = null;
    }

    public void updatePlaylistFromData(Map<String, MusicData> musicMap, String sourceDescription) {
        var newPlaylist = musicMap.entrySet().stream()
                .map(
                        entry ->
                                parseMusicEntry(entry.getKey(), entry.getValue(), sourceDescription)
                )
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        runOnSoundEngine(() -> {
            performStop();
            playlistManager.updatePlaylist(newPlaylist);
        });
    }

    private Optional<MusicInfo> parseMusicEntry(String name, MusicData data, String sourceDescription) {
        try {
            var iconLocation = Identifier.parse(data.icon());
            var source = createMusicSource(data.source_type(), data.source());
            return Optional.of(new MusicInfo(iconLocation, source, name, data.subtitle()));
        } catch (Exception e) {
            LOGGER.error("Failed to parse music entry '{}' from {}: {}", name, sourceDescription, e.getMessage());
            return Optional.empty();
        }
    }

    private MusicSource createMusicSource(String type, String path) {
        if (type.equalsIgnoreCase("RESOURCE_LOCATION")) {
            return MusicSource.fromIdentifier(Identifier.parse(path));
        }
        if (type.equalsIgnoreCase("PATH")) return MusicSource.fromAbsolutePath(path);
        throw new IllegalArgumentException("Invalid source_type: " + type);
    }

    public void play(MusicInfo info) {
        var index = playlistManager.getPlaylist().indexOf(info);
        if (index != -1) play(index);
    }

    public void play(int trackIndex) {
        runOnSoundEngine(() -> performPlay(trackIndex));
    }

    private void performPlay(int trackIndex) {
        if (
                playlistManager.getCurrentTrackIndex() == trackIndex
                        && !audioPlayer.isFinished()
                        && audioPlayer.isPlaying()
        ) return;

        playlistManager.getTrack(trackIndex).ifPresentOrElse(mediaInfo -> {
            try {
                audioPlayer.stop();
                currentTrackData = mediaInfo.source().getData();
                playlistManager.setCurrentTrackIndex(trackIndex);
                audioPlayer.play(currentTrackData, 0);
                isSessionActive.set(true);
            } catch (IOException e) {
                LOGGER.error("Failed to play media: {}", mediaInfo.name(), e);
                performStop();
            }
        }, () -> {
            LOGGER.warn("Attempted to play track with invalid index: {}", trackIndex);
            performStop();
        });
    }

    public void stop() {
        runOnSoundEngine(this::performStop);
    }

    private void performStop() {
        isSessionActive.set(false);
        playlistManager.setCurrentTrackIndex(-1);
        audioPlayer.stop();
        currentTrackData = null;
    }

    public void togglePlayPause() {
        runOnSoundEngine(this::performTogglePlayPause);
    }

    private void performTogglePlayPause() {
        if (audioPlayer.isPlaying()) audioPlayer.togglePlayPause();
        else {
            var trackIndex = playlistManager.getCurrentTrackIndex();
            if (trackIndex != -1) performPlay(trackIndex);
        }
    }

    public void playNext() {
        runOnSoundEngine(this::performPlayNext);
    }

    private void performPlayNext() {
        var nextIndex = playlistManager.nextTrackIndex();
        if (nextIndex != -1) performPlay(nextIndex);
        else performStop();
    }

    public void playPrevious() {
        runOnSoundEngine(this::performPlayPrevious);
    }

    private void performPlayPrevious() {
        var prevIndex = playlistManager.previousTrackIndex();
        if (prevIndex != -1) performPlay(prevIndex);
    }

    public void seek(float timeRatio) {
        runOnSoundEngine(() -> performSeek(timeRatio));
    }

    private void performSeek(float timeRatio) {
        if (audioPlayer.isPlaying() && currentTrackData != null) {
            audioPlayer.seek(currentTrackData, timeRatio);
        }
    }

    public void setVolume(float value) {
        runOnSoundEngine(() -> audioPlayer.setVolume(value));
    }

    public void cyclePlaybackMode() {
        playlistManager.cyclePlaybackMode();
    }

    public float getCurrentTime() {
        return audioPlayer.getCurrentTime();
    }

    public List<MusicInfo> getPlaylist() {
        return playlistManager.getPlaylist();
    }

    public boolean isPlaying() {
        return audioPlayer.isPlaying();
    }

    public boolean isPaused() {
        return audioPlayer.isPaused();
    }

    public float getTotalDuration() {
        return audioPlayer.getTotalDuration();
    }

    public PlaylistManager.PlaybackMode getPlaybackMode() {
        return playlistManager.getPlaybackMode();
    }

    public int getCurrentTrackIndex() {
        return playlistManager.getCurrentTrackIndex();
    }

    @Nullable
    public MusicInfo getCurrentMusicInfo(){
        var index = getCurrentTrackIndex();
        return index == -1 ? null : getPlaylist().get(index);
    }

    public float getVolume() {
        return audioPlayer.getVolume();
    }
}