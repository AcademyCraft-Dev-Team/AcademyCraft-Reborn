package org.academy.internal.common.ability.darkmatter;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class SyncDarkmatterStatePacket extends Packet<ClientPacketListener, SyncDarkmatterStatePacket> {
    public static final StreamCodec<ByteBuf, SyncDarkmatterStatePacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncDarkmatterStatePacket::abilityLevel,
            ByteBufCodecs.VAR_INT, SyncDarkmatterStatePacket::totalPoints,
            ByteBufCodecs.VAR_INT, SyncDarkmatterStatePacket::alphaPoints,
            ByteBufCodecs.BOOL, SyncDarkmatterStatePacket::gammaActive,
            ByteBufCodecs.FLOAT, SyncDarkmatterStatePacket::naturalMatter,
            ByteBufCodecs.FLOAT, SyncDarkmatterStatePacket::createdMatter,
            ByteBufCodecs.FLOAT, SyncDarkmatterStatePacket::reservedMatter,
            SyncDarkmatterStatePacket::new
    );

    private final int abilityLevel;
    private final int totalPoints;
    private final int alphaPoints;
    private final boolean gammaActive;
    private final float naturalMatter;
    private final float createdMatter;
    private final float reservedMatter;

    public SyncDarkmatterStatePacket(int abilityLevel, int totalPoints, int alphaPoints,
                                     boolean gammaActive, float naturalMatter,
                                     float createdMatter, float reservedMatter) {
        this.abilityLevel = Math.clamp(abilityLevel, 0, 5);
        this.totalPoints = Math.max(0, totalPoints);
        this.alphaPoints = Math.clamp(alphaPoints, 0, this.totalPoints);
        this.gammaActive = gammaActive;
        this.naturalMatter = finite(naturalMatter);
        this.createdMatter = finite(createdMatter);
        this.reservedMatter = finite(reservedMatter);
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }

    public int abilityLevel() {
        return abilityLevel;
    }

    public int totalPoints() {
        return totalPoints;
    }

    public int alphaPoints() {
        return alphaPoints;
    }

    public boolean gammaActive() {
        return gammaActive;
    }

    public float naturalMatter() {
        return naturalMatter;
    }

    public float createdMatter() {
        return createdMatter;
    }

    public float reservedMatter() {
        return reservedMatter;
    }

    @Override
    public PacketType<ClientPacketListener, SyncDarkmatterStatePacket> getPacketType() {
        return PacketTypes.SYNC_DARKMATTER_STATE.get();
    }
}
