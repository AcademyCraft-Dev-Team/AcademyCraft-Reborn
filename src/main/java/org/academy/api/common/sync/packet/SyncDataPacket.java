package org.academy.api.common.sync.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.sync.DataType;
import org.academy.api.common.sync.SyncKey;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

public final class SyncDataPacket<V> extends Packet<ClientPacketListener, SyncDataPacket<V>> {
    public static final StreamCodec<ByteBuf, SyncDataPacket<?>> CODEC = new StreamCodec<>() {
        @Override
        public SyncDataPacket<?> decode(ByteBuf buffer) {
            return innerDecode(buffer);
        }

        private <SV> SyncDataPacket<SV> innerDecode(ByteBuf buffer) {
            var syncKey = SyncKey.CODEC.decode(buffer);
            var type = UncheckedUtil.<DataType<SV>>uncheckedCast(DataType.CODEC.decode(buffer));
            var value = type.codec().decode(buffer);
            return new SyncDataPacket<>(syncKey, type, value);
        }

        @Override
        public void encode(ByteBuf buffer, SyncDataPacket<?> value) {
            innerEncode(buffer, value);
        }

        private <SV> void innerEncode(ByteBuf buffer, SyncDataPacket<SV> value) {
            SyncKey.CODEC.encode(buffer, value.getSyncKey());
            var type = value.getSyncDataType();
            DataType.CODEC.encode(buffer, type);
            type.codec().encode(buffer, value.getValue());
        }
    };

    private final SyncKey syncKey;
    private final DataType<V> dataType;
    private final V value;

    public SyncDataPacket(SyncKey syncKey, DataType<V> dataType, V value) {
        this.syncKey = syncKey;
        this.dataType = dataType;
        this.value = value;
    }

    public SyncKey getSyncKey() {
        return syncKey;
    }

    public DataType<V> getSyncDataType() {
        return dataType;
    }

    public V getValue() {
        return value;
    }

    @Override
    public PacketType<ClientPacketListener, SyncDataPacket<V>> getPacketType() {
        return UncheckedUtil.uncheckedCast(PacketTypes.SYNC_DATA.get());
    }
}