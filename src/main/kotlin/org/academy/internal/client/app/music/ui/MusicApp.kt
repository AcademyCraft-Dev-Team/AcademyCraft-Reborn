package org.academy.internal.client.app.music.ui

import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import org.academy.api.client.app.App
import org.academy.api.client.gui.animation.EasingFunctions
import org.academy.api.client.gui.animation.ObjectAnimator
import org.academy.api.client.gui.animation.ValueAnimator
import org.academy.api.client.gui.command.DrawCommand
import org.academy.api.client.gui.command.ImageCircleDrawCommand
import org.academy.api.client.gui.drawable.ColorDrawable
import org.academy.api.client.gui.drawable.StateListDrawable
import org.academy.api.client.gui.dsl.*
import org.academy.api.client.gui.environment.UiEnvironment
import org.academy.api.client.gui.layout.Gravity
import org.academy.api.client.gui.layout.Orientation
import org.academy.api.client.gui.layout.SizeMode
import org.academy.api.client.gui.state.UiState
import org.academy.api.client.gui.state.bindState
import org.academy.api.client.gui.widget.*
import org.academy.api.client.gui.widget.SeekBarWidget.OnSeekBarChangeListener
import org.academy.api.client.hud.terminal.TerminalHud
import org.academy.api.client.resources.R
import org.academy.api.common.util.L10n
import org.academy.internal.client.app.music.backend.AccountAvatarCache
import org.academy.internal.client.app.music.backend.AlbumArtworkCache
import org.academy.internal.client.app.music.backend.MusicPlayerBackend
import org.academy.internal.client.app.music.backend.OnlineMusicManager
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
        return L10n["app.academy.music_player.name"]
    }

    override fun icon(): Identifier {
        return R.textures.gui.app.music.icon
    }

    private class Context : WidgetContext {
        private var showingSearchResults = false
        private var showingAccount = false
        private val listRevisionState = UiState(0)
        private lateinit var searchBox: TextBoxWidget
        private lateinit var searchButton: ButtonWidget
        private var settingsTitle: LabelWidget? = null
        private var moreIcon: ImageWidget? = null
        private val vinyl = createVinyl()
        private var lastVinylArtwork: Identifier? = null
        private val playPauseIcon: ImageWidget = ImageWidget().apply {
            bindState(MusicPlayerBackend.getInstance().uiState) { setTexture(getPlayPauseIcon()) }
        }
        private val playbackModeIcon: ImageWidget = ImageWidget().apply {
            bindState(MusicPlayerBackend.getInstance().uiState) { setTexture(getPlaybackModeIcon()) }
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

        private lateinit var mainContainer: WidgetContainer

        override fun get(): Widget {
            return createContent()
        }

        fun createContent(): FrameLayoutWidget {
            return standaloneFrame {
                matchParent()
                paddingHorizontal(2f)

                column("root") {
                    spacing = 1f
                    sizeMode(SizeMode.MATCH_PARENT)

                    row("top_bar") {
                        sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                        spacing = 2f

                        button("back_button") {
                            margin(2f, 2f, 2f, 0f)
                            size(16f, 16f)
                            onClick { TerminalHud.INSTANCE.closeApp() }
                            image(R.textures.gui.icon.arrow_back, "arrow") {
                                sizeMode(SizeMode.MATCH_PARENT)
                            }
                        }
                        searchBox = textBox(64, "query") {
                            weight(1f)
                            height(14f)
                            gravity(Gravity.CENTER_VERTICAL)
                            padding(2f, 0f)
                            enter { search(it) }
                            clearOnEnter(false)
                        }
                        searchButton = createTextButton(L10n["app.academy.music_player.search"], 24f) {
                            search(searchBox.text)
                        }
                        add("search", searchButton)
                        settingsTitle = label(L10n["app.academy.music_player.settings.title"], "settings_title") {
                            weight(1f)
                            height(14f)
                            gravity(Gravity.CENTER)
                            visibility = Widget.Visibility.GONE
                        }
                        button("more_button") {
                            margin(2f, 2f, 2f, 0f)
                            size(16f, 16f)
                            onClick { toggleAccountView() }
                            moreIcon = image(R.textures.gui.icon.more, "icon") {
                                sizeMode(SizeMode.MATCH_PARENT)
                            }
                        }
                    }

                    fill(TerminalHud.PRIMARY_COLOR, "split_line") {
                        height(1f)
                        widthMode(SizeMode.MATCH_PARENT)
                    }

                    mainContainer = frame {
                        weight(1f)
                        widthMode(SizeMode.MATCH_PARENT)

                        add("content", createNormalView())
                    }
                }
            }
        }

        fun toggleAccountView() {
            showingAccount = !showingAccount
            mainContainer.replace("content", if (showingAccount) createSettingsView() else createNormalView())
            updateTopBarMode()
        }

        private fun updateTopBarMode() {
            if (showingAccount) {
                searchBox.visibility = Widget.Visibility.GONE
                searchButton.visibility = Widget.Visibility.GONE
                settingsTitle?.visibility = Widget.Visibility.VISIBLE
                moreIcon?.setTexture(R.textures.gui.icon.close)
                moreIcon?.parent?.let {
                    (it as? ButtonWidget)?.tooltipText = L10n["app.academy.music_player.settings.back"]
                }
            } else {
                searchBox.visibility = Widget.Visibility.VISIBLE
                searchButton.visibility = Widget.Visibility.VISIBLE
                settingsTitle?.visibility = Widget.Visibility.GONE
                moreIcon?.setTexture(R.textures.gui.icon.more)
                moreIcon?.parent?.let { (it as? ButtonWidget)?.tooltipText = null }
            }
        }

        fun createNormalView(): LinearLayoutWidget = standaloneRow {
            sizeMode(SizeMode.MATCH_PARENT)

            column {
                label(L10n["app.academy.music_player.track_list"], "playlist_title")

                scrollPanel(Orientation.VERTICAL, "music_list_area", createPlaylistPanel()) {
                    width(100f)
                    weight(1f)
                }
            }

            frame {
                weight(1f)
                heightMode(SizeMode.MATCH_PARENT)

                add("vinyl", vinyl) {
                    sampler(FilterMode.NEAREST, false)
                    size(88f, 88f)
                    margin(0f, 0f, 0f, 32f)
                    gravity(Gravity.CENTER)
                }
                updateVinylIcon()
                updateRot()
                column("info_area") {
                    gravity(Gravity.CENTER_BOTTOM)
                    size(228f, 56f)
                    margin(0f, 0f, 0f, 4f)
                    add("meta", LinearLayoutWidget().apply {
                        orientation = Orientation.VERTICAL
                        val titleLabel = label("") {
                            widthMode(SizeMode.MATCH_PARENT)
                            gravity(Gravity.CENTER)
                        }
                        val artistLabel = label("") {
                            widthMode(SizeMode.MATCH_PARENT)
                            gravity(Gravity.CENTER)
                        }
                        bindState(MusicPlayerBackend.getInstance().uiState) {
                            val mi = MusicPlayerBackend.getInstance().currentMusicInfo
                            titleLabel.text = mi?.name ?: ""
                            artistLabel.text = mi?.subtitle ?: ""
                        }
                    }) {
                        widthMode(SizeMode.MATCH_PARENT)
                        heightMode(SizeMode.WRAP_CONTENT)
                    }
                    row("progress_info_area") {
                        height(12f)
                        widthMode(SizeMode.MATCH_PARENT)
                        spacing = 4f
                        add("current_time", LabelWidget("00:00").apply {
                            setFrameUpdate {
                                text = formatTime(MusicPlayerBackend.getInstance().currentTime)
                                true
                            }
                        }) {
                            weight(1f)
                            width(0f)
                            gravity(Gravity.CENTER)
                        }
                        add("play_progress_bar", createPlayProgressBar())
                        add("music_duration", LabelWidget("00:00").apply {
                            setFrameUpdate {
                                text = formatTime(MusicPlayerBackend.getInstance().totalDuration)
                                true
                            }
                        }) {
                            weight(1f)
                            width(0f)
                            gravity(Gravity.CENTER)
                        }
                    }
                    row("control_area") {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(16f)
                        spacing = 8f

                        frame {
                            weight(1f)
                            width(0f)
                            heightMode(SizeMode.MATCH_PARENT)

                            button("playback_mode") {
                                size(16f, 16f)
                                gravity(Gravity.CENTER_RIGHT)
                                onClick { MusicPlayerBackend.getInstance().cyclePlaybackMode() }
                                add("icon", playbackModeIcon) {
                                    sampler(FilterMode.LINEAR, false)
                                }
                            }
                        }
                        button("previous") {
                            size(16f, 16f)
                            gravity(Gravity.CENTER)
                            onClick { MusicPlayerBackend.getInstance().playPrevious() }
                            image(R.textures.gui.app.music.previous) {
                                sampler(FilterMode.LINEAR, false)
                            }
                        }
                        button("play_pause") {
                            size(16f, 16f)
                            gravity(Gravity.CENTER)
                            onClick { MusicPlayerBackend.getInstance().togglePlayPause() }
                            add("icon", playPauseIcon) {
                                sampler(FilterMode.LINEAR, false)
                            }
                        }
                        button("next") {
                            size(16f, 16f)
                            gravity(Gravity.CENTER)
                            onClick { MusicPlayerBackend.getInstance().playNext() }
                            image(R.textures.gui.app.music.next) {
                                sampler(FilterMode.LINEAR, false)
                            }
                        }
                        frame {
                            weight(1f)
                            width(0f)
                            heightMode(SizeMode.MATCH_PARENT)

                            add("volume_area", createVolumeArea())
                        }
                    }
                }
            }
        }

        fun createPlaylistPanel(): LinearLayoutWidget = LinearLayoutWidget().apply {
            orientation = Orientation.VERTICAL
            bindState(OnlineMusicManager.revisionState) { rebuildSearchAndPlaylist(this as LinearLayoutWidget) }
            bindState(MusicPlayerBackend.getInstance().uiState) { rebuildSearchAndPlaylist(this as LinearLayoutWidget) }
            bindState(listRevisionState) { rebuildSearchAndPlaylist(this as LinearLayoutWidget) }
        }

        private fun createSettingsView(): ScrollPanelWidget {
            return ScrollPanelWidget(Orientation.VERTICAL).apply {
                sizeMode(SizeMode.MATCH_PARENT)
                setContent(standaloneColumn {
                    spacing = 2f
                    sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    padding(2f, 2f)

                    add("provider", createProviderPanel())
                    add("account", createAccountPanel())
                    add("status", createStatusLine())
                })
            }
        }

        private fun createProviderPanel(): FrameLayoutWidget {
            return standaloneFrame {
                sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                radioGroup("segments") {
                    orientation = Orientation.HORIZONTAL
                    spacing = 2f
                    sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    padding(2f)

                    val qq = add("qq", createProviderSegment(OnlineMusicManager.Provider.QQ))
                    val netease = add("netease", createProviderSegment(OnlineMusicManager.Provider.NETEASE))

                    selectButton(
                        if (OnlineMusicManager.selectedProvider == OnlineMusicManager.Provider.QQ)
                            qq else netease
                    )
                }
            }
        }

        private fun createProviderSegment(provider: OnlineMusicManager.Provider): RadioButtonWidget {
            val radio = RadioButtonWidget()
            radio.layoutParams = LinearLayoutWidget.LayoutParams().weight(1f).height(14f)
            radio.onClickListener = org.academy.api.client.gui.event.OnClickListener {
                OnlineMusicManager.selectProvider(provider)
            }
            val labelText = when (provider) {
                OnlineMusicManager.Provider.QQ -> L10n["app.academy.music_player.provider.qq"]
                OnlineMusicManager.Provider.NETEASE -> L10n["app.academy.music_player.provider.netease"]
            }
            val text = LabelWidget(labelText)
            text.layoutParams = FrameLayoutWidget.LayoutParams()
                .sizeMode(SizeMode.MATCH_PARENT)
                .gravity(Gravity.CENTER)
            radio.addChild("text", text)
            val bg = StateListDrawable()
            bg.addState(Widget.SELECTED, ColorDrawable(TerminalHud.CONTROL_ACTIVE_COLOR))
            bg.addState(Widget.HOVERED, ColorDrawable(TerminalHud.CONTROL_HOVER_COLOR))
            bg.setDefault(ColorDrawable(0))
            radio.background = bg
            return radio
        }

        private fun createAccountPanel(): FrameLayoutWidget {
            return standaloneFrame {
                sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)

                add("content", FrameLayoutWidget().apply {
                    fun rebuild() {
                        clearChildren()
                        add("card", buildAccountCard().apply {
                            sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                            padding(4f, 4f)
                        })
                    }

                    bindState(OnlineMusicManager.revisionState) { rebuild() }
                    sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                })
            }
        }

        private fun buildAccountCard(): Widget {
            val state = OnlineMusicManager.loginState
            if (state == OnlineMusicManager.LoginState.FETCHING
                || state == OnlineMusicManager.LoginState.WAITING
                || state == OnlineMusicManager.LoginState.EXPIRED
                || state == OnlineMusicManager.LoginState.FAILED
            ) return buildLoginFlowCard()
            return if (OnlineMusicManager.isLoggedIn()) buildLoggedInCard() else buildLoggedOutCard()
        }

        private fun buildLoggedOutCard(): LinearLayoutWidget {
            return standaloneRow(6f) {
                sizeMode(SizeMode.MATCH_PARENT)
                image(R.textures.gui.app.music.icon, "icon") {
                    sampler(FilterMode.LINEAR, false)
                    setColor(0.65f, 0.65f, 0.65f)
                    size(20f, 20f)
                    gravity(Gravity.CENTER)
                }
                column("text") {
                    weight(1f)
                    heightMode(SizeMode.MATCH_PARENT)
                    label(L10n["app.academy.music_player.account.not_logged_in"], "title") {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(10f)
                        gravity(Gravity.CENTER_LEFT)
                    }
                    label(L10n["app.academy.music_player.account.not_logged_in_hint"], "hint") {
                        scale = 0.6f
                        widthMode(SizeMode.MATCH_PARENT)
                        height(9f)
                        gravity(Gravity.CENTER_LEFT)
                    }
                }
                add(
                    "login", createStyledButton(
                        L10n["app.academy.music_player.login"], 60f, 14f
                    ) {
                        OnlineMusicManager.startLogin()
                    }) {
                    gravity(Gravity.CENTER)
                }
            }
        }

        private fun buildLoginFlowCard(): LinearLayoutWidget {
            val state = OnlineMusicManager.loginState
            val provider = OnlineMusicManager.selectedProvider
            val hint = when (state) {
                OnlineMusicManager.LoginState.FETCHING -> L10n["app.academy.music_player.account.login_fetching"]
                OnlineMusicManager.LoginState.WAITING -> L10n["app.academy.music_player.account.login_scan_hint"]
                    .replace("%s", providerLabelText(provider))

                OnlineMusicManager.LoginState.EXPIRED -> L10n["app.academy.music_player.account.qr_expired"]
                OnlineMusicManager.LoginState.FAILED -> L10n["app.academy.music_player.account.login_failed"]
                else -> ""
            }
            return standaloneColumn(3f) {
                sizeMode(SizeMode.MATCH_PARENT)
                label(hint, "hint") {
                    scale = 0.62f
                    alpha = 0.85f
                    widthMode(SizeMode.MATCH_PARENT)
                    height(10f)
                    gravity(Gravity.CENTER)
                }
                if (state == OnlineMusicManager.LoginState.WAITING) {
                    add("qr", LoginQrWidget()) {
                        size(56f, 56f)
                        gravity(Gravity.CENTER)
                    }
                }
                row("actions") {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(14f)
                    gravity(Gravity.CENTER)
                    if (state == OnlineMusicManager.LoginState.WAITING
                        || state == OnlineMusicManager.LoginState.EXPIRED
                        || state == OnlineMusicManager.LoginState.FAILED
                    ) {
                        add(
                            "refresh", createStyledButton(
                                L10n["app.academy.music_player.account.refresh_qr"], 58f, 14f
                            ) {
                                OnlineMusicManager.refreshLogin()
                            }) {
                            weight(1f)
                            width(0f)
                        }
                    }
                    add(
                        "cancel", createStyledButton(
                            L10n["app.academy.music_player.account.cancel_login"], 44f, 14f
                        ) {
                            OnlineMusicManager.cancelLogin()
                        }) {
                        weight(1f)
                        width(0f)
                    }
                }
            }
        }

        private fun buildLoggedInCard(): LinearLayoutWidget {
            val provider = OnlineMusicManager.selectedProvider
            val name = OnlineMusicManager.accountDisplayName(provider)
            val sub = "${providerLabelText(provider)} · ${L10n["app.academy.music_player.account.logged_in"]}"
            return standaloneRow(6f) {
                sizeMode(SizeMode.MATCH_PARENT)
                add("avatar", AvatarWidget(provider)) {
                    size(22f, 22f)
                    gravity(Gravity.CENTER)
                }
                column("text") {
                    weight(1f)
                    heightMode(SizeMode.MATCH_PARENT)
                    label(name, "name") {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(10f)
                        gravity(Gravity.CENTER_LEFT)
                    }
                    label(sub, "sub") {
                        scale = 0.6f
                        widthMode(SizeMode.MATCH_PARENT)
                        height(9f)
                        gravity(Gravity.CENTER_LEFT)
                    }
                }
                add(
                    "sync", createStyledButton(
                        L10n["app.academy.music_player.sync"], 54f, 14f,
                        tooltip = L10n["app.academy.music_player.account.sync_tooltip"]
                    ) {
                        OnlineMusicManager.shareCurrentTrack()
                    }) {
                    gravity(Gravity.CENTER)
                }
                add(
                    "logout", createStyledButton(
                        L10n["app.academy.music_player.logout"], 54f, 14f
                    ) {
                        OnlineMusicManager.logout()
                    }) {
                    gravity(Gravity.CENTER)
                }
            }
        }

        private fun createStatusLine(): LabelWidget {
            return LabelWidget("").apply {
                bindState(OnlineMusicManager.revisionState) {
                    val s = OnlineMusicManager.status
                    if (text != s) text = s
                    visibility = if (s.isBlank()) Widget.Visibility.GONE else Widget.Visibility.VISIBLE
                }
                scale = 0.58f
                wrapText = true
                layoutParams = WidgetContainer.LayoutParams()
                    .sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    .padding(2f, 0f)
            }
        }

        private fun providerLabelText(provider: OnlineMusicManager.Provider): String = when (provider) {
            OnlineMusicManager.Provider.QQ -> L10n["app.academy.music_player.provider.qq"]
            OnlineMusicManager.Provider.NETEASE -> L10n["app.academy.music_player.provider.netease"]
        }

        private fun rebuildSearchAndPlaylist(container: LinearLayoutWidget) {
            container.apply {
                clearChildren()

                paddingLeft(1f)
                if (showingSearchResults) {
                    add(
                        "return_list", createTextButton(
                            L10n["app.academy.music_player.back_to_list"], 24f, 0.65f
                        ) {
                            showingSearchResults = false
                            listRevisionState.value += 1
                        }) {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(14f)
                    }
                    label(L10n["app.academy.music_player.search_results"], "search_title") {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(10f)
                    }
                    OnlineMusicManager.searchResults.forEachIndexed { index, entry ->
                        row("result_$index") {
                            spacing = 2f
                            widthMode(SizeMode.MATCH_PARENT)
                            height(24f)
                            label(
                                (if (entry.vip) "[VIP] " else "") + entry.title + " - " + entry.artist,
                                "name"
                            ) {
                                scale = 0.62f
                                weight(1f)
                                height(0f)
                                gravity(Gravity.CENTER_LEFT)
                            }
                            add(
                                "add", createActionButton(
                                    R.textures.gui.icon.add,
                                    L10n["app.academy.music_player.action.add"]
                                ) {
                                    OnlineMusicManager.add(entry)
                                })
                            add(
                                "play", createActionButton(
                                    R.textures.gui.app.music.play,
                                    L10n["app.academy.music_player.action.play"]
                                ) {
                                    OnlineMusicManager.add(entry, true)
                                })
                        }
                    }
                } else {
                    MusicPlayerBackend.getInstance().playlist.forEachIndexed { index, mediaInfo ->
                        val isCurrent = index == MusicPlayerBackend.getInstance().currentTrackIndex
                        add("track_$index", ButtonWidget()) {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(16f)
                            onClick { MusicPlayerBackend.getInstance().play(index) }
                            background = createTrackBackground(isCurrent)
                            isSelected = isCurrent
                            add("content", standaloneRow(2f) {
                                sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)
                                image(AlbumArtworkCache.textureFor(mediaInfo), "icon") {
                                    sampler(FilterMode.LINEAR, false)
                                    size(12f, 12f)
                                    gravity(Gravity.CENTER)
                                }
                                column("info") {
                                    weight(1f)
                                    heightMode(SizeMode.MATCH_PARENT)
                                    gravity(Gravity.CENTER)

                                    add("top", EmptyWidget()) {
                                        weight(1f)
                                    }

                                    label(mediaInfo.name, "name") {
                                        widthMode(SizeMode.MATCH_PARENT)
                                        gravity(Gravity.CENTER_LEFT)

                                        baseFontSize = 6f
                                    }
                                    label(mediaInfo.subtitle, "author") {
                                        widthMode(SizeMode.MATCH_PARENT)
                                        gravity(Gravity.CENTER_LEFT)

                                        baseFontSize = 4f
                                    }

                                    add("bottom", EmptyWidget()) {
                                        weight(1f)
                                    }
                                }
                                label(formatTime(mediaInfo.durationSeconds.toFloat()), "duration") {
                                    scale = 0.6f
                                    width(16f)
                                    height(0f)
                                    gravity(Gravity.CENTER)
                                }
                                if (mediaInfo.provider != "local") {
                                    add(
                                        "remove", createActionButton(
                                            R.textures.gui.icon.close,
                                            L10n["app.academy.music_player.action.remove"],
                                            8f,
                                            Gravity.CENTER
                                        ) {
                                            OnlineMusicManager.remove(mediaInfo)
                                        })
                                }
                            })
                        }
                    }
                }
            }
        }

        private fun search(query: String) {
            if (query.isBlank()) return
            showingSearchResults = true
            listRevisionState.value += 1
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
                size(width, height)
                onClick { action() }
                gravity(Gravity.CENTER)
                label(text) {
                    scale = textScale
                    sizeMode(SizeMode.MATCH_PARENT)
                    gravity(Gravity.CENTER)
                }
            }
        }

        private fun createStyledButton(
            text: String,
            width: Float,
            height: Float,
            textScale: Float = 0.62f,
            tooltip: String? = null,
            action: () -> Unit
        ): ButtonWidget {
            val b = ButtonWidget()
            b.size(width, height)
            val bg = StateListDrawable()
            bg.addState(Widget.PRESSED, ColorDrawable(TerminalHud.CONTROL_ACTIVE_COLOR))
            bg.addState(Widget.HOVERED, ColorDrawable(TerminalHud.CONTROL_HOVER_COLOR))
            bg.setDefault(ColorDrawable(TerminalHud.CONTROL_BASE_COLOR))
            b.background = bg
            if (tooltip != null) b.tooltipText = tooltip
            b.onClick { action() }
            b.add("text", LabelWidget(text)) {
                scale = textScale
                sizeMode(SizeMode.MATCH_PARENT)
                gravity(Gravity.CENTER)
            }
            return b
        }

        private fun createActionButton(
            texture: Identifier,
            tooltip: String,
            size: Float = 22f,
            gravity: Int = Gravity.CENTER_VERTICAL,
            action: () -> Unit
        ): ButtonWidget {
            val b = ButtonWidget()
            b.size(size, size)
            b.gravity(gravity)
            b.tooltipText = tooltip
            b.onClick { action() }
            b.add("icon", ImageWidget(texture)) {
                sampler(FilterMode.LINEAR, false)
                if (texture == R.textures.gui.app.music.play) {
                    translationX = size * 1.25f / 22f
                    translationY = -size * 0.9f / 22f
                }
                size(size * 0.45f, size * 0.45f)
                gravity(Gravity.CENTER)
            }
            return b
        }

        private fun createTrackBackground(isCurrent: Boolean): StateListDrawable {
            val bg = StateListDrawable()
            if (isCurrent) {
                bg.addState(Widget.SELECTED, ColorDrawable(TerminalHud.CONTROL_ACTIVE_COLOR))
            }
            bg.addState(Widget.HOVERED, ColorDrawable(TerminalHud.CONTROL_HOVER_COLOR))
            bg.setDefault(ColorDrawable(TerminalHud.CONTROL_BASE_COLOR))
            return bg
        }

        private class AvatarWidget(private val provider: OnlineMusicManager.Provider) :
            ImageWidget(R.textures.gui.app.music.icon) {
            private var lastUrl: String? = null
            private var lastTexture: Identifier? = null

            init {
                setSampler(FilterMode.LINEAR, false)
                bindState(OnlineMusicManager.revisionState) { refreshAvatar() }
                bindState(AccountAvatarCache.textureState) { refreshAvatar() }
            }

            private fun refreshAvatar() {
                val url = OnlineMusicManager.accountAvatarUrl(provider)
                if (url == lastUrl) return
                lastUrl = url
                if (url == null) {
                    lastTexture = null
                    setTexture(R.textures.gui.app.music.icon)
                    return
                }
                val texture = AccountAvatarCache.textureFor(provider, url)
                if (texture != null && texture != lastTexture) {
                    lastTexture = texture
                    setTexture(texture)
                }
            }

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
        }

        private class LoginQrWidget : ImageWidget(R.textures.gui.app.music.icon) {
            private var uploadedBytes: ByteArray? = null

            init {
                setSampler(FilterMode.NEAREST, false)
                visibility = Widget.Visibility.INVISIBLE
                bindState(OnlineMusicManager.revisionState) { refreshQr() }
            }

            private fun refreshQr() {
                val bytes = OnlineMusicManager.qrBytes
                visibility =
                    if (bytes == null || bytes.isEmpty()) Widget.Visibility.INVISIBLE else Widget.Visibility.VISIBLE
                if (bytes == null || bytes.isEmpty() || bytes === uploadedBytes) return
                runCatching {
                    setTextureSource(
                        UiEnvironment.get().createDynamicTextureSource(
                            AcademyCraft.academy("music_login_qr"), bytes
                        )
                    )
                    uploadedBytes = bytes
                }.onFailure {
                    AcademyCraft.LOGGER.error("Failed to upload music login QR texture", it)
                }
            }
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

                init {
                    setFrameUpdate {
                        updateRot()
                        updateVinylIcon()
                        if (MusicPlayerBackend.getInstance().isPlaying) invalidate()
                        true
                    }
                }
            }
        }

        fun createPlayProgressBar(): SeekBarWidget {
            val progressBar: SeekBarWidget = object : SeekBarWidget() {
                init {
                    setFrameUpdate {
                        if (!isDragging) {
                            val musicPlayerBackend = MusicPlayerBackend.getInstance()
                            val progress = musicPlayerBackend.currentTime / musicPlayerBackend.totalDuration
                            setProgress(min + progress * (max - min))
                        }
                        true
                    }
                }
            }
            progressBar.size(128f, 6f)
            progressBar.gravity(Gravity.CENTER)
            progressBar.setBarColors(TerminalHud.BACKGROUND_COLOR, TerminalHud.PRIMARY_COLOR)
            progressBar.seekListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {}

                override fun onStartTrackingTouch(seekBar: SeekBarWidget) {}

                override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
                    val musicPlayerBackend = MusicPlayerBackend.getInstance()
                    musicPlayerBackend.seek(progressBar.progress / progressBar.max)
                }
            })
            return progressBar
        }

        fun createVolumeArea(): LinearLayoutWidget = standaloneRow {
            sizeMode(SizeMode.MATCH_PARENT)
            val iconImg = image(R.textures.gui.app.music.volume, "icon") {
                sampler(FilterMode.LINEAR, false)
                size(16f, 16f)
                gravity(Gravity.CENTER)
            }
            add("info", object : FrameLayoutWidget() {
                init {
                    setFrameUpdate {
                        visibility = if (iconImg.isHovered || isHovered) Widget.Visibility.VISIBLE
                        else Widget.Visibility.INVISIBLE
                        true
                    }
                }

                override var isHovered: Boolean
                    get() = super.isHovered
                    set(hovered) {
                        if (visibility == Widget.Visibility.VISIBLE) super.isHovered = hovered
                    }
            }) {
                weight(1f)
                width(0f)
                heightMode(SizeMode.MATCH_PARENT)
                val volume = { min(MusicPlayerBackend.getInstance().volume / VOLUME_SCALE, 1f) }
                val textLabel = label("${(volume() * 100).roundToInt()}%", "text") {
                    scale = 0.75f
                    marginTop(8f)
                    gravity(Gravity.CENTER)
                }

                fun setText(progress: Float) {
                    textLabel.text = "${(progress * 100f).roundToInt()}%"
                }

                seekBar("bar") {
                    size(48f, 4f)
                    marginBottom(2f)
                    gravity(Gravity.CENTER)
                    setBarColors(TerminalHud.BACKGROUND_COLOR, TerminalHud.PRIMARY_COLOR)
                    setProgress(volume() * max)
                    seekListener(object : OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                            val musicPlayerBackend = MusicPlayerBackend.getInstance()
                            val p = progress / seekBar.max
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
                }
            }
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
                val texture = AlbumArtworkCache.textureFor(mediaInfo)
                if (texture != lastVinylArtwork) {
                    lastVinylArtwork = texture
                    vinyl.setTexture(texture)
                    vinyl.setSampler(FilterMode.LINEAR, false)
                }
            }
        }

        fun formatTime(totalSeconds: Float): String {
            if (totalSeconds.isNaN() || totalSeconds < 0) return "00:00"
            return "%02d:%02d".format((totalSeconds / 60).toInt(), (totalSeconds % 60).toInt())
        }
    }
}
