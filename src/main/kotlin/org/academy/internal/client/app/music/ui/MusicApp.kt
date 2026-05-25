package org.academy.internal.client.app.music.ui

import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.resources.Identifier
import org.academy.api.client.Resource
import org.academy.api.client.app.App
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.animation.ValueAnimator
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.ImageCircleDrawCommand
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.widget.*
import org.academy.api.client.gui.widget.SeekBarWidget.OnSeekBarChangeListener
import org.academy.api.client.hud.terminal.TerminalHUD
import org.academy.internal.client.app.music.backend.MusicPlayerBackend
import org.academy.internal.client.app.music.common.PlaybackMode
import kotlin.math.min
import kotlin.math.roundToInt

object MusicApp : App {
    /**
     * 0 ~ 1
     */
    private const val VOLUME_SCALE = 0.35f

    override fun createContext(): WidgetContext {
        return Context()
    }

    override fun name(): String {
        return "Music"
    }

    override fun icon(): Identifier {
        return Resource.Textures.ICON_MUSIC_PLAYER
    }

    private class Context : WidgetContext {
        private val vinyl = createVinyl()
        private val playPauseIcon: ImageWidget = object : ImageWidget(getPlayPauseIcon()) {
            override fun tick() {
                updatePlayPauseIcon()
            }
        }
        private val playbackModeIcon: ImageWidget = object : ImageWidget(getPlaybackModeIcon()) {
            override fun tick() {
                updatePlaybackModeIcon()
            }
        }
        private val rot: ObjectAnimator = ObjectAnimator
            .ofFloat(
                { vinyl.rotation },
                { rotation ->
                    vinyl.rotation = rotation
                },
                360f
            )
            .setDuration(5000)
            .setInterpolator(EasingFunctions.LINEAR)
            .apply {
                repeatMode = ValueAnimator.RESTART
                repeatCount = ValueAnimator.INFINITE
            }

        override fun get(): Widget {
            return createContent()
        }

        fun createContent(): FrameLayoutWidget {
            val content = FrameLayoutWidget()
            content.layoutParams = WidgetContainer.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
            run {
                val root = LinearLayoutWidget()
                root.setOrientation(Orientation.VERTICAL)
                root.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                root.setSpacing(1f)
                content.addChild("root", root)
                run {
                    val topBar = LinearLayoutWidget()
                    topBar.setOrientation(Orientation.HORIZONTAL)
                    topBar.layoutParams = LinearLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    root.addChild("top_bar", topBar)
                    run {
                        val backButton = ButtonWidget()
                        backButton.layoutParams = LinearLayoutWidget.LayoutParams()
                            .margin(2f, 2f, 2f, 0f)
                            .size(16f, 16f)
                        backButton.onClickListener = { _: Widget? ->
                            TerminalHUD.instance.closeApp()
                        }
                        topBar.addChild("back_button", backButton)
                        run {
                            val arrow = ImageWidget(Resource.Textures.ARROW_BACK)
                            arrow.setSampler(FilterMode.LINEAR, false)
                            arrow.layoutParams = FrameLayoutWidget.LayoutParams()
                                .sizeMode(SizeMode.MATCH_PARENT)
                            backButton.addChild("arrow", arrow)
                        }
                    }

                    val splitLine = FillWidget(-0x1)
                    splitLine.layoutParams = LinearLayoutWidget.LayoutParams()
                        .height(1f)
                        .widthMode(SizeMode.MATCH_PARENT)
                        .padding(2f, 0f)
                    root.addChild("split_line", splitLine)
                    root.addChild("main", createMain())
                }
            }
            return content
        }

        fun createMain(): LinearLayoutWidget {
            val main = LinearLayoutWidget()
            main.layoutParams = LinearLayoutWidget.LayoutParams()
                .weight(1f)
                .padding(2f, 0f, 2f, 2f)
                .widthMode(SizeMode.MATCH_PARENT)
            main.setOrientation(Orientation.HORIZONTAL)
            run {
                val musicListArea = ScrollPanelWidget()
                musicListArea.layoutParams = LinearLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.WRAP_CONTENT, SizeMode.MATCH_PARENT)
                main.addChild("music_list_area", musicListArea)

                val playerArea = FrameLayoutWidget()
                playerArea.layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .heightMode(SizeMode.MATCH_PARENT)
                main.addChild("player_area", playerArea)

                vinyl.setSampler(FilterMode.NEAREST, false)
                vinyl.layoutParams = FrameLayoutWidget.LayoutParams()
                    .gravity(Gravity.CENTER)
                    .size(96f, 96f)
                    .margin(0f, 0f, 0f, 24f)
                playerArea.addChild("vinyl", vinyl)
                updateVinylIcon()
                updateRot()

                val musicList = LinearLayoutWidget()
                musicList.setOrientation(Orientation.VERTICAL)
                musicList.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.WRAP_CONTENT)
                musicListArea.setContent(createMusicList())

