package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Optional-mod-neutral live adapter. Reads and writes require the owning server thread. */
public interface OrientationBackendAccess {
    Optional<Body> observe(ServerLevel host, StructureRef reference);

    /**
     * Motion-only sampling for rest detection. This does not authorize restoration: callers must
     * obtain a fresh full body and repeat admission before changing any content. Backends whose
     * geometry is expensive should override this without loading chunks or scanning cells.
     */
    default Optional<RestState> observeRest(ServerLevel host, StructureRef reference) {
        return observe(host, reference).map(body -> new RestState(body.reference(), body.runtimeId(),
                body.contentRevision(), body.motion().pose(), body.linearVelocity(),
                body.motion().angularVelocity(), body.terrainReady()));
    }

    /** Backend observation uncertainty only; never changes geometry or permits added penetration. */
    default Vec3 positionRounding(Body body) { return Vec3.ZERO; }

    /** All already loaded bodies intersecting the bounds, including non-controllable obstacles. */
    Nearby nearby(ServerLevel host, AABB bounds, int maximumBodies);

    /** Compare the full observed activation/content/pose/motion before correcting angular velocity. */
    boolean apply(ServerLevel host, Body expected, Vec3 angularVelocity);

    /** Translation uses the same exact observation, plus aggregate mass for a real force bound. */
    default Optional<LinearBody> observeLinear(ServerLevel host, StructureRef reference) { return Optional.empty(); }

    /** Correct aggregate COM velocity, preserving angular motion; never change the pose. */
    default boolean applyLinear(ServerLevel host, LinearBody expected, Vec3 linearVelocity) { return false; }

    /** Atomic COM/angular correction for extraction's bounded pose hold. */
    default boolean applyMotion(ServerLevel host, LinearBody expected, Vec3 linearVelocity, Vec3 angularVelocity) {
        return expected.body().motion().angularVelocity().equals(angularVelocity) && applyLinear(host, expected, linearVelocity);
    }

    /** Transfer this exact token from older queued translation commands into planned extraction. */
    default void cancelQueuedTranslation(ServerLevel host, StructureRef reference, UUID controlId) {}

    record RestState(StructureRef reference, UUID runtimeId, long contentRevision, StructurePose pose,
                     Vec3 linearVelocity, Vec3 angularVelocity, boolean terrainReady) {
        public RestState {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(runtimeId, "runtimeId");
            Objects.requireNonNull(pose, "pose");
            OrientationTargets.requireFinite(linearVelocity, "linearVelocity");
            OrientationTargets.requireFinite(angularVelocity, "angularVelocity");
            if (contentRevision < 0) throw new IllegalArgumentException("Invalid live content revision");
        }
    }

    record LinearBody(Body body, double mass) {
        public LinearBody {
            Objects.requireNonNull(body, "body");
            if (!Double.isFinite(mass) || mass <= 0) throw new IllegalArgumentException("Invalid aggregate mass");
        }
    }

    record Body(StructureRef reference, UUID runtimeId, long contentRevision, long poseRevision,
                OrientationController.State motion, Vec3 linearVelocity, Vec3 freeAcceleration, List<AABB> localBoxes,
                OrientationTargets.Baselines baselines, boolean terrainReady) {
        public Body {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(runtimeId, "runtimeId");
            Objects.requireNonNull(motion, "motion");
            Objects.requireNonNull(baselines, "baselines");
            OrientationTargets.requireFinite(linearVelocity, "linearVelocity");
            OrientationTargets.requireFinite(freeAcceleration, "freeAcceleration");
            localBoxes = List.copyOf(localBoxes);
            if (contentRevision < 0 || poseRevision < 0 || localBoxes.isEmpty()
                    || localBoxes.size() > ExtractionPlanner.MAX_LOCAL_BOXES) {
                throw new IllegalArgumentException("Invalid live orientation geometry");
            }
        }

        public boolean sameObservation(Body other) {
            return other != null && reference.equals(other.reference) && runtimeId.equals(other.runtimeId)
                    && contentRevision == other.contentRevision && poseRevision == other.poseRevision
                    && motion.equals(other.motion) && linearVelocity.equals(other.linearVelocity)
                    && freeAcceleration.equals(other.freeAcceleration)
                    && terrainReady == other.terrainReady;
        }

        /**
         * Known free step after already-applied impulses, using velocity-first integration.
         * This includes gravity, not unknown forces queued later by other mods or collision impulses.
         * The solver remains responsible for final contact response.
         */
        public Vec3 freeStepDisplacement(double seconds) {
            requireStep(seconds);
            return linearVelocity.scale(seconds).add(freeAcceleration.scale(seconds * seconds));
        }

        /** Covers the difference from continuous/subdivided gravity integration within this step. */
        public double freeStepMargin(double seconds) {
            requireStep(seconds);
            return 0.5 * freeAcceleration.length() * seconds * seconds;
        }

        private static void requireStep(double seconds) {
            if (!Double.isFinite(seconds) || seconds <= 0 || seconds > OrientationController.MAX_SUBSTEP_SECONDS) {
                throw new IllegalArgumentException("Invalid free motion step");
            }
        }
    }

    record Nearby(List<Body> bodies, boolean complete) {
        public Nearby { bodies = List.copyOf(bodies); }
    }
}
