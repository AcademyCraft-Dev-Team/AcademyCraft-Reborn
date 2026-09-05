package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.internal.client.gui.screen.MisakaNetworkPanelScreen;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PacketTarget(ThreadType.CLIENT)
public final class MisakaPanelDataPacket extends Packet<ClientPacketListener, MisakaPanelDataPacket> {
    public static final StreamCodec<ByteBuf, MisakaPanelDataPacket> CODEC = StreamCodec.of(
            MisakaPanelDataPacket::encode,
            MisakaPanelDataPacket::decode
    );
    private static boolean clientInitialized;

    private final UUID entityUuid;
    private final UUID misakaUuid;
    private final int serial;
    private final int perception;
    private final float msk;
    private final int personality;
    private final boolean awakened;
    private final String currentNodeName;
    private final List<String> availableNodes;
    private final int relation;
    private final boolean privilege;
    private final boolean reconstructionWork;
    private final boolean reconstructionBlocked;

    public MisakaPanelDataPacket(
            UUID entityUuid,
            UUID misakaUuid,
            int serial,
            int perception,
            float msk,
            int personality,
            boolean awakened,
            String currentNodeName,
            List<String> availableNodes,
            int relation,
            boolean privilege,
            boolean reconstructionWork,
            boolean reconstructionBlocked
    ) {
        this.entityUuid = entityUuid;
        this.misakaUuid = misakaUuid;
        this.serial = serial;
        this.perception = perception;
        this.msk = msk;
        this.personality = personality;
        this.awakened = awakened;
        this.currentNodeName = currentNodeName == null ? "" : currentNodeName;
        this.availableNodes = availableNodes == null ? List.of() : List.copyOf(availableNodes);
        this.relation = relation;
        this.privilege = privilege;
        this.reconstructionWork = reconstructionWork;
        this.reconstructionBlocked = reconstructionBlocked;
    }

    private static void encode(ByteBuf buf, MisakaPanelDataPacket packet) {
        MisakaPacketCodecs.encodeUuid(buf, packet.entityUuid);
        MisakaPacketCodecs.encodeUuid(buf, packet.misakaUuid);
        ByteBufCodecs.VAR_INT.encode(buf, packet.serial);
        ByteBufCodecs.VAR_INT.encode(buf, packet.perception);
        buf.writeFloat(packet.msk);
        ByteBufCodecs.VAR_INT.encode(buf, packet.personality);
        buf.writeBoolean(packet.awakened);
        ByteBufCodecs.STRING_UTF8.encode(buf, packet.currentNodeName);
        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).encode(buf, new ArrayList<>(packet.availableNodes));
        ByteBufCodecs.VAR_INT.encode(buf, packet.relation);
        buf.writeBoolean(packet.privilege);
        buf.writeBoolean(packet.reconstructionWork);
        buf.writeBoolean(packet.reconstructionBlocked);
    }

    private static MisakaPanelDataPacket decode(ByteBuf buf) {
        return new MisakaPanelDataPacket(
                MisakaPacketCodecs.decodeUuid(buf),
                MisakaPacketCodecs.decodeUuid(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                buf.readFloat(),
                ByteBufCodecs.VAR_INT.decode(buf),
                buf.readBoolean(),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public static synchronized void initClient() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public UUID misakaUuid() {
        return misakaUuid;
    }

    public int serial() {
        return serial;
    }

    public int perception() {
        return perception;
    }

    public float msk() {
        return msk;
    }

    public int personality() {
        return personality;
    }

    public boolean awakened() {
        return awakened;
    }

    public String currentNodeName() {
        return currentNodeName;
    }

    public List<String> availableNodes() {
        return availableNodes;
    }

    public int relation() {
        return relation;
    }

    public boolean privilege() {
        return privilege;
    }

    public boolean reconstructionWork() {
        return reconstructionWork;
    }

    public boolean reconstructionBlocked() {
        return reconstructionBlocked;
    }

    @Override
    public PacketType<ClientPacketListener, MisakaPanelDataPacket> getPacketType() {
        return PacketTypes.MISAKA_PANEL_DATA.get();
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handle(MisakaPanelDataPacket packet) {
            Minecraft.getInstance().execute(() ->
                    Minecraft.getInstance().gui.setScreen(new MisakaNetworkPanelScreen(packet)));
        }
    }
}
