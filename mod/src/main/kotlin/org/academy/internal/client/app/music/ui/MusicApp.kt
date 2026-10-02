package org.academy.internal.client.app.music.ui

import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.textures.GpuSampler
import com.mojang.renderpearl.api.textures.GpuTextureView
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
import org.academy.api.client.gui.text.model.Ellipsize
import org.academy.api.client.gui.texture.IdentifierTextureSource
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
import org.academy.internal.client.app.music.data.MusicInfo
import org.academy.internal.client.app.music.session.JukeboxSessionController
import org.academy.internal.client.app.music.session.RoomSessionController
import org.academy.internal.client.app.music.session.SharedAccountClient
import org.academy.internal.common.music.SharedTrackEntry
import org.academy.internal.common.network.MusicJukeboxPackets
import kotlin.math.min
import kotlin.math.roundToInt

object MusicApp : App {
    override fun createContext(): WidgetContext {
        return Context()
    }

    override fun name(): String {
        return L10n["app.academy.music_player.name"]
    }

    override fun icon(): Identifier {
        return R.textures.gui.app.music.icon
    }

    private enum class ViewMode {
        NORMAL,
        SETTINGS,
        ROOM,
        JUKEBOX
    }

    private class Context : WidgetContext {
        private var showingSearchResults = false
        private var viewMode = ViewMode.NORMAL
        private val listRevisionState = UiState(0)
        private var playlistBuildContainer: LinearLayoutWidget? = null
        private var playlistBuildKey: String? = null
        private lateinit var searchBox: TextInputWidget
        private lateinit var searchButton: ButtonWidget
        private var settingsTitle: TextWidget? = null
        private var moreIcon: ImageWidget? = null
        private var roomNameBox: TextInputWidget? = null
        private var inviteNameBox: TextInputWidget? = null
        private var roomSearchBox: TextInputWidget? = null
        private var roomShowingSearch = false
        private val roomRevisionState = UiState(0)

        private var roomStructureKey: String? = null
        private var jukeboxShowingPlaylist = false
        private var sharedAccountHint = ""
        private var pendingRoomEntry: SharedTrackEntry? = null
        private val viewRevisionState = UiState(0)
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
            .setDuration(R.ui.music_player.vinyl_rotation_ms)
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
                lp {
                    matchParent()
                    paddingHorizontal(2f)
                }

                column("root", spacing = 1f) {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT)
                    }

                    row("bar_top", spacing = 2f) {
                        lp {
                            sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                        }

                        button("button_back") {
                            lp {
                                size(R.ui.music_player.button_size, R.ui.music_player.button_size)
                                margin(2f, 2f, 2f, 0f)
                            }

                            onClick { TerminalHud.INSTANCE.closeApp() }

                            image(R.textures.gui.icon.arrow, "icon") {
                                lp {
                                    sizeMode(SizeMode.MATCH_PARENT)
                                }
                            }
                        }

                        searchBox = textBox(R.ui.music_player.search_max_length, "box_search") {
                            lp {
                                height(R.ui.music_player.row_height)
                                gravity(Gravity.CENTER_VERTICAL)
                                padding(2f, 0f)
                            }

                            weight(1f)
                            gravity = Gravity.CENTER_VERTICAL

                            enter { search(it) }
                            clearOnEnter(false)
                        }

                        searchButton = createTextButton(L10n["app.academy.music_player.search"], 24f) {
                            search(searchBox.text)
                        }

                        add("button_search", searchButton)

                        settingsTitle = text(L10n["app.academy.music_player.settings.title"], "title_settings") {
                            lp {
                                height(R.ui.music_player.row_height)
                                gravity(Gravity.CENTER)
                            }

                            weight(1f)
                            gravity = Gravity.CENTER
                            visibility = Widget.Visibility.GONE
                        }

                        button("button_room") {
                            lp {
                                size(R.ui.music_player.button_size, R.ui.music_player.button_size)
                                margin(2f, 2f, 2f, 0f)
                            }

                            tooltipText = L10n["app.academy.music_player.room.title"]
                            onClick {
                                RoomSessionController.requestRoomList()
                                setViewMode(ViewMode.ROOM)
                            }

                            image(R.textures.gui.icon.icon_connected, "icon") {
                                lp {
                                    sizeMode(SizeMode.MATCH_PARENT)
                                }
                            }
                        }

                        button("button_jukebox") {
                            lp {
                                size(R.ui.music_player.button_size, R.ui.music_player.button_size)
                                margin(2f, 2f, 2f, 0f)
                            }

                            tooltipText = L10n["app.academy.music_player.jukebox.title"]
                            onClick {
                                JukeboxSessionController.requestState()
                                setViewMode(ViewMode.JUKEBOX)
                            }

                            image(R.textures.gui.icon.menu, "icon") {
                                lp {
                                    sizeMode(SizeMode.MATCH_PARENT)
                                }
                            }
                        }

                        button("button_more") {
                            lp {
                                size(R.ui.music_player.button_size, R.ui.music_player.button_size)
                                margin(2f, 2f, 2f, 0f)
                            }

                            onClick {
                                setViewMode(if (viewMode == ViewMode.NORMAL) ViewMode.SETTINGS else ViewMode.NORMAL)
                            }

                            moreIcon = image(R.textures.gui.icon.more, "icon") {
                                lp {
                                    sizeMode(SizeMode.MATCH_PARENT)
                                }
                            }
                        }
                    }

                    fill(R.ui.terminal_hud.primary_color, "line_split") {
                        lp {
                            height(1f)
                            widthMode(SizeMode.MATCH_PARENT)
                        }
                    }

                    mainContainer = frame {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                        }

                        weight(1f)

                        bindState(RoomSessionController.roomStateUi) { consumePendingRoomEntry() }

