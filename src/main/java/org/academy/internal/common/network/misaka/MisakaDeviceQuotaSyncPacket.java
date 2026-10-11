package org.academy.internal.common.network.misaka;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.academy.internal.client.misaka.MisakaDeviceQuotaClient;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;

@PacketTarget(ThreadType.CLIENT)
public final class MisakaDeviceQuotaSyncPacket
        extends Packet<ClientPacketListener, MisakaDeviceQuotaSyncPacket> {
    public static final StreamCodec<ByteBuf, MisakaDeviceQuotaSyncPacket> CODEC = StreamCodec.of(
            MisakaDeviceQuotaSyncPacket::encode,
            MisakaDeviceQuotaSyncPacket::decode
    );
    private static boolean clientInitialized;

    private final float playerBudgetMskPerSec;
    private final int remainingPercentSlots;
    private final List<DeviceQuotaRow> rows;

    public MisakaDeviceQuotaSyncPacket(
            float playerBudgetMskPerSec,
            int remainingPercentSlots,
            List<DeviceQuotaRow> rows
    ) {
        this.playerBudgetMskPerSec = playerBudgetMskPerSec;
        this.remainingPercentSlots = remainingPercentSlots;
        this.rows = rows == null ? List.of() : List.copyOf(rows);
    }

    private static void encode(ByteBuf buf, MisakaDeviceQuotaSyncPacket packet) {
        buf.writeFloat(packet.playerBudgetMskPerSec);
        ByteBufCodecs.VAR_INT.encode(buf, packet.remainingPercentSlots);
        ByteBufCodecs.VAR_INT.encode(buf, packet.rows.size());
        for (var row : packet.rows) {
            ByteBufCodecs.STRING_UTF8.encode(buf, row.id().toString());
            ByteBufCodecs.VAR_INT.encode(buf, row.effectivePercent());
            ByteBufCodecs.VAR_INT.encode(buf, row.lastPercent());
            buf.writeBoolean(row.present());
        }
    }

    private static MisakaDeviceQuotaSyncPacket decode(ByteBuf buf) {
        float budget = buf.readFloat();
        int remaining = ByteBufCodecs.VAR_INT.decode(buf);
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        var rows = new ArrayList<DeviceQuotaRow>(count);
        for (int i = 0; i < count; i++) {
            var id = Identifier.parse(ByteBufCodecs.STRING_UTF8.decode(buf));
            int effective = ByteBufCodecs.VAR_INT.decode(buf);
            int last = ByteBufCodecs.VAR_INT.decode(buf);
            boolean present = buf.readBoolean();
            rows.add(new DeviceQuotaRow(id, effective, last, present));
        }
        return new MisakaDeviceQuotaSyncPacket(budget, remaining, rows);
    }

    public float playerBudgetMskPerSec() {
        return playerBudgetMskPerSec;
    }

    public int remainingPercentSlots() {
        return remainingPercentSlots;
    }

    public List<DeviceQuotaRow> rows() {
        return rows;
    }

    public static synchronized void initClient() {
        if (clientInitialized) {
            return;
        }
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    @Override
    public PacketType<ClientPacketListener, MisakaDeviceQuotaSyncPacket> getPacketType() {
        return PacketTypes.MISAKA_DEVICE_QUOTA_SYNC.get();
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handle(MisakaDeviceQuotaSyncPacket packet) {
            MisakaDeviceQuotaClient.apply(packet);
        }
    }

    public record DeviceQuotaRow(
            Identifier id,
            int effectivePercent,
            int lastPercent,
            boolean present
    ) {
    }
}
