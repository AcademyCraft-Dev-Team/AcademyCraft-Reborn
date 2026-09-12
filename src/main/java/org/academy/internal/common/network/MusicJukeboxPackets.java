package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.music.PlaybackTimeline;
import org.academy.internal.common.music.QueueSnapshot;
import org.academy.internal.common.music.SharedTrackEntry;
import org.jetbrains.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/**
 * 全服点播网络包：客户端点播/投票切歌/订阅操作与服务端点播状态同步喵。
 */
public final class MusicJukeboxPackets {
    private static boolean initialized;

    private MusicJukeboxPackets() {
    }

    public static void initServer() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public enum JukeboxAction {
        REQUEST,
        VOTE_SKIP,
        SUBSCRIBE,
        REQUEST_STATE;

        private static final JukeboxAction[] VALUES = values();

        public static final StreamCodec<ByteBuf, JukeboxAction> CODEC = ByteBufCodecs.idMapper(index ->
                index >= 0 && index < VALUES.length ? VALUES[index] : null, JukeboxAction::ordinal);
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ActionPacket extends Packet<ServerGamePacketListenerImpl, ActionPacket> {
        public static final StreamCodec<ByteBuf, ActionPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    JukeboxAction.CODEC.encode(buf, packet.action);
                    ByteBufCodecs.BOOL.encode(buf, packet.flag);
                    ByteBufCodecs.BOOL.encode(buf, packet.entry != null);
                    if (packet.entry != null) SharedTrackEntry.CODEC.encode(buf, packet.entry);
                },
                buf -> {
                    var action = JukeboxAction.CODEC.decode(buf);
                    var flag = ByteBufCodecs.BOOL.decode(buf);
                    var entry = ByteBufCodecs.BOOL.decode(buf) ? SharedTrackEntry.CODEC.decode(buf) : null;
                    return new ActionPacket(action, flag, entry);
                }
        );

        private final JukeboxAction action;
        private final boolean flag;
        @Nullable
        private final SharedTrackEntry entry;

        public ActionPacket(JukeboxAction action, boolean flag, @Nullable SharedTrackEntry entry) {
            this.action = action;
            this.flag = flag;
            this.entry = entry;
        }

        public static ActionPacket request(SharedTrackEntry entry) {
            return new ActionPacket(JukeboxAction.REQUEST, false, entry);
        }

        public static ActionPacket voteSkip() {
            return new ActionPacket(JukeboxAction.VOTE_SKIP, false, null);
        }

        public static ActionPacket subscribe(boolean subscribed) {
            return new ActionPacket(JukeboxAction.SUBSCRIBE, subscribed, null);
        }

        public static ActionPacket requestState() {
            return new ActionPacket(JukeboxAction.REQUEST_STATE, false, null);
        }

        public JukeboxAction action() {
            return action;
        }

        public boolean flag() {
            return flag;
        }

        @Nullable
        public SharedTrackEntry entry() {
            return entry;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ActionPacket> getPacketType() {
            return PacketTypes.MUSIC_JUKEBOX_ACTION.get();
        }
    }

    /**
     * 0 = 空闲，1 = 点播中，2 = 预设歌单兜底播放中。
     */
    public static final int MODE_IDLE = 0;
    public static final int MODE_JUKEBOX = 1;
    public static final int MODE_PRESET = 2;

    @PacketTarget(ThreadType.CLIENT)
    public static final class SyncPacket extends Packet<ClientPacketListener, SyncPacket> {
        public static final StreamCodec<ByteBuf, SyncPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.BOOL.encode(buf, packet.enabled);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.mode);
                    ByteBufCodecs.BOOL.encode(buf, packet.timeline != null);
                    if (packet.timeline != null) PlaybackTimeline.CODEC.encode(buf, packet.timeline);
                    QueueSnapshot.CODEC.encode(buf, packet.queue);
                    ByteBufCodecs.BOOL.encode(buf, packet.voteActive);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.voteCount);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.voteThreshold);
                    ByteBufCodecs.VAR_LONG.encode(buf, packet.voteEndsAtGameTime);
                    ByteBufCodecs.VAR_LONG.encode(buf, packet.serverGameTime);
                },
                buf -> {
                    var enabled = ByteBufCodecs.BOOL.decode(buf);
                    var mode = ByteBufCodecs.VAR_INT.decode(buf);
                    var timeline = ByteBufCodecs.BOOL.decode(buf) ? PlaybackTimeline.CODEC.decode(buf) : null;
                    var queue = QueueSnapshot.CODEC.decode(buf);
                    var voteActive = ByteBufCodecs.BOOL.decode(buf);
                    var voteCount = ByteBufCodecs.VAR_INT.decode(buf);
                    var voteThreshold = ByteBufCodecs.VAR_INT.decode(buf);
                    var voteEndsAtGameTime = ByteBufCodecs.VAR_LONG.decode(buf);
                    var serverGameTime = ByteBufCodecs.VAR_LONG.decode(buf);
                    return new SyncPacket(enabled, mode, timeline, queue,
                            voteActive, voteCount, voteThreshold, voteEndsAtGameTime, serverGameTime);
                }
        );

        private final boolean enabled;
        private final int mode;
        @Nullable
        private final PlaybackTimeline timeline;
        private final QueueSnapshot queue;
        private final boolean voteActive;
        private final int voteCount;
        private final int voteThreshold;
        private final long voteEndsAtGameTime;
        private final long serverGameTime;

        public SyncPacket(
                boolean enabled,
                int mode,
                @Nullable PlaybackTimeline timeline,
                QueueSnapshot queue,
                boolean voteActive,
                int voteCount,
                int voteThreshold,
                long voteEndsAtGameTime,
                long serverGameTime
        ) {
            this.enabled = enabled;
            this.mode = mode;
            this.timeline = timeline;
            this.queue = queue == null ? new QueueSnapshot(java.util.List.of(), 0) : queue;
            this.voteActive = voteActive;
            this.voteCount = Math.max(0, voteCount);
            this.voteThreshold = Math.max(0, voteThreshold);
            this.voteEndsAtGameTime = Math.max(0L, voteEndsAtGameTime);
            this.serverGameTime = Math.max(0L, serverGameTime);
        }

        public boolean enabled() {
            return enabled;
        }

        public int mode() {
            return mode;
        }

        @Nullable
        public PlaybackTimeline timeline() {
            return timeline;
        }

        public QueueSnapshot queue() {
            return queue;
        }

        public boolean voteActive() {
            return voteActive;
        }

        public int voteCount() {
            return voteCount;
        }

        public int voteThreshold() {
            return voteThreshold;
        }

        public long voteEndsAtGameTime() {
            return voteEndsAtGameTime;
        }

        public long serverGameTime() {
            return serverGameTime;
        }

        @Override
        public PacketType<ClientPacketListener, SyncPacket> getPacketType() {
            return PacketTypes.MUSIC_JUKEBOX_SYNC.get();
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void onAction(ActionPacket packet) {
            org.academy.internal.server.music.ServerJukeboxManager.handleAction(packet);
        }
    }
}
