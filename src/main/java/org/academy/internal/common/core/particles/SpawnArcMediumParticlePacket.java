package org.academy.internal.common.core.particles;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.misaka.api.common.network.ThreadType;
import org.academy.internal.common.network.PacketTypes;

@PacketTarget(ThreadType.CLIENT)
public class SpawnArcMediumParticlePacket extends Packet<ClientGamePacketListener, SpawnArcMediumParticlePacket> {
    public static final StreamCodec<ByteBuf, SpawnArcMediumParticlePacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            SpawnArcMediumParticlePacket::getX,
            ByteBufCodecs.DOUBLE,
            SpawnArcMediumParticlePacket::getY,
            ByteBufCodecs.DOUBLE,
            SpawnArcMediumParticlePacket::getZ,
            ByteBufCodecs.FLOAT,
            SpawnArcMediumParticlePacket::getYaw,
            ByteBufCodecs.FLOAT,
            SpawnArcMediumParticlePacket::getPitch,
            SpawnArcMediumParticlePacket::new
    );

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public SpawnArcMediumParticlePacket(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    @Override
    public PacketType<ClientGamePacketListener, SpawnArcMediumParticlePacket> getPacketType() {
        return PacketTypes.SPAWN_ARC_MEDIUM_PARTICLE.get();
    }
}