package org.academy.internal.client.renderer.entity.layers.quantum;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.academy.internal.common.attachment.AttachmentTypes;

public record QuantumSyncPayload(int entityId, QuantumData data) implements CustomPacketPayload {

    public static final Type<QuantumSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("academy", "quantum_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuantumSyncPayload::entityId,
            QuantumData.CODEC, QuantumSyncPayload::data,
            QuantumSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(QuantumSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                var entity = level.getEntity(payload.entityId);
                if (entity instanceof LivingEntity living) {
                    living.setData(AttachmentTypes.QUANTUM_DATA.get(), payload.data);
                }
            }
        });
    }
}