                        add("content", createNormalView())
                    }
                }
            }
        }

        fun setViewMode(mode: ViewMode) {
            viewMode = mode
            mainContainer.replace(
                "content",
                when (mode) {
                    ViewMode.NORMAL -> createNormalView()
                    ViewMode.SETTINGS -> createSettingsView()
                    ViewMode.ROOM -> createRoomView()
                    ViewMode.JUKEBOX -> createJukeboxView()
                }
            )
            updateTopBarMode()
        }

        private fun updateTopBarMode() {
            val isNormal = viewMode == ViewMode.NORMAL
            searchBox.isEnabled = isNormal
            searchButton.isEnabled = isNormal
            searchBox.visibility = if (isNormal) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            searchButton.visibility = if (isNormal) Widget.Visibility.VISIBLE else Widget.Visibility.GONE
            settingsTitle?.text = when (viewMode) {
                ViewMode.SETTINGS -> L10n["app.academy.music_player.settings.title"]
                ViewMode.ROOM -> L10n["app.academy.music_player.room.title"]
                ViewMode.JUKEBOX -> L10n["app.academy.music_player.jukebox.title"]
                ViewMode.NORMAL -> ""
            }
            settingsTitle?.visibility = if (isNormal) Widget.Visibility.GONE else Widget.Visibility.VISIBLE
            moreIcon?.setTexture(if (isNormal) R.textures.gui.icon.more else R.textures.gui.icon.close)
            moreIcon?.parent?.let {
                (it as? ButtonWidget)?.tooltipText =
                    if (isNormal) null else L10n["app.academy.music_player.settings.back"]
            }
        }

        fun createNormalView(): LinearLayoutWidget = standaloneRow {
            lp {
                sizeMode(SizeMode.MATCH_PARENT)
            }

            column {
                text(L10n["app.academy.music_player.track_list"], "title_playlist")

                scrollPanel(Orientation.VERTICAL, "area_list_music", createPlaylistPanel()) {
                    lp {
                        width(R.ui.music_player.list_width)
                    }

                    weight(1f)
                }
            }

            frame {
                lp {
                    heightMode(SizeMode.MATCH_PARENT)
                }

                weight(1f)

                add("vinyl", vinyl) {
                    lp {
                        size(R.ui.music_player.vinyl_size, R.ui.music_player.vinyl_size)
                        margin(0f, 0f, 0f, 32f)
                        gravity(Gravity.CENTER)
                    }

                    sampler(FilterMode.NEAREST, false)
                }

                updateVinylIcon()
                updateRot()

                column("area_info") {
                    lp {
                        size(R.ui.music_player.info_area_width, R.ui.music_player.info_area_height)
                        margin(0f, 0f, 0f, 4f)
                        gravity(Gravity.CENTER_BOTTOM)
                    }

                    add("meta", LinearLayoutWidget()) {
                        lp {
                            sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                        }

                        orientation = Orientation.VERTICAL

                        val titleText = text("", "title") {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                gravity(Gravity.CENTER)
                            }

                            ellipsize = Ellipsize.MARQUEE
                            marqueeFadeSize = 6f
                            gravity = Gravity.CENTER
                        }

                        val artistText = text("", "artist") {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                gravity(Gravity.CENTER)
                            }

                            textSize = 6f
                            ellipsize = Ellipsize.MARQUEE
                            marqueeFadeSize = 6f
                            gravity = Gravity.CENTER
                        }

                        bindState(MusicPlayerBackend.getInstance().uiState) {
                            val mi = MusicPlayerBackend.getInstance().currentMusicInfo
                            titleText.text = mi?.name ?: ""
                            artistText.text = mi?.subtitle ?: ""
                        }
                    }

                    row("area_info_progress", spacing = 4f) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(12f)
                        }

                        add("time_current", TextWidget("00:00").apply {
                            setFrameUpdate {
                                text = formatTime(MusicPlayerBackend.getInstance().currentTime)
                                true
                            }
                        }) {
                            lp {
                                width(0f)
                                gravity(Gravity.CENTER)
                            }

                            weight(1f)
                            gravity = Gravity.CENTER
                        }

                        add("bar_progress_play", createPlayProgressBar())

                        add("duration_music", TextWidget("00:00").apply {
                            setFrameUpdate {
                                text = formatTime(MusicPlayerBackend.getInstance().totalDuration)
                                true
                            }
                        }) {
                            lp {
                                width(0f)
                                gravity(Gravity.CENTER)
                            }

                            weight(1f)
                            gravity = Gravity.CENTER
                        }
                    }

                    row("area_control", spacing = 8f) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(16f)
                        }

                        frame {
                            lp {
                                width(0f)
                                heightMode(SizeMode.MATCH_PARENT)
                            }

                            weight(1f)

                            button("mode_playback") {
                                lp {
                                    size(R.ui.music_player.button_size, R.ui.music_player.button_size)
                                    gravity(Gravity.CENTER_RIGHT)
                                }

                                onClick { MusicPlayerBackend.getInstance().cyclePlaybackMode() }

                                add("icon", playbackModeIcon) {
                                    sampler(FilterMode.LINEAR, false)
                                }
                            }
                        }

                        button("previous") {
                            lp {
                                size(R.ui.music_player.button_size, R.ui.music_player.button_size)
                                gravity(Gravity.CENTER)
                            }

                            onClick { runLocalTransport { MusicPlayerBackend.getInstance().playPrevious() } }

                            image(R.textures.gui.app.music.previous) {
                                sampler(FilterMode.LINEAR, false)
                            }
                        }

                        button("play_pause") {
                            lp {
                                size(R.ui.music_player.button_size, R.ui.music_player.button_size)
                                gravity(Gravity.CENTER)
                            }

                            onClick { runLocalTransport { MusicPlayerBackend.getInstance().togglePlayPause() } }

                            add("icon", playPauseIcon) {
                                sampler(FilterMode.LINEAR, false)
                            }
                        }

                        button("next") {
                            lp {
                                size(R.ui.music_player.button_size, R.ui.music_player.button_size)
                                gravity(Gravity.CENTER)
                            }

                            onClick { runLocalTransport { MusicPlayerBackend.getInstance().playNext() } }

                            image(R.textures.gui.app.music.next) {
                                sampler(FilterMode.LINEAR, false)
                            }
                        }

                        frame {
                            lp {
                                width(0f)
                                heightMode(SizeMode.MATCH_PARENT)
                            }

                            weight(1f)

                            add("area_volume", createVolumeArea())
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
            bindState(JukeboxSessionController.jukeboxStateUi) { rebuildSearchAndPlaylist(this as LinearLayoutWidget) }
        }

        private fun createSettingsView(): ScrollPanelWidget {
            return ScrollPanelWidget(Orientation.VERTICAL).apply {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT)
                }

                setContent(standaloneColumn(spacing = 2f) {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                        padding(2f, 2f)
                    }

                    add("provider", createProviderPanel())
                    add("account", createAccountPanel())
                    add("account_shared", createSharedAccountPanel())
                    add("status", createStatusLine())
                })
            }
        }

        private fun createProviderPanel(): FrameLayoutWidget {
            return standaloneFrame {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                }

                radioGroup("segments") {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                        padding(2f)
                    }

                    orientation = Orientation.HORIZONTAL
                    spacing = 2f

                    val qq = add("qq", createProviderSegment(OnlineMusicManager.Provider.QQ)) {
                        weight(1f)
                    }
                    val netease = add("netease", createProviderSegment(OnlineMusicManager.Provider.NETEASE)) {
                        weight(1f)
                    }

                    selectButton(
                        if (OnlineMusicManager.selectedProvider == OnlineMusicManager.Provider.QQ)
                            qq else netease
                    )
                }
            }
        }

        private fun createProviderSegment(provider: OnlineMusicManager.Provider): RadioButtonWidget {
            val radio = RadioButtonWidget()
            radio.lp {
                height(R.ui.music_player.row_height)
            }
            radio.onClickListener = org.academy.api.client.gui.event.OnClickListener {
                OnlineMusicManager.selectProvider(provider)
            }
            val labelText = when (provider) {
                OnlineMusicManager.Provider.QQ -> L10n["app.academy.music_player.provider.qq"]
                OnlineMusicManager.Provider.NETEASE -> L10n["app.academy.music_player.provider.netease"]
            }
            radio.add("text", TextWidget(labelText)) {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT)
                    gravity(Gravity.CENTER)
                }
            }
            val bg = StateListDrawable()
            bg.addState(Widget.SELECTED, ColorDrawable(R.ui.terminal_hud.control_active_color))
            bg.addState(Widget.HOVERED, ColorDrawable(R.ui.terminal_hud.control_hover_color))
            bg.setDefault(ColorDrawable(0))
            radio.background = bg
            return radio
        }

        private fun createAccountPanel(): FrameLayoutWidget {
            return standaloneFrame {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                }

                add("content", FrameLayoutWidget()) {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    }

                    fun rebuild() {
                        clearChildren()
                        add("card", buildAccountCard()) {
                            lp {
                                sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                                padding(4f, 4f)
                            }
                        }
                    }

                    bindState(OnlineMusicManager.revisionState) { rebuild() }
                }
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
            return standaloneRow(spacing = 6f) {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT)
                }

                image(R.textures.gui.app.music.icon, "icon") {
                    lp {
                        size(20f, 20f)
                        gravity(Gravity.CENTER)
                    }

                    sampler(FilterMode.LINEAR, false)
                    setColor(0.65f, 0.65f, 0.65f)
                }

                column("text") {
                    lp {
                        heightMode(SizeMode.MATCH_PARENT)
                    }

                    weight(1f)

                    text(L10n["app.academy.music_player.account.not_logged_in"], "title") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(10f)
                            gravity(Gravity.CENTER_LEFT)
                        }

                        gravity = Gravity.CENTER_LEFT
                    }

                    text(L10n["app.academy.music_player.account.not_logged_in_hint"], "hint") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(9f)
                            gravity(Gravity.CENTER_LEFT)
                        }

                        scaleX = 0.6f
                        scaleY = 0.6f
                        gravity = Gravity.CENTER_LEFT
                    }
                }

                add(
                    "login", createStyledButton(
                        L10n["app.academy.music_player.login"], 60f, 14f
                    ) {
                        OnlineMusicManager.startLogin()
                    }) {
                    lp {
                        gravity(Gravity.CENTER)
                    }
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
            return standaloneColumn(spacing = 3f) {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT)
                }

                text(hint, "hint") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(10f)
                        gravity(Gravity.CENTER)
                    }

                    scaleX = 0.62f
                    scaleY = 0.62f
                    alpha = 0.85f
                    gravity = Gravity.CENTER
                }

                if (state == OnlineMusicManager.LoginState.WAITING) {
                    add("qr", LoginQrWidget()) {
                        lp {
                            size(56f, 56f)
                            gravity(Gravity.CENTER)
                        }
                    }
                }

                row("actions") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(14f)
                        gravity(Gravity.CENTER)
                    }

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
                            lp {
                                width(0f)
                            }

                            weight(1f)
                        }
                    }

                    add(
                        "cancel", createStyledButton(
                            L10n["app.academy.music_player.account.cancel_login"], 44f, 14f
                        ) {
                            OnlineMusicManager.cancelLogin()
                        }) {
                        lp {
                            width(0f)
                        }

                        weight(1f)
                    }
                }
            }
        }

        private fun buildLoggedInCard(): LinearLayoutWidget {
            val provider = OnlineMusicManager.selectedProvider
            val name = OnlineMusicManager.accountDisplayName(provider)
            val sub = "${providerLabelText(provider)} · ${L10n["app.academy.music_player.account.logged_in"]}"
            return standaloneRow(spacing = 6f) {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT)
                }

                add("avatar", AvatarWidget(provider)) {
                    lp {
                        size(22f, 22f)
                        gravity(Gravity.CENTER)
                    }
                }

                column("text") {
                    lp {
                        heightMode(SizeMode.MATCH_PARENT)
                    }

                    weight(1f)

                    text(name, "name") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(10f)
                            gravity(Gravity.CENTER_LEFT)
                        }

                        gravity = Gravity.CENTER_LEFT
                    }

                    text(sub, "sub") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(9f)
                            gravity(Gravity.CENTER_LEFT)
                        }

                        scaleX = 0.6f
                        scaleY = 0.6f
                        gravity = Gravity.CENTER_LEFT
                    }
                }

                add(
                    "sync", createStyledButton(
                        L10n["app.academy.music_player.sync"], 54f, 14f,
                        tooltip = L10n["app.academy.music_player.account.sync_tooltip"]
                    ) {
                        OnlineMusicManager.shareCurrentTrack()
                    }) {
                    lp {
                        gravity(Gravity.CENTER)
                    }
                }

                add(
                    "logout", createStyledButton(
                        L10n["app.academy.music_player.logout"], 54f, 14f
                    ) {
                        OnlineMusicManager.logout()
                    }) {
                    lp {
                        gravity(Gravity.CENTER)
                    }
                }
            }
        }

        private fun createStatusLine(): TextWidget {
            return TextWidget("").apply {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    padding(2f, 0f)
                }

                scaleX = 0.58f
                scaleY = 0.58f
                singleLine = false

                bindState(OnlineMusicManager.revisionState) {
                    val s = OnlineMusicManager.status
                    if (text != s) text = s
                    visibility = if (s.isBlank()) Widget.Visibility.GONE else Widget.Visibility.VISIBLE
                }
            }
        }

        private fun providerLabelText(provider: OnlineMusicManager.Provider): String = when (provider) {
            OnlineMusicManager.Provider.QQ -> L10n["app.academy.music_player.provider.qq"]
            OnlineMusicManager.Provider.NETEASE -> L10n["app.academy.music_player.provider.netease"]
        }

        private fun rebuildSearchAndPlaylist(container: LinearLayoutWidget) {
            val backend = MusicPlayerBackend.getInstance()
            val key = buildString {
                append(showingSearchResults).append('|')
                append(backend.currentTrackIndex).append('|')
                append(backend.playlistRevision).append('|')
                append(OnlineMusicManager.revisionState.value).append('|')
                append(JukeboxSessionController.state?.enabled).append('|')
            }
            if (container === playlistBuildContainer && key == playlistBuildKey) return
            playlistBuildContainer = container
            playlistBuildKey = key

            container.apply {
                clearChildren()

                lp {
                    paddingLeft(1f)
                }

                if (showingSearchResults) {
                    add(
                        "list_return", createTextButton(
                            L10n["app.academy.music_player.back_to_list"], 24f, 0.65f
                        ) {
                            showingSearchResults = false
                            listRevisionState.value += 1
                        }) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(14f)
                        }
                    }

                    text(L10n["app.academy.music_player.search_results"], "title_search") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(10f)
                        }
                    }

                    OnlineMusicManager.searchResults.forEachIndexed { index, entry ->
                        row("result_$index", spacing = 2f) {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                height(24f)
                            }

                            text(
                                (if (entry.vip) "[VIP] " else "") + entry.title + " - " + entry.artist,
                                "name"
                            ) {
                                lp {
                                    height(0f)
                                    gravity(Gravity.CENTER_LEFT)
                                }

                                weight(1f)
                                ellipsize = Ellipsize.MARQUEE
                                marqueeFadeSize = 6f
                                gravity = Gravity.CENTER_LEFT
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

                            add(
                                "room", createActionButton(
                                    R.textures.gui.icon.icon_connected,
                                    L10n["app.academy.music_player.action.add_to_room"]
                                ) {
                                    sendToRoomOrOpenView(entry.toSharedTrackEntry())
                                })

                            if (JukeboxSessionController.state?.enabled == true) {
                                add(
                                    "request", createActionButton(
                                        R.textures.gui.icon.arrow,
                                        L10n["app.academy.music_player.jukebox.action.request"]
                                    ) {
                                        JukeboxSessionController.requestTrack(entry.toSharedTrackEntry())
                                    })
                            }
                        }
                    }
                } else {
                    MusicPlayerBackend.getInstance().playlist.forEachIndexed { index, mediaInfo ->
                        val isCurrent = index == MusicPlayerBackend.getInstance().currentTrackIndex
                        add("track_$index", ButtonWidget()) {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                height(16f)
                            }

                            background = createTrackBackground(isCurrent)
                            isSelected = isCurrent
                            onClick { runLocalTransport { MusicPlayerBackend.getInstance().play(index) } }

                            add("content", standaloneRow(spacing = 2f) {
                                lp {
                                    sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)
                                }

                                image(AlbumArtworkCache.textureFor(mediaInfo), "icon") {
                                    lp {
                                        size(12f, 12f)
                                        gravity(Gravity.CENTER)
                                    }

                                    sampler(FilterMode.LINEAR, false)
                                }

                                column("info") {
                                    lp {
                                        heightMode(SizeMode.MATCH_PARENT)
                                        gravity(Gravity.CENTER)
                                    }

                                    weight(1f)

                                    add("top", EmptyWidget()) {
                                        weight(1f)
                                    }

                                    text(mediaInfo.name, "name") {
                                        lp {
                                            widthMode(SizeMode.MATCH_PARENT)
                                            gravity(Gravity.CENTER_LEFT)
                                        }

                                        textSize = 6f
                                        ellipsize = Ellipsize.MARQUEE
                                        marqueeFadeSize = 6f
                                        gravity = Gravity.CENTER_LEFT
                                    }

                                    text(mediaInfo.subtitle, "author") {
                                        lp {
                                            widthMode(SizeMode.MATCH_PARENT)
                                            gravity(Gravity.CENTER_LEFT)
                                        }

                                        textSize = 4f
                                        ellipsize = Ellipsize.MARQUEE
                                        marqueeFadeSize = 6f
                                        gravity = Gravity.CENTER_LEFT
                                    }

                                    add("bottom", EmptyWidget()) {
                                        weight(1f)
                                    }
                                }

                                text(formatTime(mediaInfo.durationSeconds.toFloat()), "duration") {
                                    lp {
                                        width(16f)
                                        height(0f)
                                        gravity(Gravity.CENTER)
                                    }

                                    scaleX = 0.6f
                                    scaleY = 0.6f
                                    gravity = Gravity.CENTER
                                }

                                val sharedEntry = mediaInfo.toSharedTrackEntry()
                                if (sharedEntry != null) {
                                    add(
                                        "room", createActionButton(
                                            R.textures.gui.icon.icon_connected,
                                            L10n["app.academy.music_player.action.add_to_room"],
                                            10f,
                                            Gravity.CENTER
                                        ) {
                                            sendToRoomOrOpenView(sharedEntry)
                                        })
                                    if (JukeboxSessionController.state?.enabled == true) {
                                        add(
                                            "request", createActionButton(
                                                R.textures.gui.icon.arrow,
                                                L10n["app.academy.music_player.jukebox.action.request"],
                                                10f,
                                                Gravity.CENTER
                                            ) {
                                                JukeboxSessionController.requestTrack(sharedEntry)
                                            })
                                    }
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
            height: Float = R.ui.music_player.row_height,
            action: () -> Unit
        ): ButtonWidget {
            return ButtonWidget().apply {
                lp {
                    size(width, height)
                    gravity(Gravity.CENTER)
                }

                onClick { action() }

                text(text) {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT)
                        gravity(Gravity.CENTER)
                    }

                    scaleX = textScale
                    scaleY = textScale
                    gravity = Gravity.CENTER
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
            b.lp {
                size(width, height)
            }
            val bg = StateListDrawable()
            bg.addState(Widget.PRESSED, ColorDrawable(R.ui.terminal_hud.control_active_color))
            bg.addState(Widget.HOVERED, ColorDrawable(R.ui.terminal_hud.control_hover_color))
            bg.setDefault(ColorDrawable(R.ui.terminal_hud.control_base_color))
            b.background = bg
            if (tooltip != null) b.tooltipText = tooltip
            b.onClick { action() }
            b.add("text", TextWidget(text)) {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT)
                    gravity(Gravity.CENTER)
                }

                scaleX = textScale
                scaleY = textScale
                gravity = Gravity.CENTER
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
            b.lp {
                size(size, size)
                gravity(gravity)
            }
            b.tooltipText = tooltip
            b.onClick { action() }
            b.add("icon", ImageWidget(IdentifierTextureSource(texture))) {
                lp {
                    size(size * 0.45f, size * 0.45f)
                    gravity(Gravity.CENTER)
                }

                sampler(FilterMode.LINEAR, false)
                if (texture == R.textures.gui.app.music.play) {
                    translationX = size * 1.25f / 22f
                    translationY = -size * 0.9f / 22f
                }
            }
            return b
        }

        private fun createTrackBackground(isCurrent: Boolean): StateListDrawable {
            val bg = StateListDrawable()
            if (isCurrent) {
                bg.addState(Widget.SELECTED, ColorDrawable(R.ui.terminal_hud.control_active_color))
            }
            bg.addState(Widget.HOVERED, ColorDrawable(R.ui.terminal_hud.control_hover_color))
            bg.setDefault(ColorDrawable(R.ui.terminal_hud.control_base_color))
            return bg
        }

        private class AvatarWidget(private val provider: OnlineMusicManager.Provider) :
            ImageWidget(IdentifierTextureSource(R.textures.gui.app.music.icon)) {
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

        private class LoginQrWidget : ImageWidget(IdentifierTextureSource(R.textures.gui.app.music.icon)) {
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

        private class VolumeInfoWidget(private val iconWidget: ImageWidget) : FrameLayoutWidget() {
            override var isHovered: Boolean
                get() = super.isHovered
                set(hovered) {
                    if (visibility == Widget.Visibility.VISIBLE) super.isHovered = hovered
                }

            init {
                setFrameUpdate {
                    visibility = if (iconWidget.isHovered || isHovered) Widget.Visibility.VISIBLE
                    else Widget.Visibility.INVISIBLE
                    true
                }
            }
        }

        fun createVinyl(): ImageWidget {
            return object : ImageWidget(IdentifierTextureSource(R.textures.gui.app.music.now_playing)) {
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
            progressBar.lp {
                size(R.ui.music_player.progress_width, R.ui.music_player.progress_height)
                gravity(Gravity.CENTER)
            }
            progressBar.setBarColors(R.ui.terminal_hud.background_color, R.ui.terminal_hud.primary_color)
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
            lp {
                sizeMode(SizeMode.MATCH_PARENT)
            }

            val iconImg = image(R.textures.gui.app.music.volume, "icon") {
                lp {
                    size(R.ui.music_player.button_size, R.ui.music_player.button_size)
                    gravity(Gravity.CENTER)
                }

                sampler(FilterMode.LINEAR, false)
            }

            add("info", VolumeInfoWidget(iconImg)) {
                lp {
                    width(0f)
                    heightMode(SizeMode.MATCH_PARENT)
                }

                weight(1f)

                val volume = { min(MusicPlayerBackend.getInstance().volume / R.ui.music_player.volume_scale, 1f) }
                val textLabel = text("${(volume() * 100).roundToInt()}%", "text") {
                    lp {
                        marginTop(8f)
                        gravity(Gravity.CENTER)
                    }

                    scaleX = 0.75f
                    scaleY = 0.75f
                    gravity = Gravity.CENTER
                }

                fun setText(progress: Float) {
                    textLabel.text = "${(progress * 100f).roundToInt()}%"
                }

                seekBar("bar") {
                    lp {
                        size(R.ui.music_player.volume_bar_width, R.ui.music_player.volume_bar_height)
                        marginBottom(2f)
                        gravity(Gravity.CENTER)
                    }

                    setBarColors(R.ui.terminal_hud.background_color, R.ui.terminal_hud.primary_color)
                    setProgress(volume() * max)
                    seekListener(object : OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {
                            val musicPlayerBackend = MusicPlayerBackend.getInstance()
                            val p = progress / seekBar.max
                            musicPlayerBackend.volume = p * R.ui.music_player.volume_scale
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
            if (isPlaying) {
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

        private fun createRoomView(): FrameLayoutWidget {
            return standaloneFrame {
                lp {
                    matchParent()
                }

                add("content_room", FrameLayoutWidget()) {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT)
                    }

                    bindState(RoomSessionController.roomStateUi) {
                        val key = currentRoomStructureKey()
                        if (key != roomStructureKey) {
                            roomStructureKey = key
                            replace("body_room", buildRoomContent())
                        }
                    }
                    bindState(roomRevisionState) { replace("body_room", buildRoomContent()) }

                    add("body_room", buildRoomContent())
                }
            }
        }

        private fun currentRoomStructureKey(): String {
            val state = RoomSessionController.roomState ?: return "lobby"
            return "inside:${state.roomCode}:${state.roomName}:${state.isHost}:${state.hostName}"
        }

        private fun buildRoomContent(): Widget {
            return if (RoomSessionController.roomState == null) buildRoomLobby() else buildRoomInside()
        }

        private fun buildRoomLobby(): LinearLayoutWidget {
            return standaloneColumn(spacing = 2f) {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT)
                    padding(2f, 2f)
                }

                row("area_create", spacing = 2f) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(14f)
                    }

                    roomNameBox = textBox(R.ui.music_player.search_max_length, "box_name_room") {
                        lp {
                            width(0f)
                            height(14f)
                            gravity(Gravity.CENTER_VERTICAL)
                            padding(2f, 0f)
                        }

                        weight(1f)
                        gravity = Gravity.CENTER_VERTICAL

                        enter { createRoomFromInput(it) }
                        clearOnEnter(false)
                    }

                    add("create", createStyledButton(L10n["app.academy.music_player.room.create"], 38f, 14f) {
                        createRoomFromInput(roomNameBox?.text)
                    }) {
                        lp {
                            gravity(Gravity.CENTER)
                        }
                    }

                    add("refresh", createStyledButton(L10n["app.academy.music_player.room.refresh"], 28f, 14f) {
                        RoomSessionController.requestRoomList()
                    }) {
                        lp {
                            gravity(Gravity.CENTER)
                        }
                    }
                }

                add("invites_pending", createPendingPanel(onlyApply = false))

                text(L10n["app.academy.music_player.room.list.title"], "title_list") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(10f)
                    }
                }

                scrollPanel(Orientation.VERTICAL, "list_room", createRoomListPanel()) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                    }

                    weight(1f)
                }
            }
        }

        private fun createRoomListPanel(): LinearLayoutWidget {
            return LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                bindState(RoomSessionController.roomStateUi) { rebuildRoomList(this as LinearLayoutWidget) }
            }
        }

        private fun createPendingPanel(onlyApply: Boolean): LinearLayoutWidget {
            return LinearLayoutWidget().apply {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    heightMode(SizeMode.WRAP_CONTENT)
                }

                orientation = Orientation.VERTICAL
                spacing = 2f

                bindState(RoomSessionController.roomStateUi) {
                    rebuildPendingPanel(
                        this as LinearLayoutWidget,
                        onlyApply
                    )
                }
            }
        }

        private fun rebuildPendingPanel(container: LinearLayoutWidget, onlyApply: Boolean) {
            container.apply {
                clearChildren()
                val notices = RoomSessionController.pendingNotices.filter { it.apply() == onlyApply }
                notices.forEachIndexed { index, notice ->
                    add("invite_pending_$index", standaloneRow(spacing = 2f) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(12f)
                        }

                        text(
                            String.format(
                                L10n[
                                    if (notice.apply()) "app.academy.music_player.room.apply_card"
                                    else "app.academy.music_player.room.invite_card"
                                ],
                                notice.otherPlayer(),
                                notice.roomName()
                            ),
                            "text"
                        ) {
                            lp {
                                width(0f)
                                height(0f)
                                gravity(Gravity.CENTER_LEFT)
                            }

                            weight(1f)
                            scaleX = 0.58f
                            scaleY = 0.58f
                            gravity = Gravity.CENTER_LEFT
                        }

                        add(
                            "accept", createStyledButton(
                                L10n["ui.academy.music_room.accept"], 30f, 11f, 0.58f
                            ) {
                                RoomSessionController.respondToken(notice.token(), true)
                            }) {
                            lp {
                                gravity(Gravity.CENTER)
                            }
                        }

                        add(
                            "reject", createStyledButton(
                                L10n["ui.academy.music_room.reject"], 30f, 11f, 0.58f
                            ) {
                                RoomSessionController.respondToken(notice.token(), false)
                            }) {
                            lp {
                                gravity(Gravity.CENTER)
                            }
                        }
                    })
                }
            }
        }

        private fun rebuildRoomList(container: LinearLayoutWidget) {
            container.apply {
                clearChildren()
                val rooms = RoomSessionController.roomList
                if (rooms.isEmpty()) {
                    text(L10n["app.academy.music_player.room.list.empty"], "empty") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(12f)
                            gravity(Gravity.CENTER)
                        }

                        scaleX = 0.62f
                        scaleY = 0.62f
                        gravity = Gravity.CENTER
                    }
                    return
                }
                rooms.forEachIndexed { index, room ->
                    add("room_$index", ButtonWidget()) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(16f)
                        }

                        background = createTrackBackground(false)

                        add("content", standaloneRow(spacing = 2f) {
                            lp {
                                sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)
                            }

                            text("[${room.code()}]", "code") {
                                lp {
                                    width(22f)
                                    height(0f)
                                    gravity(Gravity.CENTER)
                                }

                                scaleX = 0.62f
                                scaleY = 0.62f
                                gravity = Gravity.CENTER
                            }

                            column("info") {
                                lp {
                                    heightMode(SizeMode.MATCH_PARENT)
                                    gravity(Gravity.CENTER)
                                }

                                weight(1f)

                                add("name", createMarqueeText(room.name()).apply {
                                    textSize = 6f
                                }) {
                                    lp {
                                        widthMode(SizeMode.MATCH_PARENT)
                                        gravity(Gravity.CENTER_LEFT)
                                    }

                                    gravity = Gravity.CENTER_LEFT
                                }

                                text(
                                    "${room.hostName()} · ${room.memberCount()}",
                                    "sub"
                                ) {
                                    lp {
                                        widthMode(SizeMode.MATCH_PARENT)
                                        gravity(Gravity.CENTER_LEFT)
                                    }

                                    textSize = 4f
                                    gravity = Gravity.CENTER_LEFT
                                }
                            }

                            add(
                                "apply", createStyledButton(
                                    L10n["app.academy.music_player.room.apply"], 34f, 12f, 0.58f
                                ) {
                                    RoomSessionController.applyRoom(room.code())
                                }) {
                                lp {
                                    size(34f, 12f)
                                    gravity(Gravity.CENTER)
                                }
                            }
                        })
                    }
                }
            }
        }

        private fun buildRoomInside(): LinearLayoutWidget {
            val state = RoomSessionController.roomState ?: return buildRoomLobby()
            return standaloneColumn(spacing = 2f) {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT)
                    padding(2f, 2f)
                }

                row("header", spacing = 2f) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(14f)
                    }

                    add("name", createMarqueeText("${state.roomName} [${state.roomCode}]")) {
                        lp {
                            width(0f)
                            height(0f)
                            gravity(Gravity.CENTER_LEFT)
                        }

                        weight(1f)
                        gravity = Gravity.CENTER_LEFT
                    }

                    text(
                        "${L10n["app.academy.music_player.room.host_label"]}: ${state.hostName}",
                        "host"
                    ) {
                        lp {
                            height(0f)
                            gravity(Gravity.CENTER)
                        }

                        scaleX = 0.62f
                        scaleY = 0.62f
                        gravity = Gravity.CENTER
                    }

                    add("leave", createStyledButton(L10n["app.academy.music_player.room.leave"], 42f, 14f) {
                        RoomSessionController.leaveRoom()
                    }) {
                        lp {
                            gravity(Gravity.CENTER)
                        }
                    }
                }

                if (state.isHost) {
                    add("applies_pending", createPendingPanel(onlyApply = true))
                }

                row("body", spacing = 2f) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(0f)
                    }

                    weight(1f)

                    column("members") {
                        lp {
                            width(R.ui.music_player.members_width)
                            heightMode(SizeMode.MATCH_PARENT)
                        }

                        add("title_members", createRoomMembersTitle())

                        scrollPanel(Orientation.VERTICAL, "list_member", createMemberPanel()) {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                            }

                            weight(1f)
                        }
                    }

                    column("right") {
                        lp {
                            width(0f)
                            heightMode(SizeMode.MATCH_PARENT)
                        }

                        weight(1f)

                        add("now_playing", createRoomNowPlaying())

                        row("area_search_room", spacing = 2f) {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                height(14f)
                            }

                            roomSearchBox = textBox(R.ui.music_player.search_max_length, "box_search_room") {
                                lp {
                                    width(0f)
                                    height(14f)
                                    gravity(Gravity.CENTER_VERTICAL)
                                    padding(2f, 0f)
                                }

                                weight(1f)
                                gravity = Gravity.CENTER_VERTICAL

                                enter { roomSearch(it) }
                                clearOnEnter(false)
                            }

                            add(
                                "search", createStyledButton(
                                    L10n["app.academy.music_player.search"], 26f, 14f
                                ) {
                                    roomSearch(roomSearchBox?.text)
                                }) {
                                lp {
                                    gravity(Gravity.CENTER)
                                }
                            }

                            if (roomShowingSearch) {
                                add(
                                    "back", createActionButton(
                                        R.textures.gui.icon.close,
                                        L10n["app.academy.music_player.back_to_list"],
                                        12f
                                    ) {
                                        roomShowingSearch = false
                                        roomRevisionState.value += 1
                                    }) {
                                    lp {
                                        gravity(Gravity.CENTER)
                                    }
                                }
                            }
                        }

                        add("title_queue", createRoomQueueTitle())

                        scrollPanel(Orientation.VERTICAL, "area_queue", createRoomQueuePanel()) {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                            }

                            weight(1f)
                        }
                    }
                }

                if (state.isHost) {
                    row("actions_host", spacing = 2f) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(14f)
                        }

                        inviteNameBox = textBox(R.ui.music_player.search_max_length, "box_invite") {
                            lp {
                                width(0f)
                                height(14f)
                                gravity(Gravity.CENTER_VERTICAL)
                                padding(2f, 0f)
                            }

                            weight(1f)
                            gravity = Gravity.CENTER_VERTICAL

                            enter { inviteFromInput(it) }
                            clearOnEnter(false)
                        }

                        add("invite", createStyledButton(L10n["app.academy.music_player.room.invite"], 38f, 14f) {
                            inviteFromInput(inviteNameBox?.text)
                        }) {
                            lp {
                                gravity(Gravity.CENTER)
                            }
                        }
                    }
                }
            }
        }

        private fun createRoomMembersTitle(): TextWidget {
            return TextWidget("").apply {
                bindState(RoomSessionController.roomStateUi) {
                    val count = RoomSessionController.roomState?.members?.size ?: 0
                    text = "${L10n["app.academy.music_player.room.members"]} ($count)"
                }
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(10f)
                }
            }
        }

        private fun createRoomQueueTitle(): TextWidget {
            return TextWidget("").apply {
                bindState(RoomSessionController.roomStateUi) { text = roomQueueTitle() }
                bindState(OnlineMusicManager.revisionState) { text = roomQueueTitle() }
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    height(10f)
                }
            }
        }

        private fun roomQueueTitle(): String {
            return if (roomShowingSearch) {
                L10n["app.academy.music_player.search_results"]
            } else {
                val count = RoomSessionController.roomState?.queue?.entries()?.size ?: 0
                "${L10n["app.academy.music_player.room.queue"]} ($count)"
            }
        }

        private fun createMemberPanel(): LinearLayoutWidget {
            return LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                bindState(RoomSessionController.roomStateUi) { rebuildMemberPanel(this as LinearLayoutWidget) }
            }
        }

        private fun rebuildMemberPanel(container: LinearLayoutWidget) {
            container.clearChildren()
            val state = RoomSessionController.roomState ?: return
            state.members.forEachIndexed { index, member ->
                container.add("member_$index", standaloneRow(spacing = 2f) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(12f)
                    }

                    text(
                        if (member == state.hostName) "◆ $member" else member,
                        "name"
                    ) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(0f)
                            gravity(Gravity.CENTER_LEFT)
                        }

                        scaleX = 0.62f
                        scaleY = 0.62f
                        gravity = Gravity.CENTER_LEFT
                    }
                })
            }
        }

        private fun createRoomNowPlaying(): FrameLayoutWidget {
            return FrameLayoutWidget().apply {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    heightMode(SizeMode.WRAP_CONTENT)
                }

                bindState(RoomSessionController.roomStateUi) {
                    replace("now_playing", buildRoomNowPlaying())
                }

                add("now_playing", buildRoomNowPlaying())
            }
        }

        private fun buildRoomNowPlaying(): LinearLayoutWidget {
            val timeline = RoomSessionController.roomState?.timeline
            return standaloneColumn(spacing = 1f) {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    heightMode(SizeMode.WRAP_CONTENT)
                }

                if (timeline == null) {
                    text(L10n["app.academy.music_player.room.idle"], "placeholder") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(12f)
                            gravity(Gravity.CENTER)
                        }

                        scaleX = 0.62f
                        scaleY = 0.62f
                        gravity = Gravity.CENTER
                    }
                } else {
                    val entry = timeline.entry()
                    row("meta", spacing = 4f) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(14f)
                        }

                        image(AlbumArtworkCache.textureFor(entry), "icon") {
                            lp {
                                size(12f, 12f)
                                gravity(Gravity.CENTER)
                            }

                            sampler(FilterMode.LINEAR, false)
                        }

                        column("info") {
                            lp {
                                heightMode(SizeMode.MATCH_PARENT)
                                gravity(Gravity.CENTER)
                            }

                            weight(1f)

                            add("title", createMarqueeText(entry.title()).apply {
                                textSize = 6f
                            }) {
                                lp {
                                    widthMode(SizeMode.MATCH_PARENT)
                                    gravity(Gravity.CENTER_LEFT)
                                }

                                gravity = Gravity.CENTER_LEFT
                            }

                            add("artist", createMarqueeText(entry.artist()).apply {
                                textSize = 4f
                            }) {
                                lp {
                                    widthMode(SizeMode.MATCH_PARENT)
                                    gravity(Gravity.CENTER_LEFT)
                                }

                                gravity = Gravity.CENTER_LEFT
                            }
                        }
                    }

                    row("area_progress", spacing = 4f) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(12f)
                        }

                        add("time_current", TextWidget("00:00").apply {
                            setFrameUpdate {
                                text = formatTime(RoomSessionController.expectedPositionSeconds())
                                true
                            }
                        }) {
                            lp {
                                width(0f)
                                gravity(Gravity.CENTER)
                            }

                            weight(1f)
                            gravity = Gravity.CENTER
                        }

                        add("progress", createRoomProgressBar(entry.durationSeconds().toFloat())) {
                            lp {
                                gravity(Gravity.CENTER)
                            }
                        }

                        add("duration", TextWidget(formatTime(entry.durationSeconds().toFloat()))) {
                            lp {
                                width(0f)
                                gravity(Gravity.CENTER)
                            }

                            weight(1f)
                            gravity = Gravity.CENTER
                        }
                    }
                }

                row("controls") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(14f)
                    }

                    add("spacer_lead", EmptyWidget()) {
                        weight(1f)
                    }

                    button("previous") {
                        lp {
                            size(14f, 14f)
                        }

                        onClick { RoomSessionController.previousTrack() }

                        image(R.textures.gui.app.music.previous) {
                            sampler(FilterMode.LINEAR, false)
                        }
                    }

                    button("play_pause") {
                        lp {
                            size(14f, 14f)
                        }

                        onClick {
                            when {
                                RoomSessionController.roomState?.timeline == null ->
                                    RoomSessionController.nextTrack()

                                RoomSessionController.expectedPlaying() -> RoomSessionController.pausePlayback()
                                else -> RoomSessionController.resumePlayback()
                            }
                        }

                        image(
                            if (RoomSessionController.roomState?.timeline != null
                                && RoomSessionController.expectedPlaying()
                            ) {
                                R.textures.gui.app.music.pause
                            } else {
                                R.textures.gui.app.music.play
                            }
                        ) {
                            sampler(FilterMode.LINEAR, false)
                        }
                    }

                    button("next") {
                        lp {
                            size(14f, 14f)
                        }

                        onClick { RoomSessionController.nextTrack() }

                        image(R.textures.gui.app.music.next) {
                            sampler(FilterMode.LINEAR, false)
                        }
                    }

                    add("spacer_tail", EmptyWidget()) {
                        weight(1f)
                    }
                }
            }
        }

        private fun createRoomProgressBar(durationSeconds: Float): SeekBarWidget {
            val progressBar = object : SeekBarWidget() {
                init {
                    setFrameUpdate {
                        if (!isDragging && durationSeconds > 0f) {
                            val progress = RoomSessionController.expectedPositionSeconds() / durationSeconds
                            setProgress(min + progress * (max - min))
                        }
                        true
                    }
                }
            }
            progressBar.lp {
                size(R.ui.music_player.room_progress_width, R.ui.music_player.progress_height)
                gravity(Gravity.CENTER)
            }
            progressBar.setBarColors(R.ui.terminal_hud.background_color, R.ui.terminal_hud.primary_color)
            progressBar.seekListener(object : OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBarWidget, progress: Float, fromUser: Boolean) {}

                override fun onStartTrackingTouch(seekBar: SeekBarWidget) {}

                override fun onStopTrackingTouch(seekBar: SeekBarWidget) {
                    if (durationSeconds > 0f) {
                        RoomSessionController.seekTo(progressBar.progress / progressBar.max * durationSeconds)
                    }
                }
            })
            return progressBar
        }

        private fun createRoomQueuePanel(): LinearLayoutWidget {
            return LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                bindState(RoomSessionController.roomStateUi) {
                    rebuildRoomQueue(this as LinearLayoutWidget)
                }
                bindState(OnlineMusicManager.revisionState) {
                    rebuildRoomQueue(this as LinearLayoutWidget)
                }
                rebuildRoomQueue(this)
            }
        }

        private fun rebuildRoomQueue(container: LinearLayoutWidget) {
            container.apply {
                clearChildren()
                if (roomShowingSearch) {
                    OnlineMusicManager.searchResults.forEachIndexed { index, result ->
                        row("result_$index", spacing = 2f) {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                height(16f)
                            }

                            text(
                                (if (result.vip) "[VIP] " else "") + result.title + " - " + result.artist,
                                "name"
                            ) {
                                lp {
                                    height(0f)
                                    gravity(Gravity.CENTER_LEFT)
                                }

                                weight(1f)
                                scaleX = 0.6f
                                scaleY = 0.6f
                                gravity = Gravity.CENTER_LEFT
                            }

                            add(
                                "play", createActionButton(
                                    R.textures.gui.app.music.play,
                                    L10n["app.academy.music_player.action.play"]
                                ) {
                                    RoomSessionController.playTrack(result.toSharedTrackEntry())
                                })

                            add(
                                "queue", createActionButton(
                                    R.textures.gui.icon.add,
                                    L10n["app.academy.music_player.room.action.queue_add"]
                                ) {
                                    RoomSessionController.queueAdd(result.toSharedTrackEntry())
                                })
                        }
                    }
                    return
                }
                val queue = RoomSessionController.roomState?.queue
                if (queue == null || queue.entries().isEmpty()) {
                    text(L10n["app.academy.music_player.room.queue.empty"], "empty") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(12f)
                            gravity(Gravity.CENTER)
                        }

                        scaleX = 0.6f
                        scaleY = 0.6f
                        gravity = Gravity.CENTER
                    }
                    return
                }
                queue.entries().forEachIndexed { index, queueEntry ->
                    val entry = queueEntry.entry()
                    add("queue_$index", ButtonWidget()) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(16f)
                        }

                        background = createTrackBackground(false)

                        add("content", standaloneRow(spacing = 2f) {
                            lp {
                                sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)
                            }

                            image(AlbumArtworkCache.textureFor(entry), "icon") {
                                lp {
                                    size(12f, 12f)
                                    gravity(Gravity.CENTER)
                                }

                                sampler(FilterMode.LINEAR, false)
                            }

                            column("info") {
                                lp {
                                    heightMode(SizeMode.MATCH_PARENT)
                                    gravity(Gravity.CENTER)
                                }

                                weight(1f)

                                add("name", createMarqueeText(entry.title()).apply {
                                    textSize = 6f
                                }) {
                                    lp {
                                        widthMode(SizeMode.MATCH_PARENT)
                                        gravity(Gravity.CENTER_LEFT)
                                    }

                                    gravity = Gravity.CENTER_LEFT
                                }

                                add("artist", createMarqueeText(entry.artist()).apply {
                                    textSize = 4f
                                }) {
                                    lp {
                                        widthMode(SizeMode.MATCH_PARENT)
                                        gravity(Gravity.CENTER_LEFT)
                                    }

                                    gravity = Gravity.CENTER_LEFT
                                }
                            }

                            text(
                                queueEntry.requesterName(),
                                "requester"
                            ) {
                                lp {
                                    width(26f)
                                    height(0f)
                                    gravity(Gravity.CENTER)
                                }

                                scaleX = 0.55f
                                scaleY = 0.55f
                                gravity = Gravity.CENTER
                            }

                            add(
                                "now_play", createActionButton(
                                    R.textures.gui.app.music.play,
                                    L10n["app.academy.music_player.action.play"],
                                    10f,
                                    Gravity.CENTER
                                ) {
                                    RoomSessionController.playTrack(entry)
                                })

                            add(
                                "remove", createActionButton(
                                    R.textures.gui.icon.close,
                                    L10n["app.academy.music_player.action.remove"],
                                    10f,
                                    Gravity.CENTER
                                ) {
                                    RoomSessionController.queueRemove(index)
                                })
                        })
                    }
                }
            }
        }

        private fun createRoomFromInput(name: String?) {
            val trimmed = name?.trim().orEmpty()
            if (trimmed.isEmpty()) return
            RoomSessionController.createRoom(trimmed)
        }

        private fun inviteFromInput(name: String?) {
            val trimmed = name?.trim().orEmpty()
            if (trimmed.isEmpty()) return
            RoomSessionController.invitePlayer(trimmed)
        }

        private fun roomSearch(query: String?) {
            val trimmed = query?.trim().orEmpty()
            if (trimmed.isEmpty()) return
            roomShowingSearch = true
            roomRevisionState.value += 1
            OnlineMusicManager.search(trimmed)
        }

        private fun OnlineMusicManager.SearchEntry.toSharedTrackEntry(): SharedTrackEntry {
            return SharedTrackEntry(
                provider.storageName,
                id,
                title,
                artist,
                durationSeconds,
                vip,
                artworkUrl
            )
        }

        private fun MusicInfo.toSharedTrackEntry(): SharedTrackEntry? {
            if (externalId.isNotBlank() && provider != "local") {
                return SharedTrackEntry(
                    provider,
                    externalId,
                    name,
                    subtitle,
                    durationSeconds,
                    vip,
                    artworkUrl
                )
            }
            val location = source.path as? Identifier ?: return null
            return SharedTrackEntry(
                "local",
                location.toString(),
                name,
                subtitle,
                durationSeconds,
                false,
                ""
            )
        }

        private fun sendToRoomOrOpenView(entry: SharedTrackEntry) {
            val roomState = RoomSessionController.roomState
            if (roomState == null) {
                pendingRoomEntry = entry
                RoomSessionController.requestRoomList()
                setViewMode(ViewMode.ROOM)
                return
            }
            RoomSessionController.queueAdd(entry)
        }

        private fun consumePendingRoomEntry() {
            val entry = pendingRoomEntry ?: return
            RoomSessionController.roomState ?: return
            pendingRoomEntry = null
            RoomSessionController.queueAdd(entry)
        }

        private fun createJukeboxView(): FrameLayoutWidget {
            return standaloneFrame {
                lp {
                    matchParent()
                }

                add("content_jukebox", FrameLayoutWidget()) {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT)
                    }

                    bindState(JukeboxSessionController.jukeboxStateUi) { replace("body_jukebox", buildJukeboxContent()) }
                    bindState(SharedAccountClient.accountUi) { replace("body_jukebox", buildJukeboxContent()) }
                    bindState(viewRevisionState) { replace("body_jukebox", buildJukeboxContent()) }

                    add("body_jukebox", buildJukeboxContent())
                }
            }
        }

        private fun buildJukeboxContent(): Widget {
            val state = JukeboxSessionController.state
            return if (state == null || !state.enabled) buildJukeboxDisabled() else buildJukeboxMain(state)
        }

        private fun buildJukeboxDisabled(): LinearLayoutWidget {
            return standaloneColumn(spacing = 2f) {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT)
                    padding(2f, 2f)
                }

                text(L10n["app.academy.music_player.jukebox.disabled"], "hint_disabled") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        heightMode(SizeMode.WRAP_CONTENT)
                        gravity(Gravity.CENTER)
                    }

                    scaleX = 0.62f
                    scaleY = 0.62f
                    gravity = Gravity.CENTER
                }
            }
        }

        private fun buildJukeboxMain(state: JukeboxSessionController.JukeboxState): LinearLayoutWidget {
            return standaloneColumn(spacing = 2f) {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT)
                    padding(2f, 2f)
                }

                row("header", spacing = 2f) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(14f)
                    }

                    val modeText = when (state.mode) {
                        MusicJukeboxPackets.MODE_PRESET -> L10n["app.academy.music_player.jukebox.mode_preset"]
                        MusicJukeboxPackets.MODE_JUKEBOX -> L10n["app.academy.music_player.jukebox.mode_jukebox"]
                        else -> L10n["app.academy.music_player.jukebox.mode_idle"]
                    }
                    text(modeText, "mode") {
                        lp {
                            width(0f)
                            height(0f)
                            gravity(Gravity.CENTER_LEFT)
                        }

                        weight(1f)
                        scaleX = 0.62f
                        scaleY = 0.62f
                        gravity = Gravity.CENTER_LEFT
                    }

                    add(
                        "subscribe", createStyledButton(
                            if (JukeboxSessionController.subscribed) {
                                L10n["app.academy.music_player.jukebox.mute"]
                            } else {
                                L10n["app.academy.music_player.jukebox.listen"]
                            },
                            44f, 14f
                        ) {
                            JukeboxSessionController.setSubscribed(!JukeboxSessionController.subscribed)
                        }) {
                        lp {
                            gravity(Gravity.CENTER)
                        }
                    }

                    add(
                        "toggle_playlist", createStyledButton(
                            if (jukeboxShowingPlaylist) L10n["app.academy.music_player.jukebox.playlist_back"]
                            else L10n["app.academy.music_player.jukebox.playlist"],
                            36f, 14f
                        ) {
                            jukeboxShowingPlaylist = !jukeboxShowingPlaylist
                            if (jukeboxShowingPlaylist) SharedAccountClient.requestPlaylist()
                            viewRevisionState.value += 1
                        }) {
                        lp {
                            gravity(Gravity.CENTER)
                        }
                    }
                }

                add("now_playing", buildJukeboxNowPlaying(state))

                if (jukeboxShowingPlaylist) {
                    text(
                        "${L10n["app.academy.music_player.jukebox.playlist"]} (${SharedAccountClient.serverPlaylist?.tracks?.size ?: 0})",
                        "title_playlist"
                    ) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(10f)
                        }
                    }

                    scrollPanel(Orientation.VERTICAL, "playlist_server", createServerPlaylistPanel()) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                        }

                        weight(1f)
                    }
                } else {
                    text(
                        "${L10n["app.academy.music_player.jukebox.queue"]} (${state.queue.entries().size})",
                        "title_queue"
                    ) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(10f)
                        }
                    }

                    scrollPanel(Orientation.VERTICAL, "queue_jukebox", createJukeboxQueuePanel()) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                        }

                        weight(1f)
                    }
                }
            }
        }

        private fun createServerPlaylistPanel(): LinearLayoutWidget {
            return LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                bindState(SharedAccountClient.accountUi) { rebuildServerPlaylist(this as LinearLayoutWidget) }
                rebuildServerPlaylist(this)
            }
        }

        private fun rebuildServerPlaylist(container: LinearLayoutWidget) {
            container.apply {
                clearChildren()
                val tracks = SharedAccountClient.serverPlaylist?.tracks
                if (tracks.isNullOrEmpty()) {
                    text(L10n["app.academy.music_player.jukebox.playlist.empty"], "empty") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(12f)
                            gravity(Gravity.CENTER)
                        }

                        scaleX = 0.6f
                        scaleY = 0.6f
                        gravity = Gravity.CENTER
                    }
                    return
                }
                tracks.forEachIndexed { index, entry ->
                    add("preset_$index", ButtonWidget()) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(16f)
                        }

                        background = createTrackBackground(false)

                        add("content", standaloneRow(spacing = 2f) {
                            lp {
                                sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)
                            }

                            image(AlbumArtworkCache.textureFor(entry), "icon") {
                                lp {
                                    size(12f, 12f)
                                    gravity(Gravity.CENTER)
                                }

                                sampler(FilterMode.LINEAR, false)
                            }

                            column("info") {
                                lp {
                                    heightMode(SizeMode.MATCH_PARENT)
                                    gravity(Gravity.CENTER)
                                }

                                weight(1f)

                                add("name", createMarqueeText(entry.title()).apply {
                                    textSize = 6f
                                }) {
                                    lp {
                                        widthMode(SizeMode.MATCH_PARENT)
                                        gravity(Gravity.CENTER_LEFT)
                                    }

                                    gravity = Gravity.CENTER_LEFT
                                }

                                add("artist", createMarqueeText(entry.artist()).apply {
                                    textSize = 4f
                                }) {
                                    lp {
                                        widthMode(SizeMode.MATCH_PARENT)
                                        gravity(Gravity.CENTER_LEFT)
                                    }

                                    gravity = Gravity.CENTER_LEFT
                                }
                            }

                            add(
                                "play", createActionButton(
                                    R.textures.gui.app.music.play,
                                    L10n["app.academy.music_player.action.play"]
                                ) {
                                    previewTrack(entry)
                                })

                            add(
                                "favorite", createActionButton(
                                    R.textures.gui.icon.add,
                                    L10n["app.academy.music_player.jukebox.playlist.favorite"]
                                ) {
                                    OnlineMusicManager.addSharedEntry(entry)
                                })
                        })
                    }
                }
            }
        }

        private fun runLocalTransport(action: () -> Unit) {
            if (RoomSessionController.roomState?.timeline != null || JukeboxSessionController.isActivelyPlaying()) {
                OnlineMusicManager.notifyStatus(L10n["app.academy.music_player.transport.shared_hint"])
                return
            }
            action()
        }

        private fun previewTrack(entry: SharedTrackEntry) {
            if (JukeboxSessionController.isActivelyPlaying()) {
                OnlineMusicManager.notifyStatus(
                    L10n["app.academy.music_player.jukebox.preview_blocked"]
                )
                return
            }
            val info = OnlineMusicManager.toMusicInfo(entry) ?: return
            MusicPlayerBackend.getInstance().setPlaybackController(
                org.academy.internal.client.app.music.common.PlaybackController.LOCAL
            )
            MusicPlayerBackend.getInstance().addOnlineTrack(info, true)
        }

        private fun buildJukeboxNowPlaying(state: JukeboxSessionController.JukeboxState): LinearLayoutWidget {
            val timeline = state.timeline
            return standaloneColumn(spacing = 1f) {
                lp {
                    widthMode(SizeMode.MATCH_PARENT)
                    heightMode(SizeMode.WRAP_CONTENT)
                }

                if (timeline == null) {
                    text(L10n["app.academy.music_player.jukebox.idle"], "placeholder") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(12f)
                            gravity(Gravity.CENTER)
                        }

                        scaleX = 0.62f
                        scaleY = 0.62f
                        gravity = Gravity.CENTER
                    }
                    return@standaloneColumn
                }

                val entry = timeline.entry()
                row("meta", spacing = 4f) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(14f)
                    }

                    image(AlbumArtworkCache.textureFor(entry), "icon") {
                        lp {
                            size(12f, 12f)
                            gravity(Gravity.CENTER)
                        }

                        sampler(FilterMode.LINEAR, false)
                    }

                    column("info") {
                        lp {
                            heightMode(SizeMode.MATCH_PARENT)
                            gravity(Gravity.CENTER)
                        }

                        weight(1f)

                        add("title", createMarqueeText(entry.title()).apply {
                            textSize = 6f
                        }) {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                gravity(Gravity.CENTER_LEFT)
                            }

                            gravity = Gravity.CENTER_LEFT
                        }

                        add("artist", createMarqueeText(entry.artist()).apply {
                            textSize = 4f
                        }) {
                            lp {
                                widthMode(SizeMode.MATCH_PARENT)
                                gravity(Gravity.CENTER_LEFT)
                            }

                            gravity = Gravity.CENTER_LEFT
                        }
                    }
                }

                row("area_progress", spacing = 4f) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(12f)
                    }

                    add("time_current", TextWidget("00:00").apply {
                        setFrameUpdate {
                            text = formatTime(JukeboxSessionController.expectedPositionSeconds())
                            true
                        }
                    }) {
                        lp {
                            width(0f)
                            gravity(Gravity.CENTER)
                        }

                        weight(1f)
                        gravity = Gravity.CENTER
                    }

                    add("progress", createJukeboxProgressBar(entry.durationSeconds().toFloat())) {
                        lp {
                            gravity(Gravity.CENTER)
                        }
                    }

                    add("duration", TextWidget(formatTime(entry.durationSeconds().toFloat()))) {
                        lp {
                            width(0f)
                            gravity(Gravity.CENTER)
                        }

                        weight(1f)
                        gravity = Gravity.CENTER
                    }
                }

                row("area_vote", spacing = 4f) {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(14f)
                        gravity(Gravity.CENTER)
                    }

                    if (state.voteActive) {
                        text(
                            "${L10n["app.academy.music_player.jukebox.vote_progress"]}: ${state.voteCount}/${state.voteThreshold}",
                            "vote_status"
                        ) {
                            lp {
                                height(0f)
                                gravity(Gravity.CENTER)
                            }

                            scaleX = 0.6f
                            scaleY = 0.6f
                            gravity = Gravity.CENTER
                        }
                    }

                    add(
                        "vote", createStyledButton(
                            L10n["app.academy.music_player.jukebox.vote_skip"], 60f, 14f
                        ) {
                            JukeboxSessionController.voteSkip()
                        }) {
                        lp {
                            gravity(Gravity.CENTER)
                        }
                    }
                }
            }
        }

        private fun createJukeboxProgressBar(durationSeconds: Float): SeekBarWidget {
            val progressBar = object : SeekBarWidget() {
                override fun onMousePressed(event: org.academy.api.client.gui.event.MouseEvent) {}
                override fun onMouseDragged(event: org.academy.api.client.gui.event.MouseEvent) {}
                override fun onMouseReleased(event: org.academy.api.client.gui.event.MouseEvent) {}

                init {
                    setFrameUpdate {
                        if (durationSeconds > 0f) {
                            val progress = JukeboxSessionController.expectedPositionSeconds() / durationSeconds
                            setProgress(min + progress * (max - min))
                        }
                        true
                    }
                }
            }
            progressBar.lp {
                size(R.ui.music_player.progress_width, R.ui.music_player.progress_height)
                gravity(Gravity.CENTER)
            }
            progressBar.setBarColors(R.ui.terminal_hud.background_color, R.ui.terminal_hud.primary_color)
            return progressBar
        }

        private fun createJukeboxQueuePanel(): LinearLayoutWidget {
            return LinearLayoutWidget().apply {
                orientation = Orientation.VERTICAL
                bindState(JukeboxSessionController.jukeboxStateUi) {
                    rebuildJukeboxQueue(this as LinearLayoutWidget)
                }
                rebuildJukeboxQueue(this)
            }
        }

        private fun rebuildJukeboxQueue(container: LinearLayoutWidget) {
            container.apply {
                clearChildren()
                val queue = JukeboxSessionController.state?.queue
                if (queue == null || queue.entries().isEmpty()) {
                    text(L10n["app.academy.music_player.jukebox.queue.empty"], "empty") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(12f)
                            gravity(Gravity.CENTER)
                        }

                        scaleX = 0.6f
                        scaleY = 0.6f
                        gravity = Gravity.CENTER
                    }
                    return
                }
                queue.entries().forEachIndexed { index, queueEntry ->
                    val entry = queueEntry.entry()
                    add("queue_$index", ButtonWidget()) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(16f)
                        }

                        background = createTrackBackground(false)

                        add("content", standaloneRow(spacing = 2f) {
                            lp {
                                sizeMode(SizeMode.MATCH_PARENT, SizeMode.MATCH_PARENT)
                            }

                            image(AlbumArtworkCache.textureFor(entry), "icon") {
                                lp {
                                    size(12f, 12f)
                                    gravity(Gravity.CENTER)
                                }

                                sampler(FilterMode.LINEAR, false)
                            }

                            column("info") {
                                lp {
                                    heightMode(SizeMode.MATCH_PARENT)
                                    gravity(Gravity.CENTER)
                                }

                                weight(1f)

                                add("name", createMarqueeText(entry.title()).apply {
                                    textSize = 6f
                                }) {
                                    lp {
                                        widthMode(SizeMode.MATCH_PARENT)
                                        gravity(Gravity.CENTER_LEFT)
                                    }

                                    gravity = Gravity.CENTER_LEFT
                                }

                                add("artist", createMarqueeText(entry.artist()).apply {
                                    textSize = 4f
                                }) {
                                    lp {
                                        widthMode(SizeMode.MATCH_PARENT)
                                        gravity(Gravity.CENTER_LEFT)
                                    }

                                    gravity = Gravity.CENTER_LEFT
                                }
                            }

                            text(
                                queueEntry.requesterName().ifBlank {
                                    L10n["app.academy.music_player.jukebox.preset_badge"]
                                },
                                "requester"
                            ) {
                                lp {
                                    width(30f)
                                    height(0f)
                                    gravity(Gravity.CENTER)
                                }

                                scaleX = 0.55f
                                scaleY = 0.55f
                                gravity = Gravity.CENTER
                            }
                        })
                    }
                }
            }
        }

        private fun createSharedAccountPanel(): FrameLayoutWidget {
            return standaloneFrame {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                }

                add("content", FrameLayoutWidget()) {
                    lp {
                        sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    }

                    bindState(SharedAccountClient.accountUi) { rebuildSharedAccountPanel(this as FrameLayoutWidget) }
                    rebuildSharedAccountPanel(this)
                }
            }
        }

        private fun rebuildSharedAccountPanel(container: FrameLayoutWidget) {
            container.clearChildren()
            container.add("card", standaloneColumn(spacing = 2f) {
                lp {
                    sizeMode(SizeMode.MATCH_PARENT, SizeMode.WRAP_CONTENT)
                    padding(4f, 4f)
                }

                text(L10n["app.academy.music_player.shared_account.title"], "title") {
                    lp {
                        widthMode(SizeMode.MATCH_PARENT)
                        height(10f)
                    }
                }

                for (provider in listOf("qq", "netease")) {
                    row("row_$provider", spacing = 2f) {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            height(14f)
                        }

                        text(
                            if (provider == "qq") L10n["app.academy.music_player.provider.qq"]
                            else L10n["app.academy.music_player.provider.netease"],
                            "name"
                        ) {
                            lp {
                                width(0f)
                                height(0f)
                                gravity(Gravity.CENTER_LEFT)
                            }

                            weight(1f)
                            scaleX = 0.62f
                            scaleY = 0.62f
                            gravity = Gravity.CENTER_LEFT
                        }

                        add(
                            "upload", createStyledButton(
                                L10n["app.academy.music_player.shared_account.upload"], 34f, 12f, 0.58f
                            ) {
                                sharedAccountHint = if (SharedAccountClient.uploadLocalCredential(provider)) {
                                    ""
                                } else {
                                    L10n["app.academy.music_player.shared_account.not_logged_in"]
                                }
                                viewRevisionState.value += 1
                            }) {
                            lp {
                                size(34f, 12f)
                                gravity(Gravity.CENTER)
                            }
                        }

                        add(
                            "query", createStyledButton(
                                L10n["app.academy.music_player.shared_account.query"], 34f, 12f, 0.58f
                            ) {
                                SharedAccountClient.queryStatus(provider)
                            }) {
                            lp {
                                size(34f, 12f)
                                gravity(Gravity.CENTER)
                            }
                        }
                    }
                }

                val status = SharedAccountClient.lastStatus
                val statusText = buildString {
                    if (status != null) {
                        append(
                            if (status.sharedEnabled) L10n["app.academy.music_player.shared_account.enabled"]
                            else L10n["app.academy.music_player.shared_account.disabled"]
                        )
                        append(" · ")
                        append(
                            if (status.hasCredential) L10n["app.academy.music_player.shared_account.configured"]
                            else L10n["app.academy.music_player.shared_account.missing"]
                        )
                        if (status.messageKey.isNotBlank() && status.messageKey.startsWith("message.")) {
                            append('\n')
                            append(L10n[status.messageKey])
                        }
                    }
                    if (sharedAccountHint.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(sharedAccountHint)
                    }
                }
                if (statusText.isNotBlank()) {
                    text(statusText, "status") {
                        lp {
                            widthMode(SizeMode.MATCH_PARENT)
                            heightMode(SizeMode.WRAP_CONTENT)
                            gravity(Gravity.CENTER_LEFT)
                        }

                        scaleX = 0.58f
                        scaleY = 0.58f
                        gravity = Gravity.CENTER_LEFT
                    }
                }
            })
        }

        private fun createMarqueeText(text: String): TextWidget = TextWidget(text).apply {
            ellipsize = Ellipsize.MARQUEE
            marqueeFadeSize = 6f
            gravity = Gravity.CENTER_LEFT
        }
    }
}
