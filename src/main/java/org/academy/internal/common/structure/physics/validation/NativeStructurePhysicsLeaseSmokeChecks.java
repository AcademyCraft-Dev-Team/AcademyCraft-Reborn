package org.academy.internal.common.structure.physics.validation;

import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsLease;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.internal.common.structure.physics.NativeStructurePhysics;

import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Real tick, native force and lifetime checks; never changes pose or velocity directly. */
final class NativeStructurePhysicsLeaseSmokeChecks {
    private enum Phase { LIFT, ANCHOR_EXPIRED, ANCHOR_COAST, EXPIRING_FORCE, EXPIRED_COAST,
        OCCUPIED_FORCE, COAST, LAND, DONE }
    private static final double DT = 0.05;
    private static final double GRAVITY = 9.81;
    private final StructurePhysicsRegion region;
    private final StructureHandle handle;
    private final DoubleSupplier minY;
    private final double floorTop;
    private final StringBuilder evidence;
    private final Vec3 localCom, initialCom, target;
    private final double mass;
    private final StructurePhysicsControl.Anchor anchor;
    private StructurePhysicsLease lease;
    private Phase phase = Phase.LIFT;
    private int ticks, stableTicks;
    private long beforeSteps;
    private StructureMotionState before;

    NativeStructurePhysicsLeaseSmokeChecks(StructurePhysicsRegion region, StructureHandle handle,
                                          DoubleSupplier minY, double floorTop, StringBuilder evidence) {
        require(Boolean.getBoolean("academy.nativeStructurePhysicsSmoke"), "Lease probe is opt-in only");
        this.region = region;
        this.handle = handle;
        this.minY = minY;
        this.floorTop = floorTop;
        this.evidence = evidence;
        var motion = motion();
        localCom = motion.localCenterOfMass();
        initialCom = worldCom(motion);
        target = initialCom.add(0, 2, 0);
        mass = motion.mass();
        anchor = new StructurePhysicsControl.Anchor(localCom, target, Vec3.ZERO, mass * 60, mass * 16, mass * 40);
        region.setControl(handle.reference(), anchor);
        require(region.acquireControl(handle.reference(), UUID.randomUUID(), anchor, 8).isEmpty()
                        && region.control(handle.reference()).orElseThrow().equals(anchor),
                "Acquisition replaced an unleased command");
        require(region.clearControl(handle.reference()), "Cannot clear the unleased acquisition fixture");
        lease = region.acquireControl(handle.reference(), UUID.randomUUID(), anchor, 8).orElseThrow();
        require(lease.reference().equals(handle.reference()) && lease.remainingTicks() == 8,
                "Lease did not retain its exact reference or initial duration");
        verifyExclusiveAndInvalid();
        evidence.append("lease_unleased_command_blocks_acquire=true\n");
    }

