package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.misaka.MisakaNetworkPermission;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.misaka.MisakaNetManageSupport;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.UUID;

/**
 * Grant or revoke a network permission for a named player via the Misaka manage console.
 */
@PacketTarget(ThreadType.SERVER)
public final class SetMisakaNetworkPermissionPacket
        extends Packet<ServerGamePacketListenerImpl, SetMisakaNetworkPermissionPacket> {
    public static final StreamCodec<ByteBuf, SetMisakaNetworkPermissionPacket> CODEC = StreamCodec.of(
            SetMisakaNetworkPermissionPacket::encode,
            SetMisakaNetworkPermissionPacket::decode
    );
    private static boolean serverInitialized;

    private final UUID misakaUuid;
    private final String targetPlayerName;
    private final String permissionName;
    private final boolean grant;
    private final int pageIndex;

    public SetMisakaNetworkPermissionPacket(
            UUID misakaUuid,
            String targetPlayerName,
            String permissionName,
            boolean grant,
            int pageIndex
    ) {
        this.misakaUuid = misakaUuid;
        this.targetPlayerName = targetPlayerName == null ? "" : targetPlayerName;
        this.permissionName = permissionName == null ? "" : permissionName;
        this.grant = grant;
        this.pageIndex = pageIndex;
    }

    private static void encode(ByteBuf buf, SetMisakaNetworkPermissionPacket packet) {
        MisakaPacketCodecs.encodeUuid(buf, packet.misakaUuid);
        ByteBufCodecs.STRING_UTF8.encode(buf, packet.targetPlayerName);
        ByteBufCodecs.STRING_UTF8.encode(buf, packet.permissionName);
        buf.writeBoolean(packet.grant);
        ByteBufCodecs.VAR_INT.encode(buf, packet.pageIndex);
    }

    private static SetMisakaNetworkPermissionPacket decode(ByteBuf buf) {
        return new SetMisakaNetworkPermissionPacket(
                MisakaPacketCodecs.decodeUuid(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readBoolean(),
                ByteBufCodecs.VAR_INT.decode(buf)
        );
    }

    public UUID misakaUuid() {
        return misakaUuid;
    }

    public String targetPlayerName() {
        return targetPlayerName;
    }

    public String permissionName() {
        return permissionName;
    }

    public boolean grant() {
        return grant;
    }

    public int pageIndex() {
        return pageIndex;
    }

    public static synchronized void initServer() {
        if (serverInitialized) {
            return;
        }
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, SetMisakaNetworkPermissionPacket> getPacketType() {
        return PacketTypes.SET_MISAKA_NETWORK_PERMISSION.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(SetMisakaNetworkPermissionPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            MisakaNetworkPermission permission;
            try {
                permission = MisakaNetworkPermission.valueOf(packet.permissionName());
            } catch (IllegalArgumentException ex) {
                return;
            }
            if (MisakaNetManageSupport.setMemberPermission(
                    player,
                    packet.misakaUuid(),
                    packet.targetPlayerName(),
                    permission,
                    packet.grant()
            )) {
                MisakaNetManageSupport.sendManagePage(player, packet.misakaUuid(), packet.pageIndex());
            }
        }
    }
}
