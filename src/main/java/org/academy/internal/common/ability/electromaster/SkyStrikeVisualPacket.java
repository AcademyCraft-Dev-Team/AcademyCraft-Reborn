package org.academy.internal.common.ability.electromaster;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.client.renderer.vfx.SkyStrikeVfxClient;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

@PacketTarget(ThreadType.CLIENT)
public final class SkyStrikeVisualPacket extends Packet<ClientPacketListener, SkyStrikeVisualPacket> {
    public static final double BROADCAST_RANGE = 128.0;
    public static final StreamCodec<ByteBuf, SkyStrikeVisualPacket> CODEC = StreamCodec.of(
            (buffer, packet) -> {
                Vec3.STREAM_CODEC.encode(buffer, packet.impact);
                ByteBufCodecs.LONG.encode(buffer, packet.seed);
                buffer.writeByte(packet.profile.wireId());
            },
            buffer -> new SkyStrikeVisualPacket(
                    Vec3.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.LONG.decode(buffer),
                    SkyStrikeProfile.fromWireId(buffer.readUnsignedByte())
            )
    );
    private static boolean clientInitialized;

    private final Vec3 impact;
    private final long seed;
    private final SkyStrikeProfile profile;

    public SkyStrikeVisualPacket(Vec3 impact, long seed, SkyStrikeProfile profile) {
        this.impact = impact;
        this.seed = seed;
        this.profile = profile == null ? SkyStrikeProfile.LIGHTNING_STORM : profile;
    }

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static long broadcast(ServerLevel level, Vec3 impact, SkyStrikeProfile profile) {
        var seed = level.getRandom().nextLong();
        var packet = new SkyStrikeVisualPacket(impact, seed, profile);
        var rangeSquared = BROADCAST_RANGE * BROADCAST_RANGE;
        for (var observer : level.players()) {
            if (observer.distanceToSqr(impact) <= rangeSquared) {
                MisakaNetworkServer.send(observer, packet);
            }
        }
        return seed;
    }

    public Vec3 impact() {
        return impact;
    }

    public long seed() {
        return seed;
    }

    public SkyStrikeProfile profile() {
        return profile;
    }

    @Override
    public PacketType<ClientPacketListener, SkyStrikeVisualPacket> getPacketType() {
        return PacketTypes.SKY_STRIKE_VISUAL.get();
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handle(SkyStrikeVisualPacket packet) {
            if (packet.impact == null
                    || !Double.isFinite(packet.impact.x)
                    || !Double.isFinite(packet.impact.y)
                    || !Double.isFinite(packet.impact.z)) {
                return;
            }
            var minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            SkyStrikeVfxClient.spawn(packet.impact, packet.seed, packet.profile);
        }
    }
}
