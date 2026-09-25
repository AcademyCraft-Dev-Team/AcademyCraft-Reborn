package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.internal.client.render.vfx.WingVfx;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class AdvancedWingTransitionPacket extends Packet<ClientPacketListener, AdvancedWingTransitionPacket> {
    public static final StreamCodec<ByteBuf, AdvancedWingTransitionPacket> CODEC = StreamCodec.of(
            (buf, packet) -> ByteBufCodecs.VAR_INT.encode(buf, packet.entityId),
            buf -> new AdvancedWingTransitionPacket(ByteBufCodecs.VAR_INT.decode(buf))
    );
    private static boolean clientInitialized;
    private final int entityId;

    public AdvancedWingTransitionPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    @Override
    public PacketType<ClientPacketListener, AdvancedWingTransitionPacket> getPacketType() {
        return PacketTypes.ADVANCED_WING_TRANSITION.get();
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handleTransition(AdvancedWingTransitionPacket packet) {
            WingVfx.enqueueBlackToWhiteTransition(packet.entityId);
        }
    }
}
