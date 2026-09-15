package org.academy.api.server.ability.electromaster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.electromaster.LevitationTuning;
import org.academy.api.common.ability.electromaster.MagneticMovement;
import org.jspecify.annotations.Nullable;

/**
 * Collision-preserving flight solver. Inputs can come from player controls, AI or a program.
 *
 * <p>Keeps the mover inside the magnetic field and at a sane height above whatever is underfoot. Field
 * membership and terrain-following height are deliberately answered the same way: the ground under the
 * mover's footprint is itself valid support, found by a short downward column probe, and it is the only
 * input to altitude. Any-direction support (walls, ceilings, free-standing blocks, and the case of hovering
 * beside a wall over a void) is consulted in addition, but never as the height reference.</p>
 *
 * <p>Boundary enforcement asks whether the <em>destination</em> is in the field, using that same column
 * probe. Judging the step against a remembered block instead is what made the field feel sticky: a block's
 * distance grows merely because the mover travelled away from it, so horizontal motion got truncated over
 * perfectly good terrain. Only when the destination has neither ground nor a still-reaching neighbour does
 * the step get reduced, and then per component, so tangential motion survives rather than being
 * swallowed.</p>
 */
public final class MagneticLevitation {
    /** The ground reference must stay this far inside the field, so admission never flickers at the rim. */
    private static final double CLEARANCE_REACH_MARGIN = 1.0;

    private final MagneticSupportQuery envelopeQuery;
    private final MagneticSupportQuery groundQuery;
    private @Nullable SupportReference envelope;
    private @Nullable SupportReference ground;
    private double clearance;

    public MagneticLevitation() {
        this(LevitationTuning.DEFAULT);
    }

    public MagneticLevitation(LevitationTuning tuning) {
        var limits = tuning == null ? LevitationTuning.DEFAULT : tuning;
        envelopeQuery = new MagneticSupportQuery(limits.maxSupportSamples());
        groundQuery = new MagneticSupportQuery(limits.maxSupportSamples());
    }

    /** One tick of solved motion: field presence, ground presence, velocity, clearance and clamp state. */
    public record Step(boolean supported, boolean hasGround, Vec3 velocity, double clearance, boolean clamped) {
    }

    public boolean supported(LivingEntity subject, double radius) {
        if (!(subject.level() instanceof ServerLevel level)) return false;
        resolve(level, subject, radius);
        return ground != null || envelope != null;
    }

    public Vec3 velocity(LivingEntity subject, double forward, double strafe, double vertical,
                         double speed, double radius) {
        return velocity(subject, forward, strafe, vertical, speed, radius, LevitationTuning.DEFAULT);
    }

    public Vec3 velocity(LivingEntity subject, double forward, double strafe, double vertical,
                         double speed, double radius, LevitationTuning tuning) {
        return step(subject, forward, strafe, vertical, speed, radius, tuning).velocity();
    }

    public Step step(LivingEntity subject, double forward, double strafe, double vertical,
                     double speed, double radius) {
        return step(subject, forward, strafe, vertical, speed, radius, LevitationTuning.DEFAULT);
    }

    /** Refreshes the field references once, then solves horizontal and vertical motion. */
    public Step step(LivingEntity subject, double forward, double strafe, double vertical,
                     double speed, double radius, LevitationTuning tuning) {
        var limits = tuning == null ? LevitationTuning.DEFAULT : tuning;
        if (!(subject.level() instanceof ServerLevel level)) {
            envelope = null;
            ground = null;
            return new Step(false, false, Vec3.ZERO, clearance, false);
        }
        resolve(level, subject, radius);
        if (ground == null && envelope == null) {
            return new Step(false, false, Vec3.ZERO, clearance, false);
        }

        var intent = Double.isFinite(vertical) ? vertical : 0.0;
        var horizontal = MagneticMovement.flightDirection(subject.getYRot(), forward, strafe, speed);
        var smoothed = MagneticMovement.approachHorizontal(subject.getDeltaMovement(), horizontal);
        // Altitude is capped by the field reach minus a margin: an unbounded target would leave the mover
        // asking for clearance the field cannot provide, climbing into a permanent clamp instead of holding
        // a steady ceiling, and would let the ground reference slip out of range and flicker admission.
        var climb = ground != null
                ? MagneticMovement.hoverVertical(intent, clearance, reachableClearance(radius), limits)
                : Math.clamp(intent, -1.0, 1.0) * limits.inputVerticalSpeed();
        var desired = new Vec3(smoothed.x, climb, smoothed.z);
        var solved = clamp(level, subject.getBoundingBox(), desired, radius, limits.maxSupportSamples());
        // Identity, not value equality: clamp returns the same instance when the step is accepted whole,
        // so rounding can never make an accepted step look modified.
        return new Step(true, ground != null, solved, clearance, solved != desired);
    }

