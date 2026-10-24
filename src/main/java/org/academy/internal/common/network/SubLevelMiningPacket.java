package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.SpaceBlockRef;
import org.academy.api.common.structure.StructureHit;
import org.academy.internal.common.sublevel.SubLevelActivationRegistry;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Objects;
import java.util.UUID;

/** Bounded mining intent for one plot cell; progress and authority are server-owned. */
@PacketTarget(ThreadType.SERVER)
public final class SubLevelMiningPacket extends Packet<ServerGamePacketListenerImpl, SubLevelMiningPacket> {
    public enum Action { START, CONTINUE, ABORT }

    public static final StreamCodec<ByteBuf, SubLevelMiningPacket> CODEC = new StreamCodec<>() {
        @Override public SubLevelMiningPacket decode(ByteBuf input) {
            int action = input.readUnsignedByte();
            long requestId = input.readLong();
            var id = new SubLevelId(new UUID(input.readLong(), input.readLong()));
            var local = new BlockPos(input.readInt(), input.readInt(), input.readInt());
            int face = input.readUnsignedByte();
            var point = new Vec3(input.readDouble(), input.readDouble(), input.readDouble());
            int hand = input.readUnsignedByte();
            boolean inside = input.readBoolean();
            if (action >= Action.values().length || face >= Direction.values().length || hand > 1
                    || requestId <= 0 || !Double.isFinite(point.x) || !Double.isFinite(point.y)
                    || !Double.isFinite(point.z) || input.isReadable()) {
                throw new IllegalArgumentException("Invalid SubLevel mining payload");
            }
            return new SubLevelMiningPacket(Action.values()[action], requestId, id, local,
                    Direction.values()[face], point, hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
                    inside);
        }

        @Override public void encode(ByteBuf output, SubLevelMiningPacket packet) {
            output.writeByte(packet.action.ordinal());
            output.writeLong(packet.requestId);
            output.writeLong(packet.id.value().getMostSignificantBits());
            output.writeLong(packet.id.value().getLeastSignificantBits());
            output.writeInt(packet.local.getX());
            output.writeInt(packet.local.getY());
            output.writeInt(packet.local.getZ());
            output.writeByte(packet.face.ordinal());
            output.writeDouble(packet.point.x);
            output.writeDouble(packet.point.y);
            output.writeDouble(packet.point.z);
            output.writeByte(packet.hand == InteractionHand.MAIN_HAND ? 0 : 1);
            output.writeBoolean(packet.inside);
        }
    };

    private static boolean initialized;
    private final Action action;
    private final long requestId;
    private final SubLevelId id;
    private final BlockPos local;
    private final Direction face;
    private final Vec3 point;
    private final InteractionHand hand;
    private final boolean inside;

    public SubLevelMiningPacket(Action action, long requestId, SubLevelId id, BlockPos local, Direction face,
                                Vec3 point, InteractionHand hand, boolean inside) {
        this.action = Objects.requireNonNull(action, "action");
        this.id = Objects.requireNonNull(id, "id");
        this.local = Objects.requireNonNull(local, "local").immutable();
        this.face = Objects.requireNonNull(face, "face");
        this.point = Objects.requireNonNull(point, "point");
        this.hand = Objects.requireNonNull(hand, "hand");
        if (requestId <= 0) throw new IllegalArgumentException("requestId must be positive");
        this.requestId = requestId;
        this.inside = inside;
    }

    public Action action() { return action; }
    public long requestId() { return requestId; }
    public SubLevelId id() { return id; }
    public BlockPos local() { return local; }
    public Direction face() { return face; }
    public Vec3 point() { return point; }
    public InteractionHand hand() { return hand; }
    public boolean inside() { return inside; }

    public static void send(SubLevelMiningPacket packet) {
        MisakaNetworkClient.send(packet);
    }

    public static synchronized void initServer() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override public PacketType<ServerGamePacketListenerImpl, SubLevelMiningPacket> getPacketType() {
        return PacketTypes.SUBLEVEL_MINING.get();
    }

    public static final class Server {
        @SubscribePacket public static void handle(SubLevelMiningPacket packet) {
            ServerPlayer player = packet.getPacketListener().getPlayer();
            if (player == null || player.isSpectator()) return;
            var server = player.level().getServer();
            if (server == null || !server.isSameThread()) return;
            var runtime = SubLevelServer.runtime(player.level()).orElse(null);
            if (runtime == null) return;
            var subLevel = runtime.get(packet.id()).orElse(null);
            if (subLevel == null) return;
            StructureHit observation = null;
            if (packet.action() != Action.ABORT) {
                var activation = SubLevelActivationRegistry.find(packet.id()).orElse(null);
                if (activation == null) return;
                var reference = SubLevelStructureRefs.of(subLevel, player.level().dimension().identifier(),
                        activation.generation());
                var localPoint = subLevel.pose().worldToLocalPoint(packet.point());
                observation = new StructureHit(new SpaceBlockRef(reference, packet.local()), activation.runtimeId(),
                        subLevel.geometryRevision(), subLevel.pose(), packet.face(), localPoint, packet.point(),
                        packet.inside());
            }
            var receipt = org.academy.internal.common.sublevel.SubLevelEnhancementBootstrap.INTERACTIONS.mine(player,
                    new org.academy.api.common.structure.StructureInteractionController.MiningRequest(
                            packet.requestId(), packet.action() == Action.ABORT
                            ? org.academy.api.common.structure.StructureInteractionController.MiningAction.ABORT
                            : packet.action() == Action.CONTINUE
                            ? org.academy.api.common.structure.StructureInteractionController.MiningAction.CONTINUE
                            : org.academy.api.common.structure.StructureInteractionController.MiningAction.START,
                            observation));
            SubLevelServer.publishMining(player, player.getUUID(), receipt);
        }
    }
}
