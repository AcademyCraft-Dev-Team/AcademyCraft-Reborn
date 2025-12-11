package org.academy.internal.client.renderer.entity.layers.quantum;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.jetbrains.annotations.NotNull;

public record QuantumSyncPayload(int entityId, QuantumData data) implements CustomPacketPayload {

    public static final Type<@NotNull QuantumSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("academy", "quantum_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuantumSyncPayload::entityId,
            QuantumData.CODEC, QuantumSyncPayload::data,
            QuantumSyncPayload::new
    );

    @Override
    public @NotNull Type<? extends @NotNull CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final QuantumSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                Entity entity = level.getEntity(payload.entityId);
                if (entity instanceof LivingEntity living) {
                    living.setData(AttachmentTypes.QUANTUM_DATA.get(), payload.data);
                }
            }
        });
    }
}