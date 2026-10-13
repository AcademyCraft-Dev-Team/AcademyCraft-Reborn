package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureHit;
import org.academy.internal.common.structure.space.NativeStructureInteraction;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Objects;
import java.util.WeakHashMap;

/** A bounded observation only. The server derives the actionable ray from its actual player. */
@PacketTarget(ThreadType.SERVER)
public final class NativeStructureUsePacket extends Packet<ServerGamePacketListenerImpl, NativeStructureUsePacket> {
    public static final StreamCodec<ByteBuf, NativeStructureUsePacket> CODEC = new StreamCodec<>() {
        @Override public NativeStructureUsePacket decode(ByteBuf input) {
            var buffer = new FriendlyByteBuf(input);
            long requestId = buffer.readLong();
            var hit = NativeStructureHitCodec.CODEC.decode(buffer);
            int hand = buffer.readUnsignedByte();
            if (hand > 1) throw new IllegalArgumentException("Invalid structure interaction hand");
            if (buffer.isReadable()) throw new IllegalArgumentException("Unexpected structure use payload");
            return new NativeStructureUsePacket(requestId, hit,
                    hand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        }

        @Override public void encode(ByteBuf output, NativeStructureUsePacket packet) {
            var buffer = new FriendlyByteBuf(output);
            buffer.writeLong(packet.requestId);
            NativeStructureHitCodec.CODEC.encode(buffer, packet.hit);
            buffer.writeByte(packet.hand == InteractionHand.MAIN_HAND ? 0 : 1);
        }
    };
    private static boolean initialized;
    private static final WeakHashMap<ServerGamePacketListenerImpl, NativeStructureRequestSequence> REQUESTS = new WeakHashMap<>();
    private final long requestId;
    private final StructureHit hit;
    private final InteractionHand hand;

    /** Legacy trusted tools/value tests only; real clients must allocate a connection-scoped ID. */
    @Deprecated
    public NativeStructureUsePacket(StructureHit hit) { this(1, hit, InteractionHand.MAIN_HAND); }
    /** Legacy trusted tools/replay probes only; this deliberately does not allocate a new request. */
    @Deprecated
    public NativeStructureUsePacket(StructureHit hit, InteractionHand hand) {
        this(1, hit, hand);
    }
    public NativeStructureUsePacket(long requestId, StructureHit hit, InteractionHand hand) {
        if (requestId <= 0) throw new IllegalArgumentException("Invalid structure use request identity");
        this.requestId = requestId;
        this.hit = Objects.requireNonNull(hit, "hit");
        this.hand = Objects.requireNonNull(hand, "hand");
    }
    public long requestId() { return requestId; }
    public StructureHit hit() { return hit; }
    public InteractionHand hand() { return hand; }

    public static synchronized void initServer() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    @Override public PacketType<ServerGamePacketListenerImpl, NativeStructureUsePacket> getPacketType() {
        return PacketTypes.NATIVE_STRUCTURE_USE.get();
    }

    public static final class Server {
        @SubscribePacket public static void handle(NativeStructureUsePacket packet) {
            var listener = packet.getPacketListener();
            var player = listener.getPlayer();
            if (!player.level().getServer().isSameThread()) throw new IllegalStateException("Structure use requires server thread");
            boolean accepted;
            synchronized (REQUESTS) {
                accepted = REQUESTS.computeIfAbsent(listener, ignored -> new NativeStructureRequestSequence()).consume(packet.requestId);
            }
            var result = accepted ? NativeStructureUseResultPacket.Status.from(
                    NativeStructureInteraction.useBlock(player, packet.hit, packet.hand))
                    : NativeStructureUseResultPacket.Status.REPLAYED;
            NativeStructureUseResultPacket.send(player, packet.requestId, result);
            if (Boolean.getBoolean("academy.nativeStructurePlacementSmoke")) {
                AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PLACEMENT_PACKET_RESULT result={} hand={} selected={} held={} hit={} canUse={}",
                        result, packet.hand, player.getInventory().getSelectedSlot(), player.getItemInHand(packet.hand),
                        packet.hit, NativeStructureInteraction.canUseMenu(player));
            }
        }
    }
}
