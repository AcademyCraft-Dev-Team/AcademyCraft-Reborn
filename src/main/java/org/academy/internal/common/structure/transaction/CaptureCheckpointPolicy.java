package org.academy.internal.common.structure.transaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure policy behind the backend checkpoint retry. A Sable checkpoint can fail because its own
 * removal sweep left an already-removed or zero-mass child inside the container; those invalid
 * children are pruned, the capture target itself is never touched, and the checkpoint is retried
 * only when at least one invalid child was actually removed.
 */
public final class CaptureCheckpointPolicy {
    private CaptureCheckpointPolicy() {}

    /** One container child as observed by the checkpoint adapter. */
    public record Body(UUID id, boolean removed, boolean massValid) {
        public Body {
            Objects.requireNonNull(id, "id");
        }
    }

    /** Invalid children eligible for pruning, excluding the protected capture target. */
    public static List<UUID> invalidBodies(UUID protectedTarget, List<Body> bodies) {
        Objects.requireNonNull(protectedTarget, "protectedTarget");
        Objects.requireNonNull(bodies, "bodies");
        var pruned = new ArrayList<UUID>();
        var seen = new HashSet<UUID>();
        for (var body : bodies) {
            if (body.id().equals(protectedTarget) || !seen.add(body.id())) continue;
            if (body.removed() || !body.massValid()) pruned.add(body.id());
        }
        return List.copyOf(pruned);
    }
}
