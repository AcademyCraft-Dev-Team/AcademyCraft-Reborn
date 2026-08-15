package org.academy.internal.client.ability.mentalout;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.api.common.ability.program.AbilityProgram;
import org.academy.api.common.ability.program.ProgramEditorLayout;
import org.academy.api.common.ability.program.ProgramGraph;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationManager;
import org.academy.internal.common.ability.program.PrecisionProgramExporter;
import org.academy.internal.common.ability.program.PrecisionProgramAliases;
import org.academy.internal.common.ability.program.AbilityProgramDefinitions;
import org.academy.internal.common.ability.program.ProgramBookCodec;
import org.academy.internal.common.ability.program.ProgramEditorDocument;
import org.academy.internal.common.ability.program.AbilityProgramManager;
import org.misaka.MisakaNetworkClient;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.Mth;

public final class PrecisionOperationClient {
    private static final int SLOT_COUNT = AbilityProgramManager.SLOT_COUNT;
    private static final AbilityProgram[] PROGRAMS = new AbilityProgram[SLOT_COUNT];
    private static final AbilityProgram[] SERVER_PROGRAMS = new AbilityProgram[SLOT_COUNT];
    private static final PrecisionGraph[] GRAPHS = emptyGraphs();
    private static final PrecisionGraph[] SERVER_GRAPHS = GRAPHS.clone();
    private static final PrecisionGraph.Diagnostic[] LAST_DIAGNOSTICS = emptyDiagnostics();
    private static final int[] LAST_NODES = filledInts(-1);
    private static final int[] LAST_PORTS = filledInts(-1);
    private static final boolean[] ACTIVE_SLOTS = new boolean[SLOT_COUNT];
    private static final boolean[] ACTIVE_FAILURES = new boolean[SLOT_COUNT];
    private static long revision;
    private static int selectedSlot;
    private static ModularProgramScreen screen;
    private static final ModularProgramEditorSession EDITOR_SESSION =
            new ModularProgramEditorSession() {
                @Override
                public Component title() {
                    return Component.translatable("screen.academy.precision_operation.title");
                }

                @Override
                public int slotCount() {
                    return SLOT_COUNT;
                }

                @Override
                public int selectedSlot() {
                    return selectedSlot;
                }

                @Override
                public long revision() {
                    return revision;
                }

                @Override
                public AbilityProgram editableProgram(int slot) {
                    return PrecisionOperationClient.editableProgram(slot);
                }

                @Override
                public AbilityProgram emptyProgram(int slot) {
                    return PrecisionOperationClient.emptyProgram(slot);
                }

                @Override
                public AbilityProgram restoredProgram(int slot) {
                    return PrecisionOperationClient.serverProgram(slot);
                }

                @Override
                public Set<Identifier> capabilities() {
                    return Set.of();
                }

                @Override
                public void updateLocalProgram(int slot, AbilityProgram program) {
                    PrecisionOperationClient.updateLocalProgram(slot, program);
                }

                @Override
                public void selectSlot(int slot) {
                    PrecisionOperationClient.selectSlot(slot);
                }

                @Override
                public void saveProgram(
                        int slot,
                        AbilityProgram program,
                        long expectedRevision
                ) {
                    PrecisionOperationClient.saveProgram(slot, program, expectedRevision);
                }

                @Override
                public void closed(ModularProgramScreen screen) {
                    PrecisionOperationClient.closed(screen);
                }

                @Override
                public boolean precisionRules() {
                    return true;
                }

                @Override
                public PrecisionGraph.Diagnostic diagnostic(int slot) {
                    return PrecisionOperationClient.diagnostic(slot);
                }

                @Override
                public int diagnosticNode(int slot) {
                    return PrecisionOperationClient.diagnosticNode(slot);
                }

                @Override
                public void clearDiagnostic(int slot) {
                    PrecisionOperationClient.clearDiagnostic(slot);
                }
            };

    private static PrecisionGraph[] emptyGraphs() {
        var graphs = new PrecisionGraph[SLOT_COUNT];
        Arrays.fill(graphs, PrecisionGraph.EMPTY);
        return graphs;
    }

