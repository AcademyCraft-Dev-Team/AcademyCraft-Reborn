package org.academy.internal.common.structure.physics.validation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/** Real-server support and carrying checks: a live mob stands on the visible structure and rides it. */
final class NativeStructurePhysicsSupportSmokeChecks {
    private enum Phase { ARM, SETTLE, LIFT, DESCEND, DONE }
    private static final double SUPPORT_TOLERANCE = 0.18;
    private static final double RECOVERY_TOLERANCE = 0.5;
    private static final double ALIGNMENT_TOLERANCE = 0.05;
    private static final double LIFT_TARGET = 2.0;

    private final ServerLevel host;
    private final StructurePhysicsRegion region;
    private final StructureHandle handle;
    private final DoubleSupplier minY;
    private final double floorTop;
    private final StringBuilder evidence;
    private Zombie rider;
    private double surfaceBefore, feetBefore;
    private Phase phase = Phase.ARM;
    private int ticks, stableTicks;

    NativeStructurePhysicsSupportSmokeChecks(ServerLevel host, StructurePhysicsRegion region,
                                            StructureHandle handle, DoubleSupplier minY, double floorTop,
                                            StringBuilder evidence) {
        require(Boolean.getBoolean("academy.nativeStructurePhysicsSmoke"), "Support probe is opt-in only");
        this.host = host;
        this.region = region;
        this.handle = handle;
        this.minY = minY;
        this.floorTop = floorTop;
        this.evidence = evidence;
    }

