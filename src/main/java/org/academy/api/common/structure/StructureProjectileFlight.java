package org.academy.api.common.structure;

import net.minecraft.server.level.ServerPlayer;
import org.academy.internal.common.structure.upgrade.launch.KineticStructureFlight;

/** Optional, bounded kinetic terrain penetration after an already authorized launch. */
public final class StructureProjectileFlight {
    public static final double MAX_DISTANCE = 64;
    private StructureProjectileFlight() {}

    /** Read-only geometry/capacity admission; the launch caller still owns exclusive motion authority. */
    public static boolean canObserveKinetic(ServerPlayer actor, StructureRef source) {
        return KineticStructureFlight.canObserve(actor, source);
    }

    /**
     * Observes a free body's real movement. It does not teleport, bypass collisions, acquire
     * control or grant offline destruction. Unsupported geometry keeps ordinary physics.
     */
    public static boolean observeKinetic(ServerPlayer actor, StructureRef source, int tier) {
        return KineticStructureFlight.observe(actor, source, tier);
    }

    public static boolean active(net.minecraft.server.MinecraftServer server, StructureRef source) {
        return KineticStructureFlight.active(server, source);
    }
}
