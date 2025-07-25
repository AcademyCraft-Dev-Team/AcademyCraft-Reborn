package org.academy.internal.common.core.particles;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import org.academy.api.common.network.PacketTarget;
import org.academy.api.common.network.packet.IPacket;
import org.academy.api.common.vanilla.ThreadType;
import org.jetbrains.annotations.NotNull;

@PacketTarget(ThreadType.CLIENT)
public class SpawnArcMediumParticlePacket extends IPacket<ClientPacketListener> {
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public SpawnArcMediumParticlePacket(ClientPacketListener clientPacketListener) {
        super(clientPacketListener);
    }

    public SpawnArcMediumParticlePacket(double x, double y, double z, float yaw, float pitch) {
        super(null);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public void read(@NotNull FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.yaw = buf.readFloat();
        this.pitch = buf.readFloat();
    }

    @Override
    public void write(@NotNull FriendlyByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.pitch);
    }

    public float getPitch() {
        return pitch;
    }

    public double getY() {
        return y;
    }

    public double getX() {
        return x;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }
}