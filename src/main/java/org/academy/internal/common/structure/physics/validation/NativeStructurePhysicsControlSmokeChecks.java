package org.academy.internal.common.structure.physics.validation;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.internal.common.structure.physics.NativeStructurePhysics;

import java.util.function.DoubleSupplier;

/** Bounded real-server control checks. This entire evidence window uses no pose or velocity writes. */
final class NativeStructurePhysicsControlSmokeChecks {
    private enum Phase { LIFT, HOVER_CAPTURE, RELEASE, FORCE, COAST, BLOCKED, DONE }
    private static final double DT = 0.05;
    private static final double GRAVITY = 9.81;
    private final StructurePhysicsRegion region;
    private final StructureHandle handle;
    private final DoubleSupplier minY;
    private final double floorTop;
    private final StringBuilder evidence;
    private final Vec3 localCom, initialCom, target;
    private final double mass, stiffness;
    private Phase phase = Phase.LIFT;
    private int ticks, stableTicks;
    private long beforeSteps;
    private StructureMotionState before;
    private StructurePhysicsControl.Anchor anchor;

    NativeStructurePhysicsControlSmokeChecks(StructurePhysicsRegion region, StructureHandle handle,
                                            DoubleSupplier minY, double floorTop, StringBuilder evidence) {
        require(Boolean.getBoolean("academy.nativeStructurePhysicsSmoke"), "Control probe is opt-in only");
        this.region = region;
        this.handle = handle;
        this.minY = minY;
        this.floorTop = floorTop;
        this.evidence = evidence;
        var motion = motion();
        localCom = motion.localCenterOfMass();
        initialCom = worldCom(motion);
        mass = motion.mass();
        stiffness = mass * 60;
        target = initialCom.add(0, 2, 0);
        anchor = anchor(target);
        region.setControl(handle.reference(), anchor);
        require(region.control(handle.reference()).orElseThrow().equals(anchor), "Anchor was not retained");
        rejectInvalidWithoutMutation();
        evidence.append("control_mass=").append(mass).append("\ncontrol_target=").append(target).append('\n');
    }

    String capture() { return phase == Phase.HOVER_CAPTURE ? "controlled" : ""; }

    boolean advance(boolean captured) {
        require(++ticks < 500, "Control scenario timed out: " + phase + " " + motion());
        require(region.status() == StructurePhysicsRegion.Status.READY, "Control region is not ready");
        var motion = motion();
        require(Math.abs(motion.mass() - mass) < 1.0E-8, "Control fixture geometry changed");
        require(minY.getAsDouble() >= floorTop - 0.15, "Finite controller crossed the real platform");
        switch (phase) {
            case LIFT -> {
                require(region.control(handle.reference()).orElseThrow().equals(anchor), "Persistent anchor disappeared");
                // No gravity feed-forward: the actual equilibrium is mg/k below the target.
                var equilibrium = target.add(0, -mass * GRAVITY / stiffness, 0);
                boolean converged = worldCom(motion).distanceTo(equilibrium) < 0.06
                        && motion.linearVelocity().lengthSqr() < 0.0036;
                stableTicks = converged ? stableTicks + 1 : 0;
                if (stableTicks < 15) return false;
                require(worldCom(motion).y > initialCom.y + 1.5 && minY.getAsDouble() > floorTop + 1.2,
                        "Anchor never lifted the real compound clear of contact");
                evidence.append("control_anchor_lift_converged=true\ncontrol_equilibrium_error=")
                        .append(worldCom(motion).distanceTo(equilibrium)).append('\n');
                phase = Phase.HOVER_CAPTURE;
            }
            case HOVER_CAPTURE -> {
                if (!captured) return false;
                before = motion; beforeSteps = steps();
                require(region.clearControl(handle.reference()) && region.control(handle.reference()).isEmpty(),
                        "Explicit clear failed to remove the persistent anchor");
                require(!region.clearControl(handle.reference()), "Repeated control clear was not empty");
                phase = Phase.RELEASE;
            }
            case RELEASE -> {
                require(steps() == beforeSteps + 1, "Release must be observed after exactly one outer physics step");
                assertVector(before.linearVelocity().add(0, -GRAVITY * DT, 0), motion.linearVelocity(), 1.0E-6,
                        "Clearing the anchor did not restore unmodified gravity");
                require(region.control(handle.reference()).isEmpty(), "Released control was resurrected");
                evidence.append("control_clear_releases_gravity=true\n");
                // The second request must replace the first, rather than add the two forces.
                region.setControl(handle.reference(), new StructurePhysicsControl.Force(localCom, new Vec3(-mass * 2, mass * GRAVITY, 0)));
                var force = new StructurePhysicsControl.Force(localCom, new Vec3(mass * 2, mass * GRAVITY, 0));
                region.setControl(handle.reference(), force);
                require(region.control(handle.reference()).orElseThrow().equals(force), "Last force did not replace the prior request");
                before = motion; beforeSteps = steps();
                phase = Phase.FORCE;
            }
            case FORCE -> {
                require(steps() == beforeSteps + 1, "Force must last one outer step, including all three substeps");
                assertVector(before.linearVelocity().add(2 * DT, 0, 0), motion.linearVelocity(), 1.0E-6,
                        "One-tick force did not integrate F*dt/m or replacement requests were accumulated");
                require(region.control(handle.reference()).isEmpty(), "One-tick force remained queued after consumption");
                before = motion; beforeSteps = steps();
                phase = Phase.COAST;
            }
            case COAST -> {
                require(steps() == beforeSteps + 1, "Force expiry must be checked on the immediately following step");
                assertVector(before.linearVelocity().add(0, -GRAVITY * DT, 0), motion.linearVelocity(), 1.0E-6,
                        "Consumed force leaked into a later outer step");
                evidence.append("control_force_mass_dt=true\ncontrol_force_last_write_wins=true\ncontrol_force_one_tick=true\n");
                anchor = anchor(initialCom.add(0, -2, 0));
                region.setControl(handle.reference(), anchor);
                stableTicks = 0;
                phase = Phase.BLOCKED;
            }
            case BLOCKED -> {
                require(region.control(handle.reference()).orElseThrow().equals(anchor), "Blocked anchor disappeared");
                boolean contact = Math.abs(minY.getAsDouble() - floorTop) < 0.12
                        && motion.linearVelocity().lengthSqr() < 0.0064 && motion.angularVelocity().lengthSqr() < 0.0064;
                stableTicks = contact ? stableTicks + 1 : 0;
                if (stableTicks < 20) return false;
                require(worldCom(motion).distanceTo(anchor.targetPosition()) > 1,
                        "Blocked anchor reached a target below the platform");
                require(region.clearControl(handle.reference()), "Blocked anchor could not be released");
                evidence.append("control_platform_contact_blocks_target=true\ncontrol_contact_stable_ticks=")
                        .append(stableTicks).append("\ncontrol_window_pose_writes=0\ncontrol_window_velocity_writes=0\n");
                phase = Phase.DONE;
                return true;
            }
            case DONE -> { return true; }
        }
        return false;
    }

