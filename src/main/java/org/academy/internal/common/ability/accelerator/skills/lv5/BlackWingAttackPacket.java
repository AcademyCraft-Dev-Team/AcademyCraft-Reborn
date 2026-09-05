package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.VortexAttackPattern;
import org.academy.internal.client.render.vfx.WingVfx;
import org.academy.internal.common.network.PacketTypes;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/** Server-selected pattern and fixed world target, shared by the attacker and observers. */
@PacketTarget(ThreadType.CLIENT)
public final class BlackWingAttackPacket extends Packet<ClientPacketListener, BlackWingAttackPacket> {
    public static final StreamCodec<ByteBuf, BlackWingAttackPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                ByteBufCodecs.VAR_INT.encode(buf, packet.entityId);
                ByteBufCodecs.VAR_INT.encode(buf, packet.pattern.id());
                buf.writeLong(packet.startTick);
                buf.writeDouble(packet.target.x);
                buf.writeDouble(packet.target.y);
                buf.writeDouble(packet.target.z);
            },
            buf -> new BlackWingAttackPacket(ByteBufCodecs.VAR_INT.decode(buf),
                    VortexAttackPattern.byId(ByteBufCodecs.VAR_INT.decode(buf)), buf.readLong(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
    private static boolean clientInitialized;
    private final int entityId;
    private final VortexAttackPattern pattern;
    private final long startTick;
    private final Vec3 target;

    public BlackWingAttackPacket(int entityId, VortexAttackPattern pattern, long startTick, Vec3 target) {
        this.entityId = entityId;
        this.pattern = pattern;
        this.startTick = startTick;
        this.target = target;
    }

    public int entityId() { return entityId; }
    public VortexAttackPattern pattern() { return pattern; }
    public long startTick() { return startTick; }
    public Vec3 target() { return target; }

    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    @Override
    public PacketType<ClientPacketListener, BlackWingAttackPacket> getPacketType() {
        return PacketTypes.BLACK_WING_ATTACK.get();
    }

    public static final class Client {
        private Client() { }

        @SubscribePacket
        public static void handle(BlackWingAttackPacket packet) {
            WingVfx.enqueueBlackAttack(packet.entityId, packet.pattern, packet.startTick, packet.target);
        }
    }
}
