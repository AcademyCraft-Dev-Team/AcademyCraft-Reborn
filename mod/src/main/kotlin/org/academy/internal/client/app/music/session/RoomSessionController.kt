package org.academy.internal.client.app.music.session

import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge
import org.academy.api.client.gui.state.UiState
import org.academy.api.client.vanilla.MainLoopEvent
import org.academy.internal.client.app.music.backend.MusicPlayerBackend
import org.academy.internal.client.app.music.common.PlaybackController
import org.academy.internal.common.music.PlaybackTimeline
import org.academy.internal.common.music.QueueSnapshot
import org.academy.internal.common.music.SharedTrackEntry
import org.academy.internal.common.network.MusicRoomPackets
import org.misaka.MisakaNetworkClient
import org.misaka.api.common.network.annotation.SubscribePacket

/**
 * 音乐室客户端会话：接收服务端权威时间线，经 [SharedPlaybackFollower] 对齐本地播放。
 * 处于房间期间后端为 SHARED 受控模式，本地自动连播被禁用；与全服点播互斥时房间优先喵。
 */
object RoomSessionController {
    data class RoomState(
        val roomCode: String,
        val roomName: String,
        val hostName: String,
        val isHost: Boolean,
        val members: List<String>,
        val queue: QueueSnapshot
    ) {
        val timeline: PlaybackTimeline? get() = currentTimeline
    }

    @Volatile
    var roomState: RoomState? = null
        private set

    @Volatile
    var roomList: List<MusicRoomPackets.RoomSummary> = emptyList()
        private set

    /**
     * 服务端推送的待处理申请/邀请卡片（App 音乐室内确认）喵。
     */
    @Volatile
    var pendingNotices: List<MusicRoomPackets.PendingNoticePacket.PendingItem> = emptyList()
        private set

    val roomStateUi = UiState(0)

    @Volatile
    private var currentTimeline: PlaybackTimeline? = null

    private val follower = SharedPlaybackFollower()

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        NeoForge.EVENT_BUS.register(this)
        // register(Class) 只会注册静态方法，object 的 @SubscribePacket 是实例方法，必须传实例喵
        MisakaNetworkClient.NETWORK_MANAGER.register(RoomSessionController)
    }

    fun requestRoomList() {
        send(MusicRoomPackets.ActionPacket.simple(MusicRoomPackets.RoomAction.REQUEST_LIST))
    }

    fun createRoom(name: String) {
        send(MusicRoomPackets.ActionPacket.withText(MusicRoomPackets.RoomAction.CREATE, name))
    }

    fun renameRoom(name: String) {
        send(MusicRoomPackets.ActionPacket.withText(MusicRoomPackets.RoomAction.RENAME, name))
    }

    fun applyRoom(code: String) {
        send(
            MusicRoomPackets.ActionPacket(
                MusicRoomPackets.RoomAction.APPLY, code, "", "", "", 0.0f, -1, null
            )
        )
    }

    fun invitePlayer(name: String) {
        send(MusicRoomPackets.ActionPacket.withTarget(MusicRoomPackets.RoomAction.INVITE, name))
    }

    fun leaveRoom() {
        send(MusicRoomPackets.ActionPacket.simple(MusicRoomPackets.RoomAction.LEAVE))
    }

    fun kickMember(name: String) {
        send(MusicRoomPackets.ActionPacket.withTarget(MusicRoomPackets.RoomAction.KICK, name))
    }

    fun playTrack(entry: SharedTrackEntry) {
        send(MusicRoomPackets.ActionPacket.withEntry(MusicRoomPackets.RoomAction.PLAY, entry))
    }

    fun pausePlayback() {
        send(MusicRoomPackets.ActionPacket.simple(MusicRoomPackets.RoomAction.PAUSE))
    }

    fun resumePlayback() {
        send(MusicRoomPackets.ActionPacket.simple(MusicRoomPackets.RoomAction.RESUME))
    }

    fun seekTo(seconds: Float) {
        send(MusicRoomPackets.ActionPacket.withSeek(seconds))
    }

    fun nextTrack() {
        send(MusicRoomPackets.ActionPacket.simple(MusicRoomPackets.RoomAction.NEXT))
    }

    fun previousTrack() {
        send(MusicRoomPackets.ActionPacket.simple(MusicRoomPackets.RoomAction.PREVIOUS))
    }

    fun queueAdd(entry: SharedTrackEntry) {
        send(MusicRoomPackets.ActionPacket.withEntry(MusicRoomPackets.RoomAction.QUEUE_ADD, entry))
    }

    fun queueRemove(index: Int) {
        send(MusicRoomPackets.ActionPacket.withQueueIndex(MusicRoomPackets.RoomAction.QUEUE_REMOVE, index))
    }

    /**
     * 服务端时间线是否处于播放态（与本地实际状态可能存在短暂偏差）喵。
     */
    fun expectedPlaying(): Boolean = follower.expectedPlaying()

    fun expectedPositionSeconds(): Float = follower.expectedPositionSeconds()

    @SubscribePacket
    fun onRoomSync(packet: MusicRoomPackets.SyncPacket) {
        if (!packet.inRoom()) {
            releaseSharedControl()
            return
        }
        currentTimeline = packet.timeline()
        roomState = RoomState(
            packet.roomCode(),
            packet.roomName(),
            packet.hostName(),
            packet.isHost(),
            packet.members(),
            packet.queue()
        )
        bumpUi()
        MusicPlayerBackend.getInstance().setPlaybackController(PlaybackController.SHARED)
        follower.apply(packet.timeline(), packet.serverGameTime())
    }

    @SubscribePacket
    fun onRoomList(packet: MusicRoomPackets.ListPacket) {
        roomList = packet.rooms()
        bumpUi()
    }

    @SubscribePacket
    fun onPendingNotice(packet: MusicRoomPackets.PendingNoticePacket) {
        pendingNotices = packet.items()
        bumpUi()
    }

    /**
     * 在 App 内对服务端推送的申请/邀请令牌做出同意/拒绝喵。
     */
    fun respondToken(token: String, accept: Boolean) {
        send(
            MusicRoomPackets.ActionPacket.withToken(
                if (accept) MusicRoomPackets.RoomAction.ACCEPT else MusicRoomPackets.RoomAction.REJECT,
                token
            )
        )
    }

    @SubscribeEvent
    fun onLoggingOut(@Suppress("unused") event: ClientPlayerNetworkEvent.LoggingOut) {
        releaseSharedControl()
    }

    @SubscribeEvent
    fun onMainLoop(@Suppress("unused") event: MainLoopEvent) {
        if (roomState != null) follower.correctDrift()
    }

    private fun releaseSharedControl() {
        roomState = null
        currentTimeline = null
        roomList = emptyList()
        pendingNotices = emptyList()
        follower.reset()
        MusicPlayerBackend.getInstance().setPlaybackController(PlaybackController.LOCAL)
        bumpUi()
    }

    private fun send(packet: MusicRoomPackets.ActionPacket) {
        MisakaNetworkClient.send(packet)
    }

    private fun bumpUi() {
        Minecraft.getInstance().execute { roomStateUi.value += 1 }
    }
}
