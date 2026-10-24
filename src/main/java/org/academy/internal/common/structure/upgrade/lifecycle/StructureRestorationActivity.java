package org.academy.internal.common.structure.upgrade.lifecycle;

import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.structure.StructureDirectControl;
import org.academy.api.common.structure.StructureRef;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.transaction.StructureCaptureCoordinator;
import org.academy.internal.common.structure.upgrade.control.LiveStructureExtraction;
import org.academy.internal.common.structure.upgrade.control.LiveStructureOrientation;
import org.academy.internal.common.structure.upgrade.control.OrientationBackendAccess;
import org.academy.internal.common.structure.upgrade.control.TranslationSweep;
import org.academy.internal.common.sublevel.SubLevelEnhancementBootstrap;
import org.academy.internal.common.sublevel.SubLevelStructureRefs;
import org.academy.mixin.common.MixinServerPlayerGameModeNativeStructureAccess;

/** Shared final admission for candidate polling and the actual restore transaction. */
public final class StructureRestorationActivity {
    private StructureRestorationActivity() {}

    public static boolean isBusy(ServerLevel host, StructureRef reference) {
        var body = StructureBackendRouter.orientationAccess(host, reference)
                .flatMap(access -> access.observe(host, reference)).orElse(null);
        return body == null || isBusy(host, body);
    }

    public static boolean isBusy(ServerLevel host, OrientationBackendAccess.Body body) {
        var reference = body.reference();
        if (!body.terrainReady() || controlBusy(host, reference, body.runtimeId())) return true;
        return !SubLevelStructureRefs.isSubLevel(reference) && nearbyInteraction(host, body);
    }

    /** Motion sampling must not build full collision geometry for every sleeping candidate. */
    public static boolean controlBusy(ServerLevel host, StructureRef reference, java.util.UUID runtimeId) {
        if (StructureCaptureCoordinator.isPending(host.getServer(), reference)
                || StructureDirectControl.occupied(reference, runtimeId)
                || LiveStructureExtraction.controls(host, reference)
                || LiveStructureOrientation.controls(host, reference)) return true;
        var probe = StructureBackendRouter.probeDirectControl(host, reference);
        if (probe.state() != StructureDirectControl.Probe.State.READY
                || !runtimeId.equals(probe.runtimeId())) return true;
        if (SubLevelStructureRefs.isSubLevel(reference)) {
            return SubLevelEnhancementBootstrap.INTERACTIONS.restorationBusy(host, reference);
        }
        return false;
    }

    private static boolean nearbyInteraction(ServerLevel host, OrientationBackendAccess.Body body) {
        // Sable routes mining through vanilla game mode. Conservatively defer a nearby assembly
        // while an actual mining/menu session is live; no synthetic player or endless read hold.
        if (host.players().isEmpty()) return false;
        var bounds = TranslationSweep.Box.of(body.localBoxes().getFirst(), body.motion().pose()).bounds();
        for (var box : body.localBoxes()) {
            bounds = bounds.minmax(TranslationSweep.Box.of(box, body.motion().pose()).bounds());
        }
        for (var player : host.players()) {
            if (!bounds.inflate(player.blockInteractionRange() + 1).contains(player.getEyePosition())) continue;
            var mining = (MixinServerPlayerGameModeNativeStructureAccess) player.gameMode;
            if (mining.academy$isDestroyingBlock() || mining.academy$hasDelayedDestroy()
                    || player.containerMenu != player.inventoryMenu) return true;
        }
        return false;
    }
}
