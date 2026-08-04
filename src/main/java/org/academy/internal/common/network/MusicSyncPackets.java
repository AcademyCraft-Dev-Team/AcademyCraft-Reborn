package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class MusicSyncPackets {
    private static final double SHARE_RANGE_SQR = 32.0 * 32.0;
    private static boolean initialized;

    private MusicSyncPackets() {
    }

    public static void initServer() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public record TrackSnapshot(
            String provider,
            String trackId,
            String title,
            String artist,
            int durationSeconds,
            boolean vip,
            String artworkUrl,
            float positionSeconds,
            boolean playing
    ) {
        public TrackSnapshot {
            provider = safe(provider);
            trackId = safe(trackId);
            title = safe(title);
            artist = safe(artist);
            artworkUrl = safe(artworkUrl);
            durationSeconds = Math.max(0, durationSeconds);
            positionSeconds = Float.isFinite(positionSeconds) ? Math.max(0.0f, positionSeconds) : 0.0f;
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    private static final StreamCodec<ByteBuf, TrackSnapshot> SNAPSHOT_CODEC = StreamCodec.of(
            (buf, snapshot) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, snapshot.provider);
                ByteBufCodecs.STRING_UTF8.encode(buf, snapshot.trackId);
                ByteBufCodecs.STRING_UTF8.encode(buf, snapshot.title);
                ByteBufCodecs.STRING_UTF8.encode(buf, snapshot.artist);
                ByteBufCodecs.VAR_INT.encode(buf, snapshot.durationSeconds);
                ByteBufCodecs.BOOL.encode(buf, snapshot.vip);
                ByteBufCodecs.STRING_UTF8.encode(buf, snapshot.artworkUrl);
                ByteBufCodecs.FLOAT.encode(buf, snapshot.positionSeconds);
                ByteBufCodecs.BOOL.encode(buf, snapshot.playing);
            },
            buf -> new TrackSnapshot(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)
            )
    );

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void share(SharePacket packet) {
            var sender = packet.getPacketListener().getPlayer();
            var broadcast = new SyncPacket(sender.getGameProfile().name(), packet.snapshot);
            for (var receiver : sender.level().players()) {
                if (receiver == sender || receiver.distanceToSqr(sender) > SHARE_RANGE_SQR) continue;
                MisakaNetworkServer.send(receiver, broadcast);
            }
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SharePacket extends Packet<ServerGamePacketListenerImpl, SharePacket> {
        public static final StreamCodec<ByteBuf, SharePacket> CODEC = SNAPSHOT_CODEC.map(
                SharePacket::new,
                packet -> packet.snapshot
        );
        private final TrackSnapshot snapshot;

        public SharePacket(TrackSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SharePacket> getPacketType() {
            return PacketTypes.MUSIC_SHARE.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SyncPacket extends Packet<ClientPacketListener, SyncPacket> {
        public static final StreamCodec<ByteBuf, SyncPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.senderName);
                    SNAPSHOT_CODEC.encode(buf, packet.snapshot);
                },
                buf -> new SyncPacket(ByteBufCodecs.STRING_UTF8.decode(buf), SNAPSHOT_CODEC.decode(buf))
        );
        private final String senderName;
        private final TrackSnapshot snapshot;

        public SyncPacket(String senderName, TrackSnapshot snapshot) {
            this.senderName = senderName == null ? "" : senderName;
            this.snapshot = snapshot;
        }

        public String senderName() {
            return senderName;
        }

        public TrackSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public PacketType<ClientPacketListener, SyncPacket> getPacketType() {
            return PacketTypes.MUSIC_SYNC.get();
        }
    }
}