    boolean advance() {
        require(++ticks < 450, "Lease scenario timed out: " + phase + " " + motion());
        require(region.status() == StructurePhysicsRegion.Status.READY, "Lease region is not ready");
        var motion = motion();
        require(Math.abs(motion.mass() - mass) < 1.0E-8 && minY.getAsDouble() >= floorTop - 0.15,
                "Lease fixture changed geometry or crossed its platform");
        switch (phase) {
            case LIFT -> {
                require(lease.state() == StructurePhysicsLease.State.ACTIVE
                                && lease.control().orElseThrow().equals(anchor),
                        "Renewed anchor lost its lease");
                if (ticks == 1) {
                    require(lease.remainingTicks() == 7, "Lease did not age once after its first server tick");
                    lease.setControl(anchor);
                    require(lease.remainingTicks() == 7, "Updating a command implicitly renewed its lease");
                }
                if (ticks % 4 == 0) {
                    require(lease.remainingTicks() == 4, "Lease duration was not measured in real server ticks");
                    lease.renew(8);
                    require(lease.remainingTicks() == 8, "Renewal accumulated duration instead of resetting it");
                }
                boolean converged = worldCom(motion).distanceTo(target.add(0, -GRAVITY / 60, 0)) < 0.06
                        && motion.linearVelocity().lengthSqr() < 0.0036;
                stableTicks = converged ? stableTicks + 1 : 0;
                if (stableTicks < 15) return false;
                require(ticks > 8 && worldCom(motion).y > initialCom.y + 1.5 && minY.getAsDouble() > floorTop + 1.2,
                        "Renewal did not keep the real PD anchor alive across its old deadline");
                evidence.append("lease_renewed_pd_lift=true\nlease_renew_resets_duration=true\nlease_update_does_not_renew=true\n");
                lease.renew(1);
                beforeSteps = steps();
                phase = Phase.ANCHOR_EXPIRED;
            }
            case ANCHOR_EXPIRED -> {
                require(steps() == beforeSteps + 1, "Anchor must expire after its final permitted outer step");
                assertTerminal(lease, StructurePhysicsLease.State.EXPIRED);
                require(region.control(handle.reference()).isEmpty(), "Expired persistent anchor remained queued");
                before = motion; beforeSteps = steps();
                phase = Phase.ANCHOR_COAST;
            }
            case ANCHOR_COAST -> {
                require(steps() == beforeSteps + 1, "Anchor expiry must release the immediately following tick");
                assertVector(before.linearVelocity().add(0, -GRAVITY * DT, 0), motion.linearVelocity(),
                        "Expired persistent anchor continued applying force");
                var expired = lease;
                var released = region.acquireControl(handle.reference(), expired.controllerId(), anchor, 8).orElseThrow();
                expired.close();
                require(released.state() == StructurePhysicsLease.State.ACTIVE && released.control().isPresent(),
                        "Expired anchor handle closed a successor lease");
                released.close();
                assertTerminal(released, StructurePhysicsLease.State.RELEASED);
                lease = region.acquireControl(handle.reference(), UUID.randomUUID(), force(), 1).orElseThrow();
                released.close();
                require(lease.state() == StructurePhysicsLease.State.ACTIVE && lease.control().isPresent(),
                        "Released handle closed its successor");
                before = motion; beforeSteps = steps();
                evidence.append("lease_anchor_expiry_releases_gravity=true\n");
                phase = Phase.EXPIRING_FORCE;
            }
            case EXPIRING_FORCE -> {
                require(steps() == beforeSteps + 1, "TTL 1 must allow exactly the next outer physics step");
                assertVector(before.linearVelocity().add(2 * DT, 0, 0), motion.linearVelocity(),
                        "TTL 1 force expired before integration or integrated more than once");
                assertTerminal(lease, StructurePhysicsLease.State.EXPIRED);
                require(region.control(handle.reference()).isEmpty(), "Expiry left a force queued");
                before = motion; beforeSteps = steps();
                evidence.append("lease_ttl_one_integrates_then_expires=true\n");
                phase = Phase.EXPIRED_COAST;
            }
            case EXPIRED_COAST -> {
                require(steps() == beforeSteps + 1, "Lease expiry must release the immediately following step");
                assertVector(before.linearVelocity().add(0, -GRAVITY * DT, 0), motion.linearVelocity(),
                        "Expired TTL 1 force leaked into the immediately following step");
                var expired = lease;
                lease = region.acquireControl(handle.reference(), expired.controllerId(), force(), 3).orElseThrow();
                expired.close();
                require(lease.state() == StructurePhysicsLease.State.ACTIVE && lease.remainingTicks() == 3
                                && lease.control().isPresent(), "Expired handle mutated a new lease for the same caller");
                before = motion; beforeSteps = steps();
                evidence.append("lease_expiry_releases_gravity=true\nlease_stale_handle_isolated=true\n");
                phase = Phase.OCCUPIED_FORCE;
            }
            case OCCUPIED_FORCE -> {
                require(steps() == beforeSteps + 1, "Leased force did not use exactly one outer step");
                assertVector(before.linearVelocity().add(2 * DT, 0, 0), motion.linearVelocity(),
                        "Leased force did not use F*dt/m");
                require(lease.state() == StructurePhysicsLease.State.ACTIVE && lease.remainingTicks() == 2
                                && lease.control().isEmpty() && region.control(handle.reference()).isEmpty(),
                        "Consuming a leased Force released ownership or retained the command");
                verifyExclusiveAndInvalid();
                before = motion; beforeSteps = steps();
                phase = Phase.COAST;
            }
            case COAST -> {
                require(steps() == beforeSteps + 1 && lease.remainingTicks() == 1,
                        "Command-free lease did not continue aging");
                assertVector(before.linearVelocity().add(0, -GRAVITY * DT, 0), motion.linearVelocity(),
                        "Consumed leased force leaked into a later physics tick");
                lease.close();
                assertTerminal(lease, StructurePhysicsLease.State.RELEASED);
                before = motion; beforeSteps = steps();
                evidence.append("lease_consumed_force_retains_ownership=true\nlease_consumed_force_releases_gravity=true\n");
                phase = Phase.LAND;
            }
            case LAND -> {
                if (steps() == beforeSteps + 1) {
                    assertVector(before.linearVelocity().add(0, -GRAVITY * DT, 0), motion.linearVelocity(),
                            "Closing an empty lease changed gravity or velocity");
                    evidence.append("lease_release_preserves_gravity=true\n");
                }
                boolean settled = Math.abs(minY.getAsDouble() - floorTop) < 0.12
                        && motion.linearVelocity().lengthSqr() < 0.0064 && motion.angularVelocity().lengthSqr() < 0.0064;
                stableTicks = settled ? stableTicks + 1 : 0;
                if (stableTicks < 15) return false;
                require(region.control(handle.reference()).isEmpty(), "Released lease reappeared while landing");
                evidence.append("lease_window_pose_writes=0\nlease_window_velocity_writes=0\n");
                phase = Phase.DONE;
                return true;
            }
            case DONE -> { return true; }
        }
        return false;
    }

