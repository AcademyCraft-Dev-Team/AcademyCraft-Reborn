package org.academy.internal.client.app;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.academy.api.client.Resource;
import org.academy.api.client.app.App;
import org.academy.api.client.gui.animation.EasingFunctions;
import org.academy.api.client.gui.animation.ObjectAnimator;
import org.academy.api.client.gui.animation.ValueAnimator;
import org.academy.api.client.gui.command.DrawCommand;
import org.academy.api.client.gui.command.ImageCircleDrawCommand;
import org.academy.api.client.gui.command.ImageDrawCommand;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.hud.terminal.TerminalHUD;
import org.academy.internal.client.app.music.MusicPlayerBackend;

import java.util.function.Function;

import static org.academy.api.client.hud.terminal.TerminalHUD.COLOR;

public final class MusicApp implements App {
    public static final MusicApp INSTANCE = new MusicApp();

    private static final Function<Float, String> FORMAT_TIME = (totalSeconds) -> {
        if (totalSeconds.isNaN() || totalSeconds < 0) return "00:00";
        var minutes = (int) (totalSeconds / 60);
        var seconds = (int) (totalSeconds % 60);
        return String.format("%02d:%02d", minutes, seconds);
    };

    private MusicApp() {
    }

    @Override
    public WidgetContext createContext() {
        return new Context();
    }

    @Override
    public String name() {
        return "Music";
    }

    @Override
    public Identifier icon() {
        return Resource.Textures.ICON_MUSIC_PLAYER;
    }

    private static class Context implements WidgetContext {
        private final ImageWidget vinyl = createVinyl();
        private final ImageWidget playPauseIcon = new ImageWidget(getPlayPauseIcon());
        private final ImageWidget playbackModeIcon = new ImageWidget(getPlaybackModeIcon());
        private final ObjectAnimator rot;
        
        {
            rot = ObjectAnimator
                    .ofFloat(vinyl::getRotation, vinyl::setRotation, 360)
                    .setDuration(5000)
                    .setInterpolator(EasingFunctions.LINEAR);
            rot.setRepeatMode(ValueAnimator.RESTART);
            rot.setRepeatCount(ValueAnimator.INFINITE);
        }

        private final FrameLayoutWidget content = createContent();

        @Override
        public Widget get() {
            return content;
        }

