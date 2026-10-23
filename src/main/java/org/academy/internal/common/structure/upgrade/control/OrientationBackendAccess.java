package org.academy.internal.common.structure.upgrade.control;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Optional-mod-neutral live adapter. Reads and writes require the owning server thread. */
public interface OrientationBackendAccess {
    Optional<Body> observe(ServerLevel host, StructureRef reference);

    /** All already loaded bodies intersecting the bounds, including non-controllable obstacles. */
    Nearby nearby(ServerLevel host, AABB bounds, int maximumBodies);

    /** Compare the full observed activation/content/pose/motion before correcting angular velocity. */
    boolean apply(ServerLevel host, Body expected, Vec3 angularVelocity);

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
