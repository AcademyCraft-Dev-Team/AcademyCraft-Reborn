package org.academy.internal.client.app.mediaplayer;

import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.jni.AudioDecoderJNI;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class MediaPlayerBackend {
    private static final int BUFFER_COUNT = 4;
    private static final List<MediaInfo> PLAYLIST = new ArrayList<>();
    public static final Type MUSIC_DATA_MAP_TYPE = new TypeToken<Map<String, MusicData>>() {
    }.getType();

    private static int alSource = -1;
    private static final int[] alBuffers = new int[BUFFER_COUNT];
    private static long decoderPtr = 0;
    private static ByteBuffer audioDataBuffer;
    private static ByteBuffer pcmBuffer;
    private static long baseSampleOffset = 0;

    private static final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private static final AtomicBoolean isPaused = new AtomicBoolean(false);
    private static volatile boolean isSeeking = false;

    private static int currentTrackIndex = -1;
    private static float totalDuration = 0f;
    private static float volume = 1.0f;
    private static int sampleRate = 0;
    private static int format = 0;
    private static int channels = 0;

    public enum PlaybackMode {REPEAT_LIST, REPEAT_ONE, SHUFFLE}

    private static PlaybackMode playbackMode = PlaybackMode.REPEAT_LIST;
    private static final List<Integer> shuffledPlaylist = new ArrayList<>();
    private static int shuffleIndex = -1;

    private enum FadeState {NONE, FADING_IN, FADING_OUT}

    private static FadeState fadeState = FadeState.NONE;
    private static long fadeStartTime;
    private static final float FADE_DURATION_SECONDS = 0.75f;
    private static boolean isStoppingAfterFadeOut = false;

    private MediaPlayerBackend() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new MusicLoader());
    }

    public static void updatePlaylistFromData(Map<String, MusicData> musicMap, String sourceDescription) {
        performStop();
        PLAYLIST.clear();
        shuffledPlaylist.clear();
        currentTrackIndex = -1;
        shuffleIndex = -1;

        if (musicMap == null) {
            return;
        }

        List<MediaInfo> newPlaylist = new ArrayList<>();
        for (Map.Entry<String, MusicData> entry : musicMap.entrySet()) {
            String name = entry.getKey();
            MusicData data = entry.getValue();

            ResourceLocation iconLocation;
            try {
                iconLocation = ResourceLocation.parse(data.icon());
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Invalid icon ResourceLocation '{}' for entry '{}' in {}" + e, data.icon(), name, sourceDescription);
                continue;
            }

            MediaSource source;
            String sourceTypeStr = data.source_type();
            if (sourceTypeStr == null) {
                AcademyCraft.LOGGER.error("Missing source_type for entry '{}' in {}", name, sourceDescription);
                continue;
            }

            try {
                if (sourceTypeStr.equalsIgnoreCase("RESOURCE_LOCATION")) {
                    source = MediaSource.fromResourceLocation(ResourceLocation.parse(data.source()));
                } else if (sourceTypeStr.equalsIgnoreCase("PATH")) {
                    source = MediaSource.fromAbsolutePath(data.source());
                } else {
                    AcademyCraft.LOGGER.error("Invalid source_type '{}' for entry '{}' in {}", sourceTypeStr, name, sourceDescription);
                    continue;
                }
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Invalid source path '{}' for entry '{}' in {}", data.source(), name, sourceDescription);
                continue;
            }

            newPlaylist.add(new MediaInfo(iconLocation, source, name, data.subtitle()));
        }
        PLAYLIST.addAll(newPlaylist);
    }

    public static void update() {
        if (isSeeking || (decoderPtr == 0 && !isStoppingAfterFadeOut)) return;

        if (fadeState != FadeState.NONE) {
            handleFade();
        }

        if (!isPlaying.get() || isPaused.get()) {
            return;
        }

        swapBuffers();

        if (AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            int queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
            if (queued > 0) {
                AL10.alSourcePlay(alSource);
            } else if (isPlaying.get()) {
                boolean hasMoreData = false;
                for (int bufferId : alBuffers) {
                    if (forward(bufferId)) {
                        hasMoreData = true;
                    }
                }

                if (hasMoreData) {
                    AL10.alSourcePlay(alSource);
                } else {
                    playNext();
                }
            }
        }
    }

    private static void initializeAudio() {
        if (alSource != -1) return;
        alSource = AL10.alGenSources();
        AL10.alGenBuffers(alBuffers);
        AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
    }

    private static void performStop() {
        if (alSource != -1 && AL10.alIsSource(alSource)) {
            AL10.alSourceStop(alSource);
            var queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
            if (queued > 0) {
                var tempBuffers = new int[queued];
                AL10.alSourceUnqueueBuffers(alSource, tempBuffers);
            }
        }

        if (decoderPtr != 0) {
            AudioDecoderJNI.decoder_uninit(decoderPtr);
            decoderPtr = 0;
        }
        if (pcmBuffer != null) {
            MemoryUtil.memFree(pcmBuffer);
            pcmBuffer = null;
        }

        audioDataBuffer = null;
        isPlaying.set(false);
        isPaused.set(false);
        fadeState = FadeState.NONE;
        baseSampleOffset = 0;
    }

    public static void play(int trackIndex) {
        initializeAudio();
        performStop();

        if (trackIndex < 0 || trackIndex >= PLAYLIST.size()) {
            currentTrackIndex = -1;
            return;
        }
        currentTrackIndex = trackIndex;
        var mediaInfo = PLAYLIST.get(currentTrackIndex);

        try {
            audioDataBuffer = mediaInfo.source().getData();
            decoderPtr = AudioDecoderJNI.decoder_init_memory(audioDataBuffer);
            if (decoderPtr == 0) {
                throw new IOException("Failed to initialize decoder for " + mediaInfo.name());
            }

            channels = AudioDecoderJNI.decoder_get_channels(decoderPtr);
            sampleRate = AudioDecoderJNI.decoder_get_sample_rate(decoderPtr);
            format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            totalDuration = (float) AudioDecoderJNI.decoder_get_length_in_pcm_frames(decoderPtr) / sampleRate;

            int pcmBufferSizeInFrames = sampleRate / 2;
            pcmBuffer = MemoryUtil.memAlloc(pcmBufferSizeInFrames * channels * 2);

            startPlaybackAtFrame(0, true);

        } catch (IOException e) {
            AcademyCraft.LOGGER.error("Failed to play media: {}", mediaInfo.name(), e);
            stop();
        }
    }

    private static void startPlaybackAtFrame(long frame, boolean shouldFadeIn) {
        AudioDecoderJNI.decoder_seek_to_pcm_frame(decoderPtr, frame);
        baseSampleOffset = frame;

        for (var bufferId : alBuffers) {
            forward(bufferId);
        }

        AL10.alSourcePlay(alSource);
        isPlaying.set(true);
        isPaused.set(false);

        if (shouldFadeIn) {
            AL10.alSourcef(alSource, AL10.AL_GAIN, 0f);
            fadeState = FadeState.FADING_IN;
            fadeStartTime = System.nanoTime();
        } else {
            AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
            fadeState = FadeState.NONE;
        }
    }

    public static void stop() {
        initializeAudio();
        if (!isPlaying.get() && !isPaused.get()) return;
        isStoppingAfterFadeOut = true;
        fadeState = FadeState.FADING_OUT;
        fadeStartTime = System.nanoTime();
    }

    public static void togglePlayPause() {
        initializeAudio();
        if (!isPlaying.get()) {
            play(currentTrackIndex != -1 ? currentTrackIndex : 0);
            return;
        }

        isPaused.set(!isPaused.get());

        if (isPaused.get()) {
            AL10.alSourcePause(alSource);
        } else {
            AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
            AL10.alSourcePlay(alSource);
        }
        fadeState = FadeState.NONE;
    }

    public static void playNext() {
        if (PLAYLIST.isEmpty()) return;

        var nextIndex = switch (playbackMode) {
            case REPEAT_ONE -> currentTrackIndex;
            case SHUFFLE -> {
                if (shuffledPlaylist.isEmpty() || ++shuffleIndex >= shuffledPlaylist.size()) {
                    generateShuffledPlaylist();
                }
                yield shuffledPlaylist.isEmpty() ? -1 : shuffledPlaylist.get(shuffleIndex);
            }
            case REPEAT_LIST -> (currentTrackIndex + 1) % PLAYLIST.size();
        };

        if (nextIndex != -1) {
            play(nextIndex);
        } else {
            stop();
        }
    }

    public static void playPrevious() {
        if (PLAYLIST.isEmpty()) return;

        var prevIndex = switch (playbackMode) {
            case REPEAT_ONE -> currentTrackIndex;
            case SHUFFLE -> {
                if (shuffledPlaylist.isEmpty()) {
                    generateShuffledPlaylist();
                }
                if (shuffledPlaylist.isEmpty()) {
                    yield -1;
                }
                shuffleIndex--;
                if (shuffleIndex < 0) {
                    shuffleIndex = shuffledPlaylist.size() - 1;
                }
                yield shuffledPlaylist.get(shuffleIndex);
            }
            case REPEAT_LIST -> (currentTrackIndex - 1 + PLAYLIST.size()) % PLAYLIST.size();
        };

        if (prevIndex != -1) {
            play(prevIndex);
        }
    }

    public static void seek(float timeRatio) {
        if (decoderPtr == 0 || sampleRate == 0 || isSeeking) {
            return;
        }

        isSeeking = true;
        AL10.alSourceStop(alSource);

        var queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
        if (queued > 0) {
            var tempBuffers = new int[queued];
            AL10.alSourceUnqueueBuffers(alSource, tempBuffers);
        }

        long targetFrame = (long) (timeRatio * totalDuration * sampleRate);
        baseSampleOffset = targetFrame;

        AcademyCraft.executorService.submit(() -> {
            try {
                AudioDecoderJNI.decoder_seek_to_pcm_frame(decoderPtr, targetFrame);
            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Error during background seek", e);
            } finally {
                isSeeking = false;
            }
        });
    }

    public static void setVolume(float value) {
        volume = value;
        if (fadeState != FadeState.NONE) return;
        if (alSource != -1) {
            AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
        }
    }

    public static void cyclePlaybackMode() {
        playbackMode = PlaybackMode.values()[(playbackMode.ordinal() + 1) % PlaybackMode.values().length];
        if (playbackMode == PlaybackMode.SHUFFLE && (shuffledPlaylist.isEmpty() || currentTrackIndex == -1)) {
            generateShuffledPlaylist();
        }
    }

    private static void generateShuffledPlaylist() {
        shuffledPlaylist.clear();
        if (PLAYLIST.isEmpty()) return;
        for (var i = 0; i < PLAYLIST.size(); i++) {
            shuffledPlaylist.add(i);
        }
        Collections.shuffle(shuffledPlaylist, new Random());
        if (currentTrackIndex != -1) {
            int currentItemPos = shuffledPlaylist.indexOf(currentTrackIndex);
            if (currentItemPos != -1) {
                Collections.swap(shuffledPlaylist, 0, currentItemPos);
            }
        }
        shuffleIndex = 0;
    }

    private static void handleFade() {
        var elapsedNanos = System.nanoTime() - fadeStartTime;
        var progress = Math.min(1.0f, elapsedNanos / (FADE_DURATION_SECONDS * 1_000_000_000.0f));

        if (fadeState == FadeState.FADING_IN) {
            AL10.alSourcef(alSource, AL10.AL_GAIN, volume * progress);
            if (progress >= 1.0f) {
                fadeState = FadeState.NONE;
            }
        } else if (fadeState == FadeState.FADING_OUT) {
            AL10.alSourcef(alSource, AL10.AL_GAIN, volume * (1.0f - progress));
            if (progress >= 1.0f) {
                fadeState = FadeState.NONE;
                if (isStoppingAfterFadeOut) {
                    performStop();
                    isStoppingAfterFadeOut = false;
                }
            }
        }
    }

    private static void swapBuffers() {
        var count = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_PROCESSED);

        for (var i = 0; i < count; i++) {
            var bufferId = AL10.alSourceUnqueueBuffers(alSource);
            if (channels > 0) {
                var bytesInBuf = AL10.alGetBufferi(bufferId, AL10.AL_SIZE);
                var framesInBuf = bytesInBuf / (channels * 2);
                baseSampleOffset += framesInBuf;
            }
            forward(bufferId);
        }
    }

    private static boolean forward(int bufferId) {
        if (decoderPtr == 0) return false;

        pcmBuffer.clear();
        long framesRead = AudioDecoderJNI.decoder_read_into_buffer(decoderPtr, pcmBuffer, pcmBuffer.capacity() / (channels * 2));

        if (framesRead > 0) {
            pcmBuffer.limit((int) (framesRead * channels * 2));
            AL10.alBufferData(bufferId, format, pcmBuffer.slice(), sampleRate);
            AL10.alSourceQueueBuffers(alSource, bufferId);
            return true;
        }
        return false;
    }

    public static float getCurrentTime() {
        if (alSource == -1 || !AL10.alIsSource(alSource) || sampleRate == 0) {
            return 0;
        }
        return ((float) baseSampleOffset / sampleRate) + AL10.alGetSourcef(alSource, AL11.AL_SEC_OFFSET);
    }

    public static List<MediaInfo> getPlaylist() {
        return Collections.unmodifiableList(PLAYLIST);
    }

    public static boolean isPlaying() {
        return isPlaying.get();
    }

    public static boolean isPaused() {
        return isPaused.get();
    }

    public static float getTotalDuration() {
        return totalDuration;
    }

    public static PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    public static int getCurrentTrackIndex() {
        return currentTrackIndex;
    }

    public record MediaInfo(ResourceLocation icon, MediaSource source, String name, String subtitle) {
    }

    public record MediaSource(Object path) {
        public static MediaSource fromResourceLocation(ResourceLocation location) {
            return new MediaSource(location);
        }

        public static MediaSource fromAbsolutePath(String absolutePath) {
            return new MediaSource(absolutePath);
        }

        public ByteBuffer getData() throws IOException {
            byte[] bytes;
            if (path instanceof ResourceLocation location) {
                try (InputStream stream = Minecraft.getInstance().getResourceManager().open(location)) {
                    bytes = stream.readAllBytes();
                }
            } else if (path instanceof String strPath) {
                Path filePath = Path.of(strPath);
                bytes = Files.readAllBytes(filePath);
            } else {
                throw new IOException("Unsupported media source type: " + path.getClass().getName());
            }

            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        }
    }
}