package org.academy.internal.client.app.music;

import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPauseChangeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.academy.AcademyCraft.academy;

@EventBusSubscriber(Dist.CLIENT)
public final class MusicPlayerBackend {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final int BUFFER_COUNT = 4;
    private static final List<MusicInfo> PLAYLIST = new ArrayList<>();
    public static final Type MUSIC_DATA_MAP_TYPE = new TypeToken<Map<String, MusicData>>() {
    }.getType();

    private static int alSource = -1;
    private static final int[] alBuffers = new int[BUFFER_COUNT];
    private static long stbVorbisHandle = 0;
    private static STBVorbisInfo stbVorbisInfo = null;
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

    public enum PlaybackMode {
        REPEAT_LIST, REPEAT_ONE, SHUFFLE
    }

    private static PlaybackMode playbackMode = PlaybackMode.REPEAT_LIST;
    private static final List<Integer> shuffledPlaylist = new ArrayList<>();
    private static int shuffleIndex = -1;


    private MusicPlayerBackend() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(academy("music_loader"), new MusicLoader());
    }

    @SubscribeEvent
    public static void onClientPauseChangePost(ClientPauseChangeEvent.Post event) {
        if (event.isPaused()) stop();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Pre event) {
        update();
    }

    public static void handleContextReset() {
        LOGGER.info("Sound engine reloaded, resetting media player OpenAL resources.");

        isPlaying.set(false);
        isPaused.set(false);
        isSeeking = false;

        if (stbVorbisInfo != null) {
            stbVorbisInfo.free();
            stbVorbisInfo = null;
        }
        if (stbVorbisHandle != 0) {
            STBVorbis.stb_vorbis_close(stbVorbisHandle);
            stbVorbisHandle = 0;
        }
        if (pcmBuffer != null) {
            MemoryUtil.memFree(pcmBuffer);
            pcmBuffer = null;
        }
        audioDataBuffer = null;
        baseSampleOffset = 0;

        alSource = -1;
        Arrays.fill(alBuffers, 0);
    }

    public static void updatePlaylistFromData(Map<String, MusicData> musicMap, String sourceDescription) {
        performStop();
        PLAYLIST.clear();
        shuffledPlaylist.clear();
        currentTrackIndex = -1;
        shuffleIndex = -1;

        if (musicMap == null) return;

        var newPlaylist = new ArrayList<MusicInfo>();
        for (var entry : musicMap.entrySet()) {
            var name = entry.getKey();
            var data = entry.getValue();

            Identifier iconLocation;
            try {
                iconLocation = Identifier.parse(data.icon());
            } catch (Exception e) {
                LOGGER.error(
                        "Invalid icon Identifier '{}' for entry '{}' in {}{}",
                        data.icon(), name, sourceDescription, e
                );
                continue;
            }

            var sourcePath = data.source();
            if (sourcePath == null) {
                LOGGER.error("Missing source for entry '{}' in {}", name, sourceDescription);
                continue;
            }

            if (sourcePath.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
                LOGGER.error(
                        "Unsupported format: MP3 files are not supported. Skipping entry '{}' with source '{}' in {}",
                        name, sourcePath, sourceDescription
                );
                continue;
            }

            MusicSource source;
            var sourceTypeStr = data.source_type();
            if (sourceTypeStr == null) {
                LOGGER.error("Missing source_type for entry '{}' in {}", name, sourceDescription);
                continue;
            }

            try {
                if (sourceTypeStr.equalsIgnoreCase("RESOURCE_LOCATION")) {
                    source = MusicSource.fromIdentifier(Identifier.parse(sourcePath));
                } else if (sourceTypeStr.equalsIgnoreCase("PATH")) {
                    source = MusicSource.fromAbsolutePath(sourcePath);
                } else {
                    LOGGER.error("Invalid source_type '{}' for entry '{}' in {}", sourceTypeStr, name, sourceDescription);
                    continue;
                }
            } catch (Exception e) {
                LOGGER.error("Invalid source path '{}' for entry '{}' in {}", sourcePath, name, sourceDescription);
                continue;
            }

            newPlaylist.add(new MusicInfo(iconLocation, source, name, data.subtitle()));
        }
        PLAYLIST.addAll(newPlaylist);
    }

    public static void update() {
        if (isSeeking || stbVorbisHandle == 0) return;

        if (!isPlaying.get() || isPaused.get()) return;

        swapBuffers();

        if (AL11.alGetSourcei(alSource, AL11.AL_SOURCE_STATE) != AL11.AL_PLAYING) {
            var queued = AL11.alGetSourcei(alSource, AL11.AL_BUFFERS_QUEUED);
            if (queued > 0) AL11.alSourcePlay(alSource);
            else if (isPlaying.get()) {
                var hasMoreData = false;
                for (var bufferId : alBuffers) if (forward(bufferId)) hasMoreData = true;

                if (hasMoreData) AL11.alSourcePlay(alSource);
                else playNext();
            }
        }
    }

    private static void initializeAudio() {
        if (alSource != -1) return;
        alSource = AL11.alGenSources();
        AL11.alGenBuffers(alBuffers);
        AL11.alSourcef(alSource, AL11.AL_GAIN, volume);
    }

    private static void performStop() {
        if (alSource != -1 && AL11.alIsSource(alSource)) {
            AL11.alSourceStop(alSource);
            var queued = AL11.alGetSourcei(alSource, AL11.AL_BUFFERS_QUEUED);
            if (queued > 0) {
                var tempBuffers = new int[queued];
                AL11.alSourceUnqueueBuffers(alSource, tempBuffers);
            }
        }

        if (stbVorbisInfo != null) {
            stbVorbisInfo.free();
            stbVorbisInfo = null;
        }
        if (stbVorbisHandle != 0) {
            STBVorbis.stb_vorbis_close(stbVorbisHandle);
            stbVorbisHandle = 0;
        }

        if (pcmBuffer != null) {
            MemoryUtil.memFree(pcmBuffer);
            pcmBuffer = null;
        }

        audioDataBuffer = null;
        isPlaying.set(false);
        isPaused.set(false);
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

            try (var stack = MemoryStack.stackPush()) {
                var error = stack.mallocInt(1);
                stbVorbisHandle = STBVorbis.stb_vorbis_open_memory(audioDataBuffer, error, null);
                if (stbVorbisHandle == 0) {
                    throw new IOException("Failed to open Ogg Vorbis stream, error: " + error.get(0));
                }
                stbVorbisInfo = STBVorbisInfo.malloc();
                STBVorbis.stb_vorbis_get_info(stbVorbisHandle, stbVorbisInfo);
            }

            channels = stbVorbisInfo.channels();
            sampleRate = stbVorbisInfo.sample_rate();
            format = channels == 1 ? AL11.AL_FORMAT_MONO16 : AL11.AL_FORMAT_STEREO16;
            totalDuration = (float) STBVorbis.stb_vorbis_stream_length_in_samples(stbVorbisHandle) / sampleRate;

            var pcmBufferSizeInFrames = sampleRate / 2;
            pcmBuffer = MemoryUtil.memAlloc(pcmBufferSizeInFrames * channels * 2);

            startPlaybackAtFrame(0);

        } catch (IOException e) {
            LOGGER.error("Failed to play media: {}", mediaInfo.name(), e);
            stop();
        }
    }

    private static void startPlaybackAtFrame(long frame) {
        STBVorbis.stb_vorbis_seek_frame(stbVorbisHandle, (int) frame);
        baseSampleOffset = frame;

        for (var bufferId : alBuffers) forward(bufferId);

        AL11.alSourcePlay(alSource);
        isPlaying.set(true);
        isPaused.set(false);
        AL11.alSourcef(alSource, AL11.AL_GAIN, volume);
    }

    public static void stop() {
        initializeAudio();
        performStop();
    }

    public static void togglePlayPause() {
        initializeAudio();
        if (!isPlaying.get()) return;

        isPaused.set(!isPaused.get());

        if (isPaused.get())
            AL11.alSourcePause(alSource);
        else {
            AL11.alSourcef(alSource, AL11.AL_GAIN, volume);
            AL11.alSourcePlay(alSource);
        }
    }

    public static void playNext() {
        if (PLAYLIST.isEmpty()) return;

        var nextIndex = switch (playbackMode) {
            case REPEAT_ONE -> currentTrackIndex;
            case SHUFFLE -> {
                if (shuffledPlaylist.isEmpty() || ++shuffleIndex >= shuffledPlaylist.size()) generateShuffledPlaylist();
                yield shuffledPlaylist.isEmpty() ? -1 : shuffledPlaylist.get(shuffleIndex);
            }
            case REPEAT_LIST -> (currentTrackIndex + 1) % PLAYLIST.size();
        };

        if (nextIndex != -1) play(nextIndex);
        else stop();
    }

    public static void playPrevious() {
        if (PLAYLIST.isEmpty()) return;

        var prevIndex = switch (playbackMode) {
            case REPEAT_ONE -> currentTrackIndex;
            case SHUFFLE -> {
                if (shuffledPlaylist.isEmpty()) generateShuffledPlaylist();
                if (shuffledPlaylist.isEmpty()) yield -1;
                shuffleIndex--;
                if (shuffleIndex < 0) shuffleIndex = shuffledPlaylist.size() - 1;
                yield shuffledPlaylist.get(shuffleIndex);
            }
            case REPEAT_LIST -> (currentTrackIndex - 1 + PLAYLIST.size()) % PLAYLIST.size();
        };

        if (prevIndex != -1) {
            play(prevIndex);
        }
    }

    public static void seek(float timeRatio) {
        if (stbVorbisHandle == 0 || sampleRate == 0 || isSeeking) return;

        isSeeking = true;
        AL11.alSourceStop(alSource);

        var queued = AL11.alGetSourcei(alSource, AL11.AL_BUFFERS_QUEUED);
        if (queued > 0) {
            var tempBuffers = new int[queued];
            AL11.alSourceUnqueueBuffers(alSource, tempBuffers);
        }

        var targetFrame = (long) (timeRatio * totalDuration * sampleRate);
        baseSampleOffset = targetFrame;

        AcademyCraft.EXECUTOR_SERVICE.submit(() -> {
            try {
                STBVorbis.stb_vorbis_seek_frame(stbVorbisHandle, (int) targetFrame);
            } catch (Exception e) {
                LOGGER.error("Error during background seek", e);
            } finally {
                isSeeking = false;
            }
        });
    }

    public static void setVolume(float value) {
        volume = value;
        if (alSource != -1) AL11.alSourcef(alSource, AL11.AL_GAIN, volume);
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
        for (var i = 0; i < PLAYLIST.size(); i++) shuffledPlaylist.add(i);
        Collections.shuffle(shuffledPlaylist, new Random());
        if (currentTrackIndex != -1) {
            var currentItemPos = shuffledPlaylist.indexOf(currentTrackIndex);
            if (currentItemPos != -1) Collections.swap(shuffledPlaylist, 0, currentItemPos);
        }
        shuffleIndex = 0;
    }

    private static void swapBuffers() {
        var count = AL11.alGetSourcei(alSource, AL11.AL_BUFFERS_PROCESSED);

        for (var i = 0; i < count; i++) {
            var bufferId = AL11.alSourceUnqueueBuffers(alSource);
            if (channels > 0) {
                var bytesInBuf = AL11.alGetBufferi(bufferId, AL11.AL_SIZE);
                var framesInBuf = bytesInBuf / (channels * 2);
                baseSampleOffset += framesInBuf;
            }
            forward(bufferId);
        }
    }

    private static boolean forward(int bufferId) {
        if (stbVorbisHandle == 0) return false;

        pcmBuffer.clear();
        pcmBuffer.order(ByteOrder.nativeOrder());
        var shortBuffer = pcmBuffer.asShortBuffer();

        var framesToRead = pcmBuffer.capacity() / (channels * 2);
        var maxSamplesPerChannel = Math.min(framesToRead, shortBuffer.capacity() / channels);
        shortBuffer.limit(maxSamplesPerChannel * channels);

        var samplesReadPerChannel = STBVorbis.stb_vorbis_get_samples_short_interleaved(stbVorbisHandle, channels, shortBuffer);

        if (samplesReadPerChannel > 0) {
            pcmBuffer.position(0);
            pcmBuffer.limit(samplesReadPerChannel * channels * 2);
            AL11.alBufferData(bufferId, format, pcmBuffer, sampleRate);
            AL11.alSourceQueueBuffers(alSource, bufferId);
            return true;
        }
        return false;
    }

    public static float getCurrentTime() {
        if (alSource == -1 || !AL11.alIsSource(alSource) || sampleRate == 0) return 0;
        return ((float) baseSampleOffset / sampleRate) + AL11.alGetSourcef(alSource, AL11.AL_SEC_OFFSET);
    }

    public static List<MusicInfo> getPlaylist() {
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

    public static float getVolume() {
        return volume;
    }
}