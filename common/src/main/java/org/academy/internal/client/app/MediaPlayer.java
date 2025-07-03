package org.academy.internal.client.app;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.hud.DataTerminalHUD;
import org.academy.api.client.renderer.RenderTypes;
import org.academy.api.client.resource.TextureResources;
import org.academy.api.client.util.RenderStateUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.vanilla.ClientTickEvent;
import org.joml.Matrix4f;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaPlayer implements DataTerminalHUD.App {
    private static final float MEDIA_ICON_SIZE = 20f;
    private static final float MEDIA_HEIGHT = 30f;
    private static final float MARGIN_MEDIA_ICON = 10f;
    public static final DataTerminalHUD.App INSTANCE = new MediaPlayer();
    private static final int BUFFER_COUNT = 2;

    private static final List<MediaInfo> PLAYLIST = new ArrayList<>();

    private static int alSource = -1;
    private static final int[] alBuffers = new int[BUFFER_COUNT];
    private static long vorbisHandle = 0;
    private static ByteBuffer oggDataBuffer;
    private static ShortBuffer clientBuffer;
    private static int baseSampleOffset = 0;

    private static final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private static final AtomicBoolean isPaused = new AtomicBoolean(false);

    private static int currentTrackIndex = -1;
    private static float totalDuration = 0f;
    private static float volume = 1.0f;
    private static int sampleRate = 0;
    private static int format = 0;
    private static int channels = 0;

    private static volatile boolean isProgrammaticallyUpdatingProgressBar = false;

    private static HorizontalSliderWidget progressBar;
    private static LabelWidget timeLabel;
    private static PanelWidget rootPanel;
    private static GeometricButtonWidget playPauseButton;

    public enum PlaybackMode {
        REPEAT_LIST,
        REPEAT_ONE,
        SHUFFLE,
        NORMAL
    }

    public enum ButtonShape {PLAY, PAUSE, NEXT, PREV}

    private static PlaybackMode playbackMode = PlaybackMode.REPEAT_LIST;
    private static final List<Integer> shuffledPlaylist = new ArrayList<>();
    private static int shuffleIndex = -1;

    private enum FadeState {NONE, FADING_IN, FADING_OUT}

    private static FadeState fadeState = FadeState.NONE;
    private static long fadeStartTime;
    private static final float FADE_DURATION_SECONDS = 0.75f;
    private static boolean isStoppingAfterFadeOut = false;

    static {
        PLAYLIST.add(new MediaInfo(TextureResources.TEXTURE_ICON_NODE, new ResourceLocation("minecraft:sounds/music/game/creative/creative1.ogg"), "Creative 1", "C418"));
        PLAYLIST.add(new MediaInfo(TextureResources.TEXTURE_ICON_NODE, new ResourceLocation("minecraft:sounds/music/game/calm1.ogg"), "Calm 1", "C418"));
    }

    private static void initializeAudio() {
        if (alSource != -1) return;
        alSource = AL10.alGenSources();
        AL10.alGenBuffers(alBuffers);
        AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
        AcademyCraft.EVENT_BUS.register(MediaPlayer.class);
    }

    private static float getCurrentTime() {
        if (alSource == -1 || !AL10.alIsSource(alSource) || sampleRate == 0) {
            return 0;
        }
        return ((float) baseSampleOffset / sampleRate) + AL10.alGetSourcef(alSource, AL11.AL_SEC_OFFSET);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (rootPanel == null || !rootPanel.isVisible() || sampleRate == 0) return;

        if (fadeState != FadeState.NONE) {
            handleFade();
        }

        if (!isPlaying.get() || isPaused.get()) {
            return;
        }

        swapBuffers();

        if (AL10.alGetSourcei(alSource, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(alSource);
        }

        var offset = getCurrentTime();

        if (progressBar != null && !progressBar.startDragging) {
            isProgrammaticallyUpdatingProgressBar = true;
            progressBar.setValue(totalDuration > 0 ? offset / totalDuration : 0f);
            isProgrammaticallyUpdatingProgressBar = false;
        }

        if (timeLabel != null) {
            var totalSeconds = (int) totalDuration;
            var currentSeconds = (int) offset;
            timeLabel.value = String.format("%02d:%02d / %02d:%02d",
                    currentSeconds / 60, currentSeconds % 60,
                    totalSeconds / 60, totalSeconds % 60);
        }

        if (totalDuration > 0 && offset >= totalDuration - 0.1f) {
            playNext();
        }
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


    private static void generateShuffledPlaylist() {
        shuffledPlaylist.clear();
        if (PLAYLIST.isEmpty()) return;
        for (int i = 0; i < PLAYLIST.size(); i++) {
            shuffledPlaylist.add(i);
        }
        Collections.shuffle(shuffledPlaylist, new Random());
        if (shuffledPlaylist.size() > 1 && currentTrackIndex != -1 && shuffledPlaylist.get(0) == currentTrackIndex) {
            Collections.swap(shuffledPlaylist, 0, 1);
        }
        shuffleIndex = 0;
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

        if (vorbisHandle != 0) {
            STBVorbis.stb_vorbis_close(vorbisHandle);
            vorbisHandle = 0;
        }
        if (oggDataBuffer != null) {
            MemoryUtil.memFree(oggDataBuffer);
            oggDataBuffer = null;
        }
        if (clientBuffer != null) {
            MemoryUtil.memFree(clientBuffer);
            clientBuffer = null;
        }

        isPlaying.set(false);
        isPaused.set(false);
        baseSampleOffset = 0;
        fadeState = FadeState.NONE;

        updatePlayPauseButton();
    }

    private PanelWidget createModeCycleButton(float x) {
        var modeButton = new PanelWidget(x, (float) 0, (float) 20.0, (float) 20.0);
        {
            var button = new ImageButtonWidget(0, 0, (float) 20.0, (float) 20.0, null, MediaPlayer::cyclePlaybackMode);
            modeButton.addChild("button", button);

            var modeBack = new BlendQuadWidget(0, 0, (float) 20.0, (float) 20.0);
            modeBack.drawLine = false;
            modeBack.alpha = 0.4f;
            modeButton.addChild("back", modeBack);

            var layered = new LayeredPanelWidget(0, 0, (float) 20.0, (float) 20.0);
            modeButton.addChild("layered", layered);
            {
                var modeLabel = new AutoScaleLabelWidget("", 0, 0, (float) 20.0, true);
                modeLabel.dropShadow = false;
                modeLabel.setY(((float) 20.0 - modeLabel.getHeight()) / 2f);
                layered.addChild("label", modeLabel);
            }
        }
        return modeButton;
    }

    private static void playNext() {
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
            case NORMAL -> (currentTrackIndex < PLAYLIST.size() - 1) ? currentTrackIndex + 1 : -1;
        };

        if (nextIndex != -1) {
            play(nextIndex);
        } else {
            stop();
        }
    }

    private static void playPrevious() {
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
            case REPEAT_LIST, NORMAL -> (currentTrackIndex - 1 + PLAYLIST.size()) % PLAYLIST.size();
        };

        if (prevIndex != -1) {
            play(prevIndex);
        }
    }

    private static void seek(float timeRatio) {
        if (isProgrammaticallyUpdatingProgressBar || vorbisHandle == 0 || sampleRate == 0) return;

        var wasPlaying = isPlaying.get() && !isPaused.get();
        if (wasPlaying) {
            AL10.alSourceStop(alSource);
        }

        var queued = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_QUEUED);
        if (queued > 0) {
            var tempBuffers = new int[queued];
            AL10.alSourceUnqueueBuffers(alSource, tempBuffers);
        }

        var sampleOffset = (int) ((timeRatio * totalDuration) * sampleRate);
        STBVorbis.stb_vorbis_seek(vorbisHandle, sampleOffset);
        baseSampleOffset = sampleOffset;

        for (var bufferId : alBuffers) {
            forward(bufferId);
        }

        if (wasPlaying) {
            AL10.alSourcePlay(alSource);
        }
    }

    private static void setVolume(float value) {
        volume = value;
        if (fadeState != FadeState.NONE) return;
        if (alSource != -1) {
            AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
        }
    }

    private static void swapBuffers() {
        var count = AL10.alGetSourcei(alSource, AL10.AL_BUFFERS_PROCESSED);

        for (var i = 0; i < count; i++) {
            var bufferId = AL10.alSourceUnqueueBuffers(alSource);
            var samplesInBuf = AL10.alGetBufferi(bufferId, AL10.AL_SIZE) / (channels * 2);
            baseSampleOffset += samplesInBuf;

            forward(bufferId);
        }
    }

    private static void forward(int bufferId) {
        var samplesRead = 0;
        final var maxSamples = clientBuffer.capacity();

        while (samplesRead < maxSamples) {
            clientBuffer.position(samplesRead);
            var read = STBVorbis.stb_vorbis_get_samples_short_interleaved(vorbisHandle, channels, clientBuffer);
            if (read == 0) {
                break;
            }
            samplesRead += read * channels;
        }

        if (samplesRead > 0) {
            clientBuffer.position(0).limit(samplesRead);
            AL10.alBufferData(bufferId, format, clientBuffer, sampleRate);
            clientBuffer.clear();
            AL10.alSourceQueueBuffers(alSource, bufferId);
        }

    }

    private PanelWidget create() {
        rootPanel = new PanelWidget(0, 0, 150f, 200f);
        var width = 150f;
        var height = 200f;
        {
            var back = new BlendQuadWidget(0, 0, width, height);
            back.drawLine = false;
            back.alpha = 0.25f;
            rootPanel.addChild("back", back);

            var main = new LayeredPanelWidget(0, 0, width, height);
            rootPanel.addChild("main", main);
            {
                var dockBarHeight = 60f;
                var mediaListScrollBarWidth = 4f;
                var margin = 2f;
                var mediaList = new ScrollPanelWidget(0, 0, width, height - dockBarHeight);
                main.addChild("list_media", mediaList);
                {
                    for (int i = 0; i < PLAYLIST.size(); i++) {
                        var info = PLAYLIST.get(i);
                        final var trackIndex = i;
                        var mediaWidget = createMediaWidget(info, i * (MEDIA_HEIGHT + margin), () -> play(trackIndex));
                        mediaList.addChild("media_" + i, mediaWidget);
                    }
                }

                var mediaListScrollBar = new VerticalScrollBarWidget(
                        mediaList,
                        mediaList.getWidth() - mediaListScrollBarWidth - margin, 0,
                        mediaListScrollBarWidth, mediaList.getHeight()
                );
                mediaListScrollBar.setThumbColor(0x20AAAAAA);
                mediaListScrollBar.setTrackColor(0x10202020);
                main.addChild("bar_list_media", mediaListScrollBar);

                final var dockBarPadding = 5f;
                var dockBar = new PanelWidget(0, height - dockBarHeight, width, dockBarHeight);
                main.addChild("bar_dock", dockBar);
                {
                    var dockBarBack = new BlendQuadWidget(0, 0, width, dockBarHeight);
                    dockBarBack.drawLine = false;
                    dockBarBack.alpha = 0.25f;
                    dockBar.addChild("back", dockBarBack);

                    var layered = new LayeredPanelWidget(dockBarPadding, dockBarPadding, width - dockBarPadding * 2, dockBarHeight - dockBarPadding * 2);
                    dockBar.addChild("layered", layered);
                    {
                        final var progressBarHeight = 5f;
                        progressBar = new HorizontalSliderWidget(0, 0, layered.getWidth(), progressBarHeight, 0f, 1f, 0f);
                        progressBar.onValueChanged = MediaPlayer::seek;
                        layered.addChild("progress_bar", progressBar);

                        final var timeLabelY = progressBar.getY() + progressBar.getHeight() + 2f;
                        timeLabel = new AutoScaleLabelWidget("00:00 / 00:00", 0, timeLabelY, layered.getWidth(), true);
                        timeLabel.scale = 0.7f;
                        timeLabel.dropShadow = false;
                        layered.addChild("time_label", timeLabel);

                        final var controlsY = timeLabel.getY() + timeLabel.getHeight() + 5f;
                        final var btnSize = 20f;
                        final var sliderWidth = 3f;
                        final var smallGap = 5f;
                        final var bigGap = 15f;
                        final var controlPanel = new PanelWidget(0, controlsY, layered.getWidth(), btnSize);
                        layered.addChild("control_panel", controlPanel);
                        {
                            final var totalControlsWidth = btnSize * 4 + sliderWidth + smallGap * 2 + bigGap * 2;
                            var currentX = (controlPanel.getWidth() - totalControlsWidth) / 2;

                            final var prevButton = new GeometricButtonWidget(currentX, 0, btnSize, btnSize, ButtonShape.PREV, MediaPlayer::playPrevious);
                            controlPanel.addChild("prev", prevButton);
                            currentX += btnSize + smallGap;

                            playPauseButton = new GeometricButtonWidget(currentX, 0, btnSize, btnSize, isPlaying.get() && !isPaused.get() ? ButtonShape.PAUSE : ButtonShape.PLAY, MediaPlayer::togglePlayPause);
                            controlPanel.addChild("play_pause", playPauseButton);
                            currentX += btnSize + smallGap;

                            var nextButton = new GeometricButtonWidget(currentX, 0, btnSize, btnSize, ButtonShape.NEXT, MediaPlayer::playNext);
                            controlPanel.addChild("next", nextButton);
                            currentX += btnSize + bigGap;

                            var modeButton = createModeCycleButton(currentX);
                            controlPanel.addChild("mode_button", modeButton);
                            currentX += btnSize + bigGap;

                            var volumeSlider = new VerticalSliderWidget(currentX, 0, sliderWidth, btnSize, 0f, 1f, volume) {
                                @Override
                                protected float getThumbSize() {
                                    return 3f;
                                }
                            };
                            volumeSlider.onValueChanged = MediaPlayer::setVolume;
                            controlPanel.addChild("volume_slider", volumeSlider);
                        }
                    }
                }
            }
        }
        updateModeButtonLabel();
        return rootPanel;
    }

    private static void cyclePlaybackMode() {
        playbackMode = PlaybackMode.values()[(playbackMode.ordinal() + 1) % PlaybackMode.values().length];
        if (playbackMode == PlaybackMode.SHUFFLE && shuffledPlaylist.isEmpty()) {
            generateShuffledPlaylist();
        }
        updateModeButtonLabel();
    }

    private static void updateModeButtonLabel() {
        if (rootPanel == null) return;
        var layered = rootPanel.<PanelWidget>getChildUnSafe("main")
                .<PanelWidget>getChildUnSafe("bar_dock")
                .<PanelWidget>getChildUnSafe("layered")
                .<PanelWidget>getChildUnSafe("control_panel")
                .<PanelWidget>getChildUnSafe("mode_button")
                .<LayeredPanelWidget>getChildUnSafe("layered");
        var label = layered.<AutoScaleLabelWidget>getChildUnSafe("label");
        label.setText(switch (playbackMode) {
            case NORMAL -> "N";
            case REPEAT_LIST -> "RL";
            case REPEAT_ONE -> "R1";
            case SHUFFLE -> "S";
        });
    }

    private PanelWidget createMediaWidget(MediaInfo mediaInfo, float y, Runnable onClick) {
        var root = new PanelWidget(0, y, 150f - 6f, MEDIA_HEIGHT);
        {
            var button = new ImageButtonWidget(0, 0, root.getWidth(), root.getHeight(), null, onClick);
            root.addChild("button", button);

            var back = new BlendQuadWidget(0, 0, root.getWidth(), root.getHeight());
            back.drawLine = false;
            back.alpha = 0.25f;
            root.addChild("back", back);

            var main = new LayeredPanelWidget(0, 0, root.getWidth(), root.getHeight());
            main.setEnabled(false);
            root.addChild("main", main);
            {
                var iconRenderType = RenderUtil.getPositionColorTexRenderType(mediaInfo.name(), mediaInfo.icon(), false);
                var icon = new ImageWidget(
                        MARGIN_MEDIA_ICON, (MEDIA_HEIGHT - MEDIA_ICON_SIZE) / 2,
                        MEDIA_ICON_SIZE, MEDIA_ICON_SIZE, iconRenderType
                );
                main.addChild("icon", icon);

                var name = new AutoScaleLabelWidget(
                        mediaInfo.name(),
                        icon.getX() + MEDIA_ICON_SIZE + 5, 4,
                        80f, true
                );
                name.scale = 0.8f;
                name.dropShadow = false;
                main.addChild("name", name);

                var info = new AutoScaleLabelWidget(
                        mediaInfo.info(),
                        icon.getX() + MEDIA_ICON_SIZE + 5, 16,
                        80f, true
                );
                info.dropShadow = false;
                info.scale = 0.6f;
                main.addChild("info", info);
            }
        }
        return root;
    }

    @Override
    public RenderType getIcon() {
        return RenderTypes.RENDER_TYPE_APP_MEDIA_PLAYER;
    }

    @Override
    public String getName() {
        return "Media Player";
    }

    private static void play(int trackIndex) {
        initializeAudio();
        performStop();

        if (trackIndex < 0 || trackIndex >= PLAYLIST.size()) {
            currentTrackIndex = -1;
            return;
        }
        currentTrackIndex = trackIndex;
        var mediaInfo = PLAYLIST.get(currentTrackIndex);

        try (var stack = MemoryStack.stackPush();
             var stream = Minecraft.getInstance().getResourceManager().open(mediaInfo.source())) {

            var bytes = stream.readAllBytes();
            oggDataBuffer = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();

            var error = stack.mallocInt(1);
            vorbisHandle = STBVorbis.stb_vorbis_open_memory(oggDataBuffer, error, null);
            if (vorbisHandle == 0) {
                throw new IOException("Failed to open Ogg Vorbis memory stream: " + error.get(0));
            }

            var info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(vorbisHandle, info);
            sampleRate = info.sample_rate();
            channels = info.channels();
            format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;

            var totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(vorbisHandle);
            totalDuration = (float) totalSamples / sampleRate;

            final var targetSamples = sampleRate * channels / 2;
            clientBuffer = MemoryUtil.memAllocShort(targetSamples);
            baseSampleOffset = 0;

            for (var bufferId : alBuffers) {
                forward(bufferId);
            }

            AL10.alSourcePlay(alSource);
            AL10.alSourcef(alSource, AL10.AL_GAIN, 0f);

            isPlaying.set(true);
            isPaused.set(false);
            fadeState = FadeState.FADING_IN;
            fadeStartTime = System.nanoTime();

            updatePlayPauseButton();

        } catch (IOException e) {
            AcademyCraft.LOGGER.error("Failed to play media: {}", mediaInfo.source(), e);
            stop();
        }
    }

    private static void stop() {
        initializeAudio();
        if (!isPlaying.get() && !isPaused.get()) return;
        isStoppingAfterFadeOut = true;
        fadeState = FadeState.FADING_OUT;
        fadeStartTime = System.nanoTime();
    }

    private static void togglePlayPause() {
        initializeAudio();
        if (!isPlaying.get()) {
            play(currentTrackIndex != -1 ? currentTrackIndex : 0);
            return;
        }

        boolean wasPaused;
        do {
            wasPaused = isPaused.get();
        } while (!isPaused.compareAndSet(wasPaused, !wasPaused));
        final var isNowPaused = !wasPaused;

        updatePlayPauseButton();

        if (isNowPaused) {
            AL10.alSourcePause(alSource);
        } else {
            AL10.alSourcePlay(alSource);
            AL10.alSourcef(alSource, AL10.AL_GAIN, volume);
        }
        fadeState = FadeState.NONE;
    }

    private static void updatePlayPauseButton() {
        if (playPauseButton == null) return;
        playPauseButton.shape = isPlaying.get() && !isPaused.get() ? ButtonShape.PAUSE : ButtonShape.PLAY;
    }

    @Override
    public Runnable onClick() {
        return () -> DataTerminalHUD.setAppArea(create());
    }

    private MediaPlayer() {
    }

    private record MediaInfo(ResourceLocation icon, ResourceLocation source, String name, String info) {
    }

    private static class GeometricButtonWidget extends AbstractButtonWidget {
        public ButtonShape shape;
        public int color = 0xFFFFFFFF;

        public GeometricButtonWidget(float x, float y, float width, float height, ButtonShape shape, Runnable onPress) {
            super(x, y, width, height, onPress);
            this.shape = shape;
        }

        private void drawTriangle(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float x2, float y2, float x3, float y3, float r, float g, float b, float a) {
            buffer.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x3, y3, 0).color(r, g, b, a).endVertex();
        }

        private void drawRectangle(VertexConsumer buffer, Matrix4f matrix, float x, float y, float width, float height, float r, float g, float b, float a) {
            var x2 = x + width;
            var y2 = y + height;
            buffer.vertex(matrix, x, y, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x, y2, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x2, y, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, x, y, 0).color(r, g, b, a).endVertex();
        }

        @Override
        public void render(GuiGraphics graphics, double mouseX, double mouseY, float partialTick) {
            if (!isVisible()) return;

            graphics.pose().pushPose();
            graphics.pose().translate(getX(), getY(), getZ());
            var matrix = graphics.pose().last().pose();
            var buffer = graphics.bufferSource().getBuffer(new RenderType.CompositeRenderType(
                    "geometric_button",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.TRIANGLES,
                    32,
                    false,
                    false,
                    RenderType.CompositeState
                            .builder()
                            .setCullState(RenderStateUtil.NO_CULL)
                            .setShaderState(RenderStateUtil.POSITION_COLOR_SHADER)
                            .setTransparencyState(RenderStateUtil.TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(RenderStateUtil.NO_DEPTH_TEST)
                            .setWriteMaskState(RenderStateUtil.COLOR_WRITE)
                            .createCompositeState(false)
            ));

            var w = getWidth();
            var h = getHeight();
            var r = (float) (color >> 16 & 255) / 255.0F;
            var g = (float) (color >> 8 & 255) / 255.0F;
            var b = (float) (color & 255) / 255.0F;
            var a = (isHovered() ? 1.0f : 0.7f);

            var padding = 5.0f;
            var drawW = w - padding * 2;
            var drawH = h - padding * 2;

            switch (shape) {
                case PLAY ->
                        drawTriangle(buffer, matrix, padding, padding, padding + drawW, padding + drawH / 2, padding, padding + drawH, r, g, b, a);
                case PAUSE -> {
                    var barWidth = drawW * 0.3f;
                    var gap = drawW * 0.2f;
                    var totalVisualWidth = barWidth * 2 + gap;
                    var offsetX = padding + (drawW - totalVisualWidth) / 2f;
                    drawRectangle(buffer, matrix, offsetX, padding, barWidth, drawH, r, g, b, a);
                    drawRectangle(buffer, matrix, offsetX + barWidth + gap, padding, barWidth, drawH, r, g, b, a);
                }
                case NEXT -> {
                    var triangleWidth = drawW * 0.5f;
                    var barWidth = drawW * 0.2f;
                    var gap = drawW * 0.1f;
                    var totalVisualWidth = triangleWidth + gap + barWidth;
                    var offsetX = padding + (drawW - totalVisualWidth) / 2f;
                    drawTriangle(buffer, matrix, offsetX, padding, offsetX + triangleWidth, padding + drawH / 2f, offsetX, padding + drawH, r, g, b, a);
                    drawRectangle(buffer, matrix, offsetX + triangleWidth + gap, padding, barWidth, drawH, r, g, b, a);
                }
                case PREV -> {
                    var triangleWidth = drawW * 0.5f;
                    var barWidth = drawW * 0.2f;
                    var gap = drawW * 0.1f;
                    var totalVisualWidth = triangleWidth + gap + barWidth;
                    var offsetX = padding + (drawW - totalVisualWidth) / 2f;
                    drawRectangle(buffer, matrix, offsetX, padding, barWidth, drawH, r, g, b, a);
                    drawTriangle(buffer, matrix, offsetX + barWidth + gap + triangleWidth, padding, offsetX + barWidth + gap, padding + drawH / 2f, offsetX + barWidth + gap + triangleWidth, padding + drawH, r, g, b, a);
                }
            }

            graphics.pose().popPose();
        }
    }
}