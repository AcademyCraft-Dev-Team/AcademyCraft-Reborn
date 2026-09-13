package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import org.academy.internal.client.misaka.MisakaOrbitalChargeHud;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class MisakaOrbitalChargePacket extends Packet<ClientPacketListener, MisakaOrbitalChargePacket> {
    public static final StreamCodec<ByteBuf, MisakaOrbitalChargePacket> CODEC = StreamCodec.of(
            MisakaOrbitalChargePacket::encode,
            MisakaOrbitalChargePacket::decode
    );
    private static boolean clientInitialized;

    private final boolean active;
    private final float charge;
    private final float need;
    private final float rate;

    public MisakaOrbitalChargePacket(boolean active, float charge, float need, float rate) {
        this.active = active;
        this.charge = charge;
        this.need = need;
        this.rate = rate;
    }

    public static MisakaOrbitalChargePacket clear() {
        return new MisakaOrbitalChargePacket(false, 0.0f, 0.0f, 0.0f);
    }

    private static void encode(ByteBuf buf, MisakaOrbitalChargePacket packet) {
        buf.writeBoolean(packet.active);
        buf.writeFloat(packet.charge);
        buf.writeFloat(packet.need);
        buf.writeFloat(packet.rate);
    }

    private static MisakaOrbitalChargePacket decode(ByteBuf buf) {
        return new MisakaOrbitalChargePacket(buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static synchronized void initClient() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public boolean active() {
        return active;
    }

    public float charge() {
        return charge;
    }

    public float need() {
        return need;
    }

    public float rate() {
        return rate;
    }

    @Override
    public PacketType<ClientPacketListener, MisakaOrbitalChargePacket> getPacketType() {
        return PacketTypes.MISAKA_ORBITAL_CHARGE.get();
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handle(MisakaOrbitalChargePacket packet) {
            Minecraft.getInstance().execute(() -> MisakaOrbitalChargeHud.apply(packet));
        }
    }
}
