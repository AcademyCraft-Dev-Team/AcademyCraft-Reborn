package org.academy.internal.client.app;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.framework.AbstractContainerWidget;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.hud.DataTerminalHUD;
import org.academy.internal.client.app.mediaplayer.MediaInfo;
import org.academy.internal.client.app.mediaplayer.MediaPlayerBackend;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import static org.academy.api.client.Resource.Textures.ICON_MUSIC_PLAYER;

@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class MediaPlayer implements DataTerminalHUD.App {
    private static final float MEDIA_ICON_SIZE = 20f;
    private static final float MEDIA_HEIGHT = 30f;
    private static final float MARGIN_MEDIA_ICON = 10f;
    public static final DataTerminalHUD.App INSTANCE = new MediaPlayer();

    private static SliderWidget progressBar;
    private static AutoScaleLabelWidget timeLabel;
    private static PanelWidget rootPanel;
    private static GeometricButtonWidget playPauseButton;
    private static ImageButtonWidget modeButton;
    private static boolean isProgressBarDragging = false;

    public enum ButtonShape {PLAY, PAUSE, NEXT, PREV}

    private MediaPlayer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (rootPanel == null || !rootPanel.isVisible()) return;

        MediaPlayerBackend.update();

        var offset = MediaPlayerBackend.getCurrentTime();
        var totalDuration = MediaPlayerBackend.getTotalDuration();

        if (progressBar != null && !isProgressBarDragging) {
            progressBar.setValue(totalDuration > 0 ? (offset / totalDuration) : 0f);
        }

        if (timeLabel != null) {
            var totalSeconds = (int) totalDuration;
            var currentSeconds = (int) offset;
            timeLabel.setText(String.format("%02d:%02d / %02d:%02d",
                    currentSeconds / 60, currentSeconds % 60,
                    totalSeconds / 60, totalSeconds % 60));
        }

        updatePlayPauseButton();
    }

    private PanelWidget create() {
        rootPanel = new PanelWidget(0, 0, 150f, 200f);
        var width = 150f;
        var height = 200f;
        {
            var back = new FillWidget(0, 0, width, height, 0xFF000000);
            back.setAlpha(0.25f);
            rootPanel.addChild("back", back);

            var main = new PanelWidget(0, 0, width, height);
            rootPanel.addChild("main", main);
            {
                var dockBarHeight = 60f;
                var mediaListScrollBarWidth = 4f;
                var mediaList = new ScrollPanelWidget(0, 0, width, height - dockBarHeight);
                main.addChild("list_media", mediaList);
                {
                    var playlist = MediaPlayerBackend.getPlaylist();
                    for (var i = 0; i < playlist.size(); i++) {
                        var info = playlist.get(i);
                        var trackIndex = i;
                        var mediaWidget = createMediaWidget(info, i * (MEDIA_HEIGHT), () -> MediaPlayerBackend.play(trackIndex));
                        mediaList.addChild("media_" + i, mediaWidget);
                    }
                }

                var mediaListScrollBar = new ScrollBarWidget(
                        mediaList,
                        mediaList.getWidth() - mediaListScrollBarWidth, 0,
                        mediaListScrollBarWidth, mediaList.getHeight(),
                        Orientation.VERTICAL
                );
                mediaListScrollBar.setThumbColor(0x20AAAAAA);
                mediaListScrollBar.setTrackColor(0x10202020);
                main.addChild("bar_list_media", mediaListScrollBar);

                var dockBarPadding = 5f;
                var dockBar = new PanelWidget(0, height - dockBarHeight, width, dockBarHeight);
                main.addChild("bar_dock", dockBar);
                {
                    var dockBarBack = new FillWidget(0, 0, width, dockBarHeight, 0xFF000000);
                    dockBarBack.setAlpha(0.25f);
                    dockBar.addChild("back", dockBarBack);

                    var layered = new PanelWidget(dockBarPadding, dockBarPadding, width - dockBarPadding * 2, dockBarHeight - dockBarPadding * 2);
                    dockBar.addChild("layered", layered);
                    {
                        var progressBarHeight = 5f;
                        progressBar = new SliderWidget(0, 0, layered.getWidth(), progressBarHeight, Orientation.HORIZONTAL, 0f, 1f, 0f) {
                            @Override
                            protected void onMousePressed(@NotNull MouseEvent event) {
                                super.onMousePressed(event);
                                if (event.isConsumed()) {
                                    isProgressBarDragging = true;
                                }
                            }

                            @Override
                            protected void onMouseReleased(@NotNull MouseEvent event) {
                                if (isProgressBarDragging) {
                                    MediaPlayerBackend.seek(this.getValue());
                                    isProgressBarDragging = false;
                                }
                                super.onMouseReleased(event);
                            }
                        };
                        layered.addChild("progress_bar", progressBar);

                        var timeLabelY = progressBar.getY() + progressBar.getHeight() + 2f;
                        timeLabel = new AutoScaleLabelWidget("00:00 / 00:00", 0, timeLabelY, layered.getWidth());
                        timeLabel.setScale(0.7f);
                        timeLabel.setDropShadow(false);
                        layered.addChild("time_label", timeLabel);

                        var controlsY = timeLabel.getY() + timeLabel.getHeight() + 5f;
                        var btnSize = 20f;
                        var sliderWidth = 3f;
                        var smallGap = 5f;
                        var bigGap = 15f;
                        var controlPanel = new PanelWidget(0, controlsY, layered.getWidth(), btnSize);
                        layered.addChild("control_panel", controlPanel);
                        {
                            var totalControlsWidth = btnSize * 4 + sliderWidth + smallGap * 2 + bigGap * 2;
                            var currentX = (controlPanel.getWidth() - totalControlsWidth) / 2;

                            var prevButton = new GeometricButtonWidget(currentX, 0, btnSize, btnSize, ButtonShape.PREV, MediaPlayerBackend::playPrevious);
                            controlPanel.addChild("prev", prevButton);
                            currentX += btnSize + smallGap;

                            playPauseButton = new GeometricButtonWidget(currentX, 0, btnSize, btnSize, ButtonShape.PLAY, MediaPlayerBackend::togglePlayPause);
                            controlPanel.addChild("play_pause", playPauseButton);
                            currentX += btnSize + smallGap;

                            var nextButton = new GeometricButtonWidget(currentX, 0, btnSize, btnSize, ButtonShape.NEXT, MediaPlayerBackend::playNext);
                            controlPanel.addChild("next", nextButton);
                            currentX += btnSize + bigGap;

                            modeButton = new ImageButtonWidget(currentX, 0, btnSize, btnSize, null, MediaPlayer::cyclePlaybackMode);
                            modeButton.setDefaultHoverEffect(true);
                            controlPanel.addChild("mode_button", modeButton);
                            currentX += btnSize + bigGap;

                            var volumeSlider = new SliderWidget(currentX, 0, sliderWidth, btnSize, Orientation.VERTICAL, 0f, 1f, 1.0f) {
                                @Override
                                protected float getThumbSize() {
                                    return 3f;
                                }
                            };
                            volumeSlider.setOnValueChanged(MediaPlayerBackend::setVolume);
                            controlPanel.addChild("volume_slider", volumeSlider);
                        }
                    }
                }
            }
        }
        updatePlayPauseButton();
        updateModeButtonIcon();
        return rootPanel;
    }

    private static void cyclePlaybackMode() {
        MediaPlayerBackend.cyclePlaybackMode();
        updateModeButtonIcon();
    }

    private static void updateModeButtonIcon() {
        if (modeButton == null) return;
        modeButton.setAlpha(1.0f);
        switch (MediaPlayerBackend.getPlaybackMode()) {
            case REPEAT_LIST -> modeButton.setTexture(Resource.Textures.ICON_CYCLE);
            case REPEAT_ONE -> modeButton.setTexture(Resource.Textures.ICON_SINGLE_CYCLE);
            case SHUFFLE -> modeButton.setTexture(Resource.Textures.ICON_RANDOM);
        }
    }

    private PanelWidget createMediaWidget(MediaInfo mediaInfo, float y, Runnable onClick) {
        var root = new PanelWidget(0, y, 150f - 6f, MEDIA_HEIGHT);
        {
            var button = new ImageButtonWidget(0, 0, root.getWidth(), root.getHeight(), null, onClick);
            root.addChild("button", button);

            var back = new FillWidget(0, 0, root.getWidth(), root.getHeight(), 0xFF000000);
            back.setAlpha(0.25f);
            root.addChild("back", back);

            var main = new PanelWidget(0, 0, root.getWidth(), root.getHeight());
            main.setEnabled(false);
            root.addChild("main", main);
            {
                var icon = new ImageWidget(
                        MARGIN_MEDIA_ICON, (MEDIA_HEIGHT - MEDIA_ICON_SIZE) / 2,
                        MEDIA_ICON_SIZE, MEDIA_ICON_SIZE, mediaInfo.icon()
                );
                main.addChild("icon", icon);

                var name = new AutoScaleLabelWidget(
                        mediaInfo.name(),
                        icon.getX() + MEDIA_ICON_SIZE + 5, 4,
                        80f
                );
                name.setScale(0.8f);
                name.setDropShadow(false);
                main.addChild("name", name);

                var info = new AutoScaleLabelWidget(
                        mediaInfo.subtitle(),
                        icon.getX() + MEDIA_ICON_SIZE + 5, 16,
                        80f
                );
                info.setDropShadow(false);
                info.setScale(0.6f);
                main.addChild("info", info);
            }
        }
        return root;
    }

    private static void updatePlayPauseButton() {
        if (playPauseButton == null) return;
        playPauseButton.setShape(MediaPlayerBackend.isPlaying() && !MediaPlayerBackend.isPaused() ? ButtonShape.PAUSE : ButtonShape.PLAY);
    }

    @Override
    public @NotNull ResourceLocation getIcon() {
        return ICON_MUSIC_PLAYER;
    }

    @Override
    public @NotNull String getName() {
        return "Media Player";
    }

    @Override
    public @NotNull Runnable onClick() {
        return () -> {
        };
    }

    @Override
    public @NotNull Runnable onClose() {
        return new Runnable() {
            @Override
            public void run() {

            }
        };
    }

    @Override
    public @NotNull AbstractContainerWidget getContainer() {
        return create();
    }

    private static class GeometricButtonWidget extends AbstractButtonWidget {
/*        private static final RenderType RENDER_TYPE = RenderType.create(
                "geometric_button",
                DefaultVertexFormat.POS_COLOR,
                VertexFormat.Mode.TRIANGLES,
                32,
                false,
                false,
                RenderType.CompositeState
                        .builder()
                        .setCullState(NO_CULL)
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false)
        );*/

        protected ButtonShape shape;
        protected int color = 0xFFFFFFFF;
        protected float currentAlpha = 0.7f;

        public GeometricButtonWidget(float x, float y, float width, float height, ButtonShape newShape, Runnable onPress) {
            super(x, y, width, height, onPress);
            this.shape = newShape;
        }

        @Override
        public void setHovered(boolean hovered) {
            currentAlpha = hovered ? 1.0f : 0.7f;
        }

        public void setShape(ButtonShape shape) {
            this.shape = shape;
        }

        private void drawTriangle(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float x2, float y2, float x3, float y3, float r, float g, float b, float a) {
            buffer.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x3, y3, 0).setColor(r, g, b, a);
        }

        private void drawRectangle(VertexConsumer buffer, Matrix4f matrix, float x, float y, float width, float height, float r, float g, float b, float a) {
            var x2 = x + width;
            var y2 = y + height;
            buffer.addVertex(matrix, x, y, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x, y2, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x2, y, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x, y, 0).setColor(r, g, b, a);
        }
    }
}