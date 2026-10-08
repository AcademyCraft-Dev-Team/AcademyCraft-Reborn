package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.vfx.RepairVisualParts;
import org.academy.api.common.vfx.DirectionalArea;
import org.academy.internal.client.render.vfx.DarkmatterVfxClient;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/** Upserts bounded presentation state; camera and environmental lighting stay client-side. */
@PacketTarget(ThreadType.CLIENT)
public final class DarkmatterVisualPacket extends Packet<ClientPacketListener, DarkmatterVisualPacket> {
    public enum Kind {
        INTERFERENCE("darkmatter_interference", 12), CONTACT("darkmatter_light_contact", 4),
        REPAIR("darkmatter_repair", 8), DISASSEMBLE("darkmatter_disassemble", 13),
        GENERATION("darkmatter_generation", 8), ELIMINATION("darkmatter_elimination", 8),
        SHAPING("darkmatter_shaping", 13), PHASE_TUNING("darkmatter_phase_tuning", 8),
        SUMMON("darkmatter_creation_summon", 13), DISMISS("darkmatter_creation_dismiss", 13),
        CREATURE_CONTACT("darkmatter_creation_contact", 5), CUT("darkmatter_cut", 7),
        WING_STRIKE("darkmatter_wing_strike", 7);
        public final String graph;
        public final int leaseTicks;
        Kind(String graph, int leaseTicks) { this.graph = graph; this.leaseTicks = leaseTicks; }
    }
    private static final Kind[] KINDS = Kind.values();

    public static final StreamCodec<ByteBuf, DarkmatterVisualPacket> CODEC = StreamCodec.of((b, p) -> {
        Identifier.STREAM_CODEC.encode(b, p.dimension);
        b.writeByte(p.kind.ordinal());
        ByteBufCodecs.VAR_INT.encode(b, p.entityId);
        b.writeBoolean(p.active);
        Vec3.STREAM_CODEC.encode(b, p.position);
        b.writeFloat(p.width); b.writeFloat(p.height); b.writeFloat(p.range);
        b.writeByte(p.parts); b.writeBoolean(p.finished); b.writeLong(p.seed);
        b.writeBoolean(p.area != null);
        if (p.area != null) {
            Vec3.STREAM_CODEC.encode(b, p.area.direction());
            b.writeDouble(p.area.first().radius()); b.writeDouble(p.area.first().minimumDot());
            b.writeDouble(p.area.second().radius()); b.writeDouble(p.area.second().minimumDot());
        }
        b.writeFloat(p.alpha); b.writeFloat(p.beta); b.writeFloat(p.gamma);
        ByteBufCodecs.VAR_INT.encode(b, p.variant);
        Vec3.STREAM_CODEC.encode(b, p.direction);
    }, b -> {
        var dimension = Identifier.STREAM_CODEC.decode(b);
        int kind = b.readUnsignedByte();
        if (kind >= KINDS.length) throw new IllegalArgumentException("Invalid matter effect kind");
        int entity = ByteBufCodecs.VAR_INT.decode(b);
        boolean active = b.readBoolean();
        var position = Vec3.STREAM_CODEC.decode(b);
        float width = b.readFloat(), height = b.readFloat(), range = b.readFloat();
        int parts = b.readUnsignedByte(); boolean finished = b.readBoolean(); long seed = b.readLong();
        var area = b.readBoolean() ? new DirectionalArea(position, Vec3.STREAM_CODEC.decode(b),
                new DirectionalArea.Cone(b.readDouble(), b.readDouble()), new DirectionalArea.Cone(b.readDouble(), b.readDouble())) : null;
        return new DarkmatterVisualPacket(dimension, KINDS[kind], entity, active, position,
                width, height, range, parts, finished, seed, area,
                b.readFloat(), b.readFloat(), b.readFloat(), ByteBufCodecs.VAR_INT.decode(b), Vec3.STREAM_CODEC.decode(b));
    });

    public final Identifier dimension;
    public final Kind kind;
    public final int entityId;
    public final boolean active, finished;
    public final Vec3 position;
    public final float width, height, range;
    public final int parts;
    public final long seed;
    public final @org.jspecify.annotations.Nullable DirectionalArea area;
    public final float alpha, beta, gamma;
    public final int variant;
    public final Vec3 direction;

    public DarkmatterVisualPacket(Identifier dimension, Kind kind, int entityId, boolean active, Vec3 position,
                                  float width, float height, float range, int parts, boolean finished, long seed) {
        this(dimension, kind, entityId, active, position, width, height, range, parts, finished, seed, null);
    }

    public DarkmatterVisualPacket(Identifier dimension, Kind kind, int entityId, boolean active, Vec3 position,
                                  float width, float height, float range, int parts, boolean finished, long seed,
                                  @org.jspecify.annotations.Nullable DirectionalArea area) {
        this(dimension, kind, entityId, active, position, width, height, range, parts, finished, seed,
                area, 0.5f, 0.5f, 0, 0, new Vec3(0, 0, 1));
    }

    public DarkmatterVisualPacket(Identifier dimension, Kind kind, int entityId, boolean active, Vec3 position,
                                  float width, float height, float range, int parts, boolean finished, long seed,
                                  @org.jspecify.annotations.Nullable DirectionalArea area,
                                  float alpha, float beta, float gamma, int variant, Vec3 direction) {
        if (entityId < -1 || !Double.isFinite(position.lengthSqr()) || !finiteBound(width, 32)
                || !finiteBound(height, 64) || !finiteBound(range, 512) || (parts & ~RepairVisualParts.ALL) != 0
                || !finiteBound(alpha, 16) || !finiteBound(beta, 16) || !finiteBound(gamma, 16)
                || variant < -1 || variant > 32 || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1e-12) {
            throw new IllegalArgumentException("Invalid matter visual state");
        }
        this.dimension = dimension; this.kind = kind; this.entityId = entityId; this.active = active;
        this.position = position; this.width = width; this.height = height; this.range = range;
        this.parts = parts; this.finished = finished; this.seed = seed;
        if (area != null && (kind != Kind.INTERFERENCE || !area.origin().equals(position)))
            throw new IllegalArgumentException("Area does not match visual state");
        this.area = area;
        this.alpha = alpha; this.beta = beta; this.gamma = gamma; this.variant = variant;
        this.direction = direction.normalize();
    }

    private static boolean finiteBound(float value, float max) { return Float.isFinite(value) && value >= 0 && value <= max; }

    public void broadcast(ServerLevel level) {
        double visibleDistance = 128 + (area == null ? 0 : area.radius());
        double visibleDistanceSquared = visibleDistance * visibleDistance;
        boolean globalStop = kind == Kind.INTERFERENCE && !active;
        for (var observer : level.players()) {
            if (globalStop || observer.position().distanceToSqr(position) <= visibleDistanceSquared)
                MisakaNetworkServer.send(observer, this);
        }
    }

    public static void initClient() { MisakaNetworkClient.NETWORK_MANAGER.register(Client.class); }
    @Override public PacketType<ClientPacketListener, DarkmatterVisualPacket> getPacketType() { return PacketTypes.DARKMATTER_VISUAL.get(); }
    public static final class Client {
        @SubscribePacket public static void handle(DarkmatterVisualPacket packet) { DarkmatterVfxClient.accept(packet); }
    }
}
