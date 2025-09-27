package org.academy.api.common.ability;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.academy.api.common.network.annotation.PacketTarget;
import org.academy.api.common.network.packet.PacketType;
import org.academy.api.common.network.packet.Packet;
import org.academy.api.common.vanilla.ThreadType;
import org.academy.internal.common.network.PacketTypes;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.CLIENT)
public final class PlayerSyncPacket extends Packet<ClientGamePacketListener, PlayerSyncPacket> {
    public static final StreamCodec<ByteBuf, PlayerSyncPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT,
            PlayerSyncPacket::getMaxComputingPower,
            ByteBufCodecs.FLOAT,
            PlayerSyncPacket::getCurrentComputingPower,
            PlayerSyncPacket::new
    );

    private final float maxComputingPower;
    private final float currentComputingPower;

    public PlayerSyncPacket(float maxComputingPower, float currentComputingPower) {
        this.maxComputingPower = maxComputingPower;
        this.currentComputingPower = currentComputingPower;
    }

    public float getMaxComputingPower() {
        return maxComputingPower;
    }

    public float getCurrentComputingPower() {
        return currentComputingPower;
    }

    @Override
    public @NotNull PacketType<ClientGamePacketListener, PlayerSyncPacket> getPacketType() {
        return PacketTypes.PLAYER_SYNC.get();
    }
}