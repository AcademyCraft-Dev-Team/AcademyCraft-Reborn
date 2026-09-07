package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.vfx.SkillVfxState;
import org.academy.internal.client.render.vfx.SkillVfxClient;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/** Bounded, fixed-schema snapshots replacing vanilla entity lifecycle traffic. */
@PacketTarget(ThreadType.CLIENT)
public final class SkillVfxPacket extends Packet<ClientPacketListener, SkillVfxPacket> {
    public static final StreamCodec<ByteBuf, SkillVfxPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> packet.write(buffer), SkillVfxPacket::read);
    public final Identifier dimension;
    public final long id;
    public final long revision;
    public final SkillVfxState state;

    public SkillVfxPacket(Identifier dimension, long id, long revision, SkillVfxState state) {
        this.dimension = dimension;
        this.id = id;
        this.revision = revision;
        this.state = state;
    }

    private void write(ByteBuf b) {
        Identifier.STREAM_CODEC.encode(b, dimension);
        ByteBufCodecs.VAR_LONG.encode(b, id);
        ByteBufCodecs.VAR_LONG.encode(b, revision);
        b.writeByte(switch (state) {
            case SkillVfxState.Beam ignored -> 0;
            case SkillVfxState.Plasma ignored -> 1;
            case SkillVfxState.Burst ignored -> 2;
            case SkillVfxState.End ignored -> 3;
        });
        Vec3.STREAM_CODEC.encode(b, state.position());
        switch (state) {
            case SkillVfxState.Beam s -> {
                b.writeFloat(s.xRot()); b.writeFloat(s.yRot());
                b.writeFloat(s.length()); b.writeFloat(s.scale()); b.writeFloat(s.sideOffset());
                ByteBufCodecs.VAR_INT.encode(b, s.chargeTicks());
                ByteBufCodecs.VAR_INT.encode(b, s.delayTicks());
                ByteBufCodecs.VAR_INT.encode(b, s.remainingTicks());
                b.writeByte(s.flags());
                if (s.reflected()) {
                    b.writeFloat(s.reflectionDistance()); b.writeFloat(s.returnLength());
                    Vec3.STREAM_CODEC.encode(b, s.returnDirection());
                }
                b.writeFloat(s.tickRate());
            }
            case SkillVfxState.Plasma s -> {
                b.writeBoolean(s.launched());
                if (s.launched()) {
                    Vec3.STREAM_CODEC.encode(b, s.target()); b.writeFloat(s.speed());
                    ByteBufCodecs.VAR_INT.encode(b, s.launchDelay());
                } else {
                    Vec3.STREAM_CODEC.encode(b, s.chargeOrigin());
                    b.writeFloat(s.progress()); b.writeFloat(s.chargeRate());
                }
                b.writeFloat(s.tickRate());
            }
            case SkillVfxState.Burst s -> {
                b.writeBoolean(s.plasmaImpact());
                if (!s.plasmaImpact()) Vec3.STREAM_CODEC.encode(b, s.direction());
                b.writeFloat(s.radius()); b.writeFloat(s.intensity());
                ByteBufCodecs.VAR_INT.encode(b, s.lifetimeTicks());
            }
            case SkillVfxState.End s -> b.writeBoolean(s.hidden());
        }
    }

    private static SkillVfxPacket read(ByteBuf b) {
        var dimension = Identifier.STREAM_CODEC.decode(b);
        long id = ByteBufCodecs.VAR_LONG.decode(b), revision = ByteBufCodecs.VAR_LONG.decode(b);
        int type = b.readUnsignedByte();
        var pos = vector(b);
        SkillVfxState state = switch (type) {
            case 0 -> {
                float xRot = finite(b), yRot = finite(b), length = range(b, 0, 4096);
                float scale = range(b, 0, 128), side = range(b, -128, 128);
                int charge = ticks(b), delay = ticks(b), remaining = ticks(b), flags = b.readUnsignedByte();
                if (flags > 15) throw new IllegalArgumentException("Invalid beam flags");
                float distance = 0, returnLength = 0;
                Vec3 direction = Vec3.ZERO;
                if ((flags & 8) != 0) {
                    distance = range(b, 0, 4096); returnLength = range(b, 0, 4096); direction = vector(b);
                }
                yield new SkillVfxState.Beam(pos, xRot, yRot, length, scale, side, charge, delay,
                        remaining, flags, distance, returnLength, direction, range(b, 0, 64));
            }
            case 1 -> {
                boolean launched = b.readBoolean();
                Vec3 origin = pos, target = pos;
                float speed = 0, progress = 1, chargeRate = 0;
                int delay = 0;
                if (launched) { target = vector(b); speed = range(b, 0, 256); delay = ticks(b); }
                else { origin = vector(b); progress = range(b, 0, 1); chargeRate = range(b, 0, 1); }
                yield new SkillVfxState.Plasma(pos, origin, target, progress, speed, delay,
                        launched, range(b, 0, 64), chargeRate);
            }
            case 2 -> {
                boolean impact = b.readBoolean();
                var direction = impact ? Vec3.ZERO : vector(b);
                yield new SkillVfxState.Burst(pos, direction, range(b, 0, 256), range(b, 0, 128), ticks(b), impact);
            }
            case 3 -> new SkillVfxState.End(pos, b.readBoolean());
            default -> throw new IllegalArgumentException("Unknown skill VFX type");
        };
        return new SkillVfxPacket(dimension, id, revision, state);
    }

    private static int ticks(ByteBuf b) {
        int value = ByteBufCodecs.VAR_INT.decode(b);
        if (value < 0 || value > 72000) throw new IllegalArgumentException("Invalid VFX age");
        return value;
    }

    private static float finite(ByteBuf b) {
        float value = b.readFloat();
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite VFX parameter");
        return value;
    }

    private static float range(ByteBuf b, float min, float max) {
        float value = finite(b);
        if (value < min || value > max) throw new IllegalArgumentException("Invalid VFX parameter");
        return value;
    }

    private static Vec3 vector(ByteBuf b) {
        var v = Vec3.STREAM_CODEC.decode(b);
        if (!Double.isFinite(v.lengthSqr())) throw new IllegalArgumentException("Non-finite VFX position");
        return v;
    }

    @Override
    public PacketType<ClientPacketListener, SkillVfxPacket> getPacketType() { return PacketTypes.SKILL_VFX.get(); }

    private static boolean clientInitialized;
    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static final class Client {
        private Client() {}
        @SubscribePacket
        public static void handle(SkillVfxPacket packet) { SkillVfxClient.accept(packet); }
    }
}
