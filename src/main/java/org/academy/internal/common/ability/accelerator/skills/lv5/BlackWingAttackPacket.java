package org.academy.internal.common.ability.accelerator.skills.lv5;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
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

/** Server-selected pattern and fixed world targets, shared by the attacker and observers. */
@PacketTarget(ThreadType.CLIENT)
public final class BlackWingAttackPacket extends Packet<ClientPacketListener, BlackWingAttackPacket> {
    public static final StreamCodec<ByteBuf, BlackWingAttackPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                ByteBufCodecs.VAR_INT.encode(buf, packet.entityId);
                ByteBufCodecs.VAR_INT.encode(buf, packet.pattern.id());
                buf.writeLong(packet.startTick);
                buf.writeLong(packet.epoch);
                buf.writeLong(packet.sequence);
                buf.writeFloat(packet.initialProgress);
                for (var target : packet.targets) {
                    buf.writeDouble(target.x);
                    buf.writeDouble(target.y);
                    buf.writeDouble(target.z);
                }
            },
            buf -> {
                int entityId = ByteBufCodecs.VAR_INT.decode(buf);
                var pattern = VortexAttackPattern.byId(ByteBufCodecs.VAR_INT.decode(buf));
                long startTick = buf.readLong();
                long epoch = buf.readLong();
                long sequence = buf.readLong();
                float initialProgress = buf.readFloat();
                var targets = new ArrayList<Vec3>();
                int count = pattern == VortexAttackPattern.FOURFOLD_SLAM ? 4 : 1;
                for (int i = 0; i < count; i++) {
                    targets.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
                }
                return new BlackWingAttackPacket(entityId, pattern, startTick, targets, epoch, sequence, initialProgress);
            });
    private static boolean clientInitialized;
    private final int entityId;
    private final VortexAttackPattern pattern;
    private final long startTick;
    private final List<Vec3> targets;
    private final long epoch, sequence;
    private final float initialProgress;

    public BlackWingAttackPacket(int entityId, VortexAttackPattern pattern, long startTick, List<Vec3> targets) {
        this(entityId, pattern, startTick, targets, 0, startTick, 0f);
    }

    public BlackWingAttackPacket(int entityId, VortexAttackPattern pattern, long startTick, List<Vec3> targets,
                                 long epoch, long sequence, float initialProgress) {
        this.entityId = entityId;
        this.epoch = epoch;
        this.sequence = sequence;
        this.initialProgress = initialProgress;
        if (!Float.isFinite(initialProgress) || initialProgress < 0 || initialProgress >= 1)
            throw new IllegalArgumentException("Invalid vortex snapshot progress");
        this.pattern = pattern;
        this.startTick = startTick;
        int count = pattern == VortexAttackPattern.FOURFOLD_SLAM ? 4 : 1;
        if (targets.size() != count) throw new IllegalArgumentException("Unexpected vortex landing count");
        this.targets = List.copyOf(targets);
        for (var target : targets) {
            if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z))
                throw new IllegalArgumentException("Invalid vortex target");
        }
    }

    public long epoch() { return epoch; }
    public long sequence() { return sequence; }
    public float initialProgress() { return initialProgress; }

    public int entityId() { return entityId; }
    public VortexAttackPattern pattern() { return pattern; }
    public long startTick() { return startTick; }
    public List<Vec3> targets() { return targets; }

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
            WingVfx.enqueueBlackAttack(packet.entityId, packet.pattern, packet.targets,
                    packet.epoch, packet.sequence, packet.initialProgress);
        }
    }
}
