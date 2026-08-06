package org.academy.internal.client.ability.mentalout;

import net.minecraft.client.Minecraft;
import org.academy.api.client.ability.AbilitySystemClient;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.mentalout.MentaloutRequestGuard;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraph;
import org.academy.internal.common.ability.mentalout.precision.PrecisionGraphCodec;
import org.academy.internal.common.ability.mentalout.precision.PrecisionOperationManager;
import org.misaka.MisakaNetworkClient;

public final class PrecisionOperationClient {
    private static final PrecisionGraph[] GRAPHS = new PrecisionGraph[]{
            PrecisionGraph.EMPTY,
            PrecisionGraph.EMPTY,
            PrecisionGraph.EMPTY,
            PrecisionGraph.EMPTY
    };
    private static final PrecisionGraph[] SERVER_GRAPHS = GRAPHS.clone();
    private static long revision;
    private static int selectedSlot;
    private static PrecisionGraph.Diagnostic lastDiagnostic = PrecisionGraph.Diagnostic.OK;
    private static PrecisionOperationScreen screen;

    private PrecisionOperationClient() {
    }

    public static void openEditor() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.screen() != null
                || !AbilitySystemClient.canUseSkill(Skills.PRECISION_OPERATION.get())) return;
        screen = new PrecisionOperationScreen(selectedSlot, GRAPHS[selectedSlot], revision);
        minecraft.gui.setScreen(screen);
        MisakaNetworkClient.send(PrecisionOperationManager.RequestPacket.INSTANCE);
    }

    public static void executeSelected() {
        execute(selectedSlot);
    }

    public static void execute(int slot) {
        if (Minecraft.getInstance().gui.screen() != null
                || !AbilitySystemClient.canUseSkill(Skills.PRECISION_OPERATION.get())) return;
        selectedSlot = Math.clamp(slot, 0, 3);
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
            boolean accepted,
            long serverRevision,
            PrecisionGraph.Diagnostic diagnostic
    ) {
        lastDiagnostic = diagnostic;
        if (serverRevision > revision) revision = serverRevision;
        if (screen != null && Minecraft.getInstance().gui.screen() == screen) {
            screen.applyResult(slot, accepted, revision, diagnostic);
        }
    }

    static PrecisionGraph graph(int slot) {
        return GRAPHS[Math.clamp(slot, 0, 3)];
    }

    static PrecisionGraph serverGraph(int slot) {
        return SERVER_GRAPHS[Math.clamp(slot, 0, 3)];
    }

    static long revision() {
        return revision;
    }

    static void selectSlot(int slot) {
        selectedSlot = Math.clamp(slot, 0, 3);
    }

    static void updateLocal(int slot, PrecisionGraph graph) {
        GRAPHS[Math.clamp(slot, 0, 3)] = graph;
    }

    static void save(int slot, PrecisionGraph graph, long expectedRevision) {
        var validation = graph.validate();
        if (!validation.valid()) {
            lastDiagnostic = validation.diagnostic();
            if (screen != null) screen.applyResult(slot, false, revision, validation.diagnostic());
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
}
