package org.academy.internal.common.ability.mentalout.precision;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.ability.mentalout.PrecisionOperationClient;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.skills.lv5.PrecisionOperation;
import org.academy.internal.common.ability.program.*;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.common.skilldata.SkillData;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PrecisionOperationManager {
    private static final String LEGACY_SKILL_KEY = "academy:precision_operation";
    private static final int SLOT_COUNT = AbilityProgramManager.SLOT_COUNT;
    private static final Map<UUID, CachedPrograms> COMPILED = new HashMap<>();
    private static boolean serverInitialized;

    private PrecisionOperationManager() {
    }

    public static synchronized void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static PrecisionOperation.Data getOrCreateData(ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        var playerData = system.getPlayerData(player.getUUID());
        if (playerData == null) return new PrecisionOperation.Data();
        var map = playerData.getSkillDataMap();
        var raw = map.get(LEGACY_SKILL_KEY);
        if (raw instanceof PrecisionOperation.Data data) {
            var schemaVersion = data.schemaVersion();
            var normalized = PrecisionOperation.normalizeData(player.getUUID(), data);
            if (schemaVersion != normalized.schemaVersion()) playerData.markDirty();
            return normalized;
        }
        var data = new PrecisionOperation.Data();
        if (raw != null) mergeProgress(data, raw);
        map.put(LEGACY_SKILL_KEY, data);
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

    public static void clear(MinecraftServer server) {
        COMPILED.clear();
        PrecisionOperationRuntime.clear(server);
    }

    private static void mergeProgress(PrecisionOperation.Data target, SkillData source) {
        target.setProficiency(source.getProficiency());
        target.setEnabled(source.isEnabled());
    }

    private static void sync(ServerPlayer player, PrecisionOperation.Data data) {
        var book = data.programBook(player.getUUID());
        MisakaNetworkServer.send(player, new SyncPacket(ProgramBookCodec.encode(book)));
    }

    private static CompiledSlotResult compiled(ServerPlayer player, int slot) {
        var data = getOrCreateData(player);
        var cache = COMPILED.computeIfAbsent(player.getUUID(), _ -> new CachedPrograms());
        if (cache.revision != data.revision()) {
            cache.revision = data.revision();
            Arrays.fill(cache.programs, null);
        }
        var program = cache.programs[slot];
        if (program != null) {
            return CompiledSlotResult.success(program);
        }
        var abilityProgram = data.program(player.getUUID(), slot);
        if (abilityProgram == null || abilityProgram.graph().nodes().isEmpty()) {
            return CompiledSlotResult.failure(PrecisionGraph.Diagnostic.EMPTY_PROGRAM, -1);
        }
        var definition = AbilityProgramDefinitions.mentalout();
        if (!abilityProgram.category().equals(definition.category())) {
            return CompiledSlotResult.failure(PrecisionGraph.Diagnostic.MALFORMED, -1);
        }
        var generic = PrecisionProgramCompilation.compile(abilityProgram);
        if (!generic.valid()) {
            var diagnostic = generic.diagnostics().getFirst();
            return CompiledSlotResult.failure(
                    PrecisionGraph.Diagnostic.MALFORMED, diagnostic.nodeId());
        }
        cache.programs[slot] = generic.program();
        return CompiledSlotResult.success(generic.program());
    }

    public static void executeTriggered(
            ServerPlayer player,
            ProgramTriggers.Type trigger,
            CommonProgramNodeCatalog.MovementCondition movement
    ) {
        if (!unlocked(player)) return;
        var data = getOrCreateData(player);
        var gameTime = player.level().getGameTime();
        for (var slot = 0; slot < SLOT_COUNT; slot++) {
            var abilityProgram = data.program(player.getUUID(), slot);
            if (abilityProgram == null) continue;
            var matches = trigger == ProgramTriggers.Type.HEALTH
                    ? ProgramTriggers.matchesHealth(
                    abilityProgram, player,
                    AbilityProgramDefinitions.mentalout().category(), slot)
                    : ProgramTriggers.matches(abilityProgram, trigger, movement, gameTime);
            if (!matches) continue;
            var compiled = compiled(player, slot);
            if (!compiled.valid()) continue;
            PrecisionOperationRuntime.execute(
                    player, slot, compiled.program(), false,
                    ProgramTriggers.costMultiplier(abilityProgram));
        }
    }

    private static boolean unlocked(ServerPlayer player) {
        var system = AbilitySystemServer.getSystem(player);
        var category = system.getPlayerAbilityCategory(player.getUUID());
        return category != null
                && category.getKey().equals(AbilityProgramDefinitions.mentalout().category())
                && system.getPlayerLevel(player.getUUID()) >= 5;
    }

    static void runtimeError(
            ServerPlayer player,
            int slot,
            PrecisionGraph.Diagnostic diagnostic,
            int nodeId,
            int affectedCount
    ) {
        Server.result(player, slot, FeedbackType.ERROR, getOrCreateData(player).revision(),
                diagnostic, nodeId, -1, affectedCount);
    }

    static void runtimeCompleted(ServerPlayer player, int slot) {
        Server.result(player, slot, FeedbackType.COMPLETED, getOrCreateData(player).revision(),
                PrecisionGraph.Diagnostic.OK, -1, -1, 0);
    }

    private static void writeBytes(ByteBuf buf, byte[] bytes, int maximum) {
        if (bytes == null || bytes.length > maximum) {
            throw new EncoderException("Ability program payload exceeds its limit");
        }
        ByteBufCodecs.VAR_INT.encode(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    private static byte[] readBytes(ByteBuf buf, int maximum) {
        var length = ByteBufCodecs.VAR_INT.decode(buf);
        if (length < 0 || length > maximum || length > buf.readableBytes()) {
            throw new DecoderException("Invalid ability program length");
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

    private static FeedbackType feedbackType(int ordinal) {
        var values = FeedbackType.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FeedbackType.ERROR;
    }

    public enum FeedbackType {
        SAVE,
        STARTED,
        CANCELLED,
        COMPLETED,
        ERROR
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void request(RequestPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!unlocked(player)) return;
            sync(player, getOrCreateData(player));
        }

        @SubscribePacket
        public static void save(SavePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!unlocked(player)) {
                result(player, packet.slot, FeedbackType.ERROR, 0L,
                        PrecisionGraph.Diagnostic.SKILL_UNAVAILABLE, -1, -1, 0);
                return;
            }
            var data = getOrCreateData(player);
            if (packet.slot < 0 || packet.slot >= SLOT_COUNT || packet.expectedRevision != data.revision()) {
                result(player, packet.slot, FeedbackType.ERROR, data.revision(),
                        PrecisionGraph.Diagnostic.REVISION_CONFLICT, -1, -1, 0);
                sync(player, data);
                return;
            }
            var decoded = ProgramBookCodec.decodeProgram(packet.program);
            if (!decoded.valid()) {
                result(player, packet.slot, FeedbackType.ERROR, data.revision(),
                        decoded.diagnostic() == ProgramBookCodec.Diagnostic.TOO_LARGE
                                ? PrecisionGraph.Diagnostic.TOO_LARGE
                                : PrecisionGraph.Diagnostic.MALFORMED,
                        -1, -1, 0);
                return;
            }
            var abilityProgram = decoded.program();
            if (abilityProgram == null) {
                data.replaceProgram(player.getUUID(), packet.slot, null);
                markSaved(player, data, packet.slot);
                return;
            }
            var definition = AbilityProgramDefinitions.mentalout();
            if (!abilityProgram.category().equals(definition.category())) {
                result(player, packet.slot, FeedbackType.ERROR, data.revision(),
                        PrecisionGraph.Diagnostic.MALFORMED, -1, -1, 0);
                return;
            }
            var generic = PrecisionProgramCompilation.compile(abilityProgram);
            if (!generic.valid()) {
                var diagnostic = generic.diagnostics().getFirst();
                result(player, packet.slot, FeedbackType.ERROR, data.revision(),
                        PrecisionGraph.Diagnostic.MALFORMED,
                        diagnostic.nodeId(), -1, 0);
                return;
            }
            var actionNode = abilityProgram.graph().nodes().stream()
                    .filter(node -> {
                        var kind = PrecisionProgramNodeIds.kind(node.type());
                        return kind != null && kind.isAction() && !kind.isConditionalBranch();
                    })
                    .findFirst().orElse(null);
            if (actionNode == null) {
                result(player, packet.slot, FeedbackType.ERROR, data.revision(),
                        PrecisionGraph.Diagnostic.EMPTY_PROGRAM, -1, -1, 0);
                return;
            }
            data.replaceProgram(player.getUUID(), packet.slot, abilityProgram);
            markSaved(player, data, packet.slot);
        }

        private static void markSaved(
                ServerPlayer player,
                PrecisionOperation.Data data,
                int slot
        ) {
            var playerData = AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID());
            if (playerData != null) playerData.markDirty();
            COMPILED.remove(player.getUUID());
            result(player, slot, FeedbackType.SAVE, data.revision(),
                    PrecisionGraph.Diagnostic.OK, -1, -1, 0);
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
            if (!unlocked(player)) return;
            if (packet.slot < 0 || packet.slot >= SLOT_COUNT) return;
            var compiled = compiled(player, packet.slot);
            if (!compiled.valid()) {
                result(
                        player,
                        packet.slot,
                        FeedbackType.ERROR,
                        getOrCreateData(player).revision(),
                        compiled.diagnostic(),
                        compiled.nodeId(),
                        compiled.port(),
                        0
                );
                return;
            }
            if (!ProgramTriggers.acceptsManualExecution(compiled.program())) return;
            var execution = PrecisionOperationRuntime.execute(
                    player,
                    packet.slot,
                    compiled.program()
            );
            result(
                    player,
                    packet.slot,
                    switch (execution.state()) {
                        case STARTED -> FeedbackType.STARTED;
                        case CANCELLED -> FeedbackType.CANCELLED;
                        case COMPLETED -> FeedbackType.COMPLETED;
                        case FAILED -> FeedbackType.ERROR;
                    },
                    getOrCreateData(player).revision(),
                    execution.diagnostic(),
                    execution.nodeId(),
                    execution.port(),
                    execution.affectedCount()
            );
        }

        private static void result(
                ServerPlayer player,
                int slot,
                FeedbackType type,
                long revision,
                PrecisionGraph.Diagnostic diagnostic,
                int nodeId,
                int port,
                int affectedCount
        ) {
            MisakaNetworkServer.send(player, new ResultPacket(
                    Mth.clamp(slot, 0, SLOT_COUNT - 1),
                    type,
                    revision,
                    diagnostic,
                    nodeId,
                    port,
                    affectedCount
            ));
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void sync(SyncPacket packet) {
            PrecisionOperationClient.handleSync(packet.book);
        }

        @SubscribePacket
        public static void result(ResultPacket packet) {
            PrecisionOperationClient.handleResult(
                    packet.slot,
                    packet.type,
                    packet.revision,
                    packet.diagnostic,
                    packet.nodeId,
                    packet.port,
                    packet.affectedCount
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
                    writeBytes(buf, packet.program, ProgramBookCodec.MAX_PROGRAM_ENCODED_BYTES);
                },
                buf -> new SavePacket(
                        ByteBufCodecs.VAR_INT.decode(buf),
                        buf.readLong(),
                        readBytes(buf, ProgramBookCodec.MAX_PROGRAM_ENCODED_BYTES)
                )
        );
        private final int slot;
        private final long expectedRevision;
        private final byte[] program;

        public SavePacket(int slot, long expectedRevision, byte[] program) {
            this.slot = slot;
            this.expectedRevision = expectedRevision;
            this.program = program == null ? new byte[0] : program.clone();
        }

        int slot() {
            return slot;
        }

        long expectedRevision() {
            return expectedRevision;
        }

        byte[] program() {
            return program.clone();
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
                (buf, packet) -> writeBytes(buf, packet.book, ProgramBookCodec.MAX_BOOK_ENCODED_BYTES),
                buf -> new SyncPacket(readBytes(buf, ProgramBookCodec.MAX_BOOK_ENCODED_BYTES))
        );
        private final byte[] book;

        public SyncPacket(byte[] book) {
            this.book = book == null ? new byte[0] : book.clone();
        }

        byte[] book() {
            return book.clone();
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
                    ByteBufCodecs.VAR_INT.encode(buf, packet.type.ordinal());
                    buf.writeLong(packet.revision);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.diagnostic.ordinal());
                    ByteBufCodecs.VAR_INT.encode(buf, packet.nodeId);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.port);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.affectedCount);
                },
                buf -> new ResultPacket(
                        ByteBufCodecs.VAR_INT.decode(buf),
                        feedbackType(ByteBufCodecs.VAR_INT.decode(buf)),
                        buf.readLong(),
                        PrecisionOperationManager.diagnostic(ByteBufCodecs.VAR_INT.decode(buf)),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf)
                )
        );
        private final int slot;
        private final FeedbackType type;
        private final long revision;
        private final PrecisionGraph.Diagnostic diagnostic;
        private final int nodeId;
        private final int port;
        private final int affectedCount;

        public ResultPacket(
                int slot,
                FeedbackType type,
                long revision,
                PrecisionGraph.Diagnostic diagnostic,
                int nodeId,
                int port,
                int affectedCount
        ) {
            this.slot = slot;
            this.type = type == null ? FeedbackType.ERROR : type;
            this.revision = revision;
            this.diagnostic = diagnostic == null ? PrecisionGraph.Diagnostic.MALFORMED : diagnostic;
            this.nodeId = nodeId;
            this.port = port;
            this.affectedCount = Math.max(0, affectedCount);
        }

        int slot() {
            return slot;
        }

        FeedbackType type() {
            return type;
        }

        long revision() {
            return revision;
        }

        PrecisionGraph.Diagnostic diagnostic() {
            return diagnostic;
        }

        int nodeId() {
            return nodeId;
        }

        int port() {
            return port;
        }

        int affectedCount() {
            return affectedCount;
        }

        @Override
        public PacketType<ClientPacketListener, ResultPacket> getPacketType() {
            return PacketTypes.PRECISION_OPERATION_RESULT.get();
        }
    }

    private static final class CachedPrograms {
        private final CompiledProgram[] programs = new CompiledProgram[SLOT_COUNT];
        private long revision = Long.MIN_VALUE;
    }

    private record CompiledSlotResult(
            CompiledProgram program,
            PrecisionGraph.Diagnostic diagnostic,
            int nodeId,
            int port
    ) {
        private static CompiledSlotResult success(CompiledProgram program) {
            return new CompiledSlotResult(
                    program, PrecisionGraph.Diagnostic.OK, -1, -1);
        }

        private static CompiledSlotResult failure(
                PrecisionGraph.Diagnostic diagnostic,
                int nodeId
        ) {
            return new CompiledSlotResult(null, diagnostic, nodeId, -1);
        }

        private boolean valid() {
            return program != null && diagnostic == PrecisionGraph.Diagnostic.OK;
        }
    }
}