        private FrameLayoutWidget createContent() {
            var content = new FrameLayoutWidget();
            content.setLayoutParams(
                    new WidgetContainer.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
            );
            {
                var root = new LinearLayoutWidget();
                root.setOrientation(Orientation.VERTICAL);
                root.setLayoutParams(
                        new FrameLayoutWidget.LayoutParams()
                                .sizeMode(SizeMode.MATCH_PARENT)
                );
                root.setSpacing(1);
                content.addChild("root", root);
                {
                    var topBar = new LinearLayoutWidget();
                    topBar.setOrientation(Orientation.HORIZONTAL);
                    topBar.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    );
                    root.addChild("top_bar", topBar);
                    {
                        var backButton = new ButtonWidget();
                        backButton.setLayoutParams(
                                new LinearLayoutWidget.LayoutParams()
                                        .margin(2, 2, 2, 0)
                                        .size(16, 16)
                        );
                        backButton.setOnClickListener(_ -> TerminalHUD.getInstance().closeApp());
                        topBar.addChild("back_button", backButton);
                        {
                            var arrow = new ImageWidget(Resource.Textures.ARROW_BACK);
                            arrow.setSampler(FilterMode.LINEAR, false);
                            arrow.setLayoutParams(
                                    new FrameLayoutWidget.LayoutParams()
                                            .sizeMode(SizeMode.MATCH_PARENT)
                            );
                            backButton.addChild("arrow", arrow);
                        }
                    }

                    var splitLine = new FillWidget(0xFFFFFFFF);
                    splitLine.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .height(1)
                                    .widthMode(SizeMode.MATCH_PARENT)
                                    .padding(2, 0)
                    );
                    root.addChild("split_line", splitLine);

                    root.addChild("main", createMain());
                }
            }
            return content;
        }

        private LinearLayoutWidget createMain() {
            var main = new LinearLayoutWidget();
            main.setLayoutParams(
                    new LinearLayoutWidget.LayoutParams()
                            .weight(1)
                            .padding(2, 0, 2, 2)
                            .widthMode(SizeMode.MATCH_PARENT)
            );
            main.setOrientation(Orientation.HORIZONTAL);
            {
                var musicListArea = new ScrollPanelWidget();
                musicListArea.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
                );
                main.addChild("music_list_area", musicListArea);

                var playerArea = new FrameLayoutWidget();
                playerArea.setLayoutParams(
                        new LinearLayoutWidget.LayoutParams()
                                .weight(1)
                                .heightMode(SizeMode.MATCH_PARENT)
                );
                main.addChild("player_area", playerArea);

                vinyl.setSampler(FilterMode.NEAREST, false);
                vinyl.setLayoutParams(
                        new FrameLayoutWidget.LayoutParams()
                                .gravity(Gravity.CENTER)
                                .size(96, 96)
                                .margin(0, 0, 0, 24)
                );
                playerArea.addChild("vinyl", vinyl);
                updateVinylIcon();
                updateRot();

                var musicList = new LinearLayoutWidget();
                musicList.setOrientation(Orientation.VERTICAL);
                musicList.setLayoutParams(
                        new FrameLayoutWidget.LayoutParams()
                                .sizeMode(SizeMode.WRAP_CONTENT)
                );
                musicListArea.setContent(createMusicList());

                var infoArea = new LinearLayoutWidget();
                infoArea.setLayoutParams(
                        new FrameLayoutWidget.LayoutParams()
                                .gravity(Gravity.CENTER_BOTTOM)
                                .size(192, 32)
                                .margin(0, 0, 0, 12)
                );
                infoArea.setOrientation(Orientation.VERTICAL);
                playerArea.addChild("info_area", infoArea);
                {
                    var progressInfoArea = new LinearLayoutWidget();
                    progressInfoArea.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .weight(1)
                                    .widthMode(SizeMode.MATCH_PARENT)
                    );
                    progressInfoArea.setOrientation(Orientation.HORIZONTAL);
                    infoArea.addChild("progress_info_area", progressInfoArea);
                    {
                        var p = new LinearLayoutWidget.LayoutParams()
                                .weight(1)
                                .gravity(Gravity.CENTER);

                        var currentTime = new LabelWidget("00:00") {
                            @Override
                            public void tick() {
                                setText(FORMAT_TIME.apply(MusicPlayerBackend.getCurrentTime()));
                            }
                        };
                        currentTime.setLayoutParams(p);
                        progressInfoArea.addChild("current_time", currentTime);

                        progressInfoArea.addChild("play_progress_bar", createPlayProgressBar());

                        var musicDuration = new LabelWidget("00:00") {
                            @Override
                            public void tick() {
                                setText(FORMAT_TIME.apply(MusicPlayerBackend.getTotalDuration()));
                            }
                        };
                        musicDuration.setLayoutParams(p);
                        progressInfoArea.addChild("music_duration", musicDuration);
                    }

                    var controlArea = new LinearLayoutWidget();
                    controlArea.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .widthMode(SizeMode.MATCH_PARENT)
                                    .height(16)
                    );
                    controlArea.setOrientation(Orientation.HORIZONTAL);
                    controlArea.setSpacing(8);
                    infoArea.addChild("control_area", controlArea);
                    {
                        var emptyP = new LinearLayoutWidget.LayoutParams()
                                .weight(1)
                                .width(0)
                                .heightMode(SizeMode.MATCH_PARENT);

                        var p = new LinearLayoutWidget.LayoutParams()
                                .size(16, 16)
                                .gravity(Gravity.CENTER);

                        var left = new FrameLayoutWidget();
                        left.setLayoutParams(emptyP);
                        controlArea.addChild("left", left);
                        {
                            var playbackModeButton = new ButtonWidget();
                            playbackModeButton.setLayoutParams(
                                    new LinearLayoutWidget.LayoutParams()
                                            .size(16, 16)
                                            .gravity(Gravity.CENTER_RIGHT)
                            );
                            playbackModeButton.setOnClickListener(_ -> {
                                MusicPlayerBackend.cyclePlaybackMode();
                                updatePlaybackModeIcon();
                            });
                            left.addChild("playback_mode", playbackModeButton);
                            {
                                playbackModeIcon.setSampler(FilterMode.LINEAR, false);
                                playbackModeButton.addChild("icon", playbackModeIcon);
                            }
                        }

                        var previousButton = new ButtonWidget();
                        previousButton.setLayoutParams(p);
                        previousButton.setOnClickListener(
                                _ -> {
                                    MusicPlayerBackend.playPrevious();
                                    selectTrackIndex(MusicPlayerBackend.getCurrentTrackIndex());
                                }
                        );
                        controlArea.addChild("previous", previousButton);
                        {
                            var icon = new ImageWidget(Resource.Textures.ICON_PREV);
                            icon.setSampler(FilterMode.LINEAR, false);
                            previousButton.addChild("icon", icon);
                        }

                        var playPauseButton = new ButtonWidget();
                        playPauseButton.setLayoutParams(p);
                        playPauseButton.setOnClickListener(_ -> {
                            if (MusicPlayerBackend.isPlaying()) {
                                MusicPlayerBackend.togglePlayPause();
                            } else MusicPlayerBackend.play(MusicPlayerBackend.getCurrentTrackIndex());

                            updatePlayPauseIcon();
                            updateRot();
                        });
                        controlArea.addChild("play_pause", playPauseButton);
                        {
                            playPauseIcon.setSampler(FilterMode.LINEAR, false);
                            playPauseButton.addChild("icon", playPauseIcon);
                        }

                        var nextButton = new ButtonWidget();
                        nextButton.setLayoutParams(p);
                        nextButton.setOnClickListener(
                                _ -> {
                                    MusicPlayerBackend.playNext();
                                    selectTrackIndex(MusicPlayerBackend.getCurrentTrackIndex());
                                }
                        );
                        controlArea.addChild("next", nextButton);
                        {
                            var icon = new ImageWidget(Resource.Textures.ICON_NEXT);
                            icon.setSampler(FilterMode.LINEAR, false);
                            nextButton.addChild("icon", icon);
                        }

                        var right = new FrameLayoutWidget();
                        right.setLayoutParams(emptyP);
                        controlArea.addChild("right", right);
                        {
                            right.addChild("volume_bar", createVolumeBar());
                        }
                    }
                }
            }
            return main;
        }

        private LinearLayoutWidget createMusicList() {
            var musicList = new LinearLayoutWidget();
            musicList.setOrientation(Orientation.VERTICAL);
            musicList.setLayoutParams(
                    new FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.WRAP_CONTENT)
            );
            {
                var playlist = MusicPlayerBackend.getPlaylist();
                for (var i = 0; i < playlist.size(); i++) {
                    var mediaInfo = playlist.get(i);
                    var musicButton = new ButtonWidget();
                    musicButton.setLayoutParams(
                            new LinearLayoutWidget.LayoutParams()
                                    .sizeMode(SizeMode.WRAP_CONTENT)
                    );
                    var trackIndex = i;
                    musicButton.setOnClickListener(_ -> selectTrackIndex(trackIndex));
                    musicList.addChild(mediaInfo.name(), musicButton);
                    {
                        var back = new FillWidget(COLOR);
                        back.setLayoutParams(
                                new FrameLayoutWidget.LayoutParams()
                                        .sizeMode(SizeMode.MATCH_PARENT)
                        );
                        musicButton.addChild("back", back);

                        var info = new LinearLayoutWidget();
                        info.setLayoutParams(
                                new FrameLayoutWidget.LayoutParams()
                                        .sizeMode(SizeMode.WRAP_CONTENT)
                        );
                        info.setOrientation(Orientation.HORIZONTAL);
                        info.setSpacing(2);
                        musicButton.addChild("info", info);
                        {
                            var icon = new ImageWidget(mediaInfo.icon());
                            icon.setLayoutParams(
                                    new LinearLayoutWidget.LayoutParams()
                                            .size(16, 16)
                                            .gravity(Gravity.CENTER)
                                            .margin(2, 2, 0, 2)
                            );
                            icon.setSampler(FilterMode.LINEAR, false);
                            info.addChild("icon", icon);

                            var text = new LinearLayoutWidget();
                            text.setLayoutParams(
                                    new LinearLayoutWidget.LayoutParams()
                                            .height(16)
                                            .gravity(Gravity.CENTER)
                            );
                            text.setOrientation(Orientation.VERTICAL);
                            info.addChild("text", text);
                            {
                                var name = new LabelWidget(mediaInfo.name());
                                name.setLayoutParams(
                                        new LinearLayoutWidget.LayoutParams()
                                                .width(48)
                                                .gravity(Gravity.CENTER_LEFT)
                                );
                                text.addChild("name", name);

                                var subtitle = new LabelWidget(mediaInfo.subtitle());
                                subtitle.setLayoutParams(
                                        new LinearLayoutWidget.LayoutParams()
                                                .width(32)
                                                .gravity(Gravity.CENTER_LEFT)
                                );
                                text.addChild("subtitle", subtitle);
                            }
                        }
                    }
                }
            }
            return musicList;
        }

        private ImageWidget createVinyl() {
            return new ImageWidget(Resource.Textures.ICON_NOW_PLAYING) {
                @Override
                protected DrawCommand generateDrawCommand(
                        GpuTextureView texture, GpuSampler sampler,
                        float width, float height,
                        float u0, float v0, float u1, float v1,
                        float red, float green, float blue, float alpha
                ) {
                    return new ImageCircleDrawCommand(
                            texture, sampler,
                            width, height,
                            u0, v0, u1, v1,
                            red, green, blue, alpha
                    );
                }

                @Override
                public void tick() {
                    updateVinylIcon();
                }
            };
        }

        private SeekBarWidget createPlayProgressBar() {
            var progressBar = new SeekBarWidget() {
                @Override
                public void tick() {
                    if (!isDragging) {
                        var progress = MusicPlayerBackend.getCurrentTime() / MusicPlayerBackend.getTotalDuration();
                        setProgress(getMin() + progress * (getMax() - getMin()));
                    }
                }
            };
            progressBar.setLayoutParams(
                    new WidgetContainer.LayoutParams()
                            .width(128)
                            .heightMode(SizeMode.MATCH_PARENT)
                            .margin(0, 6)
                            .gravity(Gravity.CENTER)
            );
            progressBar.setOnSeekBarChangeListener(new SeekBarWidget.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBarWidget seekBar, float progress, boolean fromUser) {
                }

                @Override
                public void onStartTrackingTouch(SeekBarWidget seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBarWidget seekBar) {
                    MusicPlayerBackend.seek(progressBar.getProgress() / progressBar.getMax());
                }
            });
            return progressBar;
        }

        private SeekBarWidget createVolumeBar() {
            var volumeBar = new SeekBarWidget() {
                @Override
                protected DrawCommand generateBackDrawCommand(
                        float width, float height,
                        float red, float green, float blue, float alpha
                ) {
                    var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                    var textureManager = Minecraft.getInstance().getTextureManager();
                    var textureView = textureManager.getTexture(Resource.Textures.ICON_VOLUME).getTextureView();
                    return new ImageDrawCommand(
                            textureView, sampler,
                            width, height,
                            0, 0, 1, 1,
                            red, green, blue, alpha
                    );
                }

                @Override
                protected DrawCommand generateProgressDrawCommand(
                        float width, float height,
                        float red, float green, float blue, float alpha
                ) {
                    var progress = (getProgress() - getMin()) / (getMax() - getMin());
                    var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                    var textureManager = Minecraft.getInstance().getTextureManager();
                    var textureView = textureManager.getTexture(Resource.Textures.ICON_VOLUME).getTextureView();
                    return new ImageDrawCommand(
                            textureView, sampler,
                            width, height,
                            0, 0,
                            getOrientation() == Orientation.HORIZONTAL ? progress : 1,
                            getOrientation() == Orientation.VERTICAL ? progress : 1,
                            red, green, blue, alpha
                    );
                }

                @Override
                public void tick() {
                    if (!isDragging) setProgress(getMin() + MusicPlayerBackend.getVolume() * (getMax() - getMin()));
                }
            };
            volumeBar.setLayoutParams(
                    new WidgetContainer.LayoutParams()
                            .size(32, 16)
                            .gravity(Gravity.BOTTOM_LEFT)
            );
            volumeBar.setOnSeekBarChangeListener(new SeekBarWidget.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBarWidget seekBar, float progress, boolean fromUser) {
                    MusicPlayerBackend.setVolume(progress / volumeBar.getMax());
                }

                @Override
                public void onStartTrackingTouch(SeekBarWidget seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBarWidget seekBar) {
                }
            });
            return volumeBar;
        }

        private Identifier getPlayPauseIcon() {
            return MusicPlayerBackend.isPlaying() && !MusicPlayerBackend.isPaused()
                    ? Resource.Textures.ICON_PAUSE
                    : Resource.Textures.ICON_PLAY;
        }

        private Identifier getPlaybackModeIcon() {
            return switch (MusicPlayerBackend.getPlaybackMode()) {
                case REPEAT_LIST -> Resource.Textures.ICON_CYCLE;
                case REPEAT_ONE -> Resource.Textures.ICON_SINGLE_CYCLE;
                case SHUFFLE -> Resource.Textures.ICON_RANDOM_PLAY;
            };
        }

        private void updatePlayPauseIcon() {
            playPauseIcon.setTexture(getPlayPauseIcon());
        }

        private void updatePlaybackModeIcon() {
            playbackModeIcon.setTexture(getPlaybackModeIcon());
        }

        private void updateRot() {
            if (MusicPlayerBackend.isPlaying() && !MusicPlayerBackend.isPaused()) {
                startRot();
                return;
            }
            pauseRot();
        }

        private void startRot() {
            if (!rot.isRunning()) rot.start();
            else if (rot.isPaused()) rot.resume();
        }

        private void pauseRot() {
            if (rot.isRunning()) rot.pause();
        }

        private void selectTrackIndex(int trackIndex) {
            MusicPlayerBackend.play(trackIndex);
            MusicPlayerBackend.togglePlayPause();
            updatePlayPauseIcon();
            updateVinylIcon();
            updateRot();
        }

        private void updateVinylIcon() {
            var playList = MusicPlayerBackend.getPlaylist();
            var index = MusicPlayerBackend.getCurrentTrackIndex();
            if (-1 < index && index < playList.size()) {
                var mediaInfo = playList.get(index);
                vinyl.setTexture(mediaInfo.icon());
                vinyl.setSampler(FilterMode.LINEAR, false);
            }
        }
    }
}