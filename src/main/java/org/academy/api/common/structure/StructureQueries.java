package org.academy.api.common.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.StructureBackendRouter;

import java.util.Optional;

/** Server-thread targeting of the selected backend's loaded structures. */
public final class StructureQueries {
    public static final double MAX_RAY_LENGTH = 256.0;

    private StructureQueries() {
    }

    /**
     * Finds the nearest visible block selection shape on a host-world segment, ignoring
     * fluids. Host blocks and nearer structures occlude later hits. Unloaded host data is
     * rejected conservatively; targeting never loads chunks or structures.
     * This data query does not guarantee client presentation; interactive callers must
     * independently verify that the relevant projection is available.
     *
     * @throws IllegalArgumentException if the ray is non-finite, zero length, or exceeds 256 blocks
     * @throws IllegalStateException if called off the owning server thread
     */
    public static Optional<StructureHit> raycast(ServerLevel host, Vec3 worldFrom, Vec3 worldTo) {
        return StructureBackendRouter.raycast(host, worldFrom, worldTo);
    }

    /** Skill targeting may request collision shapes; ordinary interactions still use outlines. */
    public static Optional<StructureHit> raycast(ServerLevel host, Vec3 worldFrom, Vec3 worldTo,
                                                ClipContext.Block shape) {
        return StructureBackendRouter.raycast(host, worldFrom, worldTo, shape);
    }

    /**
     * True only when the entire segment is loaded, supported and clear of host and structure
     * selection shapes. An absent raycast hit alone does not establish this: incompatible
     * backends, unsupported structures, exhausted query budgets and stale observations fail closed.
     * Ordinary entity targeting should check this before applying an effect behind a structure.
     */
    public static boolean clearSightline(ServerLevel host, Vec3 worldFrom, Vec3 worldTo) {
        return StructureBackendRouter.clearSightline(host, worldFrom, worldTo);
    }

    /**
     * Recomputes targeting from a trusted server-owned view segment. Callers must derive its
     * endpoints and permitted reach from their actor or program, never from a packet's claimed
     * hit. Identity, runtime lifetime, geometry, pose, range and visibility are rechecked.
     * Success contains the new server observation rather than the supplied value. Permission
     * and resource checks for the eventual action remain the caller's responsibility.
     */
    public static StructureHitValidation validate(ServerLevel host, Vec3 trustedWorldFrom,
                                                  Vec3 trustedWorldTo, StructureHit proposed) {
        return StructureBackendRouter.validate(host, trustedWorldFrom, trustedWorldTo, proposed);
    }
}
