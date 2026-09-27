package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.music.SharedTrackEntry;
import org.academy.internal.server.music.PresetPlaylistStore;
import org.academy.internal.server.music.ResolveService;
import org.academy.internal.server.music.SharedAccountService;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;

/**
 * 共享账号/预设歌单网络包：直链解析请求响应、服主凭证上传查询（OP 校验）、服务器歌单下发喵。
 */
public final class MusicAccountPackets {
    private static final int MAX_PLAYLIST_TRACKS = 512;
    private static boolean initialized;

    private MusicAccountPackets() {
    }

    public static void initServer() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ResolveRequestPacket extends Packet<ServerGamePacketListenerImpl, ResolveRequestPacket> {
        public static final StreamCodec<ByteBuf, ResolveRequestPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.provider);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.trackId);
                },
                buf -> new ResolveRequestPacket(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );

        private final String provider;
        private final String trackId;

        public ResolveRequestPacket(String provider, String trackId) {
            this.provider = provider == null ? "" : provider;
            this.trackId = trackId == null ? "" : trackId;
        }

        public String provider() {
            return provider;
        }

        public String trackId() {
            return trackId;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ResolveRequestPacket> getPacketType() {
            return PacketTypes.MUSIC_RESOLVE_REQUEST.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ResolveResponsePacket extends Packet<ClientPacketListener, ResolveResponsePacket> {
        public static final StreamCodec<ByteBuf, ResolveResponsePacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.provider);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.trackId);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.error);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.urls.size());
                    for (var url : packet.urls) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, url);
                    }
                },
                buf -> {
                    var provider = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var trackId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var error = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var size = Math.min(ByteBufCodecs.VAR_INT.decode(buf), 16);
                    var urls = new ArrayList<String>(size);
                    for (var index = 0; index < size; index++) {
                        urls.add(ByteBufCodecs.STRING_UTF8.decode(buf));
                    }
                    return new ResolveResponsePacket(provider, trackId, error, urls);
                }
        );

        private final String provider;
        private final String trackId;
        private final String error;
        private final List<String> urls;

        public ResolveResponsePacket(String provider, String trackId, String error, List<String> urls) {
            this.provider = provider == null ? "" : provider;
            this.trackId = trackId == null ? "" : trackId;
            this.error = error == null ? "" : error;
            this.urls = List.copyOf(urls);
        }

        public String provider() {
            return provider;
        }

        public String trackId() {
            return trackId;
        }

        public String error() {
            return error;
        }

        public List<String> urls() {
            return urls;
        }

        @Override
        public PacketType<ClientPacketListener, ResolveResponsePacket> getPacketType() {
            return PacketTypes.MUSIC_RESOLVE_RESPONSE.get();
        }
    }

    public enum AccountAction {
        UPLOAD,
        CLEAR,
        STATUS;

        private static final AccountAction[] VALUES = values();

        public static final StreamCodec<ByteBuf, AccountAction> CODEC = ByteBufCodecs.idMapper(index ->
                index >= 0 && index < VALUES.length ? VALUES[index] : null, AccountAction::ordinal);
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class AccountActionPacket extends Packet<ServerGamePacketListenerImpl, AccountActionPacket> {
        public static final StreamCodec<ByteBuf, AccountActionPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    AccountAction.CODEC.encode(buf, packet.action);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.provider);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.payload);
                },
                buf -> new AccountActionPacket(
                        AccountAction.CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );

        private final AccountAction action;
        private final String provider;
        private final String payload;

        public AccountActionPacket(AccountAction action, String provider, String payload) {
            this.action = action;
            this.provider = provider == null ? "" : provider;
            this.payload = payload == null ? "" : payload;
        }

        public AccountAction action() {
            return action;
        }

        public String provider() {
            return provider;
        }

        public String payload() {
            return payload;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, AccountActionPacket> getPacketType() {
            return PacketTypes.MUSIC_ACCOUNT_ACTION.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class AccountStatusPacket extends Packet<ClientPacketListener, AccountStatusPacket> {
        public static final StreamCodec<ByteBuf, AccountStatusPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.BOOL.encode(buf, packet.sharedEnabled);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.provider);
                    ByteBufCodecs.BOOL.encode(buf, packet.hasCredential);
                    ByteBufCodecs.BOOL.encode(buf, packet.valid);
                    ByteBufCodecs.VAR_LONG.encode(buf, packet.expiresAtEpochSeconds);
                    ByteBufCodecs.BOOL.encode(buf, packet.success);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.messageKey);
                },
                buf -> new AccountStatusPacket(
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );

        private final boolean sharedEnabled;
        private final String provider;
        private final boolean hasCredential;
        private final boolean valid;
        private final long expiresAtEpochSeconds;
        private final boolean success;
        private final String messageKey;

        public AccountStatusPacket(
                boolean sharedEnabled,
                String provider,
                boolean hasCredential,
                boolean valid,
                long expiresAtEpochSeconds,
                boolean success,
                String messageKey
        ) {
            this.sharedEnabled = sharedEnabled;
            this.provider = provider == null ? "" : provider;
            this.hasCredential = hasCredential;
            this.valid = valid;
            this.expiresAtEpochSeconds = Math.max(0L, expiresAtEpochSeconds);
            this.success = success;
            this.messageKey = messageKey == null ? "" : messageKey;
        }

        public boolean sharedEnabled() {
            return sharedEnabled;
        }

        public String provider() {
            return provider;
        }

        public boolean hasCredential() {
            return hasCredential;
        }

        public boolean valid() {
            return valid;
        }

        public long expiresAtEpochSeconds() {
            return expiresAtEpochSeconds;
        }

        public boolean success() {
            return success;
        }

        public String messageKey() {
            return messageKey;
        }

        @Override
        public PacketType<ClientPacketListener, AccountStatusPacket> getPacketType() {
            return PacketTypes.MUSIC_ACCOUNT_STATUS.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ServerPlaylistPacket extends Packet<ClientPacketListener, ServerPlaylistPacket> {
        public static final StreamCodec<ByteBuf, ServerPlaylistPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.BOOL.encode(buf, packet.enabled);
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.mode);
                    ByteBufCodecs.VAR_INT.encode(buf, Math.min(packet.tracks.size(), MAX_PLAYLIST_TRACKS));
                    for (var index = 0; index < Math.min(packet.tracks.size(), MAX_PLAYLIST_TRACKS); index++) {
                        SharedTrackEntry.CODEC.encode(buf, packet.tracks.get(index));
                    }
                },
                buf -> {
                    var enabled = ByteBufCodecs.BOOL.decode(buf);
                    var mode = ByteBufCodecs.STRING_UTF8.decode(buf);
                    var size = Math.min(ByteBufCodecs.VAR_INT.decode(buf), MAX_PLAYLIST_TRACKS);
                    var tracks = new ArrayList<SharedTrackEntry>(size);
                    for (var index = 0; index < size; index++) {
                        tracks.add(SharedTrackEntry.CODEC.decode(buf));
                    }
                    return new ServerPlaylistPacket(enabled, mode, tracks);
                }
        );

        private final boolean enabled;
        private final String mode;
        private final List<SharedTrackEntry> tracks;

        public ServerPlaylistPacket(boolean enabled, String mode, List<SharedTrackEntry> tracks) {
            this.enabled = enabled;
            this.mode = mode == null ? "" : mode;
            this.tracks = List.copyOf(tracks);
        }

        public boolean enabled() {
            return enabled;
        }

        public String mode() {
            return mode;
        }

        public List<SharedTrackEntry> tracks() {
            return tracks;
        }

        @Override
        public PacketType<ClientPacketListener, ServerPlaylistPacket> getPacketType() {
            return PacketTypes.MUSIC_SERVER_PLAYLIST.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class PlaylistRequestPacket extends Packet<ServerGamePacketListenerImpl, PlaylistRequestPacket> {
        public static final StreamCodec<ByteBuf, PlaylistRequestPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                },
                buf -> new PlaylistRequestPacket()
        );

        @Override
        public PacketType<ServerGamePacketListenerImpl, PlaylistRequestPacket> getPacketType() {
            return PacketTypes.MUSIC_PLAYLIST_REQUEST.get();
        }
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void onResolveRequest(ResolveRequestPacket packet) {
            ResolveService.handleRequest(packet, packet.getPacketListener().getPlayer());
        }

        @SubscribePacket
        public static void onAccountAction(AccountActionPacket packet) {
            SharedAccountService.handleAction(packet, packet.getPacketListener().getPlayer());
        }

        @SubscribePacket
        public static void onPlaylistRequest(PlaylistRequestPacket packet) {
            var store = PresetPlaylistStore.getInstance();
            MisakaNetworkServer.send(
                    packet.getPacketListener().getPlayer(),
                    new ServerPlaylistPacket(store.isEnabled(), store.mode().name(), store.tracks())
            );
        }
    }
}
