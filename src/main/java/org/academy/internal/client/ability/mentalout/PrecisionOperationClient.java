package org.academy.internal.client.ability.mentalout;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraphCodec;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationManager;
import org.misaka.MisakaNetworkClient;

import java.util.Locale;
import net.minecraft.util.Mth;

public final class PrecisionOperationClient {
    private static final PrecisionGraph[] GRAPHS = new PrecisionGraph[]{
            PrecisionGraph.EMPTY,
            PrecisionGraph.EMPTY,
            PrecisionGraph.EMPTY,
            PrecisionGraph.EMPTY
    };
    private static final PrecisionGraph[] SERVER_GRAPHS = GRAPHS.clone();
    private static final PrecisionGraph.Diagnostic[] LAST_DIAGNOSTICS = {
            PrecisionGraph.Diagnostic.OK,
            PrecisionGraph.Diagnostic.OK,
            PrecisionGraph.Diagnostic.OK,
            PrecisionGraph.Diagnostic.OK
    };
    private static final int[] LAST_NODES = {-1, -1, -1, -1};
    private static final int[] LAST_PORTS = {-1, -1, -1, -1};
    private static final boolean[] ACTIVE_SLOTS = new boolean[4];
    private static final boolean[] ACTIVE_FAILURES = new boolean[4];
    private static long revision;
    private static int selectedSlot;
    private static PrecisionOperationScreen screen;

    private PrecisionOperationClient() {
    }

    public static void openEditor() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null
                || !AbilitySystemClient.canUseSkill(Skills.PRECISION_OPERATION.get())) return;
        screen = new PrecisionOperationScreen(selectedSlot, GRAPHS[selectedSlot], revision);
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
                || !AbilitySystemClient.canUseSkill(Skills.PRECISION_OPERATION.get())) return;
        selectedSlot = Mth.clamp(slot, 0, 3);
        MisakaNetworkClient.send(new PrecisionOperationManager.ExecutePacket(
                selectedSlot,
                MentaloutRequestGuard.nextClientSequence()
        ));
    }

    public static void handleSync(long serverRevision, byte[][] encoded) {
        if (serverRevision < revision || encoded == null || encoded.length != 4) return;
        var decoded = new PrecisionGraph[4];
        for (var slot = 0; slot < 4; slot++) {
            var result = PrecisionGraphCodec.decode(encoded[slot]);
            if (!result.valid()) return;
            decoded[slot] = result.graph();
        }
        revision = serverRevision;
        for (var slot = 0; slot < 4; slot++) {
            GRAPHS[slot] = decoded[slot];
            SERVER_GRAPHS[slot] = decoded[slot];
        }
        if (screen != null && Minecraft.getInstance().gui.screen() == screen) {
            screen.applyServerState(selectedSlot, GRAPHS[selectedSlot], revision);
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
        slot = Mth.clamp(slot, 0, 3);
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

    static PrecisionGraph graph(int slot) {
        return GRAPHS[Mth.clamp(slot, 0, 3)];
    }

    static PrecisionGraph serverGraph(int slot) {
        return SERVER_GRAPHS[Mth.clamp(slot, 0, 3)];
    }

    static long revision() {
        return revision;
    }

    static void selectSlot(int slot) {
        selectedSlot = Mth.clamp(slot, 0, 3);
    }

    static void updateLocal(int slot, PrecisionGraph graph) {
        GRAPHS[Mth.clamp(slot, 0, 3)] = graph;
    }

    static void save(int slot, PrecisionGraph graph, long expectedRevision) {
        var validation = graph.validate();
        if (!validation.valid()) {
            handleResult(
                    slot,
                    PrecisionOperationManager.FeedbackType.ERROR,
                    revision,
                    validation.diagnostic(),
                    validation.nodeId(),
                    validation.port(),
                    0
            );
            return;
        }
        updateLocal(slot, validation.normalized());
        MisakaNetworkClient.send(new PrecisionOperationManager.SavePacket(
                slot,
                expectedRevision,
                PrecisionGraphCodec.encode(validation.normalized())
        ));
    }

    static void closed(PrecisionOperationScreen closed) {
        if (screen == closed) screen = null;
    }

    static void clearDiagnostic(int slot) {
        slot = Mth.clamp(slot, 0, 3);
        LAST_DIAGNOSTICS[slot] = PrecisionGraph.Diagnostic.OK;
        LAST_NODES[slot] = -1;
        LAST_PORTS[slot] = -1;
    }

    static PrecisionGraph.Diagnostic diagnostic(int slot) {
        return LAST_DIAGNOSTICS[Mth.clamp(slot, 0, 3)];
    }

    static int diagnosticNode(int slot) {
        return LAST_NODES[Mth.clamp(slot, 0, 3)];
    }

    static int diagnosticPort(int slot) {
        return LAST_PORTS[Mth.clamp(slot, 0, 3)];
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
            var node = GRAPHS[slot].nodes().stream()
                    .filter(candidate -> candidate.id() == nodeId)
                    .findFirst()
                    .orElse(null);
            var label = node == null
                    ? Component.literal("#" + nodeId)
                    : Component.translatable("screen.academy.precision_operation.node."
                    + node.kind().name().toLowerCase(Locale.ROOT));
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
