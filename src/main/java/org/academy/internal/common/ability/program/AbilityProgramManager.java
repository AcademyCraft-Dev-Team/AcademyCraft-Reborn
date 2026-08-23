package org.academy.internal.common.ability.program;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramBook;
import org.academy.api.common.ability.program.ProgramDiagnosticCode;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.client.ability.program.AbilityProgramEditorClient;
import org.academy.internal.common.ability.AbilityCategoryNames;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramExecutionBridge;
import org.academy.internal.common.ability.accelerator.program.AcceleratorProgramNodeCatalog;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramExecutionBridge;
import org.academy.internal.common.ability.aeromanip.program.AeromanipProgramNodeCatalog;
import org.academy.internal.common.ability.darkmatter.program.DarkmatterProgramExecutionBridge;
import org.academy.internal.common.ability.darkmatter.program.DarkmatterProgramNodeCatalog;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramExecutionBridge;
import org.academy.internal.common.ability.electromaster.program.ElectromasterProgramNodeCatalog;
import org.academy.internal.common.ability.electromaster.program.ServerElectromasterProgramRuntime;
import org.academy.internal.common.ability.meltdowner.program.MeltdownerProgramExecutionBridge;
import org.academy.internal.common.ability.meltdowner.program.MeltdownerProgramNodeCatalog;
import org.academy.internal.common.ability.teleport.program.TeleportProgramExecutionBridge;
import org.academy.internal.common.ability.teleport.program.TeleportProgramNodeCatalog;
import org.academy.internal.common.network.PacketTypes;
import org.academy.internal.server.world.level.storage.Player;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative program books and execution gateway for non-mentalout categories. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AbilityProgramManager {
    public static final int SLOT_COUNT = 10;
    private static final int MAX_CATEGORY_LENGTH = 128;
    private static final int MAX_BOOK_BASE64_LENGTH =
            (ProgramBookCodec.MAX_BOOK_ENCODED_BYTES + 2) / 3 * 4;
    private static final Map<UUID, Long> LAST_EXECUTION_SEQUENCE = new HashMap<>();
    private static final Map<Identifier, CategoryExecutionAdapter> EXECUTION_ADAPTERS = Map.of(
            AcceleratorProgramNodeCatalog.ACCELERATOR,
            AbilityProgramManager::executeAccelerator,
            AeromanipProgramNodeCatalog.AEROMANIP,
            AbilityProgramManager::executeAeromanip,
            DarkmatterProgramNodeCatalog.DARKMATTER,
            AbilityProgramManager::executeDarkmatter,
            ElectromasterProgramNodeCatalog.ELECTROMASTER,
            AbilityProgramManager::executeElectromaster,
            MeltdownerProgramNodeCatalog.MELTDOWNER,
            AbilityProgramManager::executeMeltdowner,
            TeleportProgramNodeCatalog.TELEPORT,
            AbilityProgramManager::executeTeleport
    );
    private static boolean serverInitialized;

    private AbilityProgramManager() {
    }

    public static synchronized void initServer() {
        if (serverInitialized) return;
        serverInitialized = true;
        MisakaNetworkServer.NETWORK_MANAGER.register(Server.class);
    }

    public static boolean isSupportedCategory(@Nullable Identifier category) {
        return category != null
                && !category.equals(AcademyCraft.academy(AbilityCategoryNames.LEVEL0))
                && !category.equals(AcademyCraft.academy(AbilityCategoryNames.MENTALOUT))
                && AbilityProgramDefinitions.find(category) != null;
    }

    static boolean validBook(Identifier category, @Nullable ProgramBook book) {
        if (book == null
                || book.schemaVersion() != ProgramBook.CURRENT_SCHEMA_VERSION
                || book.slots().size() != SLOT_COUNT) {
            return false;
        }
        return book.slots().stream().allMatch(slot -> slot.empty()
                || slot.program().schemaVersion() == AbilityProgram.CURRENT_SCHEMA_VERSION
                && slot.program().category().equals(category));
    }

    static ProgramBook decodeStoredBook(Identifier category, @Nullable String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_BOOK_BASE64_LENGTH) {
            return ProgramBook.empty(SLOT_COUNT);
        }
        try {
            var decoded = ProgramBookCodec.decode(Base64.getDecoder().decode(encoded));
            if (decoded.valid()) {
                var book = decoded.book();
                if (book.schemaVersion() == ProgramBook.CURRENT_SCHEMA_VERSION
                        && book.slots().size() > 0
                        && book.slots().size() <= SLOT_COUNT
                        && book.slots().stream().allMatch(slot -> slot.empty()
                        || slot.program().schemaVersion() == AbilityProgram.CURRENT_SCHEMA_VERSION
                        && slot.program().category().equals(category))) {
                    return book.resize(SLOT_COUNT);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // A corrupt category book is isolated and replaced when the player next saves it.
        }
        return ProgramBook.empty(SLOT_COUNT);
    }

    static String encodeStoredBook(ProgramBook book) {
        return Base64.getEncoder().encodeToString(ProgramBookCodec.encode(book));
    }

    static Set<Identifier> learnedCapabilities(Player playerData) {
        if (playerData == null) return Set.of();
        var capabilities = new HashSet<Identifier>();
        for (var skillId : playerData.getSkillDataMap().keySet()) {
            var parsed = Identifier.tryParse(skillId);
            if (parsed != null) capabilities.add(parsed);
        }
        return Set.copyOf(capabilities);
    }

    public static void executeTriggered(
            ServerPlayer player,
            ProgramTriggers.Type trigger,
            CommonProgramNodeCatalog.MovementCondition movement
    ) {
        if (!unlocked(player)) return;
        var currentCategory = AbilitySystemServer.getSystem(player)
                .getPlayerAbilityCategory(player.getUUID());
        if (currentCategory == null || !isSupportedCategory(currentCategory.getKey())) return;
        var category = currentCategory.getKey();
        var playerData = AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID());
        if (playerData == null) return;
        var current = book(playerData, category);
        var definition = AbilityProgramDefinitions.require(category);
        var adapter = EXECUTION_ADAPTERS.get(category);
        if (adapter == null) return;
        var gameTime = player.level().getGameTime();
        var capabilities = learnedCapabilities(playerData);
        for (var slot = 0; slot < SLOT_COUNT; slot++) {
            var program = current.slot(slot).program();
            if (program == null) continue;
            var matches = trigger == ProgramTriggers.Type.HEALTH
                    ? ProgramTriggers.matchesHealth(program, player, category, slot)
                    : ProgramTriggers.matches(program, trigger, movement, gameTime);
            if (!matches) continue;
            var compiled = definition.compile(program, capabilities);
            if (compiled.valid()) adapter.execute(
                    compiled.program(), player, ProgramTriggers.costMultiplier(program));
        }
    }

    public static void clear() {
        LAST_EXECUTION_SEQUENCE.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_EXECUTION_SEQUENCE.remove(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerElectromasterProgramRuntime.releaseControlled(player);
        }
    }

    private static ProgramBook book(Player playerData, Identifier category) {
        return decodeStoredBook(category, playerData.getAbilityProgramBook(category.toString()));
    }

    private static boolean empty(ProgramBook book) {
        return book.revision() == 0L && book.slots().stream().allMatch(ProgramBook.Slot::empty);
    }

    private static void store(Player playerData, Identifier category, ProgramBook book) {
        playerData.setAbilityProgramBook(category.toString(), encodeStoredBook(book));
    }

    private static @Nullable Identifier parseCategory(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_CATEGORY_LENGTH) return null;
        return Identifier.tryParse(raw);
    }

    private static boolean ownsCategory(ServerPlayer player, Identifier category) {
        var current = AbilitySystemServer.getSystem(player)
                .getPlayerAbilityCategory(player.getUUID());
        return current != null && current.getKey().equals(category);
    }

    private static boolean unlocked(ServerPlayer player) {
        return AbilitySystemServer.getSystem(player).getPlayerLevel(player.getUUID()) >= 5;
    }

    private static boolean acceptSequence(ServerPlayer player, long sequence) {
        if (sequence < 0) return false;
        var previous = LAST_EXECUTION_SEQUENCE.get(player.getUUID());
        if (previous != null && sequence <= previous) return false;
        LAST_EXECUTION_SEQUENCE.put(player.getUUID(), sequence);
        return true;
    }

    private static void sync(ServerPlayer player, Identifier category, ProgramBook book) {
        MisakaNetworkServer.send(player, new SyncPacket(
                category.toString(), ProgramBookCodec.encode(book)));
    }

    private static ExecutionOutcome executeAccelerator(
            CompiledProgram program,
            ServerPlayer player,
            float costMultiplier
    ) {
        var execution = AcceleratorProgramExecutionBridge.executeServer(
                program, player, costMultiplier);
        if (execution.successful()) return ExecutionOutcome.success();
        var transaction = execution.transactionResult().orElse(null);
        return new ExecutionOutcome(
                false,
                transaction == null ? execution.vmResult().nodeId() : transaction.nodeId(),
                transactionDiagnostic(transaction, execution.vmResult().diagnostic())
        );
    }

    private static ExecutionOutcome executeAeromanip(
            CompiledProgram program,
            ServerPlayer player,
            float costMultiplier
    ) {
        var execution = AeromanipProgramExecutionBridge.executeServer(
                program, player, costMultiplier);
        if (execution.successful()) return ExecutionOutcome.success();
        var transaction = execution.transactionResult().orElse(null);
        return new ExecutionOutcome(
                false,
                transaction == null ? execution.vmResult().nodeId() : transaction.nodeId(),
                transactionDiagnostic(transaction, execution.vmResult().diagnostic())
        );
    }

    private static ExecutionOutcome executeDarkmatter(
            CompiledProgram program,
            ServerPlayer player,
            float costMultiplier
    ) {
        var execution = DarkmatterProgramExecutionBridge.executeServer(
                program, player, costMultiplier);
        if (execution.successful()) return ExecutionOutcome.success();
        var transaction = execution.transactionResult().orElse(null);
        return new ExecutionOutcome(
                false,
                transaction == null ? execution.vmResult().nodeId() : transaction.nodeId(),
                transactionDiagnostic(transaction, execution.vmResult().diagnostic())
        );
    }

    private static ExecutionOutcome executeElectromaster(
            CompiledProgram program,
            ServerPlayer player,
            float costMultiplier
    ) {
        var execution = ElectromasterProgramExecutionBridge.executeServer(
                program, player, costMultiplier);
        if (execution.successful()) return ExecutionOutcome.success();
        var transaction = execution.transactionResult().orElse(null);
        return new ExecutionOutcome(
                false,
                transaction == null ? execution.vmResult().nodeId() : transaction.nodeId(),
                transactionDiagnostic(transaction, execution.vmResult().diagnostic())
        );
    }

    private static ExecutionOutcome executeTeleport(
            CompiledProgram program,
            ServerPlayer player,
            float costMultiplier
    ) {
        var execution = TeleportProgramExecutionBridge.executeServer(
                program, player, costMultiplier);
        if (execution.successful()) return ExecutionOutcome.success();
        var transaction = execution.transactionResult().orElse(null);
        return new ExecutionOutcome(
                false,
                transaction == null ? execution.vmResult().nodeId() : transaction.nodeId(),
                transactionDiagnostic(transaction, execution.vmResult().diagnostic())
        );
    }

    private static ExecutionOutcome executeMeltdowner(
            CompiledProgram program,
            ServerPlayer player,
            float costMultiplier
    ) {
        var execution = MeltdownerProgramExecutionBridge.executeServer(
                program, player, costMultiplier);
        if (execution.successful()) return ExecutionOutcome.success();
        var transaction = execution.transactionResult().orElse(null);
        return new ExecutionOutcome(
                false,
                transaction == null ? execution.vmResult().nodeId() : transaction.nodeId(),
                transactionDiagnostic(transaction, execution.vmResult().diagnostic())
        );
    }

    private static ProgramVmDiagnostic transactionDiagnostic(
            ProgramActionTransaction.Result transaction,
            ProgramVmDiagnostic vmDiagnostic
    ) {
        if (transaction == null) return vmDiagnostic;
        var cause = transaction.cause();
        var message = cause == null || cause.getMessage() == null
                ? "" : cause.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (message.contains("insufficient cp")) return ProgramVmDiagnostic.INSUFFICIENT_CP;
        if (message.contains("outside program range") || message.contains("exceeds its strength limit")) {
            return ProgramVmDiagnostic.TARGET_OUT_OF_RANGE;
        }
        if (message.contains("skill is unavailable")) return ProgramVmDiagnostic.SKILL_UNAVAILABLE;
        return ProgramVmDiagnostic.ACTION_REJECTED;
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

    private static FeedbackType feedbackType(int ordinal) {
        var values = FeedbackType.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : FeedbackType.ERROR;
    }

    private static ResultCode resultCode(int ordinal) {
        var values = ResultCode.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ResultCode.INVALID_PROGRAM;
    }

    private static @Nullable ProgramDiagnosticCode programDiagnostic(int ordinal) {
        var values = ProgramDiagnosticCode.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    private static ProgramVmDiagnostic vmDiagnostic(int ordinal) {
        var values = ProgramVmDiagnostic.values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal] : ProgramVmDiagnostic.NONE;
    }

    public enum FeedbackType {
        SAVE,
        IMPORT,
        COMPLETED,
        ERROR
    }

    public enum ResultCode {
        OK,
        INVALID_CATEGORY,
        REVISION_CONFLICT,
        TOO_LARGE,
        INVALID_PROGRAM,
        EMPTY_PROGRAM,
        EXECUTION_UNSUPPORTED,
        EXECUTION_FAILED
    }

    public static final class Server {
        private Server() {
        }

        @SubscribePacket
        public static void request(RequestPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!unlocked(player)) return;
            var category = parseCategory(packet.category);
            if (!isSupportedCategory(category) || !ownsCategory(player, category)) return;
            var data = AbilitySystemServer.getSystem(player).getPlayerData(player.getUUID());
            sync(player, category, book(data, category));
        }

        @SubscribePacket
        public static void importBook(ImportPacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!unlocked(player)) return;
            var category = parseCategory(packet.category);
            if (!isSupportedCategory(category) || !ownsCategory(player, category)) {
                result(player, packet.category, 0, FeedbackType.ERROR, 0L,
                        ResultCode.INVALID_CATEGORY, null, -1, ProgramVmDiagnostic.NONE);
                return;
            }
            var playerData = AbilitySystemServer.getSystem(player)
                    .getPlayerData(player.getUUID());
            var current = book(playerData, category);
            if (packet.expectedRevision != current.revision() || !empty(current)) {
                result(player, packet.category, current.selectedSlot(), FeedbackType.ERROR,
                        current.revision(), ResultCode.REVISION_CONFLICT,
                        null, -1, ProgramVmDiagnostic.NONE);
                sync(player, category, current);
                return;
            }
            var decoded = ProgramBookCodec.decode(packet.book);
            if (!decoded.valid() || !validBook(category, decoded.book())) {
                result(player, packet.category, 0, FeedbackType.ERROR,
                        current.revision(),
                        decoded.diagnostic() == ProgramBookCodec.Diagnostic.TOO_LARGE
                                ? ResultCode.TOO_LARGE : ResultCode.INVALID_PROGRAM,
                        null, -1, ProgramVmDiagnostic.NONE);
                return;
            }
            var capabilities = learnedCapabilities(playerData);
            for (var slot = 0; slot < SLOT_COUNT; slot++) {
                var program = decoded.book().slot(slot).program();
                if (program == null) continue;
                var compiled = AbilityProgramDefinitions.require(category)
                        .compile(program, capabilities);
                if (!compiled.valid()) {
                    var diagnostic = compiled.diagnostics().getFirst();
                    result(player, packet.category, slot, FeedbackType.ERROR,
                            current.revision(), ResultCode.INVALID_PROGRAM,
                            diagnostic.code(), diagnostic.nodeId(), ProgramVmDiagnostic.NONE);
                    return;
                }
            }
            var imported = new ProgramBook(
                    ProgramBook.CURRENT_SCHEMA_VERSION,
                    current.revision() + 1L,
                    decoded.book().selectedSlot(),
                    decoded.book().slots()
            );
            store(playerData, category, imported);
            result(player, packet.category, imported.selectedSlot(), FeedbackType.IMPORT,
                    imported.revision(), ResultCode.OK, null, -1, ProgramVmDiagnostic.NONE);
            sync(player, category, imported);
        }

        @SubscribePacket
        public static void save(SavePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!unlocked(player)) return;
            var category = parseCategory(packet.category);
            if (!isSupportedCategory(category) || !ownsCategory(player, category)) {
                result(player, packet.category, packet.slot, FeedbackType.ERROR, 0L,
                        ResultCode.INVALID_CATEGORY, null, -1, ProgramVmDiagnostic.NONE);
                return;
            }
            var playerData = AbilitySystemServer.getSystem(player)
                    .getPlayerData(player.getUUID());
            var current = book(playerData, category);
            if (packet.slot < 0 || packet.slot >= SLOT_COUNT
                    || packet.expectedRevision != current.revision()) {
                result(player, packet.category, packet.slot, FeedbackType.ERROR,
                        current.revision(), ResultCode.REVISION_CONFLICT,
                        null, -1, ProgramVmDiagnostic.NONE);
                sync(player, category, current);
                return;
            }
            var decoded = ProgramBookCodec.decodeProgram(packet.program);
            if (!decoded.valid()) {
                result(player, packet.category, packet.slot, FeedbackType.ERROR,
                        current.revision(),
                        decoded.diagnostic() == ProgramBookCodec.Diagnostic.TOO_LARGE
                                ? ResultCode.TOO_LARGE : ResultCode.INVALID_PROGRAM,
                        null, -1, ProgramVmDiagnostic.NONE);
                return;
            }
            var program = decoded.program();
            if (program != null) {
                if (!program.category().equals(category)) {
                    result(player, packet.category, packet.slot, FeedbackType.ERROR,
                            current.revision(), ResultCode.INVALID_CATEGORY,
                            ProgramDiagnosticCode.CATEGORY_MISMATCH, -1,
                            ProgramVmDiagnostic.NONE);
                    return;
                }
                var compiled = AbilityProgramDefinitions.require(category).compile(
                        program, learnedCapabilities(playerData));
                if (!compiled.valid()) {
                    var diagnostic = compiled.diagnostics().getFirst();
                    result(player, packet.category, packet.slot, FeedbackType.ERROR,
                            current.revision(), ResultCode.INVALID_PROGRAM,
                            diagnostic.code(), diagnostic.nodeId(), ProgramVmDiagnostic.NONE);
                    return;
                }
            }
            var changed = current.replaceSlot(packet.slot, program).select(packet.slot);
            store(playerData, category, changed);
            result(player, packet.category, packet.slot, FeedbackType.SAVE,
                    changed.revision(), ResultCode.OK, null, -1, ProgramVmDiagnostic.NONE);
            sync(player, category, changed);
        }

        @SubscribePacket
        public static void execute(ExecutePacket packet) {
            var player = packet.getPacketListener().getPlayer();
            if (!unlocked(player)) return;
            var category = parseCategory(packet.category);
            if (!isSupportedCategory(category) || !ownsCategory(player, category)) {
                result(player, packet.category, packet.slot, FeedbackType.ERROR, 0L,
                        ResultCode.INVALID_CATEGORY, null, -1, ProgramVmDiagnostic.NONE);
                return;
            }
            if (packet.slot < 0 || packet.slot >= SLOT_COUNT
                    || !acceptSequence(player, packet.sequence)) return;
            var playerData = AbilitySystemServer.getSystem(player)
                    .getPlayerData(player.getUUID());
            var current = book(playerData, category);
            var program = current.slot(packet.slot).program();
            if (program == null || program.graph().nodes().isEmpty()) {
                result(player, packet.category, packet.slot, FeedbackType.ERROR,
                        current.revision(), ResultCode.EMPTY_PROGRAM,
                        ProgramDiagnosticCode.EMPTY_PROGRAM, -1, ProgramVmDiagnostic.NONE);
                return;
            }
            var compiled = AbilityProgramDefinitions.require(category).compile(
                    program, learnedCapabilities(playerData));
            if (!compiled.valid()) {
                var diagnostic = compiled.diagnostics().getFirst();
                result(player, packet.category, packet.slot, FeedbackType.ERROR,
                        current.revision(), ResultCode.INVALID_PROGRAM,
                        diagnostic.code(), diagnostic.nodeId(), ProgramVmDiagnostic.NONE);
                return;
            }
            if (!ProgramTriggers.acceptsManualExecution(compiled.program())) return;
            var adapter = EXECUTION_ADAPTERS.get(category);
            if (adapter == null) {
                result(player, packet.category, packet.slot, FeedbackType.ERROR,
                        current.revision(), ResultCode.EXECUTION_UNSUPPORTED,
                        null, -1, ProgramVmDiagnostic.NONE);
                return;
            }
            var outcome = adapter.execute(compiled.program(), player, 1.0f);
            result(player, packet.category, packet.slot,
                    outcome.successful ? FeedbackType.COMPLETED : FeedbackType.ERROR,
                    current.revision(),
                    outcome.successful ? ResultCode.OK : ResultCode.EXECUTION_FAILED,
                    null, outcome.nodeId, outcome.vmDiagnostic);
        }

        private static void result(
                ServerPlayer player,
                String category,
                int slot,
                FeedbackType type,
                long revision,
                ResultCode code,
                @Nullable ProgramDiagnosticCode diagnostic,
                int nodeId,
                ProgramVmDiagnostic vmDiagnostic
        ) {
            MisakaNetworkServer.send(player, new ResultPacket(
                    category,
                    Mth.clamp(slot, 0, SLOT_COUNT - 1),
                    type,
                    Math.max(0L, revision),
                    code,
                    diagnostic,
                    nodeId,
                    vmDiagnostic
            ));
        }
    }

    public static final class Client {
        private Client() {
        }

        @SubscribePacket
        public static void sync(SyncPacket packet) {
            var category = parseCategory(packet.category);
            if (category != null) AbilityProgramEditorClient.handleSync(category, packet.book);
        }

        @SubscribePacket
        public static void result(ResultPacket packet) {
            var category = parseCategory(packet.category);
            if (category == null) return;
            AbilityProgramEditorClient.handleResult(
                    category,
                    packet.slot,
                    packet.type,
                    packet.revision,
                    packet.code,
                    packet.diagnostic,
                    packet.nodeId,
                    packet.vmDiagnostic
            );
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class RequestPacket extends Packet<ServerGamePacketListenerImpl, RequestPacket> {
        public static final StreamCodec<ByteBuf, RequestPacket> CODEC =
                ByteBufCodecs.STRING_UTF8.map(RequestPacket::new, packet -> packet.category);
        private final String category;

        public RequestPacket(Identifier category) {
            this(Objects.requireNonNull(category, "category").toString());
        }

        private RequestPacket(String category) {
            this.category = category == null ? "" : category;
        }

        String category() {
            return category;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, RequestPacket> getPacketType() {
            return PacketTypes.ABILITY_PROGRAM_REQUEST.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ImportPacket extends Packet<ServerGamePacketListenerImpl, ImportPacket> {
        public static final StreamCodec<ByteBuf, ImportPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.category);
                    buf.writeLong(packet.expectedRevision);
                    writeBytes(buf, packet.book, ProgramBookCodec.MAX_BOOK_ENCODED_BYTES);
                },
                buf -> new ImportPacket(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        buf.readLong(),
                        readBytes(buf, ProgramBookCodec.MAX_BOOK_ENCODED_BYTES)
                )
        );
        private final String category;
        private final long expectedRevision;
        private final byte[] book;

        public ImportPacket(Identifier category, long expectedRevision, byte[] book) {
            this(Objects.requireNonNull(category, "category").toString(), expectedRevision, book);
        }

        private ImportPacket(String category, long expectedRevision, byte[] book) {
            this.category = category == null ? "" : category;
            this.expectedRevision = expectedRevision;
            this.book = book == null ? new byte[0] : book.clone();
        }

        String category() {
            return category;
        }

        long expectedRevision() {
            return expectedRevision;
        }

        byte[] book() {
            return book.clone();
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ImportPacket> getPacketType() {
            return PacketTypes.ABILITY_PROGRAM_IMPORT.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class SavePacket extends Packet<ServerGamePacketListenerImpl, SavePacket> {
        public static final StreamCodec<ByteBuf, SavePacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.category);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.slot);
                    buf.writeLong(packet.expectedRevision);
                    writeBytes(buf, packet.program, ProgramBookCodec.MAX_PROGRAM_ENCODED_BYTES);
                },
                buf -> new SavePacket(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        buf.readLong(),
                        readBytes(buf, ProgramBookCodec.MAX_PROGRAM_ENCODED_BYTES)
                )
        );
        private final String category;
        private final int slot;
        private final long expectedRevision;
        private final byte[] program;

        public SavePacket(
                Identifier category,
                int slot,
                long expectedRevision,
                byte[] program
        ) {
            this(Objects.requireNonNull(category, "category").toString(),
                    slot, expectedRevision, program);
        }

        private SavePacket(String category, int slot, long expectedRevision, byte[] program) {
            this.category = category == null ? "" : category;
            this.slot = slot;
            this.expectedRevision = expectedRevision;
            this.program = program == null ? new byte[0] : program.clone();
        }

        String category() {
            return category;
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
            return PacketTypes.ABILITY_PROGRAM_SAVE.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static final class ExecutePacket extends Packet<ServerGamePacketListenerImpl, ExecutePacket> {
        public static final StreamCodec<ByteBuf, ExecutePacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.category);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.slot);
                    buf.writeLong(packet.sequence);
                },
                buf -> new ExecutePacket(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        buf.readLong()
                )
        );
        private final String category;
        private final int slot;
        private final long sequence;

        public ExecutePacket(Identifier category, int slot, long sequence) {
            this(Objects.requireNonNull(category, "category").toString(), slot, sequence);
        }

        private ExecutePacket(String category, int slot, long sequence) {
            this.category = category == null ? "" : category;
            this.slot = slot;
            this.sequence = sequence;
        }

        String category() {
            return category;
        }

        int slot() {
            return slot;
        }

        long sequence() {
            return sequence;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, ExecutePacket> getPacketType() {
            return PacketTypes.ABILITY_PROGRAM_EXECUTE.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class SyncPacket extends Packet<ClientPacketListener, SyncPacket> {
        public static final StreamCodec<ByteBuf, SyncPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.category);
                    writeBytes(buf, packet.book, ProgramBookCodec.MAX_BOOK_ENCODED_BYTES);
                },
                buf -> new SyncPacket(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        readBytes(buf, ProgramBookCodec.MAX_BOOK_ENCODED_BYTES)
                )
        );
        private final String category;
        private final byte[] book;

        public SyncPacket(String category, byte[] book) {
            this.category = category == null ? "" : category;
            this.book = book == null ? new byte[0] : book.clone();
        }

        String category() {
            return category;
        }

        byte[] book() {
            return book.clone();
        }

        @Override
        public PacketType<ClientPacketListener, SyncPacket> getPacketType() {
            return PacketTypes.ABILITY_PROGRAM_SYNC.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static final class ResultPacket extends Packet<ClientPacketListener, ResultPacket> {
        public static final StreamCodec<ByteBuf, ResultPacket> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, packet.category);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.slot);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.type.ordinal());
                    buf.writeLong(packet.revision);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.code.ordinal());
                    ByteBufCodecs.VAR_INT.encode(buf,
                            packet.diagnostic == null ? -1 : packet.diagnostic.ordinal());
                    ByteBufCodecs.VAR_INT.encode(buf, packet.nodeId);
                    ByteBufCodecs.VAR_INT.encode(buf, packet.vmDiagnostic.ordinal());
                },
                buf -> new ResultPacket(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        feedbackType(ByteBufCodecs.VAR_INT.decode(buf)),
                        buf.readLong(),
                        resultCode(ByteBufCodecs.VAR_INT.decode(buf)),
                        programDiagnostic(ByteBufCodecs.VAR_INT.decode(buf)),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        AbilityProgramManager.vmDiagnostic(ByteBufCodecs.VAR_INT.decode(buf))
                )
        );
        private final String category;
        private final int slot;
        private final FeedbackType type;
        private final long revision;
        private final ResultCode code;
        private final @Nullable ProgramDiagnosticCode diagnostic;
        private final int nodeId;
        private final ProgramVmDiagnostic vmDiagnostic;

        public ResultPacket(
                String category,
                int slot,
                FeedbackType type,
                long revision,
                ResultCode code,
                @Nullable ProgramDiagnosticCode diagnostic,
                int nodeId,
                ProgramVmDiagnostic vmDiagnostic
        ) {
            this.category = category == null ? "" : category;
            this.slot = slot;
            this.type = type == null ? FeedbackType.ERROR : type;
            this.revision = Math.max(0L, revision);
            this.code = code == null ? ResultCode.INVALID_PROGRAM : code;
            this.diagnostic = diagnostic;
            this.nodeId = nodeId;
            this.vmDiagnostic = vmDiagnostic == null
                    ? ProgramVmDiagnostic.NONE : vmDiagnostic;
        }

        String category() {
            return category;
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

        ResultCode code() {
            return code;
        }

        @Nullable ProgramDiagnosticCode diagnostic() {
            return diagnostic;
        }

        int nodeId() {
            return nodeId;
        }

        ProgramVmDiagnostic vmDiagnostic() {
            return vmDiagnostic;
        }

        @Override
        public PacketType<ClientPacketListener, ResultPacket> getPacketType() {
            return PacketTypes.ABILITY_PROGRAM_RESULT.get();
        }
    }

    @FunctionalInterface
    private interface CategoryExecutionAdapter {
        ExecutionOutcome execute(
                CompiledProgram program,
                ServerPlayer player,
                float costMultiplier
        );
    }

    private record ExecutionOutcome(
            boolean successful,
            int nodeId,
            ProgramVmDiagnostic vmDiagnostic
    ) {
        private static ExecutionOutcome success() {
            return new ExecutionOutcome(true, -1, ProgramVmDiagnostic.NONE);
        }
    }
}
