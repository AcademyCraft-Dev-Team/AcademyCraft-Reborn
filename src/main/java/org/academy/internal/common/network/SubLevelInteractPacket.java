package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.sublevel.SubLevelInteractionService;
import org.academy.internal.common.sublevel.SubLevelServer;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Objects;
import java.util.UUID;

/** Phase 3/4 server-authoritative interaction request for a SubLevel plot cell. */
@PacketTarget(ThreadType.SERVER)
public final class SubLevelInteractPacket extends Packet<ServerGamePacketListenerImpl, SubLevelInteractPacket> {
    public enum Action { USE, BREAK, PLACE }

    public static final StreamCodec<ByteBuf, SubLevelInteractPacket> CODEC = new StreamCodec<>() {
        @Override public SubLevelInteractPacket decode(ByteBuf input) {
            var id = new SubLevelId(new UUID(input.readLong(), input.readLong()));
            var local = new BlockPos(input.readInt(), input.readInt(), input.readInt());
            int face = input.readUnsignedByte();
            var point = new Vec3(input.readDouble(), input.readDouble(), input.readDouble());
            int hand = input.readUnsignedByte();
            int action = input.readUnsignedByte();
            if (face >= Direction.values().length || hand > 1 || action >= Action.values().length || input.isReadable()) {
                throw new IllegalArgumentException("Invalid SubLevel interaction payload");
            }
            return new SubLevelInteractPacket(id, local, Direction.values()[face], point,
                    hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, Action.values()[action]);
        }

        @Override public void encode(ByteBuf output, SubLevelInteractPacket packet) {
            output.writeLong(packet.id.value().getMostSignificantBits());
            output.writeLong(packet.id.value().getLeastSignificantBits());
            output.writeInt(packet.local.getX()); output.writeInt(packet.local.getY()); output.writeInt(packet.local.getZ());
            output.writeByte(packet.face.ordinal());
            output.writeDouble(packet.point.x); output.writeDouble(packet.point.y); output.writeDouble(packet.point.z);
            output.writeByte(packet.hand == InteractionHand.MAIN_HAND ? 0 : 1);
            output.writeByte(packet.action.ordinal());
        }
    };

    private static boolean initialized;
    private final SubLevelId id;
    private final BlockPos local;
    private final Direction face;
    private final Vec3 point;
    private final InteractionHand hand;
    private final Action action;

    public SubLevelInteractPacket(SubLevelId id, BlockPos local, Direction face, Vec3 point,
                                  InteractionHand hand, Action action) {
        this.id = Objects.requireNonNull(id, "id");
        this.local = Objects.requireNonNull(local, "local").immutable();
        this.face = Objects.requireNonNull(face, "face");
        this.point = Objects.requireNonNull(point, "point");
        this.hand = Objects.requireNonNull(hand, "hand");
        this.action = Objects.requireNonNull(action, "action");
    }

    public SubLevelId id() { return id; }
    public BlockPos local() { return local; }
    public Direction face() { return face; }
    public Vec3 point() { return point; }
    public InteractionHand hand() { return hand; }
    public Action action() { return action; }

    public static void send(SubLevelInteractPacket packet) {
        org.misaka.MisakaNetworkClient.send(packet);
    }

    public static synchronized void initServer() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override public PacketType<ServerGamePacketListenerImpl, SubLevelInteractPacket> getPacketType() {
        return PacketTypes.SUBLEVEL_INTERACT.get();
    }

    public static final class Server {
        @SubscribePacket public static void handle(SubLevelInteractPacket packet) {
            ServerPlayer player = packet.getPacketListener().getPlayer();
            if (player == null || player.isSpectator()) return;
            var server = player.level().getServer();
            if (server == null || !server.isSameThread()) return;
            var runtime = SubLevelServer.runtime(player.level()).orElse(null);
            if (runtime == null) return;
            var subLevel = runtime.get(packet.id()).orElse(null);
            if (subLevel == null) return;
            var observation = observation(player, subLevel, packet);
            if (observation == null) return;
            var controller = org.academy.internal.common.sublevel.SubLevelEnhancementBootstrap.INTERACTIONS;
            if (packet.action() == Action.BREAK) {
                // Legacy compatibility: one-shot BREAK starts or renews a server-owned session
                // and never performs an instant plot destruction.
                var receipt = controller.continueLegacy(player, observation);
                SubLevelServer.publishMining(player, player.getUUID(), receipt);
                return;
            }
            var request = new org.academy.api.common.structure.StructureInteractionController.ActionRequest(
                    controller.nextRequestId(), observation, packet.hand());
            if (packet.action() == Action.PLACE) controller.place(player, request);
            else controller.use(player, request);
        }

        private static org.academy.api.common.structure.@org.jspecify.annotations.Nullable StructureHit observation(
                ServerPlayer player, org.academy.internal.common.sublevel.SubLevel subLevel,
                SubLevelInteractPacket packet) {
            var activation = org.academy.internal.common.sublevel.SubLevelActivationRegistry
                    .find(packet.id()).orElse(null);
            if (activation == null) return null;
            var reference = org.academy.internal.common.sublevel.SubLevelStructureRefs.of(subLevel,
                    player.level().dimension().identifier(), activation.generation());
            var localPoint = subLevel.pose().worldToLocalPoint(packet.point());
            return new org.academy.api.common.structure.StructureHit(
                    new org.academy.api.common.structure.SpaceBlockRef(reference, packet.local()),
                    activation.runtimeId(), subLevel.geometryRevision(), subLevel.pose(), packet.face(),
                    localPoint, packet.point(), false);
        }
    }
}
