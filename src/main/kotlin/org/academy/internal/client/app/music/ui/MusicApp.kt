package org.academy.internal.client.app.music.ui

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.locale.Language
import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
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
import org.academy.api.client.hud.terminal.TerminalHud
import org.academy.api.client.resources.R
import org.academy.internal.client.app.music.backend.AlbumArtworkCache
import org.academy.internal.client.app.music.backend.MusicPlayerBackend
import org.academy.internal.client.app.music.backend.OnlineMusicManager
import org.academy.internal.client.app.music.common.PlaybackMode
import java.io.ByteArrayInputStream
import java.util.function.Supplier
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
        return tr("app.academy.music_player.name")
    }

    override fun icon(): Identifier {
        return R.textures.gui.app.music.icon
    }

    private class Context : WidgetContext {
        private var showingSearchResults = false
        private var libraryViewRevision = 0
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
                root.orientation = Orientation.VERTICAL
                root.layoutParams = FrameLayoutWidget.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT)
                root.spacing = 1f
                content.addChild("root", root)
                run {
                    val topBar = LinearLayoutWidget()
                    topBar.orientation = Orientation.HORIZONTAL
                    topBar.layoutParams = LinearLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    root.addChild("top_bar", topBar)
                    run {
                        val backButton = ButtonWidget()
                        backButton.layoutParams = LinearLayoutWidget.LayoutParams()
                            .margin(2f, 2f, 2f, 0f)
                            .size(16f, 16f)
                        backButton.onClickListener = { _: Widget? ->
                            TerminalHud.INSTANCE.closeApp()
                        }
                        topBar.addChild("back_button", backButton)
                        run {
                            val arrow = ImageWidget(R.textures.gui.icon.arrow_back)
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
            main.orientation = Orientation.HORIZONTAL
            run {
                val musicListArea = ScrollPanelWidget()
                musicListArea.layoutParams = LinearLayoutWidget.LayoutParams()
                    .width(142f)
                    .heightMode(SizeMode.MATCH_PARENT)
                musicListArea.setContent(createLibraryPanel())
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

                val infoArea = LinearLayoutWidget()
                infoArea.layoutParams = FrameLayoutWidget.LayoutParams()
                    .gravity(Gravity.CENTER_BOTTOM)
                    .size(224f, 32f)
                    .margin(0f, 0f, 0f, 12f)
                infoArea.orientation = Orientation.VERTICAL
                playerArea.addChild("info_area", infoArea)
                run {
                    val progressInfoArea = LinearLayoutWidget()
                    progressInfoArea.layoutParams = LinearLayoutWidget.LayoutParams()
                        .weight(1f)
                        .widthMode(SizeMode.MATCH_PARENT)
                    progressInfoArea.orientation = Orientation.HORIZONTAL
                    progressInfoArea.spacing = 4f
                    infoArea.addChild("progress_info_area", progressInfoArea)
                    run {
                        val p = LinearLayoutWidget.LayoutParams()
                            .weight(1f)
                            .width(0f)
                            .gravity(Gravity.CENTER)
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
                    controlArea.orientation = Orientation.HORIZONTAL
                    controlArea.spacing = 8f
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
                            val icon = ImageWidget(R.textures.gui.app.music.previous)
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
                            val icon = ImageWidget(R.textures.gui.app.music.next)
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

        private fun createLibraryPanel(): LinearLayoutWidget {
            val panel = LinearLayoutWidget()
            panel.orientation = Orientation.VERTICAL
            panel.spacing = 2f
            panel.layoutParams = WidgetContainer.LayoutParams()
                .width(138f)
                .heightMode(SizeMode.WRAP_CONTENT)

            val searchBox = TextBoxWidget(64).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .height(14f)
                    .padding(2f, 0f)
                setWhenEnter { query -> search(query) }
                setClearWhenEnter(false)
            }
            val searchRow = LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = 2f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(14f)
            }
            searchRow.addChild("query", searchBox)
            searchRow.addChild("search", createTextButton(tr("app.academy.music_player.search"), 24f) {
                search(searchBox.text)
            })
            searchRow.addChild(
                "return_list", createTextButton(
                    tr("app.academy.music_player.back_to_list"), 24f, 0.5f
                ) {
                    showingSearchResults = false
                    libraryViewRevision++
                })
            panel.addChild("search_row", searchRow)

            val providers = LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = 2f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(14f)
            }
            providers.addChild("qq", createTextButton(tr("app.academy.music_player.provider.qq"), 0f) {
                OnlineMusicManager.selectProvider(OnlineMusicManager.Provider.QQ)
            }.apply { layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).height(14f) })
            providers.addChild("netease", createTextButton(tr("app.academy.music_player.provider.netease"), 0f) {
                OnlineMusicManager.selectProvider(OnlineMusicManager.Provider.NETEASE)
            }.apply { layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).height(14f) })
            panel.addChild("providers", providers)

            val accountActions = LinearLayoutWidget().apply {
                orientation = Orientation.HORIZONTAL
                spacing = 2f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(14f)
            }
            accountActions.addChild("login", createTextButton(tr("app.academy.music_player.login"), 0f) {
                OnlineMusicManager.startLogin()
            }.apply { layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).height(14f) })
            accountActions.addChild("logout", createTextButton(tr("app.academy.music_player.logout"), 0f) {
                OnlineMusicManager.logout()
            }.apply { layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).height(14f) })
            accountActions.addChild("sync", createTextButton(tr("app.academy.music_player.sync"), 0f) {
                OnlineMusicManager.shareCurrentTrack()
            }.apply { layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).height(14f) })
            panel.addChild("account_actions", accountActions)

            panel.addChild("status", object : LabelWidget(OnlineMusicManager.status) {
                override fun tick() {
                    super.tick()
                    OnlineMusicManager.tick()
                    text = OnlineMusicManager.status
                }
            }.apply {
                scale = 0.65f
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(12f)
                    .gravity(Gravity.CENTER_LEFT)
            })

            panel.addChild("qr", LoginQrWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .size(72f, 72f)
                    .gravity(Gravity.CENTER)
            })

            panel.addChild("dynamic", object : LinearLayoutWidget() {
                private var lastOnlineRevision = -1
                private var lastPlaylistRevision = -1
                private var lastViewRevision = -1

                init {
                    orientation = Orientation.VERTICAL
                    spacing = 2f
                    layoutParams = LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .heightMode(SizeMode.WRAP_CONTENT)
                }

                override fun tick() {
                    super.tick()
                    val onlineRevision = OnlineMusicManager.revision
                    val playlistRevision = MusicPlayerBackend.getInstance().playlistRevision
                    if (onlineRevision == lastOnlineRevision
                        && playlistRevision == lastPlaylistRevision
                        && libraryViewRevision == lastViewRevision
                    ) return
                    lastOnlineRevision = onlineRevision
                    lastPlaylistRevision = playlistRevision
                    lastViewRevision = libraryViewRevision
                    rebuildSearchAndPlaylist(this)
                }
            })
            return panel
        }

        private fun rebuildSearchAndPlaylist(container: LinearLayoutWidget) {
            container.clearChildren()
            if (showingSearchResults) {
                container.addChild("search_title", LabelWidget(tr("app.academy.music_player.search_results")).apply {
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(10f)
                })
                OnlineMusicManager.searchResults.forEachIndexed { index, entry ->
                    val row = LinearLayoutWidget().apply {
                        orientation = Orientation.HORIZONTAL
                        spacing = 2f
                        layoutParams = LinearLayoutWidget.LayoutParams()
                            .widthMode(SizeMode.MATCH_PARENT)
                            .height(24f)
                    }
                    row.addChild(
                        "name", LabelWidget(
                            (if (entry.vip) "[VIP] " else "") + entry.title + " - " + entry.artist
                        ).apply {
                            scale = 0.62f
                            layoutParams = LinearLayoutWidget.LayoutParams()
                                .weight(1f)
                                .height(0f)
                                .gravity(Gravity.CENTER_LEFT)
                        })
                    row.addChild("add", createTextButton("+", 16f) {
                        OnlineMusicManager.add(entry)
                    })
                    row.addChild("play", createTextButton("▶", 24f, 0.9f, 22f) {
                        OnlineMusicManager.add(entry, true)
                    })
                    container.addChild("result_$index", row)
                }
                return
            }

            container.addChild("playlist_title", LabelWidget(tr("app.academy.music_player.track_list")).apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .widthMode(SizeMode.MATCH_PARENT)
                    .height(10f)
            })
            MusicPlayerBackend.getInstance().playlist.forEachIndexed { index, mediaInfo ->
                val row = LinearLayoutWidget().apply {
                    orientation = Orientation.HORIZONTAL
                    spacing = 2f
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .widthMode(SizeMode.MATCH_PARENT)
                        .height(24f)
                }
                row.addChild("play", ButtonWidget().apply {
                    layoutParams = LinearLayoutWidget.LayoutParams()
                        .weight(1f)
                        .height(24f)
                    onClickListener = { MusicPlayerBackend.getInstance().play(index) }
                    addChild("text", LabelWidget(mediaInfo.name).apply {
                        scale = 0.68f
                        layoutParams = FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                            .gravity(Gravity.CENTER_LEFT)
                            .padding(2f, 0f)
                    })
                })
                if (mediaInfo.provider != "local") {
                    row.addChild("remove", createTextButton("×", 24f, 0.9f, 22f) {
                        OnlineMusicManager.remove(mediaInfo)
                    })
                }
                container.addChild("track_$index", row)
            }
        }

        private fun search(query: String) {
            if (query.isBlank()) return
            showingSearchResults = true
            libraryViewRevision++
            OnlineMusicManager.search(query)
        }

        private fun createTextButton(
            text: String,
            width: Float,
            textScale: Float = 0.65f,
            height: Float = 14f,
            action: () -> Unit
        ): ButtonWidget {
            return ButtonWidget().apply {
                layoutParams = LinearLayoutWidget.LayoutParams()
                    .width(width)
                    .height(height)
                onClickListener = { action() }
                addChild("text", LabelWidget(text).apply {
                    scale = textScale
                    layoutParams = FrameLayoutWidget.LayoutParams()
                        .sizeMode(SizeMode.MATCH_PARENT)
                        .gravity(Gravity.CENTER)
                })
            }
        }

        private fun tr(key: String): String = Language.getInstance().getOrDefault(key)

        private class LoginQrWidget : ImageWidget(R.textures.gui.app.music.icon) {
            private var uploadedBytes: ByteArray? = null

            init {
                setSampler(FilterMode.NEAREST, false)
                visibility = Widget.Visibility.INVISIBLE
            }

            override fun tick() {
                super.tick()
                val bytes = OnlineMusicManager.qrBytes
                visibility =
                    if (bytes == null || bytes.isEmpty()) Widget.Visibility.INVISIBLE else Widget.Visibility.VISIBLE
                if (bytes == null || bytes.isEmpty() || bytes === uploadedBytes) return
                runCatching {
                    val image = NativeImage.read(ByteArrayInputStream(bytes))
                    val texture = DynamicTexture(Supplier { "academy_music_login_qr" }, image)
                    val location = AcademyCraft.academy("music_login_qr")
                    Minecraft.getInstance().textureManager.register(location, texture)
                    setTexture(location)
                    uploadedBytes = bytes
                }.onFailure {
                    AcademyCraft.LOGGER.error("Failed to upload music login QR texture", it)
                }
            }
        }

        fun createMusicList(): LinearLayoutWidget {
            val musicList = LinearLayoutWidget()
            musicList.orientation = Orientation.VERTICAL
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
                        val back = FillWidget(TerminalHud.COLOR)
                        back.layoutParams = FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.MATCH_PARENT)
                        musicButton.addChild("back", back)

                        val info = LinearLayoutWidget()
                        info.layoutParams = FrameLayoutWidget.LayoutParams()
                            .sizeMode(SizeMode.WRAP_CONTENT)
                        info.orientation = Orientation.HORIZONTAL
                        info.spacing = 2f
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
                            text.orientation = Orientation.VERTICAL
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
            return object : ImageWidget(R.textures.gui.app.music.now_playing) {
                override fun generateDrawCommand(
                    texture: GpuTextureView,
                    sampler: GpuSampler,
                    width: Float,
                    height: Float,
                    u0: Float,
                    v0: Float,
                    u1: Float,
                    v1: Float,
                    u2: Float,
                    v2: Float,
                    u3: Float,
                    v3: Float,
                    red: Float,
                    green: Float,
                    blue: Float,
                    alpha: Float
                ): DrawCommand {
                    return ImageCircleDrawCommand(
                        texture, sampler,
                        width, height,
                        u0, v0, u1, v1, u2, v2, u3, v3,
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
            content.orientation = Orientation.HORIZONTAL
            run {
                val p = LinearLayoutWidget.LayoutParams()
                    .size(16f, 16f)
                    .gravity(Gravity.CENTER)
                val emptyP = LinearLayoutWidget.LayoutParams()
                    .weight(1f)
                    .width(0f)
                    .heightMode(SizeMode.MATCH_PARENT)

                val icon = ImageWidget(R.textures.gui.app.music.volume)
                icon.setSampler(FilterMode.LINEAR, false)
                icon.layoutParams = p
                content.addChild("icon", icon)

                val info = object : FrameLayoutWidget() {
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
            return if (isPlaying) R.textures.gui.app.music.pause
            else R.textures.gui.app.music.play
        }

        fun getPlaybackModeIcon(): Identifier {
            val musicPlayerBackend = MusicPlayerBackend.getInstance()
            return when (musicPlayerBackend.playbackMode) {
                PlaybackMode.REPEAT_LIST -> R.textures.gui.app.music.cycle
                PlaybackMode.REPEAT_ONE -> R.textures.gui.app.music.single_cycle
                PlaybackMode.SHUFFLE -> R.textures.gui.app.music.random_play
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
                vinyl.setTexture(AlbumArtworkCache.textureFor(mediaInfo))
                vinyl.setSampler(FilterMode.LINEAR, false)
            }
        }

        fun formatTime(totalSeconds: Float): String {
            if (totalSeconds.isNaN() || totalSeconds < 0) return "00:00"
            return "%02d:%02d".format((totalSeconds / 60).toInt(), (totalSeconds % 60).toInt())
        }
    }

    private fun tr(key: String): String = Language.getInstance().getOrDefault(key)
}
