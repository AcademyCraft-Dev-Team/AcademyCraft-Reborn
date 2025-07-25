package org.academy.internal.common.attachment;

import com.mojang.serialization.Codec;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.academy.api.common.network.FriendlyByteBufDeserializers;
import org.academy.api.common.network.FriendlyByteBufSerializers;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

import static net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES;
import static org.academy.AcademyCraft.MODID;

public final class AttachmentTypes {
    public static final DeferredRegister<AttachmentType<?>> REGISTER = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);
    public static final Supplier<Boolean> DEFAULT_FALSE = () -> false;
    public static final Supplier<Boolean> DEFAULT_TRUE = () -> true;
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> HAS_DATA_TERMINAL = REGISTER.register("has_data_terminal",
            () -> AttachmentType
                    .builder(DEFAULT_FALSE)
                    .serialize(Codec.BOOL)
                    .build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> ACTIVATED_STORM_WING = REGISTER.register("activated_storm_wing",
            () -> AttachmentType
                    .builder(DEFAULT_FALSE)
                    .serialize(Codec.BOOL)
                    .build());

    /**
     * SubscribePacket is not needed.
     */
    @PacketTarget(ThreadType.CLIENT)
    public static final class AttachmentDataSyncPacket<T> extends IPacket<ClientPacketListener> {
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

        @Override
        public void read(@NotNull FriendlyByteBuf buf) {
            var id = buf.readInt();
            entity = getPacketListener().getLevel().getEntity(id);
            var typeId = buf.readInt();
            type = (AttachmentType<T>) ATTACHMENT_TYPES.byIdOrThrow(typeId);
            var deserializerId = buf.readInt();
            var deserializer = FriendlyByteBufDeserializers.getRequiredDeserializer(deserializerId);
            var value = (T) deserializer.deserialize(buf);
            entity.setData(type, value);
        }

        @Override
        public void write(@NotNull FriendlyByteBuf buf) {
            buf.writeInt(entity.getId());
            buf.writeInt(ATTACHMENT_TYPES.getIdOrThrow(type));
            var value = entity.getData(type);
            var clazz = value.getClass();
            buf.writeInt(FriendlyByteBufDeserializers.getDeserializerId(clazz));
            FriendlyByteBufSerializers.getRequiredSerializer(clazz).serialize(buf, value);
        }
    }
}