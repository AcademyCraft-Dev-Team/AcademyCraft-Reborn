package org.academy.api.client.gui.apps;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.academy.AcademyCraft;
import org.academy.api.client.Resource;
import org.academy.api.client.gui.event.MouseEvent;
import org.academy.api.client.gui.layout.Gravity;
import org.academy.api.client.gui.layout.Orientation;
import org.academy.api.client.gui.layout.SizeMode;
import org.academy.api.client.gui.render.RenderContext;
import org.academy.api.client.gui.widget.*;
import org.academy.api.client.util.ClientUtil;
import org.joml.Quaternionf;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicApp extends AbstractApp {

    private static final MusicController musicController = new MusicController();

    private final List<MusicTrack> l1 = new ArrayList<>();
    private final List<MusicTrack> l2 = new ArrayList<>();
    private List<MusicTrack> currentPlaylist;

    private int currentIndex = 0;
    private LoopMode loopMode = LoopMode.LIST_LOOP;

    private VinylWidget vinylWidget;
    private LabelWidget titleLabel;
    private LabelWidget timeLabel;
    private FillWidget progressBarFill;
    private ImageWidget modeIcon;
    private ImageWidget playPauseIcon;

    public enum LoopMode {
        LIST_LOOP,
        SINGLE_LOOP,
        SHUFFLE
    }

    public record MusicTrack(String name, Identifier id) {
    }

    public static class TrackRegistry {
        private static final List<MusicTrack> registry = new ArrayList<>();

        static {
            register("only my railgun", AcademyCraft.academy("sounds/music/fripside_only_my_railgun.wav"));
        }

        public static void register(String name, Identifier id) {
            registry.add(new MusicTrack(name, id));
        }

        public static List<MusicTrack> getTracks() {
            return registry;
        }
    }

    public MusicApp(Identifier iconRes) {
        super("Music", iconRes);

        l1.addAll(TrackRegistry.getTracks());

        if (l1.isEmpty()) {
            l1.add(new MusicTrack("No Music Registered", null));
        }

        currentPlaylist = l1;

        musicController.setOnCompletionListener(() -> {
            if (loopMode != LoopMode.SINGLE_LOOP) {
                playNext();
            }
        });
    }

    @Override
    protected void initAppContent(FrameLayoutWidget content) {
        var root = new LinearLayoutWidget();
        root.setOrientation(Orientation.VERTICAL);
        root.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT));
        content.addChild("root", root);

        var vinylArea = new FrameLayoutWidget();
        vinylArea.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT));
        root.addChild("vinyl_area", vinylArea);

        vinylWidget = new VinylWidget(Resource.Textures.ICON_MUSIC_VINYL);
        vinylWidget.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(100, 100)
                .gravity(Gravity.CENTER));
        vinylArea.addChild("vinyl", vinylWidget);

        var needle = new ImageWidget(Resource.Textures.ICON_MUSIC_NEEDLE);
        needle.setLayoutParams(new FrameLayoutWidget.LayoutParams()
                .size(60, 60)
                .gravity(Gravity.TOP | Gravity.RIGHT)
                .margin(0, 20, 30, 0));
        vinylArea.addChild("needle", needle);

        var controls = new LinearLayoutWidget();
        controls.setOrientation(Orientation.VERTICAL);
        controls.setSpacing(4);
        controls.setLayoutParams(new LinearLayoutWidget.LayoutParams()
                .widthMode(SizeMode.MATCH_PARENT)
                .height(70)
                .padding(8));
        root.addChild("controls", controls);

        var infoRow = new LinearLayoutWidget();
        infoRow.setOrientation(Orientation.HORIZONTAL);
        infoRow.setLayoutParams(new LinearLayoutWidget.LayoutParams().widthMode(SizeMode.MATCH_PARENT).height(12));
        controls.addChild("info", infoRow);

        titleLabel = new LabelWidget("Ready");
        titleLabel.setLayoutParams(new LinearLayoutWidget.LayoutParams().weight(1).gravity(Gravity.CENTER_VERTICAL));
        infoRow.addChild("title", titleLabel);

        timeLabel = new LabelWidget("0%");
        timeLabel.setLayoutParams(new LinearLayoutWidget.LayoutParams().gravity(Gravity.CENTER_VERTICAL));
        infoRow.addChild("time", timeLabel);

        var progressContainer = new FrameLayoutWidget();
        progressContainer.setLayoutParams(new LinearLayoutWidget.LayoutParams().widthMode(SizeMode.MATCH_PARENT).height(4).margin(0, 2));
        controls.addChild("progress", progressContainer);

        var progressBg = new FillWidget(0x40FFFFFF);
        progressBg.setLayoutParams(new FrameLayoutWidget.LayoutParams().sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT));
        progressContainer.addChild("bg", progressBg);

        progressBarFill = new FillWidget(0xFF00BFFF);
        progressBarFill.setLayoutParams(new FrameLayoutWidget.LayoutParams().height(4).width(0));
        progressContainer.addChild("fill", progressBarFill);

        var btns = new LinearLayoutWidget();
        btns.setOrientation(Orientation.HORIZONTAL);
        btns.setSpacing(8);
        btns.setLayoutParams(new LinearLayoutWidget.LayoutParams().gravity(Gravity.CENTER).marginTop(6));
        controls.addChild("btns", btns);

        if (loopMode == null) {
            loopMode = LoopMode.LIST_LOOP;
        }

        modeIcon = new ImageWidget(getModeTexture(loopMode));
        modeIcon.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(16, 16).gravity(Gravity.CENTER));
        btns.addChild("mode", createButton(modeIcon, 24, this::cycleMode));

        var prevIcon = new ImageWidget(Resource.Textures.ICON_PREV);
        prevIcon.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(16, 16).gravity(Gravity.CENTER));
        btns.addChild("prev", createButton(prevIcon, 24, this::playPrev));

        playPauseIcon = new ImageWidget(Resource.Textures.ICON_PLAY);
        playPauseIcon.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(16, 16).gravity(Gravity.CENTER));
        btns.addChild("play", createButton(playPauseIcon, 24, this::togglePlayState));

        var nextIcon = new ImageWidget(Resource.Textures.ICON_NEXT);
        nextIcon.setLayoutParams(new FrameLayoutWidget.LayoutParams().size(16, 16).gravity(Gravity.CENTER));
        btns.addChild("next", createButton(nextIcon, 24, this::playNext));

        if (musicController.isPlaying()) {
            syncUIWithCurrentTrack();
        }
    }

    private void togglePlayState() {
        if (musicController.isPlaying()) {
            musicController.pause();
            playPauseIcon.setTexture(Resource.Textures.ICON_PLAY);
        } else if (musicController.isPaused()) {
            musicController.resume();
            playPauseIcon.setTexture(Resource.Textures.ICON_PAUSE);
        } else {
            playTrack(currentIndex);
        }
    }

    private void playNext() {
        if (currentPlaylist.isEmpty()) return;
        currentIndex = (currentIndex + 1) % currentPlaylist.size();
        playTrack(currentIndex);
    }

    private void playPrev() {
        if (currentPlaylist.isEmpty()) return;
        currentIndex = (currentIndex - 1 + currentPlaylist.size()) % currentPlaylist.size();
        playTrack(currentIndex);
    }

    private void playTrack(int index) {
        if (index < 0 || index >= currentPlaylist.size()) return;
        var track = currentPlaylist.get(index);

        if (track.id != null) {
            boolean forceLoop = (loopMode == LoopMode.SINGLE_LOOP);
            musicController.play(track.id, forceLoop);
            syncUIWithCurrentTrack();
        }
    }

    private void syncUIWithCurrentTrack() {
        if (currentIndex >= 0 && currentIndex < currentPlaylist.size()) {
            MusicTrack track = currentPlaylist.get(currentIndex);
            titleLabel.setText(track.name);
            playPauseIcon.setTexture(Resource.Textures.ICON_PAUSE);
        }
    }

    private void cycleMode() {
        switch (loopMode) {
            case LIST_LOOP -> {
                loopMode = LoopMode.SINGLE_LOOP;
                if (musicController.isPlaying()) musicController.setLoop(true);
            }
            case SINGLE_LOOP -> {
                loopMode = LoopMode.SHUFFLE;
                if (musicController.isPlaying()) musicController.setLoop(false);

                l2.clear();
                l2.addAll(l1);
                Collections.shuffle(l2);

                if (!l1.isEmpty()) {
                    var currentTrack = l1.get(currentIndex);
                    l2.remove(currentTrack);
                    l2.add(0, currentTrack);
                }

                currentPlaylist = l2;
                currentIndex = 0;
            }
            case SHUFFLE -> {
                loopMode = LoopMode.LIST_LOOP;
                if (musicController.isPlaying()) musicController.setLoop(false);

                var currentTrack = currentPlaylist.get(currentIndex);
                currentPlaylist = l1;
                currentIndex = l1.indexOf(currentTrack);
                if (currentIndex == -1) currentIndex = 0;
            }
        }
        modeIcon.setTexture(getModeTexture(loopMode));
    }

    private Identifier getModeTexture(LoopMode mode) {
        return switch (mode) {
            case LIST_LOOP -> Resource.Textures.ICON_CYCLE;
            case SINGLE_LOOP -> Resource.Textures.ICON_SINGLE_CYCLE;
            case SHUFFLE -> Resource.Textures.ICON_RANDOM;
        };
    }

    @Override
    public void render(RenderContext context) {
        if (musicController.isPlaying()) {
            vinylWidget.setRunning(true);

            float p = musicController.getProgress();
            if (progressBarFill.getParent() instanceof WidgetContainer parent) {
                progressBarFill.setWidth(parent.getWidth() * p);
            }
            timeLabel.setText(String.format("%d%%", (int) (p * 100)));

        } else {
            vinylWidget.setRunning(false);
        }
        super.render(context);
    }

    private Widget createButton(Widget content, float width, Runnable onClick) {
        var btn = new FrameLayoutWidget() {
            @Override
            protected void onMousePressed(MouseEvent event) {
                if (isMouseOver(event.getX(), event.getY())) {
                    ClientUtil.playDownSound();
                    onClick.run();
                    event.consume();
                }
            }
        };
        btn.setClickable(true);
        btn.setLayoutParams(new LinearLayoutWidget.LayoutParams().width(width).height(20).gravity(Gravity.CENTER_VERTICAL));

        var bg = new FillWidget(0x80000000);
        btn.addChild("bg", bg);
        btn.addChild("content", content);
        return btn;
    }

    public static class VinylWidget extends ImageWidget {
        private boolean isRunning = false;
        private float rotation = 0;
        private long lastTime;

        public VinylWidget(Identifier texture) {
            super(texture);
        }

        public void setRunning(boolean running) {
            this.isRunning = running;
            if (!running) lastTime = 0;
        }

        @Override
        public void render(RenderContext context) {
            if (isRunning) {
                long now = System.currentTimeMillis();
                if (lastTime > 0) {
                    float delta = (now - lastTime) / 2000f;
                    rotation += delta * 120f;
                    rotation %= 360f;
                }
                lastTime = now;
            }
            context.pose().pushPose();
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            context.pose().translate(cx, cy, 0);
            context.pose().mulPose(new Quaternionf().fromAxisAngleDeg(0, 0, 1, rotation));
            context.pose().translate(-cx, -cy, 0);
            super.render(context);
            context.pose().popPose();
        }
    }

    public static class MusicController {
        private Clip currentClip;
        private FloatControl volumeControl;
        private boolean isPaused = false;
        private boolean isLooping = false;
        private Runnable onCompletion;

        public void play(Identifier resourceLocation, boolean loop) {
            stop();
            this.isLooping = loop;

            try {
                var resource = Minecraft.getInstance().getResourceManager().getResource(resourceLocation);
                if (resource.isEmpty()) {
                    AcademyCraft.LOGGER.error("Music file not found: {}", resourceLocation);
                    return;
                }
                var audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(resource.get().open()));

                currentClip = AudioSystem.getClip();
                currentClip.open(audioStream);

                if (currentClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    volumeControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
                    setVolume(0.5f);
                }

                if (loop) {
                    currentClip.loop(Clip.LOOP_CONTINUOUSLY);
                }

                currentClip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        if (!isPaused && !isLooping && currentClip != null &&
                                Math.abs(currentClip.getMicrosecondLength() - currentClip.getMicrosecondPosition()) < 10000) {
                            if (onCompletion != null) {
                                Minecraft.getInstance().execute(onCompletion);
                            }
                        }
                    }
                });

                currentClip.start();
                this.isPaused = false;

            } catch (Exception e) {
                AcademyCraft.LOGGER.error("Error playing music: {}", resourceLocation, e);
            }
        }

        public void setOnCompletionListener(Runnable listener) {
            this.onCompletion = listener;
        }

        public void setLoop(boolean loop) {
            this.isLooping = loop;
            if (currentClip != null) {
                if (loop) currentClip.loop(Clip.LOOP_CONTINUOUSLY);
                else currentClip.loop(0);
            }
        }

        public void pause() {
            if (currentClip != null && currentClip.isRunning()) {
                currentClip.stop();
                isPaused = true;
            }
        }

        public void resume() {
            if (currentClip != null && isPaused) {
                currentClip.start();
                isPaused = false;
            }
        }

        public void stop() {
            if (currentClip != null) {
                currentClip.stop();
                currentClip.close();
                currentClip = null;
            }
            isPaused = false;
        }

        public void setVolume(float volume) {
            if (volumeControl != null) {
                var min = volumeControl.getMinimum();
                var max = volumeControl.getMaximum();
                var dB = (float) (Math.log(Math.max(0.0001, volume)) / Math.log(10.0) * 20.0);
                dB = Math.max(min, Math.min(max, dB));
                volumeControl.setValue(dB);
            }
        }

        public boolean isPlaying() {
            return currentClip != null && currentClip.isRunning();
        }

        public boolean isPaused() {
            return isPaused;
        }

        public float getProgress() {
            if (currentClip != null) {
                long len = currentClip.getMicrosecondLength();
                if (len == 0) return 0;
                long pos = currentClip.getMicrosecondPosition();
                return Mth.clamp((float) pos / len, 0f, 1f);
            }
            return 0f;
        }
    }
}