    static StructurePhysicsLease armAtCurrentCom(StructurePhysicsRegion region, StructureHandle handle) {
        var motion = region.motion(handle.reference()).orElseThrow();
        var anchor = new StructurePhysicsControl.Anchor(motion.localCenterOfMass(), worldCom(motion), Vec3.ZERO,
                motion.mass() * 60, motion.mass() * 16, motion.mass() * 40);
        return region.acquireControl(handle.reference(), UUID.randomUUID(), anchor, 200).orElseThrow();
    }

    static void assertInvalidated(StructurePhysicsLease lease) {
        require(lease != null, "No lifecycle lease was armed");
        assertTerminal(lease, StructurePhysicsLease.State.INVALIDATED);
    }

    private void verifyExclusiveAndInvalid() {
        var previous = lease.control();
        var motion = motion();
        long steps = steps();
        int duration = lease.remainingTicks();
        require(region.acquireControl(handle.reference(), UUID.randomUUID(), anchor, 8).isEmpty()
                        && region.acquireControl(handle.reference(), lease.controllerId(), anchor, 8).isEmpty(),
                "A competitor or matching UUID stole the active lease");
        rejectState(() -> region.setControl(handle.reference(), anchor));
        rejectState(() -> region.clearControl(handle.reference()));
        rejectState(() -> region.setVelocity(handle.reference(), Vec3.ZERO, Vec3.ZERO));
        rejectState(() -> region.applyImpulse(handle.reference(), Vec3.ZERO, worldCom(motion)));
        rejectArgument(() -> lease.renew(0));
        rejectArgument(() -> lease.renew(201));
        rejectArgument(() -> lease.setControl(new StructurePhysicsControl.Force(localCom.add(100, 0, 0), Vec3.ZERO)));
        rejectArgument(() -> region.acquireControl(handle.reference(), UUID.randomUUID(), anchor, 0));
        rejectArgument(() -> region.acquireControl(handle.reference(), UUID.randomUUID(),
                new StructurePhysicsControl.Force(localCom.add(100, 0, 0), Vec3.ZERO), 8));
        require(lease.state() == StructurePhysicsLease.State.ACTIVE && lease.remainingTicks() == duration
                        && lease.control().equals(previous) && region.control(handle.reference()).equals(previous)
                        && motion().equals(motion) && steps() == steps,
                "Rejected lease requests changed ownership, expiry, command or published motion");
        evidence.append(previous.isPresent() ? "lease_exclusive_writes_rejected=true\nlease_invalid_preserves_existing=true\n"
                : "lease_empty_slot_still_exclusive=true\n");
    }

    private static void assertTerminal(StructurePhysicsLease lease, StructurePhysicsLease.State state) {
        require(lease.state() == state && lease.remainingTicks() == 0 && lease.control().isEmpty(),
                "Lease has an incorrect terminal state: " + lease.state());
        rejectState(() -> lease.renew(1));
        rejectState(() -> lease.setControl(new StructurePhysicsControl.Force(Vec3.ZERO, Vec3.ZERO)));
        lease.close();
        require(lease.state() == state, "Closing a stale lease changed its terminal state");
    }

    private StructurePhysicsControl.Force force() { return new StructurePhysicsControl.Force(localCom, new Vec3(mass * 2, mass * GRAVITY, 0)); }
    private StructureMotionState motion() { return region.motion(handle.reference()).orElseThrow(); }
    private long steps() { return ((NativeStructurePhysics.Region) region).diagnostics().steps(); }
    private static Vec3 worldCom(StructureMotionState motion) { return motion.pose().localToWorldPoint(motion.localCenterOfMass()); }
    private static void rejectState(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalStateException expected) { rejected = true; }
        require(rejected, "Invalid lease or unleased write was accepted");
    }
    private static void rejectArgument(Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "Invalid lease argument was accepted");
    }
    private static void assertVector(Vec3 expected, Vec3 actual, String message) {
        require(expected.distanceTo(actual) < 1.0E-6, message + ": expected=" + expected + ", actual=" + actual);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
