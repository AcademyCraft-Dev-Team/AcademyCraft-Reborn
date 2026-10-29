package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.sublevel.SubLevelId;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One bounded pose message per viewer and tick, retaining full precision and update frequency. */
@PacketTarget(ThreadType.CLIENT)
public final class SubLevelPoseBatchPacket extends Packet<ClientPacketListener, SubLevelPoseBatchPacket> {
    public static final int MAX_ENTRIES = 256;
    public record Entry(SubLevelId id, StructurePose pose, boolean teleport) {
        public Entry { Objects.requireNonNull(id, "id"); Objects.requireNonNull(pose, "pose"); }
        public Entry(SubLevelId id, StructurePose pose) { this(id, pose, false); }
    }

    public static final StreamCodec<ByteBuf, SubLevelPoseBatchPacket> CODEC = new StreamCodec<>() {
        @Override public SubLevelPoseBatchPacket decode(ByteBuf input) {
            var dimension = Identifier.STREAM_CODEC.decode(input);
            int count = input.readUnsignedShort();
            if (count < 1 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid pose batch size");
            var entries = new ArrayList<Entry>(count);
            for (int i = 0; i < count; i++) {
                var id = new SubLevelId(new UUID(input.readLong(), input.readLong()));
                var pose = new StructurePose(new Vec3(input.readDouble(), input.readDouble(), input.readDouble()),
                        input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble());
                entries.add(new Entry(id, pose, input.readBoolean()));
            }
            if (input.isReadable()) throw new IllegalArgumentException("Unexpected pose batch payload");
            return new SubLevelPoseBatchPacket(dimension, entries);
        }

        @Override public void encode(ByteBuf output, SubLevelPoseBatchPacket packet) {
            Identifier.STREAM_CODEC.encode(output, packet.dimension);
            output.writeShort(packet.entries.size());
            for (var entry : packet.entries) {
                output.writeLong(entry.id().value().getMostSignificantBits());
                output.writeLong(entry.id().value().getLeastSignificantBits());
                var pose = entry.pose();
                output.writeDouble(pose.worldOrigin().x); output.writeDouble(pose.worldOrigin().y);
                output.writeDouble(pose.worldOrigin().z); output.writeDouble(pose.rotationX());
                output.writeDouble(pose.rotationY()); output.writeDouble(pose.rotationZ()); output.writeDouble(pose.rotationW());
                output.writeBoolean(entry.teleport());
            }
        }
    };

    private static boolean initialized;
    private final Identifier dimension;
    private final List<Entry> entries;

    public SubLevelPoseBatchPacket(Identifier dimension, List<Entry> entries) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.entries = List.copyOf(entries);
        if (entries.isEmpty() || entries.size() > MAX_ENTRIES
                || new HashSet<>(entries.stream().map(Entry::id).toList()).size() != entries.size()) {
            throw new IllegalArgumentException("Invalid or duplicate pose batch entries");
        }
    }

    public Identifier dimension() { return dimension; }
    public List<Entry> entries() { return entries; }

    public static void send(ServerPlayer recipient, List<Entry> entries) {
        for (int start = 0; start < entries.size(); start += MAX_ENTRIES) {
            MisakaNetworkServer.send(recipient, new SubLevelPoseBatchPacket(recipient.level().dimension().identifier(),
                    entries.subList(start, Math.min(entries.size(), start + MAX_ENTRIES))));
        }
    }

    public static synchronized void initClient() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    @Override public PacketType<ClientPacketListener, SubLevelPoseBatchPacket> getPacketType() {
        return PacketTypes.SUBLEVEL_POSE_BATCH.get();
    }

    public static final class Client {
        @SubscribePacket public static void handle(SubLevelPoseBatchPacket packet) {
            net.minecraft.client.Minecraft.getInstance().execute(
                    () -> org.academy.internal.client.sublevel.SubLevelClientState.acceptPoses(packet));
        }
    }
}