    /**
     * Bounded sink with reduced horizontal authority, used while a lost field is still recoverable. The
     * mover keeps steering and full climb authority, so it can get back inside instead of only dropping.
     */
    public Vec3 degradedVelocity(LivingEntity subject, double forward, double strafe, double vertical,
                                 double speed, LevitationTuning tuning) {
        var limits = tuning == null ? LevitationTuning.DEFAULT : tuning;
        var horizontal = MagneticMovement.flightDirection(subject.getYRot(), forward, strafe,
                speed * limits.graceSpeedFactor());
        var smoothed = MagneticMovement.approachHorizontal(subject.getDeltaMovement(), horizontal);
        return new Vec3(smoothed.x, MagneticMovement.degradedVertical(vertical, limits), smoothed.z);
    }

    /** The field envelope reference from the last refresh, or null when ground alone carried membership. */
    public @Nullable SupportReference envelope() {
        return envelope;
    }

    /** The ground reference from the last refresh; null above a drop or outside ground reach. */
    public @Nullable SupportReference ground() {
        return ground;
    }

    /** Feet-to-ground gap from the last refresh. */
    public double clearance() {
        return clearance;
    }

    /** Forces the next refresh to rescan instead of reusing the cached envelope reference. */
    public void invalidate() {
        envelopeQuery.invalidate();
        envelope = null;
    }

    private static double reachableClearance(double radius) {
        return Math.max(MagneticMovement.MIN_CLEARANCE, radius - CLEARANCE_REACH_MARGIN);
    }

    /**
     * Resolves membership and the height reference. The column probe is cheap and is asked every tick,
     * because it alone answers both questions over any terrain; the any-direction envelope is only needed
     * when there is no ground underfoot, which keeps the expensive probe off the ordinary flight path.
     */
    private void resolve(ServerLevel level, LivingEntity subject, double radius) {
        var bounds = subject.getBoundingBox();
        ground = groundQuery.groundBelow(level, bounds, radius);
        if (ground != null) {
            clearance = Math.max(0.0, bounds.minY - ground.surfaceY());
            // Ground underfoot already means "in the field", so skip the envelope entirely.
            envelope = null;
            return;
        }
        // No ground underfoot: membership rests on any-direction support, and the reported clearance is
        // undefined rather than a leftover from wherever the mover last had ground.
        clearance = 0.0;
        envelope = envelopeQuery.nearest(level, bounds, radius);
    }

    /**
     * Keeps the mover inside the field without killing its speed.
     *
     * <p>The step is accepted whole whenever the destination is still in the field, which is the ordinary
     * case over terrain. When it is not, only the component pointing <em>away</em> from the support is
     * removed: that is the only part that would widen the gap and leave, so flight along the boundary keeps
     * its full tangential speed instead of being frozen. Trimming axis components independently — an
     * earlier version of this method — pinned the mover with near-zero velocity that flickered on and off as
     * it crossed the rim, which is the stutter this replaced.</p>
     */
    private Vec3 clamp(ServerLevel level, AABB bounds, Vec3 desired, double radius, int maxSamples) {
        if (inField(level, bounds, desired, radius, maxSamples)) return desired;
        var outward = outwardDirection(bounds);
        if (outward == null) return Vec3.ZERO;
        var outwardSpeed = desired.dot(outward);
        // Moving inward or purely along the boundary is never the reason the step failed, so leave it be
        // rather than truncating something that is not the problem.
        if (outwardSpeed <= 0) return desired;
        return desired.subtract(outward.scale(outwardSpeed));
    }

    /** Unit vector from the nearest known support surface towards the mover, or null when nothing is known. */
    private @Nullable Vec3 outwardDirection(AABB bounds) {
        var reference = ground != null ? ground : envelope;
        if (reference == null) return null;
        var outward = bounds.getCenter().subtract(reference.closestPoint());
        return outward.lengthSqr() < 1.0e-8 ? null : outward.normalize();
    }

    private boolean inField(ServerLevel level, AABB bounds, Vec3 delta, double radius, int maxSamples) {
        var moved = bounds.move(delta);
        // Terrain case, and the overwhelmingly common one: ground under the destination. A short column
        // probe, and it is exactly the question admission asks, so the two can never disagree.
        if (groundQuery.groundBelow(level, moved, radius) != null) return true;
        // Wall or ceiling case: a known neighbour that still measures inside the radius, one block read.
        if (MagneticSupportQuery.holds(level, moved, envelope, radius)) return true;
        var probed = MagneticSupportQuery.probeAt(level, moved, radius, maxSamples);
        if (probed == null) return false;
        // Adopt the fresh reference so later ticks reuse it instead of probing again from scratch.
        envelope = probed;
        envelopeQuery.adopt(probed);
        return true;
    }
}
