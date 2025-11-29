package org.academy.api.common.ability.pakcet;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class SyncAbilityCategoryPacket extends Packet<ClientPacketListener, SyncAbilityCategoryPacket> {
    public static final StreamCodec<ByteBuf, SyncAbilityCategoryPacket> CODEC = StreamCodec.composite(
            AbilityCategory.STREAM_CODEC,
            SyncAbilityCategoryPacket::getAbilityCategory,
            SyncAbilityCategoryPacket::new
    );

    private final AbilityCategory abilityCategory;

    public SyncAbilityCategoryPacket(AbilityCategory abilityCategory) {
        this.abilityCategory = abilityCategory;
    }

    public AbilityCategory getAbilityCategory() {
        return abilityCategory;
    }

    @Override
    public PacketType<ClientPacketListener, SyncAbilityCategoryPacket> getPacketType() {
        return PacketTypes.SYNC_ABILITY_CATEGORY.get();
    }
}