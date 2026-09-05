package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.misaka.InteractionGate;
import org.academy.internal.common.world.entity.misaka.WanderStyle;
import org.academy.internal.common.world.entity.misaka.favor.FavorService;
import org.academy.internal.server.misaka.MisakaPanelSupport;
import org.academy.internal.server.world.level.storage.MisakaSisterRoster;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.UUID;

@PacketTarget(ThreadType.SERVER)
public final class SetMisakaWanderStylePacket
        extends Packet<ServerGamePacketListenerImpl, SetMisakaWanderStylePacket> {
    public static final StreamCodec<ByteBuf, SetMisakaWanderStylePacket> CODEC = StreamCodec.composite(
            MisakaPacketCodecs.UUID_STREAM_CODEC,
            SetMisakaWanderStylePacket::entityUuid,
            ByteBufCodecs.VAR_INT,
            SetMisakaWanderStylePacket::style,
            SetMisakaWanderStylePacket::new
    );
    private static boolean serverInitialized;

    private final UUID entityUuid;
    private final int style;

    public SetMisakaWanderStylePacket(UUID entityUuid, int style) {
        this.entityUuid = entityUuid;
        this.style = style;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public int style() {
        return style;
    }

    public static synchronized void initServer() {
        if (serverInitialized) {
            return;
        }
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override
    public PacketType<ServerGamePacketListenerImpl, SetMisakaWanderStylePacket> getPacketType() {
        return PacketTypes.SET_MISAKA_WANDER_STYLE.get();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void handle(SetMisakaWanderStylePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            var sister = MisakaPanelSupport.findLoadedEntity((ServerLevel) player.level(), packet.entityUuid()).orElse(null);
            if (sister == null) {
                return;
            }
            var record = sister.rosterRecord().orElse(null);
            if (record == null) {
                return;
            }
            String name = player.getGameProfile().name();
            if (!InteractionGate.allow(record, name, InteractionGate.Intent.STATE)) {
                return;
            }
            InteractionGate.touchBenevolent(record, name, ((ServerLevel) player.level()).getServer());
            var wanderStyle = WanderStyle.fromOrdinal(packet.style());
            MisakaSisterRoster.get(((ServerLevel) player.level()).getServer()).modify(record.misakaUuid, sisterRecord -> {
                sisterRecord.wanderStyle = wanderStyle;
            });
            sister.setWanderStyle(wanderStyle);
        }
    }
}
