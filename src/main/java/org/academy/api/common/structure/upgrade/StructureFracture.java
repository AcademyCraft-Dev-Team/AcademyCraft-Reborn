package org.academy.api.common.structure.upgrade;

import net.minecraft.core.BlockPos;
import org.academy.api.common.structure.StructureRef;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Ownership-first fracture. Planning/reservation never consumes the source or produces loot. */
public interface StructureFracture {
    StructureOperation.Result<Plan> plan(StructureImpactFeed.LaunchAccess access, Request request);
    StructureOperation.Result<Reservation> reserve(StructureImpactFeed.LaunchAccess access, Plan plan);
    /** Publish children only after durable ownership transfer; stale reservations fail without mutation. */
    StructureOperation.Result<Receipt> commit(StructureImpactFeed.LaunchAccess access, Reservation reservation);

    /** Authorize against the persisted launch/operation owner before exposing state; source may be retired. */
    StructureOperation.Result<Progress> status(StructureImpactFeed.LaunchAuthority authority, UUID operationId);

    /**
     * Releases pre-commit reservations after verified rollback, if needed. After ownership commit,
     * abort must not resurrect the parent or remove published children; continue idempotent cleanup.
     */
    StructureOperation.Result<Progress> abort(StructureImpactFeed.LaunchAuthority authority, UUID operationId);

    enum Phase { PLANNING, READY, RESERVING, RESERVED, COMMITTING, CLEANING_SOURCE, COMPLETED, ABORTED, RECOVERY_REQUIRED }

    record Request(UUID launchId, StructureImpactFeed.Contact contact, double energy, int maxFragments) {
        public Request {
            Objects.requireNonNull(launchId, "launchId");
            Objects.requireNonNull(contact, "contact");
            if (!Double.isFinite(energy) || energy < 0 || energy > 1.0e12 || maxFragments < 1 || maxFragments > 64) {
                throw new IllegalArgumentException("Invalid fracture budget");
            }
        }
    }

    record Fragment(List<BlockPos> cells) {
        public Fragment {
            cells = cells.stream().map(BlockPos::immutable).toList();
            if (cells.isEmpty() || cells.size() > 65_536 || new HashSet<>(cells).size() != cells.size()) {
                throw new IllegalArgumentException("Invalid fragment cells");
            }
        }
    }

    /** The coordinator also proves complete source coverage and retains unsupported content. */
    record Plan(UUID planId, UUID launchId, List<Fragment> fragments) {
        public Plan {
            Objects.requireNonNull(planId, "planId");
            Objects.requireNonNull(launchId, "launchId");
            fragments = List.copyOf(fragments);
            if (fragments.isEmpty() || fragments.size() > 64) throw new IllegalArgumentException("Invalid fragment count");
            var owned = new HashSet<BlockPos>();
            for (var fragment : fragments) for (var cell : fragment.cells()) {
                if (!owned.add(cell) || owned.size() > 65_536) throw new IllegalArgumentException("Duplicate/oversized ownership");
            }
        }
    }

    /** Opaque server-issued identity, not permission to commit an arbitrary caller-created plan. */
    record Reservation(UUID operationId, UUID planId) {
        public Reservation {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(planId, "planId");
        }
    }

    record Receipt(List<StructureRef> children) {
        public Receipt { children = List.copyOf(children); }
    }

    record Progress(UUID operationId, UUID planId, Phase phase, Optional<Plan> preparedPlan,
                    Optional<Reservation> reservation, Optional<Receipt> receipt) {
        public Progress {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(planId, "planId");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(preparedPlan, "preparedPlan");
            Objects.requireNonNull(reservation, "reservation");
            Objects.requireNonNull(receipt, "receipt");
            if (phase == Phase.READY && preparedPlan.isEmpty()) {
                throw new IllegalArgumentException("Ready fracture must expose its prepared plan");
            }
            if (phase == Phase.RESERVED && reservation.isEmpty()) {
                throw new IllegalArgumentException("Reserved fracture must expose its reservation");
            }
            if (phase == Phase.COMPLETED && receipt.isEmpty()) {
                throw new IllegalArgumentException("Completed fracture must expose its ownership receipt");
            }
            if (reservation.filter(value -> !value.operationId().equals(operationId)
                    || !value.planId().equals(planId)).isPresent()) {
                throw new IllegalArgumentException("Fracture progress cannot expose another operation's reservation");
            }
            if (preparedPlan.filter(value -> !value.planId().equals(planId)).isPresent()) {
                throw new IllegalArgumentException("Fracture progress cannot expose another plan");
            }
        }
    }
}
