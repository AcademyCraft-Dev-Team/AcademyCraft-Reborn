package org.academy.api.common.structure;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.academy.internal.common.structure.StructureBackendRouter;

import java.util.Optional;
import java.util.Objects;

/** Read-only observations of a complete, already simulated body in the selected backend. */
public final class StructureDynamics {
    private StructureDynamics() {}

    /**
     * Returns the whole body's actual solver mass, center of mass and motion, regardless of
     * which cell was targeted. Never creates a body, loads a structure or activates a solver.
     * Missing, incompatible and unsimulated structures return empty. A local body held for
     * missing terrain coverage remains observable with zero velocities; its saved resumption
     * velocity is internal to the solver. Paused regions may instead be unavailable.
     * This observation is not a control capability; subsequent actions must revalidate their
     * exact activation.
     */
    public static Optional<State> motion(ServerLevel host, StructureRef reference) {
        return StructureBackendRouter.motion(host, reference);
    }

    /** Backend-neutral motion; does not invent collision epochs or sleep state for foreign solvers. */
    public record State(StructurePose pose, Vec3 linearVelocity, Vec3 angularVelocity,
                        Vec3 localCenterOfMass, double mass) {
        public State {
            Objects.requireNonNull(pose, "pose");
            finite(linearVelocity); finite(angularVelocity); finite(localCenterOfMass);
            if (!Double.isFinite(mass) || mass <= 0) throw new IllegalArgumentException("Invalid body mass");
        }

        public static State from(StructureMotionState motion) {
            return new State(motion.pose(), motion.linearVelocity(), motion.angularVelocity(),
                    motion.localCenterOfMass(), motion.mass());
        }

        private static void finite(Vec3 vector) {
            Objects.requireNonNull(vector, "vector");
            if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
                throw new IllegalArgumentException("Invalid body vector");
            }
        }
    }
}
