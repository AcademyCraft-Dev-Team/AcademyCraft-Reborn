package org.academy.internal.common.structure.physics.validation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.structure.StructureHandle;
import org.academy.api.common.structure.StructureMotionState;
import org.academy.api.common.structure.StructurePhysicsControl;
import org.academy.api.common.structure.StructurePhysicsRegion;
import org.academy.api.common.structure.StructurePose;
import org.academy.internal.common.world.entity.structure.NativeStructureProjectionEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/** Real-server support and carrying checks: a live mob stands on the visible structure and rides it. */
final class NativeStructurePhysicsSupportSmokeChecks {
    private enum Phase { ARM, SETTLE, LIFT, DESCEND, ROTATE_LIFT, ROTATE_SPIN, ROTATE_SETTLE, JUMP, EDGE_TRANSLATE,
        EDGE_WALK, CEILING_RESTORE, CEILING_LIFT, CEILING_SETTLE, DONE }
    private static final double SUPPORT_TOLERANCE = 0.18;
    private static final double RECOVERY_TOLERANCE = 0.5;
    private static final double ALIGNMENT_TOLERANCE = 0.05;
    private static final double LIFT_TARGET = 2.0;
    private static final double ROTATE_LIFT_TARGET = 0.9;
    private static final double ROTATE_SPIN_SPEED = 1.5;
    private static final double ROTATE_MIN_ANGLE = Math.toRadians(60.0);
    private static final double ROTATE_MIN_RIDER_TRAVEL = 0.2;
    private static final double ROTATE_MAX_RIDER_DRIFT = 0.3;
    private static final double JUMP_SPEED = 0.55;
    private static final int JUMP_AIR_TICKS = 5;
    private static final double EDGE_DISTANCE = 2.0;
    private static final double EDGE_FOLLOW_TOLERANCE = 0.35;
    private static final double EDGE_WALK_SPEED = 0.15;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

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
    private final List<BlockPos> ceilingCells = new ArrayList<>();
    private Vec3 rotateTarget, spinRiderStart, spinLocalStart;
    private StructurePose spinPrevious;
    private double spinAngle;
    private boolean jumpStarted, jumpLeftSurface;
    private double jumpSurface, jumpPeak;
    private int jumpAirTicks;
    private Vec3 edgeComStart, edgeRiderStart, edgeRiderLocal;
    private double edgeMaxFollowError;
    private boolean edgeLeft, edgeSawFall, ceilingReturned;
    private double ceilingBottom, ceilingRiderTop, ceilingMaxRise;

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
                phase = Phase.ROTATE_LIFT;
            }
            case ROTATE_LIFT -> {
                var motion = motion();
                if (rotateTarget == null) {
                    var com = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                    rotateTarget = com.add(0, ROTATE_LIFT_TARGET, 0);
                    anchorTo(motion, rotateTarget);
                }
                if (minY.getAsDouble() < floorTop + ROTATE_LIFT_TARGET - 0.25) return false;
                var surface = projection.supportSurfaceY(rider.getBoundingBox(), RECOVERY_TOLERANCE);
                require(surface.isPresent() && Math.abs(surface.getAsDouble() - rider.getBoundingBox().minY)
                        < ALIGNMENT_TOLERANCE, "Rider lost its seat before the rotation");
                spinRiderStart = rider.position();
                spinLocalStart = motion.pose().worldToLocalPoint(spinRiderStart);
                spinPrevious = motion.pose();
                spinAngle = 0.0;
                region.setVelocity(handle.reference(), Vec3.ZERO, new Vec3(0, ROTATE_SPIN_SPEED, 0));
                // setVelocity clears the accepted control; re-arm the same hold so the body stays aloft.
                anchorTo(motion, rotateTarget);
                phase = Phase.ROTATE_SPIN;
            }
            case ROTATE_SPIN -> {
                var pose = motion().pose();
                spinAngle += angleBetween(spinPrevious, pose);
                spinPrevious = pose;
                var surface = projection.supportSurfaceY(rider.getBoundingBox(), RECOVERY_TOLERANCE);
                require(surface.isPresent(), "Rider lost its support while the structure rotated");
                require(Math.abs(surface.getAsDouble() - rider.getBoundingBox().minY) < ALIGNMENT_TOLERANCE,
                        "Rider did not stay aligned while the structure rotated");
                if (spinAngle < ROTATE_MIN_ANGLE) return false;
                var localNow = pose.worldToLocalPoint(rider.position());
                var traveled = Math.hypot(rider.getX() - spinRiderStart.x, rider.getZ() - spinRiderStart.z);
                var drift = Math.hypot(localNow.x - spinLocalStart.x, localNow.z - spinLocalStart.z);
                require(traveled > ROTATE_MIN_RIDER_TRAVEL, "Rider did not follow the rotation: " + traveled);
                require(drift < ROTATE_MAX_RIDER_DRIFT, "Rider slipped on the rotating structure: " + drift);
                evidence.append("support_rotation_supported=true\nsupport_rotation_angle=")
                        .append(Math.toDegrees(spinAngle)).append("\nsupport_rotation_rider_followed=true\n")
                        .append("support_rotation_rider_travel=").append(traveled)
                        .append("\nsupport_rotation_rider_local_drift=").append(drift).append('\n');
                region.setVelocity(handle.reference(), Vec3.ZERO, Vec3.ZERO);
                stableTicks = 0;
                phase = Phase.ROTATE_SETTLE;
            }
            case ROTATE_SETTLE -> {
                if (minY.getAsDouble() > floorTop + 0.15) return false;
                var current = motion();
                boolean contact = Math.abs(minY.getAsDouble() - floorTop) < 0.12
                        && current.linearVelocity().lengthSqr() < 0.0064
                        && current.angularVelocity().lengthSqr() < 0.0064;
                stableTicks = contact ? stableTicks + 1 : 0;
                if (stableTicks < 20) return false;
                var surface = projection.supportSurfaceY(rider.getBoundingBox(), RECOVERY_TOLERANCE);
                require(surface.isPresent(), "Rider lost support while the rotated structure descended");
                require(Math.abs(surface.getAsDouble() - rider.getBoundingBox().minY) < ALIGNMENT_TOLERANCE,
                        "Rider did not stay aligned through the rotated descent");
                evidence.append("support_rotation_settled=true\nsupport_rotation_feet=")
                        .append(rider.getBoundingBox().minY).append('\n');
                phase = Phase.JUMP;
            }
            case JUMP -> {
                if (!jumpStarted) {
                    var surface = projection.supportSurfaceY(rider.getBoundingBox(), SUPPORT_TOLERANCE);
                    require(surface.isPresent() && Math.abs(surface.getAsDouble() - rider.getBoundingBox().minY)
                            < ALIGNMENT_TOLERANCE, "Rider must rest on the structure before the jump");
                    jumpSurface = surface.getAsDouble();
                    jumpPeak = rider.getBoundingBox().minY;
                    rider.setDeltaMovement(0.0, JUMP_SPEED, 0.0);
                    jumpStarted = true;
                    return false;
                }
                // A no-AI rider skips LivingEntity travel, so advance the vanilla arc explicitly.
                rider.travel(Vec3.ZERO);
                var feet = rider.getBoundingBox().minY;
                jumpPeak = Math.max(jumpPeak, feet);
                if (!jumpLeftSurface) {
                    jumpAirTicks = feet > jumpSurface + RECOVERY_TOLERANCE ? jumpAirTicks + 1 : 0;
                    if (jumpAirTicks < JUMP_AIR_TICKS) return false;
                    jumpLeftSurface = true;
                    evidence.append("support_jump_leaves_surface=true\nsupport_jump_air_ticks=")
                            .append(jumpAirTicks).append('\n');
                    return false;
                }
                var landing = projection.supportSurfaceY(rider.getBoundingBox(), SUPPORT_TOLERANCE);
                if (landing.isEmpty() || Math.abs(landing.getAsDouble() - feet) >= ALIGNMENT_TOLERANCE) return false;
                require(rider.onGround(), "Rider did not report ground after re-landing on the structure");
                evidence.append("support_jump_relands=true\nsupport_jump_peak=")
                        .append(jumpPeak - jumpSurface).append('\n');
                phase = Phase.EDGE_TRANSLATE;
            }
            case EDGE_TRANSLATE -> {
                var motion = motion();
                if (edgeComStart == null) {
                    var com = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                    edgeComStart = com;
                    edgeRiderStart = rider.position();
                    anchorTo(motion, com.add(EDGE_DISTANCE, 0, 0));
                    return false;
                }
                var com = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                var moved = com.x - edgeComStart.x;
                var riderMoved = rider.position().x - edgeRiderStart.x;
                var surface = projection.supportSurfaceY(rider.getBoundingBox(), RECOVERY_TOLERANCE);
                require(surface.isPresent(), "Rider lost support during the horizontal structure move");
                require(Math.abs(surface.getAsDouble() - rider.getBoundingBox().minY) < ALIGNMENT_TOLERANCE,
                        "Rider did not stay aligned during the horizontal structure move");
                var error = Math.abs(riderMoved - moved);
                edgeMaxFollowError = Math.max(edgeMaxFollowError, error);
                require(error < EDGE_FOLLOW_TOLERANCE, "Rider did not follow the horizontal move: " + error);
                if (moved < EDGE_DISTANCE - 0.25) return false;
                require(moved > 1.5, "Horizontal anchor only moved the structure by " + moved);
                region.setVelocity(handle.reference(), Vec3.ZERO, Vec3.ZERO);
                edgeRiderLocal = motion.pose().worldToLocalPoint(rider.position());
                evidence.append("support_edge_translation_follows=true\nsupport_edge_translation_distance=")
                        .append(moved).append("\nsupport_edge_translation_error=").append(edgeMaxFollowError)
                        .append('\n');
                stableTicks = 0;
                phase = Phase.EDGE_WALK;
            }
            case EDGE_WALK -> {
                // Walk back toward the platform center so the landing stays inside the host fixture.
                rider.setDeltaMovement(-EDGE_WALK_SPEED, rider.getDeltaMovement().y, 0.0);
                // A no-AI rider skips LivingEntity travel, so advance the vanilla walk explicitly.
                rider.travel(Vec3.ZERO);
                var feet = rider.getBoundingBox().minY;
                if (edgeLeft) edgeSawFall |= rider.getDeltaMovement().y < -0.01;
                if (!edgeLeft) {
                    var surface = projection.supportSurfaceY(rider.getBoundingBox(), SUPPORT_TOLERANCE);
                    if (surface.isEmpty() && feet > floorTop + 0.2) edgeLeft = true;
                    return false;
                }
                if (!(rider.onGround() && Math.abs(feet - floorTop) < 0.05)) return false;
                require(edgeSawFall, "Rider was not falling naturally after leaving the structure edge");
                require(projection.supportSurfaceY(rider.getBoundingBox(), SUPPORT_TOLERANCE).isEmpty(),
                        "Rider still reports structure support on the real floor");
                require(region.status() == StructurePhysicsRegion.Status.READY, "Region left READY after the edge walk");
                rider.setDeltaMovement(Vec3.ZERO);
                evidence.append("support_edge_walk_off_falls=true\nsupport_edge_landing_feet=")
                        .append(feet).append("\nsupport_edge_region_ready=true\n");
                phase = Phase.CEILING_RESTORE;
            }
            case CEILING_RESTORE -> {
                if (!ceilingReturned) {
                    var back = motion().pose().localToWorldPoint(edgeRiderLocal);
                    rider.setDeltaMovement(Vec3.ZERO);
                    rider.setPos(back.x, back.y + 1.0E-4, back.z);
                    ceilingReturned = true;
                    stableTicks = 0;
                    return false;
                }
                if (!ceilingCells.isEmpty()) {
                    // A fresh host-terrain observation is required before the next control request.
                    var motion = motion();
                    var com = motion.pose().localToWorldPoint(motion.localCenterOfMass());
                    anchorTo(motion, com.add(0, LIFT_TARGET, 0));
                    evidence.append("support_ceiling_bottom=").append(ceilingBottom).append('\n');
                    phase = Phase.CEILING_LIFT;
                    return false;
                }
                var surface = projection.supportSurfaceY(rider.getBoundingBox(), SUPPORT_TOLERANCE);
                if (surface.isEmpty()) {
                    stableTicks = 0;
                    return false;
                }
                boolean aligned = Math.abs(surface.getAsDouble() - rider.getBoundingBox().minY) < ALIGNMENT_TOLERANCE
                        && rider.getDeltaMovement().lengthSqr() < 1.0E-4;
                stableTicks = aligned ? stableTicks + 1 : 0;
                if (stableTicks < 5) return false;
                var ceiling = rider.blockPosition().above(3);
                for (var cell : List.of(ceiling, ceiling.east(), ceiling.west())) {
                    require(host.getBlockState(cell).isAir(), "Ceiling fixture cell must start empty: " + cell);
                    require(host.setBlock(cell, Blocks.STONE.defaultBlockState(), FLAGS),
                            "Cannot place the ceiling fixture: " + cell);
                    ceilingCells.add(cell);
                }
                ceilingBottom = ceiling.getY();
                ceilingRiderTop = rider.getBoundingBox().maxY;
            }
            case CEILING_LIFT -> {
                require(rider.getBoundingBox().maxY <= ceilingBottom + 1.0E-3,
                        "Carried rider clipped through the ceiling");
                var rise = rider.getBoundingBox().maxY - ceilingRiderTop;
                ceilingMaxRise = Math.max(ceilingMaxRise, rise);
                if (minY.getAsDouble() < floorTop + LIFT_TARGET - 0.4) return false;
                require(ceilingMaxRise < LIFT_TARGET - 0.5,
                        "Ceiling did not stop the rider rising with the structure: " + ceilingMaxRise);
                evidence.append("support_ceiling_blocks_rider=true\nsupport_ceiling_no_clip=true\n")
                        .append("support_ceiling_rider_rise=").append(ceilingMaxRise)
                        .append("\nsupport_ceiling_floor_clearance=").append(minY.getAsDouble() - floorTop)
                        .append("\nsupport_ceiling_structure_through_rider=")
                        .append(rider.getBoundingBox().minY < structureTopOver(projection) - 0.05).append('\n');
                region.setVelocity(handle.reference(), Vec3.ZERO, Vec3.ZERO);
                for (var cell : ceilingCells) {
                    require(host.getBlockState(cell).is(Blocks.STONE), "Ceiling fixture was modified: " + cell);
                    require(host.setBlock(cell, Blocks.AIR.defaultBlockState(), FLAGS),
                            "Cannot remove the ceiling fixture: " + cell);
                }
                ceilingCells.clear();
                stableTicks = 0;
                phase = Phase.CEILING_SETTLE;
            }
            case CEILING_SETTLE -> {
                if (minY.getAsDouble() > floorTop + 0.15) return false;
                var current = motion();
                boolean contact = Math.abs(minY.getAsDouble() - floorTop) < 0.12
                        && current.linearVelocity().lengthSqr() < 0.0064
                        && current.angularVelocity().lengthSqr() < 0.0064;
                stableTicks = contact ? stableTicks + 1 : 0;
                if (stableTicks < 20) return false;
                require(region.control(handle.reference()).isEmpty(), "Ceiling scenario retained a control");
                require(ceilingCells.isEmpty(), "Ceiling fixture was not removed");
                evidence.append("support_ceiling_settled_min_y=").append(minY.getAsDouble()).append('\n');
                if (rider != null) {
                    rider.discard();
                    rider = null;
                }
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

    /** Highest collision box directly over the rider's footprint, or NaN when none covers it. */
    private double structureTopOver(NativeStructureProjectionEntity projection) {
        double top = Double.NaN;
        var feet = rider.getBoundingBox();
        for (var box : projection.worldCollisionBoxes()) {
            if (feet.maxX > box.minX + 1.0E-7 && feet.minX < box.maxX - 1.0E-7
                    && feet.maxZ > box.minZ + 1.0E-7 && feet.minZ < box.maxZ - 1.0E-7) {
                top = Double.isNaN(top) ? box.maxY : Math.max(top, box.maxY);
            }
        }
        return top;
    }

    private void anchorTo(StructureMotionState motion, Vec3 target) {
        region.setControl(handle.reference(), new StructurePhysicsControl.Anchor(
                motion.localCenterOfMass(), target, Vec3.ZERO,
                motion.mass() * 60, motion.mass() * 16, motion.mass() * 40));
    }

    private static double angleBetween(StructurePose previous, StructurePose current) {
        var dot = Math.abs(previous.rotationX() * current.rotationX() + previous.rotationY() * current.rotationY()
                + previous.rotationZ() * current.rotationZ() + previous.rotationW() * current.rotationW());
        return 2.0 * Math.acos(Math.min(1.0, dot));
    }

    private StructureMotionState motion() {
        return region.motion(handle.reference()).orElseThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
