package org.academy.internal.common.ability.mentalout.precision;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.ability.mentalout.PrecisionOperationClient;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.skills.PrecisionOperation;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.SkillData;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PrecisionOperationManager {
    private static final Map<UUID, CachedPrograms> COMPILED = new HashMap<>();
    private static boolean clientInitialized;
    private static boolean serverInitialized;

    private PrecisionOperationManager() {
    }

    public static synchronized void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        MisakaNetworkClient.NETWORK_MANAGER.register(Client.class);
    }

    public static synchronized void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static PrecisionOperation.Data getOrCreateData(ServerPlayer player) {
        var skill = Skills.PRECISION_OPERATION.get();
        var system = AbilitySystemServer.getSystem(player);
        var playerData = system.getPlayerData(player.getUUID());
        if (playerData == null) return new PrecisionOperation.Data();
        var map = playerData.getSkillDataMap();
        var raw = map.get(skill.getKeyString());
        if (raw instanceof PrecisionOperation.Data data) {
            var schemaVersion = data.schemaVersion();
            var normalized = PrecisionOperation.normalizeData(data);
            if (schemaVersion != normalized.schemaVersion()) playerData.markDirty();
            return normalized;
        }
        var data = (PrecisionOperation.Data) skill.createData(player);
        if (raw != null) mergeProgress(data, raw);
        map.put(skill.getKeyString(), data);
        playerData.markDirty();
        return data;
    }

    public static void releaseController(UUID controllerId) {
        COMPILED.remove(controllerId);
        PrecisionOperationRuntime.releaseController(controllerId);
    }

    public static void releaseController(ServerPlayer player) {
        if (player == null) return;
        COMPILED.remove(player.getUUID());
        PrecisionOperationRuntime.releaseController(player);
    }

    public static void clear(net.minecraft.server.MinecraftServer server) {
        COMPILED.clear();
        PrecisionOperationRuntime.clear(server);
    }

    private static void mergeProgress(PrecisionOperation.Data target, SkillData source) {
        target.setLevel(source.getLevel());
        target.setMaxExp(source.getMaxExp());
        target.setExp(source.getExp());
        target.setEnabled(source.isEnabled());
    }

    private static void sync(ServerPlayer player, PrecisionOperation.Data data) {
        var encoded = new byte[4][];
        for (var slot = 0; slot < 4; slot++) encoded[slot] = PrecisionGraphCodec.encode(data.slot(slot));
        MisakaNetworkServer.send(player, new SyncPacket(data.revision(), encoded));
    }

    private static CompiledPrecisionProgram compiled(ServerPlayer player, int slot) {
        var data = getOrCreateData(player);
        var cache = COMPILED.computeIfAbsent(player.getUUID(), _ -> new CachedPrograms());
        if (cache.revision != data.revision()) {
            cache.revision = data.revision();
            java.util.Arrays.fill(cache.programs, null);
        }
        var program = cache.programs[slot];
        if (program != null) return program;
        var result = CompiledPrecisionProgram.compile(data.slot(slot));
        if (!result.valid()) return null;
        cache.programs[slot] = result.program();
        return result.program();
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void request(RequestPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.PRECISION_OPERATION.get().isEnabled(player)) return;
            sync(player, getOrCreateData(player));
        }

        @SubscribePacket
        public static void save(SavePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!Skills.PRECISION_OPERATION.get().isEnabled(player)) {
                result(player, packet.slot, false, 0L, PrecisionGraph.Diagnostic.SKILL_UNAVAILABLE);
                return;
            }
            var data = getOrCreateData(player);
            if (packet.slot < 0 || packet.slot >= 4 || packet.expectedRevision != data.revision()) {
                result(player, packet.slot, false, data.revision(), PrecisionGraph.Diagnostic.REVISION_CONFLICT);
                sync(player, data);
                return;
            }
            var decoded = PrecisionGraphCodec.decode(packet.graph);
            if (!decoded.valid()) {
                result(player, packet.slot, false, data.revision(), decoded.diagnostic());
                return;
            }
            var compiled = CompiledPrecisionProgram.compile(decoded.graph());
            if (!decoded.graph().nodes().isEmpty() && !compiled.valid()) {
                result(player, packet.slot, false, data.revision(), compiled.diagnostic());
                return;
            }
            data.replaceSlot(packet.slot, decoded.graph());
            var playerData = AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID());
            if (playerData != null) playerData.markDirty();
            COMPILED.remove(player.getUUID());
            result(player, packet.slot, true, data.revision(), PrecisionGraph.Diagnostic.OK);
            sync(player, data);
        }

        @SubscribePacket
        public static void execute(ExecutePacket packet) {
            if (!MentaloutRequestGuard.acceptSkillUse(
                    packet.getPacketListener(),
                    MentaloutRequestGuard.SkillUse.PRECISION_OPERATION,
                    packet.sequence
            )) return;
            var player = packet.getPacketListener().getPlayer();
            if (packet.slot < 0 || packet.slot >= 4) return;
            var program = compiled(player, packet.slot);
            if (program == null) {
                result(
                        player,
                        packet.slot,
                        false,
                        getOrCreateData(player).revision(),
                        PrecisionGraph.Diagnostic.EMPTY_PROGRAM
                );
                return;
            }
            var execution = PrecisionOperationRuntime.toggle(player, packet.slot, program);
            result(
                    player,
                    packet.slot,
                    execution.changed(),
                    getOrCreateData(player).revision(),
                    execution.diagnostic()
            );
        }

        private static void result(
                ServerPlayer player,
                int slot,
                boolean accepted,
                long revision,
                PrecisionGraph.Diagnostic diagnostic
        ) {
            MisakaNetworkServer.send(player, new ResultPacket(
                    Math.clamp(slot, 0, 3),
                    accepted,
                    revision,
                    diagnostic
            ));
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void sync(SyncPacket packet) {
            PrecisionOperationClient.handleSync(packet.revision, packet.graphs);
        }

        @SubscribePacket
        public static void result(ResultPacket packet) {
            PrecisionOperationClient.handleResult(
                    packet.slot,
                    packet.accepted,
                    packet.revision,
                    packet.diagnostic
            );
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class RequestPacket extends Packet<ServerGamePacketListenerImpl, RequestPacket> {
        public static final RequestPacket INSTANCE = new RequestPacket();
        public static final StreamCodec<ByteBuf, RequestPacket> CODEC = StreamCodec.unit(INSTANCE);

        private RequestPacket() {
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, RequestPacket> getPacketType() {
            return PacketTypes.PRECISION_OPERATION_REQUEST.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SavePacket extends Packet<ServerGamePacketListenerImpl, SavePacket> {
        public static final StreamCodec<ByteBuf, SavePacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.slot);
                    buf.writeLong(packet.expectedRevision);
                    writeBytes(buf, packet.graph);
                },
                buf -> new SavePacket(
                        ByteBufCodecs.VAR_INT.decode(buf),
                        buf.readLong(),
                        readBytes(buf)
                )
        );
        private final int slot;
        private final long expectedRevision;
        private final byte[] graph;

        public SavePacket(int slot, long expectedRevision, byte[] graph) {
            this.slot = slot;
            this.expectedRevision = expectedRevision;
            this.graph = graph == null ? new byte[0] : graph.clone();
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, SavePacket> getPacketType() {
            return PacketTypes.PRECISION_OPERATION_SAVE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ExecutePacket extends Packet<ServerGamePacketListenerImpl, ExecutePacket> {
        public static final StreamCodec<ByteBuf, ExecutePacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.slot);
                    buf.writeLong(packet.sequence);
                },
                buf -> new ExecutePacket(ByteBufCodecs.VAR_INT.decode(buf), buf.readLong())
        );
        private final int slot;
        private final long sequence;

        public ExecutePacket(int slot, long sequence) {
            this.slot = slot;
            this.sequence = sequence;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ExecutePacket> getPacketType() {
            return PacketTypes.PRECISION_OPERATION_EXECUTE.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SyncPacket extends Packet<ClientPacketListener, SyncPacket> {
        public static final StreamCodec<ByteBuf, SyncPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeLong(packet.revision);
                    for (var slot = 0; slot < 4; slot++) writeBytes(buf, packet.graphs[slot]);
                },
                buf -> new SyncPacket(
                        buf.readLong(),
                        new byte[][]{readBytes(buf), readBytes(buf), readBytes(buf), readBytes(buf)}
                )
        );
        private final long revision;
        private final byte[][] graphs;

        public SyncPacket(long revision, byte[][] graphs) {
            this.revision = revision;
            this.graphs = new byte[4][];
            for (var slot = 0; slot < 4; slot++) {
                this.graphs[slot] = graphs != null && slot < graphs.length && graphs[slot] != null
                        ? graphs[slot].clone()
                        : new byte[0];
            }
        }

        @Override
        public PacketType<ClientPacketListener, SyncPacket> getPacketType() {
            return PacketTypes.PRECISION_OPERATION_SYNC.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ResultPacket extends Packet<ClientPacketListener, ResultPacket> {
        public static final StreamCodec<ByteBuf, ResultPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, packet.slot);
                    buf.writeBoolean(packet.accepted);
                    buf.writeLong(packet.revision);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.diagnostic.ordinal());
                },
                buf -> new ResultPacket(
                        ByteBufCodecs.VAR_INT.decode(buf),
                        buf.readBoolean(),
                        buf.readLong(),
                        diagnostic(ByteBufCodecs.VAR_INT.decode(buf))
                )
        );
        private final int slot;
        private final boolean accepted;
        private final long revision;
        private final PrecisionGraph.Diagnostic diagnostic;

        public ResultPacket(
                int slot,
                boolean accepted,
                long revision,
                PrecisionGraph.Diagnostic diagnostic
        ) {
            this.slot = slot;
            this.accepted = accepted;
            this.revision = revision;
            this.diagnostic = diagnostic == null ? PrecisionGraph.Diagnostic.MALFORMED : diagnostic;
        }

        @Override
        public PacketType<ClientPacketListener, ResultPacket> getPacketType() {
            return PacketTypes.PRECISION_OPERATION_RESULT.get();
        }
    }

    private static void writeBytes(ByteBuf buf, byte[] bytes) {
        if (bytes == null || bytes.length > PrecisionGraph.MAX_ENCODED_BYTES) {
            throw new EncoderException("Precision program exceeds 16 KiB");
        }
        ByteBufCodecs.VAR_INT.encode(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    private static byte[] readBytes(ByteBuf buf) {
        var length = ByteBufCodecs.VAR_INT.decode(buf);
        if (length < 0 || length > PrecisionGraph.MAX_ENCODED_BYTES || length > buf.readableBytes()) {
            throw new DecoderException("Invalid precision program length");
        }
        var bytes = new byte[length];
        buf.readBytes(bytes);
        return bytes;
    }

    private static PrecisionGraph.Diagnostic diagnostic(int ordinal) {
        var values = PrecisionGraph.Diagnostic.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : PrecisionGraph.Diagnostic.MALFORMED;
    }

    private static final class CachedPrograms {
        private long revision = Long.MIN_VALUE;
        private final CompiledPrecisionProgram[] programs = new CompiledPrecisionProgram[4];
    }
}
