package org.academy.api.common.structure.upgrade;

import java.util.Map;
import java.util.Objects;

/** Upgrade-service support, separate from the existing live backend's legacy capabilities. */
public record StructureCapabilities(Map<Capability, Support> entries) {
    public StructureCapabilities {
        entries = Map.copyOf(entries);
        if (entries.size() != Capability.values().length) {
            throw new IllegalArgumentException("Every capability requires an explicit support decision");
        }
    }

    public enum Capability {
        EXTRACTION, ORIENTATION, PROVENANCE, GRID_RESTORATION, DAMAGE, IMPACT_FEED, FRACTURE
    }

    public enum Readiness { SCAFFOLD, EXPERIMENTAL, VERIFIED }

    /** Availability still depends on permission, activation, coverage and runtime validation. */
    public record Support(Readiness readiness, boolean enabled, String reason) {
        public Support {
            Objects.requireNonNull(readiness, "readiness");
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank() || reason.length() > 256) throw new IllegalArgumentException("Invalid reason");
            if (readiness == Readiness.SCAFFOLD && enabled) {
                throw new IllegalArgumentException("Scaffold cannot be enabled");
            }
        }
    }
}
