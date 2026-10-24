package org.academy.api.common.structure.upgrade;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.SpaceBlockRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shared damage query/mutation port. All positions are host-world or fixed structure-local. */
public interface StructureDamage {
    /**
     * Runtime skill actors and previously authorized launches use the same mutation pipeline.
     * A launched claim must match the server's persistent launch ledger, owner, policy and
     * authorization expiry before every query, mutation or status disclosure. Missing/offline
     * owners do not bypass protection; unknown authorization fails closed without a fake entity.
     */
    sealed interface Authority permits ActiveActor, Launched {}

    record ActiveActor(StructureOperation.ActorContext context) implements Authority {
        public ActiveActor { Objects.requireNonNull(context, "context"); }
    }

    record Launched(StructureImpactFeed.LaunchAuthority authority) implements Authority {
        public Launched { Objects.requireNonNull(authority, "authority"); }
    }

    /** Query actual content selection shapes, including plants with empty collision shapes. */
    StructureOperation.Result<Batch> query(Authority authority, Request request);

    /**
     * Revalidate each target and protection after callbacks. Deduplicate by effect + stable cell,
     * not revision. BREAK uses the existing global drop path; TRANSFER never emits loot.
     * Every mutation invalidates old observations immediately; costly rebuilds may be coalesced.
     * The server-owned plan must be bound to its host and exact active actor or launch authority;
     * constructing a Batch cannot transfer that authority to another caller.
     */
    StructureOperation.Result<Receipt> apply(Authority authority, Batch batch);

    /** Authorize the persisted task owner before exposing a plan or its mutation progress. */
    StructureOperation.Result<Progress> status(Authority authority, UUID operationId);

    /** Stops unprocessed cells only; already executed drops and block callbacks cannot be rolled back. */
    StructureOperation.Result<Progress> cancel(Authority authority, UUID operationId);

    enum DropPolicy { EXISTING_SKILL_RULES, NO_DROPS }
    enum Phase { QUERYING, READY, APPLYING, COMPLETED, CANCELLED, RECOVERY_REQUIRED }

    record Request(UUID effectId, Vec3 center, double radius, double energy,
                   int maxScans, int maxChanges, DropPolicy drops) {
        public Request {
            Objects.requireNonNull(effectId, "effectId");
            Objects.requireNonNull(center, "center");
            Objects.requireNonNull(drops, "drops");
            if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)
                    || !Double.isFinite(radius) || radius < 0 || radius > 256
                    || !Double.isFinite(energy) || energy < 0 || energy > 1.0e12
                    || maxScans < 1 || maxScans > 65_536 || maxChanges < 1 || maxChanges > maxScans) {
                throw new IllegalArgumentException("Invalid bounded damage query");
            }
        }
    }

    /** Exactly one address; worldPosition is never a plot/storage coordinate. */
    record Cell(Optional<BlockPos> worldPosition, Optional<SpaceBlockRef> structurePosition,
                long expectedRevision, String expectedStateDigest) {
        public Cell {
            worldPosition = Objects.requireNonNull(worldPosition, "worldPosition").map(BlockPos::immutable);
            Objects.requireNonNull(structurePosition, "structurePosition");
            Objects.requireNonNull(expectedStateDigest, "expectedStateDigest");
            if (worldPosition.isPresent() == structurePosition.isPresent() || expectedRevision < 0
                    || expectedStateDigest.isBlank() || expectedStateDigest.length() > 128) {
                throw new IllegalArgumentException("Invalid cell identity");
            }
        }
    }

    /** Server-owned single-use plan; callers cannot confer authority by constructing this record. */
    record Batch(UUID planId, Request request, List<Cell> cells) {
        public Batch {
            Objects.requireNonNull(planId, "planId");
            Objects.requireNonNull(request, "request");
            cells = List.copyOf(cells);
            if (cells.size() > request.maxChanges()) throw new IllegalArgumentException("Damage batch exceeds budget");
        }
    }

    record Receipt(int broken, int rejected, int deferred) {
        public Receipt {
            if (broken < 0 || rejected < 0 || deferred < 0) throw new IllegalArgumentException("Negative count");
        }
    }

    /** A completed asynchronous query exposes its authenticated plan; mutation progress exposes counts. */
    record Progress(UUID operationId, UUID effectId, Phase phase, Receipt receipt, Optional<Batch> preparedBatch) {
        public Progress {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(effectId, "effectId");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(receipt, "receipt");
            Objects.requireNonNull(preparedBatch, "preparedBatch");
            if (phase == Phase.READY && preparedBatch.isEmpty()) {
                throw new IllegalArgumentException("Ready damage query requires its prepared batch");
            }
            if (preparedBatch.filter(batch -> !batch.request().effectId().equals(effectId)).isPresent()) {
                throw new IllegalArgumentException("Damage progress cannot expose another effect's plan");
            }
        }
    }
}