    boolean advance() {
        require(++ticks < 700, "Support scenario timed out: " + phase);
        require(region.status() == StructurePhysicsRegion.Status.READY, "Support region is not ready");
        require(minY.getAsDouble() >= floorTop - 0.15, "Support platform crossed the real floor");
        var projection = projection().orElse(null);
        if (projection == null) return false;
        if (ticks % 40 == 0) {
            AcademyCraft.LOGGER.info("NATIVE_STRUCTURE_PHYSICS_SUPPORT_PHASE phase={} ticks={} rider={} surface={}",
                    phase, ticks, rider == null ? "none" : rider.position(),
                    rider == null ? "n/a" : projection.supportSurfaceY(rider.getBoundingBox(), RECOVERY_TOLERANCE));
        }
        switch (phase) {
            case ARM -> {
                var boxes = projection.worldCollisionBoxes();
                var landing = landingCenter(boxes);
                if (landing == null) return false;
                rider = EntityTypes.ZOMBIE.create(host, EntitySpawnReason.TRIGGERED);
                require(rider != null, "Could not create the support rider entity");
                rider.setNoAi(true);
                rider.setInvulnerable(true);
                rider.setPersistenceRequired();
                rider.setPos(landing.x, landing.y + 0.05, landing.z);
                require(host.addFreshEntity(rider), "Could not admit the support rider");
                evidence.append("support_rider_spawned=true\nsupport_collision_boxes=").append(boxes.size()).append('\n');
                phase = Phase.SETTLE;
            }
            case SETTLE -> {
                var surface = projection.supportSurfaceY(rider.getBoundingBox(), SUPPORT_TOLERANCE);
                if (surface.isEmpty()) {
                    stableTicks = 0;
                    return false;
                }
                var aligned = Math.abs(surface.getAsDouble() - rider.getBoundingBox().minY) < ALIGNMENT_TOLERANCE;
                stableTicks = aligned && rider.getDeltaMovement().lengthSqr() < 1.0E-4 ? stableTicks + 1 : 0;
                if (stableTicks < 10) return false;
                require(rider.onGround(), "Rider never reported standing on the structure view");
                require(rider.getBoundingBox().minY > floorTop + 0.1,
                        "Rider fell past the structure collision onto the real floor");
                evidence.append("support_rider_standing=true\nsupport_standing_feet=")
                        .append(rider.getBoundingBox().minY).append("\nsupport_expected_surface=")
                        .append(surface.getAsDouble()).append('\n');
                var motion = motion();
                var com = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                region.setControl(handle.reference(), new StructurePhysicsControl.Anchor(
                        motion.localCenterOfMass(), com.add(0, LIFT_TARGET, 0), Vec3.ZERO,
                        motion.mass() * 60, motion.mass() * 16, motion.mass() * 40));
                require(region.control(handle.reference()).isPresent(), "Support lift anchor was not retained");
                surfaceBefore = surface.getAsDouble();
                feetBefore = rider.getBoundingBox().minY;
                phase = Phase.LIFT;
            }
            case LIFT -> {
                require(region.control(handle.reference()).isPresent(), "Support lift anchor disappeared");
                if (minY.getAsDouble() < floorTop + LIFT_TARGET - 0.4) return false;
                var surface = projection.supportSurfaceY(rider.getBoundingBox(), RECOVERY_TOLERANCE);
                require(surface.isPresent(), "Rider lost its support surface during the lift");
                require(Math.abs(surface.getAsDouble() - rider.getBoundingBox().minY) < ALIGNMENT_TOLERANCE,
                        "Rider did not stay aligned while the structure moved");
                require(rider.getBoundingBox().minY > feetBefore + 1.2,
                        "Rider was not carried by the moving structure");
                require(rider.getBoundingBox().minY > surfaceBefore + 1.2,
                        "Rider fell behind the rising support surface");
                require(rider.onGround() && rider.getDeltaMovement().y <= 1.0E-6,
                        "Carried rider kept downward velocity");
                evidence.append("support_carried_delta=")
                        .append(rider.getBoundingBox().minY - feetBefore)
                        .append("\nsupport_lift_floor_clearance=").append(minY.getAsDouble() - floorTop)
                        .append("\nsupport_translation_carrying=true\n");
                require(region.clearControl(handle.reference()), "Support lift anchor could not be released");
                stableTicks = 0;
                phase = Phase.DESCEND;
            }
            case DESCEND -> {
                if (minY.getAsDouble() > floorTop + 0.15) return false;
                // The dropped fixture must settle before later phases: a tumbling body would
                // invalidate the following bounded-control and real-skill scenarios.
                var current = motion();
                boolean contact = Math.abs(minY.getAsDouble() - floorTop) < 0.12
                        && current.linearVelocity().lengthSqr() < 0.0064
                        && current.angularVelocity().lengthSqr() < 0.0064;
                stableTicks = contact ? stableTicks + 1 : 0;
                if (stableTicks < 20) return false;
                var surface = projection.supportSurfaceY(rider.getBoundingBox(), RECOVERY_TOLERANCE);
                require(surface.isPresent(), "Rider lost support while the structure descended");
                require(Math.abs(surface.getAsDouble() - rider.getBoundingBox().minY) < ALIGNMENT_TOLERANCE,
                        "Rider did not stay aligned while the structure descended");
                require(rider.getBoundingBox().minY < feetBefore + 0.3,
                        "Rider did not descend with the structure");
                evidence.append("support_descend_feet=").append(rider.getBoundingBox().minY)
                        .append("\nsupport_settled_min_y=").append(minY.getAsDouble())
                        .append("\nsupport_carrying_both_directions=true\n");
                phase = Phase.DONE;
                return true;
            }
            case DONE -> {
                return true;
            }
        }
        return false;
    }

    void close() {
        if (rider != null) {
            rider.discard();
            rider = null;
        }
        if (region.status() == StructurePhysicsRegion.Status.READY) region.clearControl(handle.reference());
    }

    private Optional<NativeStructureProjectionEntity> projection() {
        for (var entity : host.getEntitiesOfClass(NativeStructureProjectionEntity.class,
                region.bounds().inflate(2.0))) {
            if (entity.viewState().reference().equals(handle.reference())) return Optional.of(entity);
        }
        return Optional.empty();
    }

    private static Vec3 landingCenter(List<AABB> boxes) {
        double maxTop = Double.NEGATIVE_INFINITY;
        for (var box : boxes) maxTop = Math.max(maxTop, box.maxY);
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        boolean any = false;
        for (var box : boxes) {
            if (box.maxY < maxTop - 0.05) continue;
            minX = Math.min(minX, box.minX);
            maxX = Math.max(maxX, box.maxX);
            minZ = Math.min(minZ, box.minZ);
            maxZ = Math.max(maxZ, box.maxZ);
            any = true;
        }
        // The axis-aligned union of subdivided top pieces keeps its center over the real surface.
        return any ? new Vec3((minX + maxX) / 2.0, maxTop, (minZ + maxZ) / 2.0) : null;
    }

    private StructureMotionState motion() {
        return region.motion(handle.reference()).orElseThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
