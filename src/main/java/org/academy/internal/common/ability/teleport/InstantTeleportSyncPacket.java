package org.academy.internal.common.ability.teleport;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/**
 * Client snap used because vanilla 26.2 interpolates short absolute entity-position updates.
 */
@PacketTarget(ThreadType.CLIENT)
public final class InstantTeleportSyncPacket
        extends Packet<ClientPacketListener, InstantTeleportSyncPacket> {
    public static final StreamCodec<ByteBuf, InstantTeleportSyncPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            InstantTeleportSyncPacket::entityId,
            Vec3.STREAM_CODEC,
            InstantTeleportSyncPacket::position,
            ByteBufCodecs.FLOAT,
            InstantTeleportSyncPacket::yRot,
            ByteBufCodecs.FLOAT,
            InstantTeleportSyncPacket::xRot,
            InstantTeleportSyncPacket::new
    );
    private static boolean clientInitialized;

    private final int entityId;
    private final Vec3 position;
    private final float yRot;
    private final float xRot;

    public InstantTeleportSyncPacket(int entityId, Vec3 position, float yRot, float xRot) {
        this.entityId = entityId;
        this.position = position;
        this.yRot = yRot;
        this.xRot = xRot;
    }

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public int entityId() {
        return entityId;
    }

    public Vec3 position() {
        return position;
    }

    public float yRot() {
        return yRot;
    }

    public float xRot() {
        return xRot;
    }

    @Override
    public PacketType<ClientPacketListener, InstantTeleportSyncPacket> getPacketType() {
        return PacketTypes.INSTANT_TELEPORT_SYNC.get();
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void handle(InstantTeleportSyncPacket packet) {
            var minecraft = Minecraft.getInstance();
            var level = minecraft.level;
            if (level == null || packet.position == null || !finite(packet.position)) return;
            var entity = level.getEntity(packet.entityId);
            if (entity == null) return;
            var localPlayer = entity == minecraft.player;
            var yRot = resolveRotation(localPlayer, entity.getYRot(), packet.yRot);
            var xRot = resolveRotation(localPlayer, entity.getXRot(), packet.xRot);
            entity.getPositionCodec().setBase(packet.position);
            entity.snapTo(packet.position, yRot, xRot);
            entity.setOldPosAndRot();
        }

        private static boolean finite(Vec3 value) {
            return Double.isFinite(value.x)
                    && Double.isFinite(value.y)
                    && Double.isFinite(value.z);
        }
    }

    static float resolveRotation(
            boolean localPlayer,
            float clientRotation,
            float synchronizedRotation
    ) {
        return localPlayer ? clientRotation : synchronizedRotation;
    }
}
