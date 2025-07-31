package org.academy.api.common.attachment;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.academy.api.common.network.FBBDeserializers;
import org.academy.api.common.network.FBBSerializers;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.PacketType;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;

import static net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES;

/**
 * SubscribePacket is not needed.
 */
@PacketTarget(ThreadType.CLIENT)
public final class AttachmentDataSyncPacket<T> extends Packet<ClientPacketListener> {
    private Entity entity;
    private AttachmentType<T> type;

    public AttachmentDataSyncPacket(ClientPacketListener clientPacketListener) {
        super(clientPacketListener);
    }

    public AttachmentDataSyncPacket(Entity entity, AttachmentType<T> type) {
        super(null);
        this.entity = entity;
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        var id = buf.readInt();
        entity = getPacketListener().getLevel().getEntity(id);
        var typeId = buf.readInt();
        type = (AttachmentType<T>) ATTACHMENT_TYPES.byIdOrThrow(typeId);
        var deserializerId = buf.readInt();
        var deserializer = FBBDeserializers.getRequiredDeserializer(deserializerId);
        var value = (T) deserializer.deserialize(buf);
        entity.setData(type, value);
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeInt(entity.getId());
        buf.writeInt(ATTACHMENT_TYPES.getIdOrThrow(type));
        var value = entity.getData(type);
        var clazz = value.getClass();
        buf.writeInt(FBBDeserializers.getDeserializerId(clazz));
        FBBSerializers.getRequiredSerializer(clazz).serialize(buf, value);
    }

    @Override
    public @NotNull PacketType<ClientPacketListener, ? extends Packet<ClientPacketListener>> getPacketType() {
        return PacketTypes.ATTACHMENT_DATA_SYNC.get();
    }
}