    static void armAtCurrentCom(StructurePhysicsRegion region, StructureHandle handle) {
        var motion = region.motion(handle.reference()).orElseThrow();
        var control = new StructurePhysicsControl.Anchor(motion.localCenterOfMass(), worldCom(motion), Vec3.ZERO,
                motion.mass() * 60, motion.mass() * 16, motion.mass() * 40);
        region.setControl(handle.reference(), control);
        require(region.control(handle.reference()).orElseThrow().equals(control), "Lifecycle anchor was not installed");
    }

    private void rejectInvalidWithoutMutation() {
        var original = motion();
        long steps = steps();
        reject(() -> region.setControl(handle.reference(), new StructurePhysicsControl.Force(localCom, new Vec3(Double.NaN, 0, 0))));
        reject(() -> region.setControl(handle.reference(), new StructurePhysicsControl.Force(localCom, new Vec3(10001, 0, 0))));
        reject(() -> region.setControl(handle.reference(), new StructurePhysicsControl.Force(localCom.add(100, 0, 0), Vec3.ZERO)));
        reject(() -> region.setControl(handle.reference(), anchor(new Vec3(region.bounds().maxX + 1, target.y, target.z))));
        require(region.control(handle.reference()).orElseThrow().equals(anchor) && motion().equals(original)
                        && steps() == steps && region.status() == StructurePhysicsRegion.Status.READY,
                "Invalid controls changed the existing request or published body state");
        evidence.append("control_invalid_preserves_existing=true\n");
    }

    private StructurePhysicsControl.Anchor anchor(Vec3 position) {
        return new StructurePhysicsControl.Anchor(localCom, position, Vec3.ZERO, stiffness, mass * 16, mass * 40);
    }

    private StructureMotionState motion() { return region.motion(handle.reference()).orElseThrow(); }
    private long steps() { return ((NativeStructurePhysics.Region) region).diagnostics().steps(); }
    private static Vec3 worldCom(StructureMotionState motion) { return motion.pose().localToWorldPoint(motion.localCenterOfMass()); }
    private static void reject(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Invalid finite-control request was accepted");
    }
    private static void assertVector(Vec3 expected, Vec3 actual, double tolerance, String message) {
        require(expected.distanceTo(actual) <= tolerance, message + ": expected=" + expected + ", actual=" + actual);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
