package org.academy.internal.common.ability.accelerator.skills.lv2;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.academy.internal.client.renderer.effect.DirStrikeGroundEffect;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class DirStrikeVisualPacket extends Packet<ClientPacketListener, DirStrikeVisualPacket> {
    public static final StreamCodec<ByteBuf, DirStrikeVisualPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                Vec3.STREAM_CODEC.encode(buffer, packet.center);
                BlockPos.STREAM_CODEC.encode(buffer, packet.origin);
                ByteBufCodecs.VAR_INT.encode(buffer, packet.radius);
                ByteBufCodecs.BOOL.encode(buffer, packet.airborne);
                ByteBufCodecs.FLOAT.encode(buffer, packet.lookX);
                ByteBufCodecs.FLOAT.encode(buffer, packet.lookZ);
                ByteBufCodecs.LONG.encode(buffer, packet.seed);
            },
            buffer -> new DirStrikeVisualPacket(
                    Vec3.STREAM_CODEC.decode(buffer),
                    BlockPos.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.LONG.decode(buffer)
            )
    );
    private static final double BROADCAST_RANGE = 96.0;
    private static final int MAX_RADIUS = 32;
    private static boolean clientInitialized;

    private final Vec3 center;
    private final BlockPos origin;
    private final int radius;
    private final boolean airborne;
    private final float lookX;
    private final float lookZ;
    private final long seed;

    private DirStrikeVisualPacket(Vec3 center, BlockPos origin, int radius, boolean airborne,
                                  float lookX, float lookZ, long seed) {
        this.center = center;
        this.origin = origin;
        this.radius = radius;
        this.airborne = airborne;
        this.lookX = lookX;
        this.lookZ = lookZ;
        this.seed = seed;
    }

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
        NeoForge.EVENT_BUS.register(DirStrikeGroundEffect.class);
    }

    public static void broadcast(ServerLevel level, Vec3 center, BlockPos origin, int radius,
                                 boolean airborne, Vec3 look) {
        var seed = level.getRandom().nextLong();
        var packet = new DirStrikeVisualPacket(
                center, origin, radius, airborne, (float) look.x, (float) look.z, seed);
        var rangeSquared = BROADCAST_RANGE * BROADCAST_RANGE;
        for (var observer : level.players()) {
            if (observer.distanceToSqr(center) <= rangeSquared) {
                MisakaNetworkServer.send(observer, packet);
            }
        }
    }

    @Override
    public PacketType<ClientPacketListener, DirStrikeVisualPacket> getPacketType() {
        return PacketTypes.DIR_STRIKE_VISUAL.get();
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handle(DirStrikeVisualPacket packet) {
            if (!valid(packet)) return;
            var minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            DirStrikeGroundEffect.spawn(
                    packet.center,
                    packet.origin,
                    packet.radius,
                    packet.airborne,
                    packet.lookX,
                    packet.lookZ,
                    packet.seed
            );
        }

        private static boolean valid(DirStrikeVisualPacket packet) {
            return packet.center != null
                    && packet.origin != null
                    && packet.radius > 0
                    && packet.radius <= MAX_RADIUS
                    && Double.isFinite(packet.center.x)
                    && Double.isFinite(packet.center.y)
                    && Double.isFinite(packet.center.z)
                    && Float.isFinite(packet.lookX)
                    && Float.isFinite(packet.lookZ);
        }
    }
}
