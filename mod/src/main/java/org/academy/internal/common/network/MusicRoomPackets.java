package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.music.PlaybackTimeline;
import org.academy.internal.common.music.QueueSnapshot;
import org.academy.internal.common.music.SharedTrackEntry;
import org.academy.internal.server.music.MusicRoomManager;
import org.jetbrains.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;

/**
 * 音乐室网络包：客户端操作请求（ActionPacket）与服务端房间状态/列表同步（SyncPacket/ListPacket）喵。
 */
public final class MusicRoomPackets {
    private static boolean initialized;

    private MusicRoomPackets() {
    }

    public static void initServer() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public enum RoomAction {
        CREATE,
        RENAME,
        APPLY,
        INVITE,
        ACCEPT,
        REJECT,
        LEAVE,
        KICK,
        PLAY,
        PAUSE,
        RESUME,
        SEEK,
        NEXT,
        PREVIOUS,
        QUEUE_ADD,
        QUEUE_REMOVE,
        REQUEST_LIST;

        private static final RoomAction[] VALUES = values();

        public static StreamCodec<ByteBuf, RoomAction> CODEC = ByteBufCodecs.idMapper(index ->
                index >= 0 && index < VALUES.length ? VALUES[index] : null, RoomAction::ordinal);
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActionPacket extends Packet<ServerGamePacketListenerImpl, ActionPacket> {
        public static final StreamCodec<ByteBuf, ActionPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    RoomAction.CODEC.encode(buf, packet.action);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.roomCode);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.text);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.targetName);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.token);
                    ByteBufCodecs.FLOAT.encode(buf, packet.seekSeconds);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.queueIndex);
                    ByteBufCodecs.BOOL.encode(buf, packet.entry != null);
                    if (packet.entry != null) SharedTrackEntry.CODEC.encode(buf, packet.entry);
                },
                buf -> {
                    var action = RoomAction.CODEC.decode(buf);
                    var roomCode = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var text = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var targetName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var token = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var seekSeconds = ByteBufCodecs.FLOAT.decode(buf);
                    var queueIndex = ByteBufCodecs.VAR_INT.decode(buf);
                    var entry = ByteBufCodecs.BOOL.decode(buf) ? SharedTrackEntry.CODEC.decode(buf) : null;
                    return new ActionPacket(action, roomCode, text, targetName, token, seekSeconds, queueIndex, entry);
                }
        );

        private final RoomAction action;
        private final String roomCode;
        private final String text;
        private final String targetName;
        private final String token;
        private final float seekSeconds;
        private final int queueIndex;
        @Nullable
        private final SharedTrackEntry entry;

        public ActionPacket(
                RoomAction action,
                String roomCode,
                String text,
                String targetName,
                String token,
                float seekSeconds,
                int queueIndex,
                @Nullable SharedTrackEntry entry
        ) {
            this.action = action;
            this.roomCode = roomCode == null ? "" : roomCode;
            this.text = text == null ? "" : text;
            this.targetName = targetName == null ? "" : targetName;
            this.token = token == null ? "" : token;
            this.seekSeconds = Float.isFinite(seekSeconds) ? Math.max(0.0f, seekSeconds) : 0.0f;
            this.queueIndex = queueIndex;
            this.entry = entry;
        }

        public static ActionPacket simple(RoomAction action) {
            return new ActionPacket(action, "", "", "", "", 0.0f, -1, null);
        }

        public static ActionPacket withText(RoomAction action, String text) {
            return new ActionPacket(action, "", text, "", "", 0.0f, -1, null);
        }

        public static ActionPacket withTarget(RoomAction action, String targetName) {
            return new ActionPacket(action, "", "", targetName, "", 0.0f, -1, null);
        }

        public static ActionPacket withToken(RoomAction action, String token) {
            return new ActionPacket(action, "", "", "", token, 0.0f, -1, null);
        }

        public static ActionPacket withEntry(RoomAction action, SharedTrackEntry entry) {
            return new ActionPacket(action, "", "", "", "", 0.0f, -1, entry);
        }

        public static ActionPacket withSeek(float seekSeconds) {
            return new ActionPacket(RoomAction.SEEK, "", "", "", "", seekSeconds, -1, null);
        }

        public static ActionPacket withQueueIndex(RoomAction action, int queueIndex) {
            return new ActionPacket(action, "", "", "", "", 0.0f, queueIndex, null);
        }

        public RoomAction action() {
            return action;
        }

        public String roomCode() {
            return roomCode;
        }

        public String text() {
            return text;
        }

        public String targetName() {
            return targetName;
        }

        public String token() {
            return token;
        }

        public float seekSeconds() {
            return seekSeconds;
        }

        public int queueIndex() {
            return queueIndex;
        }

        @Nullable
        public SharedTrackEntry entry() {
            return entry;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActionPacket> getPacketType() {
            return PacketTypes.MUSIC_ROOM_ACTION.get();
        }
    }

    public record RoomSummary(String code, String name, String hostName, int memberCount) {
        public RoomSummary {
            code = code == null ? "" : code;
            name = name == null ? "" : name;
            hostName = hostName == null ? "" : hostName;
            memberCount = Math.max(0, memberCount);
        }

        public static final StreamCodec<ByteBuf, RoomSummary> CODEC = StreamCodec.of(
                (buf, summary) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, summary.code);
                    ByteBufCodecs.STRING_UTF8.encode(buf, summary.name);
                    ByteBufCodecs.STRING_UTF8.encode(buf, summary.hostName);
                    ByteBufCodecs.VAR_INT.encode(buf, summary.memberCount);
                },
                buf -> new RoomSummary(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)
                )
        );
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SyncPacket extends Packet<ClientPacketListener, SyncPacket> {
        public static final StreamCodec<ByteBuf, SyncPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.BOOL.encode(buf, packet.inRoom);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.roomCode);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.roomName);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.hostName);
                    ByteBufCodecs.BOOL.encode(buf, packet.isHost);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.members.size());
                    for (var member : packet.members) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, member);
                    }
                    ByteBufCodecs.BOOL.encode(buf, packet.timeline != null);
                    if (packet.timeline != null) PlaybackTimeline.CODEC.encode(buf, packet.timeline);
                    QueueSnapshot.CODEC.encode(buf, packet.queue);
                    ByteBufCodecs.VAR_LONG.encode(buf, packet.serverGameTime);
                },
                buf -> {
                    var inRoom = ByteBufCodecs.BOOL.decode(buf);
                    var roomCode = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var roomName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var hostName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var isHost = ByteBufCodecs.BOOL.decode(buf);
                    var memberCount = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 64);
                    var members = new ArrayList<String>(memberCount);
                    for (var index = 0; index < memberCount; index++) {
                        members.add(ByteBufCodecs.STRING_UTF8.decode(buf));
                    }
                    var timeline = ByteBufCodecs.BOOL.decode(buf) ? PlaybackTimeline.CODEC.decode(buf) : null;
                    var queue = QueueSnapshot.CODEC.decode(buf);
                    var serverGameTime = ByteBufCodecs.VAR_LONG.decode(buf);
                    return new SyncPacket(inRoom, roomCode, roomName, hostName, isHost, members, timeline, queue, serverGameTime);
                }
        );

        private final boolean inRoom;
        private final String roomCode;
        private final String roomName;
        private final String hostName;
        private final boolean isHost;
        private final List<String> members;
        private final @Nullable PlaybackTimeline timeline;
        private final QueueSnapshot queue;
        private final long serverGameTime;

        public SyncPacket(
                boolean inRoom,
                String roomCode,
                String roomName,
                String hostName,
                boolean isHost,
                List<String> members,
                @Nullable PlaybackTimeline timeline,
                QueueSnapshot queue,
                long serverGameTime
        ) {
            this.inRoom = inRoom;
            this.roomCode = roomCode == null ? "" : roomCode;
            this.roomName = roomName == null ? "" : roomName;
            this.hostName = hostName == null ? "" : hostName;
            this.isHost = isHost;
            this.members = List.copyOf(members);
            this.timeline = timeline;
            this.queue = queue == null ? new QueueSnapshot(List.of(), 0) : queue;
            this.serverGameTime = Math.max(0L, serverGameTime);
        }

        public static SyncPacket left() {
            return new SyncPacket(false, "", "", "", false, List.of(), null, new QueueSnapshot(List.of(), 0), 0L);
        }

        public boolean inRoom() {
            return inRoom;
        }

        public String roomCode() {
            return roomCode;
        }

        public String roomName() {
            return roomName;
        }

        public String hostName() {
            return hostName;
        }

        public boolean isHost() {
            return isHost;
        }

        public List<String> members() {
            return members;
        }

        public @Nullable PlaybackTimeline timeline() {
            return timeline;
        }

        public QueueSnapshot queue() {
            return queue;
        }

        public long serverGameTime() {
            return serverGameTime;
        }

        @Override
        public PacketType<ClientPacketListener, SyncPacket> getPacketType() {
            return PacketTypes.MUSIC_ROOM_SYNC.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ListPacket extends Packet<ClientPacketListener, ListPacket> {
        public static final StreamCodec<ByteBuf, ListPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.rooms.size());
                    for (var room : packet.rooms) {
                        RoomSummary.CODEC.encode(buf, room);
                    }
                },
                buf -> {
                    var size = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 64);
                    var rooms = new ArrayList<RoomSummary>(size);
                    for (var index = 0; index < size; index++) {
                        rooms.add(RoomSummary.CODEC.decode(buf));
                    }
                    return new ListPacket(rooms);
                }
        );

        private final List<RoomSummary> rooms;

        public ListPacket(List<RoomSummary> rooms) {
            this.rooms = List.copyOf(rooms);
        }

        public List<RoomSummary> rooms() {
            return rooms;
        }

        @Override
        public PacketType<ClientPacketListener, ListPacket> getPacketType() {
            return PacketTypes.MUSIC_ROOM_LIST.get();
        }
    }

    /**
     * 推送给接收方（房主/被邀者）的待处理申请/邀请列表，客户端在 App 音乐室内渲染同意/拒绝按钮喵。
     */
    @PacketTarget(ThreadType.CLIENT)
    public static final class PendingNoticePacket extends Packet<ClientPacketListener, PendingNoticePacket> {
        public record PendingItem(String token, boolean apply, String roomName, String otherPlayer) {
            public PendingItem {
                token = token == null ? "" : token;
                roomName = roomName == null ? "" : roomName;
                otherPlayer = otherPlayer == null ? "" : otherPlayer;
            }

            public static final StreamCodec<ByteBuf, PendingItem> CODEC = StreamCodec.of(
                    (buf, item) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, item.token);
                        ByteBufCodecs.BOOL.encode(buf, item.apply);
                        ByteBufCodecs.STRING_UTF8.encode(buf, item.roomName);
                        ByteBufCodecs.STRING_UTF8.encode(buf, item.otherPlayer);
                    },
                    buf -> new PendingItem(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)
                    )
            );
        }

        public static final StreamCodec<ByteBuf, PendingNoticePacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.items.size());
                    for (var item : packet.items) {
                        PendingItem.CODEC.encode(buf, item);
                    }
                },
                buf -> {
                    var size = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 64);
                    var items = new ArrayList<PendingItem>(size);
                    for (var index = 0; index < size; index++) {
                        items.add(PendingItem.CODEC.decode(buf));
                    }
                    return new PendingNoticePacket(items);
                }
        );

        private final List<PendingItem> items;

        public PendingNoticePacket(List<PendingItem> items) {
            this.items = List.copyOf(items);
        }

        public List<PendingItem> items() {
            return items;
        }

        @Override
        public PacketType<ClientPacketListener, PendingNoticePacket> getPacketType() {
            return PacketTypes.MUSIC_ROOM_PENDING.get();
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void onAction(ActionPacket packet) {
            MusicRoomManager.handleAction(packet);
        }
    }
}