                val infoArea = LinearLayoutWidget()
                infoArea.layoutParams = FrameLayoutWidget.LayoutParams()
                    .gravity(Gravity.CENTER_BOTTOM)
                    .size(224f, 32f)
                    .margin(0f, 0f, 0f, 12f)
                infoArea.setOrientation(Orientation.VERTICAL)
                playerArea.addChild("info_area", infoArea)
                run {
                    val progressInfoArea = LinearLayoutWidget()
                    progressInfoArea.layoutParams = LinearLayoutWidget.LayoutParams()
                        .weight(1f)
                        .widthMode(SizeMode.MATCH_PARENT)
                    progressInfoArea.setOrientation(Orientation.HORIZONTAL)
                    progressInfoArea.setSpacing(4f)
                    infoArea.addChild("progress_info_area", progressInfoArea)
                    run {
                        val p = LinearLayoutWidget.LayoutParams()
                            .weight(1f)
                            .width(0f)
                            .gravity(Gravity.CENTER_TOP)
                        val currentTime: LabelWidget = object : LabelWidget("00:00") {
                            override fun tick() {
                                text = formatTime(MusicPlayerBackend.getInstance().currentTime)
                            }
                        }
                        currentTime.layoutParams = p
                        progressInfoArea.addChild("current_time", currentTime)

                        progressInfoArea.addChild("play_progress_bar", createPlayProgressBar())

                        val musicDuration: LabelWidget = object : LabelWidget("00:00") {
                            override fun tick() {
                                text = formatTime(MusicPlayerBackend.getInstance().totalDuration)
                            }
                        }
                        musicDuration.layoutParams = p
                        progressInfoArea.addChild("music_duration", musicDuration)
                    }

                    val controlArea = LinearLayoutWidget()
                    controlArea.layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(16f)
                    controlArea.setOrientation(Orientation.HORIZONTAL)
                    controlArea.setSpacing(8f)
                    infoArea.addChild("control_area", controlArea)
                    run {
                        val emptyP = LinearLayoutWidget.LayoutParams()
                            .weight(1f)
                            .width(0f)
                            .heightMode(SizeMode.MATCH_PARENT)
                        val p = LinearLayoutWidget.LayoutParams()
                            .size(16f, 16f)
                            .gravity(Gravity.CENTER)

                        val left = FrameLayoutWidget()
                        left.layoutParams = emptyP
                        controlArea.addChild("left", left)
                        run {
                            val playbackModeButton = ButtonWidget()
                            playbackModeButton.layoutParams = LinearLayoutWidget.LayoutParams()
                                .size(16f, 16f)
                                .gravity(Gravity.CENTER_RIGHT)
                            playbackModeButton.onClickListener = {
                                MusicPlayerBackend.getInstance().cyclePlaybackMode()
                            }
                            left.addChild("playback_mode", playbackModeButton)
                            run {
                                playbackModeIcon.setSampler(FilterMode.LINEAR, false)
                                playbackModeButton.addChild("icon", playbackModeIcon)
                            }
                        }

                        val previousButton = ButtonWidget()
                        previousButton.layoutParams = p
                        previousButton.onClickListener = {
                            MusicPlayerBackend.getInstance().playPrevious()
                        }
                        controlArea.addChild("previous", previousButton)
                        run {
                            val icon = ImageWidget(Resource.Textures.ICON_PREV)
                            icon.setSampler(FilterMode.LINEAR, false)
                            previousButton.addChild("icon", icon)
                        }

                        val playPauseButton = ButtonWidget()
                        playPauseButton.layoutParams = p
                        playPauseButton.onClickListener = {
                            MusicPlayerBackend.getInstance().togglePlayPause()
                        }
                        controlArea.addChild("play_pause", playPauseButton)
                        run {
                            playPauseIcon.setSampler(FilterMode.LINEAR, false)
                            playPauseButton.addChild("icon", playPauseIcon)
                        }

                        val nextButton = ButtonWidget()
                        nextButton.layoutParams = p
                        nextButton.onClickListener = { MusicPlayerBackend.getInstance().playNext() }
                        controlArea.addChild("next", nextButton)
                        run {
                            val icon = ImageWidget(Resource.Textures.ICON_NEXT)
                            icon.setSampler(FilterMode.LINEAR, false)
                            nextButton.addChild("icon", icon)
                        }

                        val right = FrameLayoutWidget()
                        right.layoutParams = emptyP
                        controlArea.addChild("right", right)
                        run {
                            right.addChild("volume_area", createVolumeArea())
                        }
                    }
                }
            }
            return main
        }

        fun createMusicList(): LinearLayoutWidget {
            val musicList = LinearLayoutWidget()
            musicList.setOrientation(Orientation.VERTICAL)
            musicList.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.WRAP_CONTENT)
            run {
                val playlist = MusicPlayerBackend.getInstance().playlist
                for (mediaInfo in playlist) {
                    val musicButton = ButtonWidget()
                    musicButton.layoutParams = LinearLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.WRAP_CONTENT)
                    musicButton.onClickListener = { MusicPlayerBackend.getInstance().play(mediaInfo) }
                    musicList.addChild(mediaInfo.name, musicButton)
                    run {
                        val back = FillWidget(TerminalHUD.COLOR)
                        back.layoutParams = FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                        musicButton.addChild("back", back)

                        val info = LinearLayoutWidget()
                        info.layoutParams = FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.WRAP_CONTENT)
                        info.setOrientation(Orientation.HORIZONTAL)
                        info.setSpacing(2f)
                        musicButton.addChild("info", info)
                        run {
                            val icon = ImageWidget(mediaInfo.icon)
                            icon.layoutParams = LinearLayoutWidget.LayoutParams()
                                .size(16f, 16f)
                                .gravity(Gravity.CENTER)
                                .margin(2f, 2f, 0f, 2f)
                            icon.setSampler(FilterMode.LINEAR, false)
                            info.addChild("icon", icon)

                            val text = LinearLayoutWidget()
                            text.layoutParams = LinearLayoutWidget.LayoutParams()
                                .height(16f)
                                .gravity(Gravity.CENTER)
                            text.setOrientation(Orientation.VERTICAL)
                            info.addChild("stringBuilder", text)
                            run {
                                val name = LabelWidget(mediaInfo.name)
                                name.layoutParams = LinearLayoutWidget.LayoutParams()
                                    .weight(1f)
                                    .size(48f, 0f)
                                    .gravity(Gravity.CENTER_LEFT)
                                text.addChild("name", name)

                                val subtitle = LabelWidget(mediaInfo.subtitle)
                                subtitle.layoutParams = LinearLayoutWidget.LayoutParams()
                                    .weight(1f)
                                    .size(32f, 0f)
                                    .gravity(Gravity.CENTER_LEFT)
                                text.addChild("subtitle", subtitle)
                            }
                        }
                    }
                }
            }
            return musicList
        }

        fun createVinyl(): ImageWidget {
            return object : ImageWidget(Resource.Textures.ICON_NOW_PLAYING) {
                override fun generateDrawCommand(
                    texture: GpuTextureView, sampler: GpuSampler,
                    width: Float, height: Float,
                    u0: Float, v0: Float, u1: Float, v1: Float,
                    red: Float, green: Float, blue: Float, alpha: Float
                ): DrawCommand {
                    return ImageCircleDrawCommand(
                        texture, sampler,
                        width, height,
                        u0, v0, u1, v1,
                        red, green, blue, alpha
                    )
                }

                override fun tick() {
                    updateRot()
                    updateVinylIcon()
                }
            }
        }

        fun createPlayProgressBar(): SeekBarWidget {
            val progressBar: SeekBarWidget = object : SeekBarWidget() {
                override fun tick() {
                    if (!isDragging) {
                        val musicPlayerBackend = MusicPlayerBackend.getInstance()
                        val progress = musicPlayerBackend.currentTime / musicPlayerBackend.totalDuration
                        setProgress(min + progress * (max - min))
                    }
                }
            }
            progressBar.layoutParams = WidgetContainer.LayoutParams()
                .size(128f, 6f)
                .gravity(Gravity.CENTER)
            progressBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {}

                override fun onStartTrackingTouch(seekBar: SeekBarWidget) {}

                override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
                    val musicPlayerBackend = MusicPlayerBackend.getInstance()
                    musicPlayerBackend.seek(progressBar.progress / progressBar.max)
                }
            })
            return progressBar
        }

        fun createVolumeArea(): LinearLayoutWidget {
            val content = LinearLayoutWidget()
            content.layoutParams = LinearLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
            content.setOrientation(Orientation.HORIZONTAL)
            run {
                val p = LinearLayoutWidget.LayoutParams()
                    .size(16f, 16f)
                    .gravity(Gravity.CENTER)
                val emptyP = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .width(0f)
                    .heightMode(SizeMode.MATCH_PARENT)

                val icon = ImageWidget(Resource.Textures.ICON_VOLUME)
                icon.setSampler(FilterMode.LINEAR, false)
                icon.layoutParams = p
                content.addChild("icon", icon)

                val info: FrameLayoutWidget = object : FrameLayoutWidget() {
                    override fun tick() {
                        super.tick()
                        visibility = if (icon.isHovered || isHovered) Widget.Visibility.VISIBLE
                        else Widget.Visibility.INVISIBLE
                    }

                    override var isHovered: Boolean
                        get() = super.isHovered
                        set(hovered) {
                            if (visibility == Widget.Visibility.VISIBLE) super.isHovered = hovered
                        }
                }
                info.layoutParams = emptyP
                content.addChild("info", info)
                run {
                    val volume = { min(MusicPlayerBackend.getInstance().volume / VOLUME_SCALE, 1f) }

                    val text = LabelWidget("${(volume() * 100).roundToInt()}%")
                    text.scale = 0.75f
                    text.layoutParams = WidgetContainer.LayoutParams()
                        .marginTop(8f)
                        .gravity(Gravity.CENTER)
                    info.addChild("text", text)

                    fun setText(progress: Float) {
                        text.text = "${(progress * 100f).roundToInt()}%"
                    }

                    val volumeBar = SeekBarWidget()
                    volumeBar.setProgress(volume() * volumeBar.max)
                    volumeBar.layoutParams = WidgetContainer.LayoutParams()
                        .size(48f, 4f)
                        .marginBottom(2f)
                        .gravity(Gravity.CENTER)
                    volumeBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                            val musicPlayerBackend = MusicPlayerBackend.getInstance()
                            val p = progress / volumeBar.max
                            musicPlayerBackend.volume = p * VOLUME_SCALE
                            setText(p)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBarWidget) {
                            setText(seekBar.progress / seekBar.max)
                        }

                        override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
                            setText(seekBar.progress / seekBar.max)
                        }
                    })
                    info.addChild("bar", volumeBar)
                }
            }
            return content
        }

        val isPlaying: Boolean
            get() = MusicPlayerBackend.getInstance().isPlaying

        fun getPlayPauseIcon(): Identifier {
            return if (isPlaying) Resource.Textures.ICON_PAUSE
            else Resource.Textures.ICON_PLAY
        }

        fun getPlaybackModeIcon(): Identifier {
            val musicPlayerBackend = MusicPlayerBackend.getInstance()
            return when (musicPlayerBackend.playbackMode) {
                PlaybackMode.REPEAT_LIST -> Resource.Textures.ICON_CYCLE
                PlaybackMode.REPEAT_ONE -> Resource.Textures.ICON_SINGLE_CYCLE
                PlaybackMode.SHUFFLE -> Resource.Textures.ICON_RANDOM_PLAY
            }
        }

        fun updatePlayPauseIcon() {
            playPauseIcon.setTexture(getPlayPauseIcon())
        }

        fun updatePlaybackModeIcon() {
            playbackModeIcon.setTexture(getPlaybackModeIcon())
        }

        fun updateRot() {
            if (this.isPlaying) {
                startRot()
                return
            }
            pauseRot()
        }

        fun startRot() {
            if (!rot.isRunning) rot.start()
            else if (rot.isPaused) rot.resume()
        }

        fun pauseRot() {
            if (rot.isRunning) rot.pause()
        }

        fun updateVinylIcon() {
            val musicPlayerBackend = MusicPlayerBackend.getInstance()
            val mediaInfo = musicPlayerBackend.currentMusicInfo
            if (mediaInfo != null) {
                vinyl.setTexture(mediaInfo.icon)
                vinyl.setSampler(FilterMode.LINEAR, false)
            }
        }

        fun formatTime(totalSeconds: Float): String {
            if (totalSeconds.isNaN() || totalSeconds < 0) return "00:00"
            return "%02d:%02d".format((totalSeconds / 60).toInt(), (totalSeconds % 60).toInt())
        }
    }
}