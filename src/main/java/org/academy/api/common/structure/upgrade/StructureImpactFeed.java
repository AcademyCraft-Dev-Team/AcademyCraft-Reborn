package org.academy.api.common.structure.upgrade;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded step-local facts; callbacks must not mutate Minecraft or re-enter the solver. */
public interface StructureImpactFeed {
    /**
     * Immediate bounded read, not a new asynchronous operation. A gap/overflow invalidates
     * first-impact ordering; it must never return a fake empty success. Verify access.authority.launchId
     * matches the cursor and authorize the launch ledger before exposing facts.
     */
    StructureOperation.Result<Batch> drain(LaunchAccess access, Cursor cursor);

    /**
     * Declared persisted launch attribution, never a bearer token. Providers must match the server's
     * launch ledger, owner and authorization-expiry policy; offline/absent actors cannot bypass
     * protection. The UUID alone does not permit reading impacts or changing a structure.
     */
    record LaunchAuthority(ServerLevel host, UUID launchId, Optional<UUID> ownerId, Identifier policyId) {
        public LaunchAuthority {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(launchId, "launchId");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(policyId, "policyId");
        }
    }

    /** Current activation binding; durable operation lookup uses LaunchAuthority without old runtime ids. */
    record LaunchAccess(LaunchAuthority authority, StructureRef reference, UUID runtimeId,
                        long expectedContentRevision) {
        public LaunchAccess {
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(runtimeId, "runtimeId");
            if (expectedContentRevision < 0) throw new IllegalArgumentException("Negative launch content revision");
        }
    }

    /** Opaque event cursor, so a bounded page can resume inside one physics step without losing contacts. */
    record Cursor(UUID launchId, long afterCursor, int maxReports) {
        public Cursor {
            Objects.requireNonNull(launchId, "launchId");
            if (afterCursor < 0 || maxReports < 1 || maxReports > 4096) throw new IllegalArgumentException("Invalid cursor");
        }
    }

    enum FirstImpactState { ARMED, CONSUMED, IMPACT_UNKNOWN, SETTLING }
    enum TimingPrecision { STEP_FRACTION, SUBSTEP_ONLY, STEP_ONLY }

    /**
     * counterparty is a stable backend-scoped identity, never a native pointer. timeFraction is
     * measured within the complete physics step and is usable only for STEP_FRACTION reports.
     * Coarse reports cannot break first-impact ties by callback/list order; the caller must apply
     * its explicit reduced-precision policy. A provider must not invent fine timing from substep ids.
     */
    record Contact(StructureRef structure, UUID runtimeId, long geometryRevision,
                   long step, int substep, double timeFraction, TimingPrecision timingPrecision,
                   String counterparty, Vec3 worldPoint, Vec3 worldNormal,
                   double closingSpeed, double normalImpulse) {
        public Contact {
            Objects.requireNonNull(structure, "structure");
            Objects.requireNonNull(runtimeId, "runtimeId");
            Objects.requireNonNull(timingPrecision, "timingPrecision");
            Objects.requireNonNull(counterparty, "counterparty");
            finite(worldPoint); finite(worldNormal);
            if (geometryRevision < 0 || step < 0 || substep < 0
                    || !Double.isFinite(timeFraction) || timeFraction < 0 || timeFraction > 1
                    || counterparty.isBlank() || counterparty.length() > 256
                    || Math.abs(worldNormal.lengthSqr() - 1.0) > 1.0e-6
                    || !Double.isFinite(closingSpeed) || closingSpeed < 0
                    || !Double.isFinite(normalImpulse) || normalImpulse < 0) {
                throw new IllegalArgumentException("Invalid contact report");
            }
        }

        private static void finite(Vec3 value) {
            Objects.requireNonNull(value, "vector");
            if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
                throw new IllegalArgumentException("Non-finite contact vector");
            }
        }
    }

    /** nextCursor acknowledges only this page; throughStep may be -1 before any physics step. */
    record Batch(long nextCursor, long throughStep, boolean overflow, List<Contact> reports) {
        public Batch {
            reports = List.copyOf(reports);
            if (nextCursor < 0 || throughStep < -1 || reports.size() > 4096
                    || reports.stream().anyMatch(report -> report.step() > throughStep)) {
                throw new IllegalArgumentException("Invalid contact batch");
            }
        }
    }
}