    private static PrecisionGraph.Diagnostic[] emptyDiagnostics() {
        var diagnostics = new PrecisionGraph.Diagnostic[SLOT_COUNT];
        Arrays.fill(diagnostics, PrecisionGraph.Diagnostic.OK);
        return diagnostics;
    }

    private static int[] filledInts(int value) {
        var values = new int[SLOT_COUNT];
        Arrays.fill(values, value);
        return values;
    }

    private PrecisionOperationClient() {
    }

    public static void openEditor() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null
                || AbilitySystemClient.category == null
                || !AbilitySystemClient.category.getKey().equals(
                        AbilityProgramDefinitions.mentalout().category())
                || AbilitySystemClient.getLevel().getLevelCode() < 5) return;
        screen = new ModularProgramScreen(EDITOR_SESSION);
        if (LAST_DIAGNOSTICS[selectedSlot] != PrecisionGraph.Diagnostic.OK) {
            screen.applyResult(
                    selectedSlot,
                    PrecisionOperationManager.FeedbackType.ERROR,
                    revision,
                    LAST_DIAGNOSTICS[selectedSlot],
                    LAST_NODES[selectedSlot],
                    LAST_PORTS[selectedSlot]
            );
        }
        minecraft.gui.setScreen(screen);
        MisakaNetworkClient.send(PrecisionOperationManager.RequestPacket.INSTANCE);
    }

    public static void executeSelected() {
        execute(selectedSlot);
    }

    public static void execute(int slot) {
        if (Minecraft.getInstance().gui.screen() != null
                || AbilitySystemClient.category == null
                || !AbilitySystemClient.category.getKey().equals(
                        AbilityProgramDefinitions.mentalout().category())
                || AbilitySystemClient.getLevel().getLevelCode() < 5) return;
        selectedSlot = Mth.clamp(slot, 0, SLOT_COUNT - 1);
        MisakaNetworkClient.send(new PrecisionOperationManager.ExecutePacket(
                selectedSlot,
                MentaloutRequestGuard.nextClientSequence()
        ));
    }

    public static void handleSync(byte[] encoded) {
        var result = ProgramBookCodec.decode(encoded);
        if (!result.valid() || result.book().slots().isEmpty()
                || result.book().slots().size() > SLOT_COUNT
                || result.book().revision() < revision) return;
        var book = PrecisionProgramAliases.canonicalize(result.book().resize(SLOT_COUNT));
        var decoded = new PrecisionGraph[SLOT_COUNT];
        for (var slot = 0; slot < SLOT_COUNT; slot++) {
            var exported = PrecisionProgramExporter.export(book.slot(slot).program());
            decoded[slot] = exported.valid() ? exported.graph() : PrecisionGraph.EMPTY;
        }
        revision = book.revision();
        for (var slot = 0; slot < SLOT_COUNT; slot++) {
            PROGRAMS[slot] = book.slot(slot).program();
            SERVER_PROGRAMS[slot] = PROGRAMS[slot];
            GRAPHS[slot] = decoded[slot];
            SERVER_GRAPHS[slot] = decoded[slot];
        }
        if (screen != null && Minecraft.getInstance().gui.screen() == screen) {
            screen.applyServerState(selectedSlot, editableProgram(selectedSlot), revision);
        }
    }

    public static void handleResult(
            int slot,
            PrecisionOperationManager.FeedbackType type,
            long serverRevision,
            PrecisionGraph.Diagnostic diagnostic,
            int nodeId,
            int port,
            int affectedCount
    ) {
        slot = Mth.clamp(slot, 0, SLOT_COUNT - 1);
        if (serverRevision > revision) revision = serverRevision;
        if (type == PrecisionOperationManager.FeedbackType.ERROR) {
            LAST_DIAGNOSTICS[slot] = diagnostic;
            LAST_NODES[slot] = nodeId;
            LAST_PORTS[slot] = port;
            if (ACTIVE_SLOTS[slot]) ACTIVE_FAILURES[slot] = true;
            showError(slot, diagnostic, nodeId, affectedCount);
        } else if (type == PrecisionOperationManager.FeedbackType.STARTED) {
            clearDiagnostic(slot);
            ACTIVE_SLOTS[slot] = true;
            ACTIVE_FAILURES[slot] = false;
            showActionBar("message.academy.precision_operation.feedback.started", slot);
        } else if (type == PrecisionOperationManager.FeedbackType.CANCELLED) {
            ACTIVE_SLOTS[slot] = false;
            showActionBar("message.academy.precision_operation.feedback.cancelled", slot);
        } else if (type == PrecisionOperationManager.FeedbackType.COMPLETED) {
            if (!ACTIVE_SLOTS[slot] || !ACTIVE_FAILURES[slot]) clearDiagnostic(slot);
            ACTIVE_SLOTS[slot] = false;
            showActionBar("message.academy.precision_operation.feedback.completed", slot);
        }
        if (screen != null && Minecraft.getInstance().gui.screen() == screen) {
            screen.applyResult(slot, type, revision, diagnostic, nodeId, port);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetSession();
    }

    static void resetSession() {
        Arrays.fill(GRAPHS, PrecisionGraph.EMPTY);
        Arrays.fill(SERVER_GRAPHS, PrecisionGraph.EMPTY);
        Arrays.fill(PROGRAMS, null);
        Arrays.fill(SERVER_PROGRAMS, null);
        Arrays.fill(LAST_DIAGNOSTICS, PrecisionGraph.Diagnostic.OK);
        Arrays.fill(LAST_NODES, -1);
        Arrays.fill(LAST_PORTS, -1);
        Arrays.fill(ACTIVE_SLOTS, false);
        Arrays.fill(ACTIVE_FAILURES, false);
        revision = 0L;
        selectedSlot = 0;
        screen = null;
    }

    static PrecisionGraph graph(int slot) {
        return GRAPHS[Mth.clamp(slot, 0, SLOT_COUNT - 1)];
    }

    static PrecisionGraph serverGraph(int slot) {
        return SERVER_GRAPHS[Mth.clamp(slot, 0, SLOT_COUNT - 1)];
    }

    static AbilityProgram program(int slot) {
        return PROGRAMS[Mth.clamp(slot, 0, SLOT_COUNT - 1)];
    }

    static AbilityProgram serverProgram(int slot) {
        return SERVER_PROGRAMS[Mth.clamp(slot, 0, SLOT_COUNT - 1)];
    }

    static long revision() {
        return revision;
    }

    static void selectSlot(int slot) {
        selectedSlot = Mth.clamp(slot, 0, SLOT_COUNT - 1);
    }

    static void updateLocal(int slot, PrecisionGraph graph) {
        GRAPHS[Mth.clamp(slot, 0, SLOT_COUNT - 1)] = graph;
    }

    static void updateLocalProgram(int slot, AbilityProgram program) {
        slot = Mth.clamp(slot, 0, SLOT_COUNT - 1);
        PROGRAMS[slot] = program;
        var exported = PrecisionProgramExporter.export(program);
        GRAPHS[slot] = exported.valid() ? exported.graph() : PrecisionGraph.EMPTY;
    }

    static void saveProgram(int slot, AbilityProgram program, long expectedRevision) {
        slot = Mth.clamp(slot, 0, SLOT_COUNT - 1);
        var definition = AbilityProgramDefinitions.mentalout();
        if (program != null && !program.category().equals(definition.category())) {
            handleResult(
                    slot,
                    PrecisionOperationManager.FeedbackType.ERROR,
                    revision,
                    PrecisionGraph.Diagnostic.MALFORMED,
                    -1,
                    -1,
                    0
            );
            return;
        }
        if (program != null) {
            var validation = new ProgramEditorDocument(
                    program,
                    definition,
                    java.util.Set.of()
            ).validation();
            if (!validation.valid()) {
                var diagnostic = validation.diagnostics().getFirst();
                handleResult(
                        slot,
                        PrecisionOperationManager.FeedbackType.ERROR,
                        revision,
                        PrecisionGraph.Diagnostic.MALFORMED,
                        diagnostic.nodeId(),
                        -1,
                        0
                );
                return;
            }
        }
        final byte[] encoded;
        try {
            encoded = ProgramBookCodec.encodeProgram(program);
        } catch (IllegalArgumentException exception) {
            handleResult(
                    slot,
                    PrecisionOperationManager.FeedbackType.ERROR,
                    revision,
                    PrecisionGraph.Diagnostic.TOO_LARGE,
                    -1,
                    -1,
                    0
            );
            return;
        }
        PROGRAMS[slot] = program;
        MisakaNetworkClient.send(new PrecisionOperationManager.SavePacket(
                slot,
                expectedRevision,
                encoded
        ));
    }

    static AbilityProgram editableProgram(int slot) {
        slot = Mth.clamp(slot, 0, SLOT_COUNT - 1);
        var existing = PROGRAMS[slot];
        if (existing != null) return existing;
        return emptyProgram(slot);
    }

    static AbilityProgram emptyProgram(int slot) {
        slot = Mth.clamp(slot, 0, SLOT_COUNT - 1);
        var player = Minecraft.getInstance().player;
        var ownerId = player == null ? new UUID(0L, 0L) : player.getUUID();
        var programId = UUID.nameUUIDFromBytes((
                "academy:precision_operation:" + ownerId + ":" + slot
        ).getBytes(StandardCharsets.UTF_8));
        return new AbilityProgram(
                AbilityProgram.CURRENT_SCHEMA_VERSION,
                programId,
                "Precision " + (slot + 1),
                AbilityProgramDefinitions.mentalout().category(),
                ProgramGraph.EMPTY,
                ProgramEditorLayout.EMPTY
        );
    }

    static void closed(Object closed) {
        if (screen == closed) screen = null;
    }

    static void clearDiagnostic(int slot) {
        slot = Mth.clamp(slot, 0, SLOT_COUNT - 1);
        LAST_DIAGNOSTICS[slot] = PrecisionGraph.Diagnostic.OK;
        LAST_NODES[slot] = -1;
        LAST_PORTS[slot] = -1;
    }

    static PrecisionGraph.Diagnostic diagnostic(int slot) {
        return LAST_DIAGNOSTICS[Mth.clamp(slot, 0, SLOT_COUNT - 1)];
    }

    static int diagnosticNode(int slot) {
        return LAST_NODES[Mth.clamp(slot, 0, SLOT_COUNT - 1)];
    }

    static int diagnosticPort(int slot) {
        return LAST_PORTS[Mth.clamp(slot, 0, SLOT_COUNT - 1)];
    }

    private static void showActionBar(String key, int slot) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendOverlayMessage(Component.translatable(key, slot + 1));
    }

    private static void showError(
            int slot,
            PrecisionGraph.Diagnostic diagnostic,
            int nodeId,
            int affectedCount
    ) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var message = Component.translatable(
                "message.academy.precision_operation.feedback.error",
                slot + 1,
                Component.translatable(diagnostic.translationKey())
        );
        if (nodeId >= 0) {
            var programNode = PROGRAMS[slot] == null ? null
                    : PROGRAMS[slot].graph().nodes().stream()
                    .filter(candidate -> candidate.id() == nodeId).findFirst().orElse(null);
            var entry = programNode == null ? null
                    : AbilityProgramDefinitions.mentalout().editorCatalog().entry(programNode.type());
            var label = entry == null ? Component.literal("#" + nodeId)
                    : Component.translatable(entry.translationKey());
            message.append(Component.translatable(
                    "message.academy.precision_operation.feedback.node", label, nodeId));
        }
        if (affectedCount > 1) {
            message.append(Component.translatable(
                    "message.academy.precision_operation.feedback.affected", affectedCount));
        }
        Minecraft.getInstance().gui.chatListener()
                .handleSystemMessage(message.withStyle(ChatFormatting.RED), false);
    }
}
