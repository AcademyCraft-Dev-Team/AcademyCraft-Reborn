package org.academy.internal.client.app;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.gui.framework.Orientation;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.hud.DataTerminalHUD;
import org.academy.api.client.render.MatrixStack;
import org.academy.api.client.render.RenderTypes;
import org.academy.api.client.util.RenderUtil;
import org.academy.internal.client.app.mediaplayer.MediaPlayerBackend;
import org.joml.Matrix4f;

import static net.minecraft.client.renderer.RenderStateShard.*;

@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class MediaPlayer implements DataTerminalHUD.App {
    private static final float MEDIA_ICON_SIZE = 20f;
    private static final float MEDIA_HEIGHT = 30f;
    private static final float MARGIN_MEDIA_ICON = 10f;
    public static final DataTerminalHUD.App INSTANCE = new MediaPlayer();

    private static SliderWidget progressBar;
    private static LabelWidget timeLabel;
    private static PanelWidget rootPanel;
    private static GeometricButtonWidget playPauseButton;
    private static ImageButtonWidget modeButton;

    public enum ButtonShape {PLAY, PAUSE, NEXT, PREV}

    private MediaPlayer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (rootPanel == null || !rootPanel.isVisible()) return;

        MediaPlayerBackend.update();

        var offset = MediaPlayerBackend.getCurrentTime();
        var totalDuration = MediaPlayerBackend.getTotalDuration();

        if (progressBar != null && !progressBar.startDragging) {
            progressBar.setValue(totalDuration > 0 ? offset / totalDuration : 0f);
        }

        if (timeLabel != null) {
            var totalSeconds = (int) totalDuration;
            var currentSeconds = (int) offset;
            timeLabel.value = String.format("%02d:%02d / %02d:%02d",
                    currentSeconds / 60, currentSeconds % 60,
                    totalSeconds / 60, totalSeconds % 60);
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

            var main = new LayeredPanelWidget(0, 0, width, height);
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

                    var layered = new LayeredPanelWidget(dockBarPadding, dockBarPadding, width - dockBarPadding * 2, dockBarHeight - dockBarPadding * 2);
                    dockBar.addChild("layered", layered);
                    {
                        var progressBarHeight = 5f;
                        progressBar = new SliderWidget(0, 0, layered.getWidth(), progressBarHeight, Orientation.HORIZONTAL, 0f, 1f, 0f) {
                            @Override
                            public boolean mouseReleased(double mouseX, double mouseY, int button) {
                                if (this.startDragging) {
                                    MediaPlayerBackend.seek(this.getValue());
                                }
                                return super.mouseReleased(mouseX, mouseY, button);
                            }
                        };
                        layered.addChild("progress_bar", progressBar);

                        var timeLabelY = progressBar.getY() + progressBar.getHeight() + 2f;
                        timeLabel = new AutoScaleLabelWidget("00:00 / 00:00", 0, timeLabelY, layered.getWidth(), true);
                        timeLabel.scale = 0.7f;
                        timeLabel.dropShadow = false;
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
                            modeButton.defaultHoverEffect = true;
                            controlPanel.addChild("mode_button", modeButton);
                            currentX += btnSize + bigGap;

                            var volumeSlider = new SliderWidget(currentX, 0, sliderWidth, btnSize, Orientation.VERTICAL, 0f, 1f, 1.0f) {
                                @Override
                                protected float getThumbSize() {
                                    return 3f;
                                }
                            };
                            volumeSlider.onValueChanged = MediaPlayerBackend::setVolume;
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
            case REPEAT_LIST -> modeButton.renderType = RenderTypes.ICON_CYCLE;
            case REPEAT_ONE -> modeButton.renderType = RenderTypes.ICON_SINGLE_CYCLE;
            case SHUFFLE -> modeButton.renderType = RenderTypes.ICON_RANDOM;
        }
    }

    private PanelWidget createMediaWidget(MediaPlayerBackend.MediaInfo mediaInfo, float y, Runnable onClick) {
        var root = new PanelWidget(0, y, 150f - 6f, MEDIA_HEIGHT);
        {
            var button = new ImageButtonWidget(0, 0, root.getWidth(), root.getHeight(), null, onClick);
            root.addChild("button", button);

            var back = new FillWidget(0, 0, root.getWidth(), root.getHeight(), 0xFF000000);
            back.setAlpha(0.25f);
            root.addChild("back", back);

            var main = new LayeredPanelWidget(0, 0, root.getWidth(), root.getHeight());
            main.setEnabled(false);
            root.addChild("main", main);
            {
                var iconRenderType = RenderUtil.getPositionColorTexRenderType(mediaInfo.name(), mediaInfo.icon(), true);
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
                        mediaInfo.subtitle(),
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

    private static void updatePlayPauseButton() {
        if (playPauseButton == null) return;
        playPauseButton.shape = MediaPlayerBackend.isPlaying() && !MediaPlayerBackend.isPaused() ? ButtonShape.PAUSE : ButtonShape.PLAY;
    }

    @Override
    public RenderType getIcon() {
        return RenderTypes.ICON_MEDIA_PLAYER;
    }

    @Override
    public String getName() {
        return "Media Player";
    }

    @Override
    public Runnable onClick() {
        return () -> DataTerminalHUD.setAppArea(create());
    }

    private static class GeometricButtonWidget extends AbstractButtonWidget {
        private static final RenderType RENDER_TYPE = RenderType.create(
                "geometric_button",
                DefaultVertexFormat.POSITION_COLOR,
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
        );

        public ButtonShape shape;
        public int color = 0xFFFFFFFF;

        public GeometricButtonWidget(float x, float y, float width, float height, ButtonShape newShape, Runnable onPress) {
            super(x, y, width, height, onPress);
            shape = newShape;
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

        @Override
        public void render(MatrixStack stack, MultiBufferSource.BufferSource bufferSource, double mouseX, double mouseY, float partialTick) {
            if (!isVisible()) return;

            stack.pushPose();
            stack.translate(getX(), getY(), getZ());
            var matrix = stack.lastMatrix();
            var buffer = bufferSource.getBuffer(RENDER_TYPE);
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

            stack.popPose();
        }
    }
}