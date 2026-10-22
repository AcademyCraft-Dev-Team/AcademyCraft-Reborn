package org.academy.api.common.structure.upgrade;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persistent creation metadata, committed by the capture/fragment transaction, never by this view. */
public interface StructureProvenance {
    int MAX_POLICY_PARAMETERS = 16;
    int MAX_PARAMETER_NAME_LENGTH = 64;
    double MAX_PARAMETER_MAGNITUDE = 1_000_000.0;

    /** An empty value may mean unmarked only after the provider has successfully read its ledger. */
    StructureOperation.Result<Metadata> query(StructureOperation.Context context);

    /**
     * Validates a proposed metadata image for an owning transaction. READY is preparation only:
     * it does not mark a structure, save a ledger, or grant permission to change world content.
     * The owning transaction must recheck runtime/content identity and durably commit the image.
     */
    StructureOperation.Result<Preparation> prepare(StructureOperation.Context context, Metadata metadata);

    enum CreationKind {
        SKILL_CAPTURE,
        SKILL_FRAGMENT
    }

    /** Immutable unit quaternion of the source block grid, with no storage/world translation. */
    record GridRotation(double x, double y, double z, double w) {
        public GridRotation {
            var normalized = new StructurePose(Vec3.ZERO, x, y, z, w);
            x = normalized.rotationX();
            y = normalized.rotationY();
            z = normalized.rotationZ();
            w = normalized.rotationW();
        }
    }

    /** Unknown policy ids/versions must retain content; parameters are data, never executable rules. */
    record RestorePolicy(Identifier id, int version, Map<String, Double> parameters) {
        public RestorePolicy {
            Objects.requireNonNull(id, "id");
            if (version < 1) throw new IllegalArgumentException("policy version must be positive");
            Objects.requireNonNull(parameters, "parameters");
            if (parameters.size() > MAX_POLICY_PARAMETERS) {
                throw new IllegalArgumentException("too many restore policy parameters");
            }
            for (var parameter : parameters.entrySet()) {
                var name = Objects.requireNonNull(parameter.getKey(), "parameter name");
                var value = Objects.requireNonNull(parameter.getValue(), "parameter value");
                if (name.isBlank() || name.length() > MAX_PARAMETER_NAME_LENGTH) {
                    throw new IllegalArgumentException("invalid restore policy parameter name");
                }
                if (!Double.isFinite(value) || Math.abs(value) > MAX_PARAMETER_MAGNITUDE) {
                    throw new IllegalArgumentException("restore policy parameter must be finite and bounded");
                }
            }
            parameters = Map.copyOf(parameters);
        }
    }

    /**
     * Complete immutable image for a marked creation. Missing old-save metadata remains unmarked.
     * Owner may identify a player or another entity; it is attribution, not a permission grant.
     * A fragment inherits lineage, policy and source-grid rotation, but never a launch reward.
     */
    record Metadata(CreationKind source, UUID creationOperationId, UUID rootLineageId,
                    Optional<StructureRef> parent, Optional<UUID> ownerId, Identifier skillId,
                    GridRotation sourceGridRotation, RestorePolicy restorePolicy, long createdGameTime) {
        public Metadata {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(creationOperationId, "creationOperationId");
            Objects.requireNonNull(rootLineageId, "rootLineageId");
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(skillId, "skillId");
            Objects.requireNonNull(sourceGridRotation, "sourceGridRotation");
            Objects.requireNonNull(restorePolicy, "restorePolicy");
            if (createdGameTime < 0) throw new IllegalArgumentException("createdGameTime cannot be negative");
            if (source == CreationKind.SKILL_CAPTURE && parent.isPresent()) {
                throw new IllegalArgumentException("a new skill capture cannot have a fragment parent");
            }
            if (source == CreationKind.SKILL_FRAGMENT && parent.isEmpty()) {
                throw new IllegalArgumentException("a skill fragment requires its parent identity");
            }
        }
    }

    record Preparation(StructureRef reference, UUID runtimeId, long contentRevision, Metadata metadata) {
        public Preparation {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(runtimeId, "runtimeId");
            Objects.requireNonNull(metadata, "metadata");
            if (contentRevision < 0) throw new IllegalArgumentException("contentRevision cannot be negative");
            if (metadata.parent().filter(reference::equals).isPresent()) {
                throw new IllegalArgumentException("a structure cannot be its own fragment parent");
            }
        }
    }
}
