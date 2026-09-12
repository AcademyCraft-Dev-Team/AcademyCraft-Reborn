package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.world.entity.misaka.InteractionGate;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;
import org.academy.internal.common.world.entity.misaka.WanderStyle;
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
            var session = MisakaPanelSupport.load(packet.getPacketListener().getPlayer(), packet.entityUuid());
            if (session == null) {
                return;
            }
            if (!session.allow(InteractionGate.Intent.STATE)) {
                return;
            }
            session.touch();
            if (!session.inRange(MisakaSisterEntity.PANEL_RANGE_SQR)) {
                return;
            }
            var wanderStyle = WanderStyle.fromOrdinal(packet.style());
            MisakaSisterRoster.get(session.level().getServer()).modify(session.record().misakaUuid, sisterRecord -> {
                sisterRecord.wanderStyle = wanderStyle;
            });
            session.sister().setWanderStyle(wanderStyle);
        }
    }
}
