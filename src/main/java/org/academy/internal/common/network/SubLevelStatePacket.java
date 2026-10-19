package org.academy.internal.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.client.sublevel.SubLevelClientState;
import org.academy.internal.common.sublevel.PlotPos;
import org.academy.internal.common.sublevel.SubLevelId;
import org.academy.internal.common.structure.space.NativeStructureView;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Phase 4 prototype snapshot: one SubLevel plot pose and its exact collision boxes. */
@PacketTarget(ThreadType.CLIENT)
public final class SubLevelStatePacket extends Packet<ClientPacketListener, SubLevelStatePacket> {
    private static final int MAX_BOXES = 16_384;
    public static final StreamCodec<ByteBuf, SubLevelStatePacket> CODEC = new StreamCodec<>() {
        @Override public SubLevelStatePacket decode(ByteBuf input) {
            var id = new SubLevelId(new UUID(input.readLong(), input.readLong()));
            var plot = new PlotPos(input.readInt(), input.readInt());
            boolean removed = input.readBoolean();
            boolean fullView = input.readBoolean();
            var pose = new StructurePose(new Vec3(input.readDouble(), input.readDouble(), input.readDouble()),
                    input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble());
            int count = input.readUnsignedShort();
            if (count > MAX_BOXES) throw new IllegalArgumentException("Too many SubLevel collision boxes");
            var boxes = new ArrayList<AABB>(count);
            for (int index = 0; index < count; index++) {
                boxes.add(new AABB(input.readDouble(), input.readDouble(), input.readDouble(),
                        input.readDouble(), input.readDouble(), input.readDouble()));
            }
            int cellCount = input.readUnsignedShort();
            if (cellCount > NativeStructureView.MAX_CELLS) {
                throw new IllegalArgumentException("Too many SubLevel view cells");
            }
            boolean solid = input.readBoolean();
            var cells = new ArrayList<NativeStructureView.Cell>(cellCount);
            for (int index = 0; index < cellCount; index++) {
                var position = new BlockPos(input.readShort(), input.readShort(), input.readShort());
                var state = Block.BLOCK_STATE_REGISTRY.byId(input.readInt());
                if (state == null) throw new IllegalArgumentException("Unknown SubLevel block state");
                cells.add(new NativeStructureView.Cell(position, state));
            }
            if (input.isReadable()) throw new IllegalArgumentException("Unexpected SubLevel state payload");
            var view = cells.isEmpty() ? NativeStructureView.EMPTY : new NativeStructureView(cells, solid);
            return new SubLevelStatePacket(id, plot, pose, boxes, view, removed, fullView);
        }

        @Override public void encode(ByteBuf output, SubLevelStatePacket packet) {
            output.writeLong(packet.id.value().getMostSignificantBits());
            output.writeLong(packet.id.value().getLeastSignificantBits());
            output.writeInt(packet.plot.x());
            output.writeInt(packet.plot.z());
            output.writeBoolean(packet.removed);
            output.writeBoolean(packet.fullView);
            output.writeDouble(packet.pose.worldOrigin().x);
            output.writeDouble(packet.pose.worldOrigin().y);
            output.writeDouble(packet.pose.worldOrigin().z);
            output.writeDouble(packet.pose.rotationX());
            output.writeDouble(packet.pose.rotationY());
            output.writeDouble(packet.pose.rotationZ());
            output.writeDouble(packet.pose.rotationW());
            output.writeShort(packet.boxes.size());
            for (var box : packet.boxes) {
                output.writeDouble(box.minX); output.writeDouble(box.minY); output.writeDouble(box.minZ);
                output.writeDouble(box.maxX); output.writeDouble(box.maxY); output.writeDouble(box.maxZ);
            }
            output.writeShort(packet.view.cells().size());
            output.writeBoolean(packet.view.solid());
            for (var cell : packet.view.cells()) {
                output.writeShort(cell.position().getX());
                output.writeShort(cell.position().getY());
                output.writeShort(cell.position().getZ());
                output.writeInt(Block.getId(cell.state()));
            }
        }
    };

    private static boolean initialized;
    private final SubLevelId id;
    private final PlotPos plot;
    private final StructurePose pose;
    private final List<AABB> boxes;
    private final NativeStructureView view;
    private final boolean removed;
    private final boolean fullView;

    public SubLevelStatePacket(SubLevelId id, PlotPos plot, StructurePose pose, List<AABB> boxes,
                               NativeStructureView view, boolean removed) {
        this(id, plot, pose, boxes, view, removed, true);
    }

    public SubLevelStatePacket(SubLevelId id, PlotPos plot, StructurePose pose, List<AABB> boxes,
                               NativeStructureView view, boolean removed, boolean fullView) {
        this.id = Objects.requireNonNull(id, "id");
        this.plot = Objects.requireNonNull(plot, "plot");
        this.pose = Objects.requireNonNull(pose, "pose");
        this.boxes = List.copyOf(boxes);
        this.view = Objects.requireNonNull(view, "view");
        if (this.boxes.size() > MAX_BOXES) throw new IllegalArgumentException("Too many collision boxes");
        this.removed = removed;
        this.fullView = fullView;
    }

    public static void send(ServerPlayer recipient, SubLevelId id, PlotPos plot, StructurePose pose,
                            List<AABB> boxes, NativeStructureView view, boolean removed) {
        send(recipient, id, plot, pose, boxes, view, removed, true);
    }

    public static void send(ServerPlayer recipient, SubLevelId id, PlotPos plot, StructurePose pose,
                            List<AABB> boxes, NativeStructureView view, boolean removed, boolean fullView) {
        MisakaNetworkServer.send(recipient,
                new SubLevelStatePacket(id, plot, pose, boxes, view, removed, fullView));
    }

    public SubLevelId id() { return id; }
    public PlotPos plot() { return plot; }
    public StructurePose pose() { return pose; }
    public List<AABB> boxes() { return boxes; }
    public NativeStructureView view() { return view; }
    public boolean removed() { return removed; }
    public boolean fullView() { return fullView; }

    public static synchronized void initClient() {
        if (initialized) return;
        initialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    @Override public PacketType<ClientPacketListener, SubLevelStatePacket> getPacketType() {
        return PacketTypes.SUBLEVEL_STATE.get();
    }

    public static final class Client {
        @SubscribePacket public static void handle(SubLevelStatePacket packet) {
            org.academy.AcademyCraft.LOGGER.debug("SubLevelStatePacket received id={} cells={}",
                    packet.id().value(), packet.view().cells().size());
            net.minecraft.client.Minecraft.getInstance().execute(() -> SubLevelClientState.accept(packet));
        }
    }
}
