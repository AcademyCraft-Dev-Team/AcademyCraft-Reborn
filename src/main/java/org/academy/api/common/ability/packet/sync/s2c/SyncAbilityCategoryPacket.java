package org.academy.api.common.ability.packet.sync.s2c;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.api.common.ability.AbilityCategory;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.misaka.api.common.network.ThreadType;
import org.academy.internal.common.network.PacketTypes;

@PacketTarget(ThreadType.CLIENT)
public final class SyncAbilityCategoryPacket extends Packet<ClientGamePacketListener, SyncAbilityCategoryPacket> {
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
    public PacketType<ClientGamePacketListener, SyncAbilityCategoryPacket> getPacketType() {
        return PacketTypes.SYNC_ABILITY_CATEGORY.get();
    }
}