package org.academy.api.common.structure;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * A bounded, server-thread-owned physics region in one host level. Every method, including
 * reads and close, requires its owning server thread. This interface exposes no raw native
 * handles, storage levels, player dependence or backend memory.
 *
 * <p>Ordinary structure saving checkpoints attachments, bounds and the last coherently published
 * motion together with the pose. Server shutdown releases native resources while retaining these
 * associations; the next session requires an explicit {@link StructurePhysics#resumeRegion}.
 * Runtime identities and solver contact caches are never restored. Saved sleep is an observation;
 * reconstruction wakes bodies. An attachment with no collision body resumes from zero velocity.</p>
 *
 * <p>Attached structures are controlled by the solver: {@link StructureHandle#setPose}
 * rejects them. Detach before explicitly changing their pose. Closing this region detaches
 * its structures at their last published poses, forgets their saved associations and releases its native resources; it neither
 * deletes stored blocks nor unloads their structure containers.</p>
 *
 * <p>Unloading a member preserves its checkpoint and pauses this region's live cohort until an explicit
 * structure load makes it available again. No host chunk is force-loaded. On restoration, a
 * changed collision digest pauses the entire region rather than applying old velocity to new
 * geometry. Online geometry changes retain the existing rigid velocity field at the new COM.
 * Other regions in the same host continue independently. Distinct regions cannot overlap or
 * touch: they have a one-block separation and no cross-region dynamic contacts or automatic migration.</p>
 *
 * <p>This prototype does not implement entity carrying, skill migration or Sable interoperability.
 * Physics pause is separate from storage-level
 * block/entity ticking and must not be interpreted as freezing all local world logic.</p>
 */
public interface StructurePhysicsRegion extends AutoCloseable {
    enum Status {
        READY,
        PAUSED,
        FAILED,
        CLOSED
    }

    /** Stable saved region identity; a recreated region has a new solver session but retains this ID. */
    UUID id();

    /** Fixed host-coordinate simulation bounds; queries do not expand or load the region. */
    AABB bounds();

    Status status();

    /** Human-readable state detail, not a stable identifier for programmatic branching. */
    String diagnostic();

    /**
     * Attaches this exact active runtime handle, never an implicitly reloaded replacement.
     * The structure must belong to this host and independent backend and fit the region's
     * audited geometry/resource limits. Geometry is checked on the next server tick;
     * unsupported or unavailable geometry pauses the region without stepping stale colliders.
     * This operation does not transfer block ownership or consume an actor's inventory.
     */
    void attach(StructureHandle handle);

    /**
     * Removes a current attachment and its saved motion without changing stored content or the published pose.
     * Returns false when the exact reference is not attached; never resolves another generation.
     */
    boolean detach(StructureRef reference);

    /**
     * Returns the attachment's last published coherent snapshot. Empty means no attachment,
     * no successful step yet, or a current published geometry with no collision body.
     */
    Optional<StructureMotionState> motion(StructureRef reference);

    /**
     * Requests world-space center-of-mass velocity in blocks/second and world-space angular
     * velocity in radians/second. Non-finite or out-of-budget values, stale references and
     * unavailable simulation states are rejected rather than queued across a pause.
     */
    void setVelocity(StructureRef reference, Vec3 linear, Vec3 angular);

    /**
     * Applies a finite world-space impulse at a finite host-world point. Impulse uses the
     * solver's mass units times blocks/second; it is not a force applied repeatedly each tick.
     * Invalid inputs, stale references and unavailable simulation states are rejected.
     */
    void applyImpulse(StructureRef reference, Vec3 impulse, Vec3 worldPoint);

    /**
     * Replaces this exact attachment's one transient control slot. A Force acts over the next
     * successful 0.05-second physics tick only; an Anchor remains until cleared or invalidated.
     * Repeated calls replace rather than accumulate. Both use the solver's mass units and seconds.
     * The local point must be inside the current collision AABB (it need not name a solid cell);
     * an anchor target must be inside this region and the host world border. No chunks are loaded.
     *
     * <p>Requires the same current, unpaused geometry as immediate controls. Invalid requests
     * leave the old control unchanged. Region pause, attachment/geometry invalidation, unloading,
     * failure, close and shutdown clear controls; they are never saved or replayed on resume.
     * Successful setVelocity/applyImpulse also clear the affected attachment's control.
     * Terrain-only replacement can preserve a control if the region never pauses.</p>
     *
     * <p>This trusted server API does not grant player reach, ownership or protection permission.
     * One anchor controls a point through bounded force, not orientation or a collision-free path.</p>
     */
    void setControl(StructureRef reference, StructurePhysicsControl control);

    /** Last accepted transient configuration, not permission or a promise that the next step can run. */
    Optional<StructurePhysicsControl> control(StructureRef reference);

    /** Clears the exact attachment's control, including while paused; does not change velocity. */
    boolean clearControl(StructureRef reference);

    /** Idempotent after successful release; a failed native release remains an explicit error. */
    @Override
    void close();
}
