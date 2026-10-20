package org.academy.internal.common.sublevel;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.academy.api.common.structure.StructureContactResolver;
import org.academy.api.common.structure.StructurePose;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Shared client/server contact resolver for plot-backed structures. Entity movement is clipped
 * against the actual oriented cuboids; a world AABB only selects broad-phase candidates. The
 * same rules run on both sides, so prediction and authority agree.
 */
public final class SubLevelContactResolver implements StructureContactResolver {
    /** Upper bound on oriented boxes tested for one entity movement. */
    public static final int MAX_CANDIDATE_BOXES = 768;
    private static final int DEFAULT_STRUCTURES = 8;
    private static final int DEFAULT_PAIRS = 256;
    private static final int DEFAULT_ITERATIONS = 2;
    private static final double DEFAULT_MAX_CORRECTION = 0.55;
    private static final double EPSILON = 1.0E-7;
    private static final double SUPPORT_TOLERANCE = 0.35;
    private static final double MAX_CARRY_DISTANCE_SQUARED = 16.0;

    /** Entry point for the entity movement mixin after vanilla block clipping. */
    public static Vec3 resolveMovement(Entity entity, Vec3 requested, Vec3 vanilla) {
        if (entity == null || entity.noPhysics || entity.isRemoved()) return vanilla;
        if (requested.lengthSqr() <= 1.0E-12 && vanilla.lengthSqr() <= 1.0E-12) return vanilla;
        var level = entity.level();
        if (level == null) return vanilla;
        var index = SubLevelCollisionRuntime.index(level).orElse(null);
        if (index == null || index.size() == 0) return vanilla;
        var box = entity.getBoundingBox();
        var sweep = box.expandTowards(requested).inflate(Math.max(0.6, entity.maxUpStep()) + 0.15);
        var entries = index.query(sweep);
        if (entries.isEmpty()) return vanilla;
        var obstacles = obstacles(entries, sweep, level.dimension().identifier());
        if (obstacles.isEmpty()) return vanilla;
        // Re-solve both sets together. Clipping only the vanilla result is not safe: the
        // structure may change the intermediate box or add a step below a host ceiling.
        var hostShapes = Entity.collectAllColliders(entity, level, sweep);
        var result = clipMovement(box, requested, obstacles, hostShapes,
                entity.maxUpStep(), entity.onGround(), 0, true);
        return result.movement();
    }

    @Override
    public List<Candidate> broadPhase(Level host, Entity entity, Vec3 requestedMovement, Limits limits) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(requestedMovement, "requestedMovement");
        var limitsValue = safeLimits(limits);
        var sweep = entity.getBoundingBox().expandTowards(requestedMovement)
                .inflate(Math.max(0.6, entity.maxUpStep()) + 0.15);
        var index = SubLevelCollisionRuntime.index(host).orElse(null);
        if (index == null) return List.of();
        var entries = index.query(sweep);
        var result = new ArrayList<Candidate>(Math.min(entries.size(), limitsValue.candidateStructures()));
        for (var entry : entries) {
            if (result.size() >= limitsValue.candidateStructures()) break;
            result.add(new Candidate(new org.academy.api.common.structure.StructureRef(entry.id().value(),
                    host.dimension().identifier(), SubLevelStructureRefs.BACKEND, 0),
                    entry.geometryRevision(), entry.poseRevision(), entry.previousPose(), entry.pose(),
                    entry.localBoxes(), entry.worldBounds()));
        }
        return List.copyOf(result);
    }

    @Override
    public ContactFrame narrowPhase(Level host, Entity entity, Vec3 requestedMovement,
                                    List<Candidate> candidates, Limits limits) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(requestedMovement, "requestedMovement");
        Objects.requireNonNull(candidates, "candidates");
        var limitsValue = safeLimits(limits);
        var obstacles = new ArrayList<Obstacle>();
        for (var candidate : candidates) {
            for (var local : candidate.localCollisionBoxes()) {
                obstacles.add(new Obstacle(SubLevelOrientedCollision.Box.of(local, candidate.currentPose()),
                        candidate.reference(), candidate.geometryVersion(), candidate.poseVersion(),
                        local.getCenter()));
                if (obstacles.size() >= Math.max(1, limitsValue.shapePairs())) break;
            }
            if (obstacles.size() >= Math.max(1, limitsValue.shapePairs())) break;
        }
        var sweep = entity.getBoundingBox().expandTowards(requestedMovement)
                .inflate(Math.max(0.6, entity.maxUpStep()) + 0.15);
        var result = clipMovement(entity.getBoundingBox(), requestedMovement, obstacles,
                Entity.collectAllColliders(entity, host, sweep), entity.maxUpStep(),
                entity.onGround(), Math.max(0, limitsValue.shapePairs()), true);
        Contact support = null;
        for (var contact : result.contacts()) {
            if (contact.worldNormal().y > 0.5) {
                support = contact;
                break;
            }
        }
        if (support == null && result.contacts().isEmpty()) {
            for (var candidate : candidates) {
                if (SubLevelOrientedCollision.supports(candidate.localCollisionBoxes(), candidate.currentPose(),
                        entity.getBoundingBox(), SUPPORT_TOLERANCE)) {
                    support = new Contact(candidate.reference(), candidate.geometryVersion(), candidate.poseVersion(),
                            Vec3.ZERO, entity.position(), new Vec3(0, 1, 0), 0, 0);
                    break;
                }
            }
        }
        return new ContactFrame(entity.getUUID(), levelTick(host), result.contacts(), result.movement(),
                Vec3.ZERO, java.util.Optional.ofNullable(support), result.exhausted());
    }

    @Override
    public CarryResult applyCarry(ServerLevel host, Entity entity, ContactFrame frame, Limits limits) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(frame, "frame");
        if (host.getGameTime() != frame.simulationTick()) return new CarryResult(Vec3.ZERO, false, true);
        var contact = frame.support().orElse(null);
        if (contact == null) return new CarryResult(Vec3.ZERO, false, true);
        var runtime = SubLevelServer.runtime(host).orElse(null);
        if (runtime == null) return new CarryResult(Vec3.ZERO, false, true);
        var id = SubLevelStructureRefs.id(contact.reference()).orElse(null);
        var subLevel = id == null ? null : runtime.get(id).orElse(null);
        if (subLevel == null) return new CarryResult(Vec3.ZERO, false, true);
        var moved = carry(host, entity, subLevel.carryPose(), subLevel.pose(),
                runtime.localCollisionBoxes(id), new HashSet<>(), limits);
        return new CarryResult(moved, moved.lengthSqr() > 0, moved.lengthSqr() <= 0);
    }

    @Override
    public void clear(Level host, UUID entityId) {
        // Carry decisions are recomputed from the live pose and support every tick; no history is retained.
    }

    /**
     * Applies at most one rigid carry for an entity supported by the structure's previous pose.
     * The caller owns the per-tick applied set so two structures cannot move the same body twice.
     */
    public static Vec3 carry(ServerLevel host, Entity entity, StructurePose previousPose, StructurePose currentPose,
                             List<AABB> localBoxes, Set<Entity> applied, Limits limits) {
        if (entity == null || entity.isRemoved() || entity.noPhysics || entity.isPassenger()
                || entity.isSpectator() || !entity.isAlive()) return Vec3.ZERO;
        if (applied.contains(entity)) return Vec3.ZERO;
        if (previousPose.equals(currentPose) || localBoxes.isEmpty()) return Vec3.ZERO;
        if (!SubLevelOrientedCollision.supports(localBoxes, previousPose, entity.getBoundingBox(), SUPPORT_TOLERANCE)) {
            return Vec3.ZERO;
        }
        var feetLocal = previousPose.worldToLocalPoint(entity.position());
        var target = currentPose.localToWorldPoint(feetLocal);
        var move = target.subtract(entity.position());
        if (!finite(move)) return Vec3.ZERO;
        var maximum = limits == null ? DEFAULT_MAX_CORRECTION : Math.max(0, limits.maxCorrection());
        if (move.lengthSqr() > Math.max(MAX_CARRY_DISTANCE_SQUARED, maximum * maximum * 4)) return Vec3.ZERO;
        // A moving support cannot teleport its rider through host walls, ceilings or entities.
        var hostShapes = Entity.collectAllColliders(entity, host, entity.getBoundingBox().expandTowards(move));
        move = clipHostMovement(entity.getBoundingBox(), move, hostShapes);
        if (move.lengthSqr() <= 1.0E-10) return Vec3.ZERO;
        carried(host, entity, move, applied);
        return move;
    }

    private static void carried(ServerLevel host, Entity root, Vec3 move, Set<Entity> applied) {
        if (!applied.add(root)) return;
        var passengers = List.copyOf(root.getPassengers());
        var targets = new ArrayList<Vec3>(passengers.size());
        for (var passenger : passengers) {
            targets.add(passenger.position().add(move));
            applied.add(passenger);
        }
        root.teleportTo(root.getX() + move.x, root.getY() + move.y, root.getZ() + move.z);
        for (int index = 0; index < passengers.size(); index++) {
            var target = targets.get(index);
            passengers.get(index).teleportTo(target.x, target.y, target.z);
        }
    }

    static ClipResult clipMovement(AABB box, Vec3 movement, List<Obstacle> obstacles,
                                           List<VoxelShape> hostShapes,
                                           double maxUpStep, boolean grounded, int contactBudget,
                                           boolean allowStep) {
        var contacts = new ArrayList<Contact>();
        var push = Vec3.ZERO;
        for (var obstacle : obstacles) {
            var candidate = SubLevelOrientedCollision.depenetration(obstacle.box(), box);
            if (candidate.lengthSqr() > push.lengthSqr()) push = candidate;
        }
        if (push.lengthSqr() > 0) {
            push = clipHostMovement(box, push, hostShapes);
            box = box.move(push);
        }
        var resolved = Vec3.ZERO;
        var order = Math.abs(movement.x) < Math.abs(movement.z)
                ? new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X}
                : new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z};
        for (var axis : order) {
            double delta = switch (axis) {
                case X -> movement.x;
                case Y -> movement.y;
                case Z -> movement.z;
            };
            if (Math.abs(delta) <= EPSILON) continue;
            var moved = box.move(resolved);
            double allowed = Shapes.collide(axis, moved, hostShapes, delta);
            for (var obstacle : obstacles) {
                var clip = SubLevelOrientedCollision.clip(obstacle.box(), moved, axis, delta);
                if (!clip.blocked() || Math.abs(clip.movement()) >= Math.abs(allowed)) continue;
                allowed = clip.movement();
                if (contactBudget > 0 && contacts.size() < contactBudget) {
                    contacts.add(new Contact(obstacle.reference(), obstacle.geometryVersion(), obstacle.poseVersion(),
                            obstacle.localAnchor(), clip.point(), clip.normal(), 0.0, clip.toi()));
                }
            }
            resolved = resolved.with(axis, allowed);
        }
        boolean horizontalBlocked = Math.abs(resolved.x - movement.x) > EPSILON
                || Math.abs(resolved.z - movement.z) > EPSILON;
        boolean verticalCollision = Math.abs(resolved.y - movement.y) > EPSILON;
        if (allowStep && maxUpStep > 0.0 && horizontalBlocked
                && (grounded || verticalCollision && movement.y < 0.0)) {
            var groundedBox = verticalCollision ? box.move(0, resolved.y, 0) : box;
            var heights = stepHeights(obstacles, hostShapes, groundedBox, movement, maxUpStep, resolved.y);
            double horizontal = resolved.x * resolved.x + resolved.z * resolved.z;
            for (double height : heights) {
                var step = clipMovement(groundedBox, new Vec3(movement.x, height, movement.z),
                        obstacles, hostShapes, 0, true, 0, false);
                double candidate = step.movement().x * step.movement().x
                        + step.movement().z * step.movement().z;
                if (candidate <= horizontal + EPSILON) continue;
                double distanceToGround = box.minY - groundedBox.minY;
                return new ClipResult(step.movement().subtract(0, distanceToGround, 0).add(push),
                        step.contacts(), step.exhausted()
                        || contactBudget > 0 && contacts.size() >= contactBudget);
            }
        }
        return new ClipResult(resolved.add(push), contacts,
                contactBudget > 0 && contacts.size() >= contactBudget);
    }

    static Vec3 clipHostMovement(AABB box, Vec3 movement, List<VoxelShape> shapes) {
        var resolved = Vec3.ZERO;
        for (var axis : Direction.axisStepOrder(movement)) {
            double delta = movement.get(axis);
            if (Math.abs(delta) <= EPSILON) continue;
            resolved = resolved.with(axis, Shapes.collide(axis, box.move(resolved), shapes, delta));
        }
        return resolved;
    }

    private static double[] stepHeights(List<Obstacle> obstacles, List<VoxelShape> hostShapes,
                                        AABB grounded, Vec3 movement, double maxUpStep,
                                        double verticalMovement) {
        var values = new TreeSet<Double>();
        double limit = grounded.minY + maxUpStep;
        var sweep = grounded.expandTowards(movement.x, maxUpStep, movement.z);
        for (var shape : hostShapes) {
            for (double y : shape.getCoords(Direction.Axis.Y)) {
                double relative = y - grounded.minY;
                if (relative > EPSILON && relative <= maxUpStep + EPSILON
                        && Math.abs(relative - verticalMovement) > 1.0E-5) values.add(relative);
            }
        }
        for (var obstacle : obstacles) {
            if (!obstacle.box().bounds().intersects(sweep)) continue;
            var box = obstacle.box();
            for (int corner = 0; corner < 8; corner++) {
                double y = box.centerY()
                        + ((corner & 1) == 0 ? -1 : 1) * box.halfY() * box.axisYY()
                        + ((corner & 2) == 0 ? -1 : 1) * box.halfX() * box.axisXY()
                        + ((corner & 4) == 0 ? -1 : 1) * box.halfZ() * box.axisZY();
                if (y < grounded.minY - EPSILON || y > limit + EPSILON) continue;
                double relative = y - grounded.minY;
                if (relative <= EPSILON || Math.abs(relative - verticalMovement) <= 1.0E-5) continue;
                values.add(relative);
            }
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static List<Obstacle> obstacles(List<SubLevelCollisionIndex.Entry> entries, AABB sweep,
                                             net.minecraft.resources.Identifier dimension) {
        var result = new ArrayList<Obstacle>();
        for (var entry : entries) {
            var localQuery = inverseBounds(sweep, entry.pose());
            for (var local : entry.localBoxesIn(localQuery)) {
                var reference = new org.academy.api.common.structure.StructureRef(entry.id().value(),
                        dimension, SubLevelStructureRefs.BACKEND, 0);
                result.add(new Obstacle(SubLevelOrientedCollision.Box.of(local, entry.pose()), reference,
                        entry.geometryRevision(), entry.poseRevision(), local.getCenter()));
                if (result.size() >= MAX_CANDIDATE_BOXES) return result;
            }
        }
        return result;
    }

    private static AABB inverseBounds(AABB worldBounds, StructurePose pose) {
        AABB result = null;
        for (int corner = 0; corner < 8; corner++) {
            var point = pose.worldToLocalPoint(new Vec3(
                    (corner & 1) == 0 ? worldBounds.minX : worldBounds.maxX,
                    (corner & 2) == 0 ? worldBounds.minY : worldBounds.maxY,
                    (corner & 4) == 0 ? worldBounds.minZ : worldBounds.maxZ));
            var unit = new AABB(point, point);
            result = result == null ? unit : result.minmax(unit);
        }
        return Objects.requireNonNull(result);
    }

    private static long levelTick(Level level) {
        return level.getGameTime();
    }

    private static Limits safeLimits(Limits limits) {
        if (limits == null) return new Limits(DEFAULT_STRUCTURES, DEFAULT_PAIRS, DEFAULT_ITERATIONS, DEFAULT_MAX_CORRECTION);
        return new Limits(Math.max(1, limits.candidateStructures()), Math.max(1, limits.shapePairs()),
                Math.max(1, limits.iterations()),
                Double.isFinite(limits.maxCorrection()) ? Math.max(0, limits.maxCorrection()) : DEFAULT_MAX_CORRECTION);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    record Obstacle(SubLevelOrientedCollision.Box box,
                            org.academy.api.common.structure.StructureRef reference,
                            long geometryVersion, long poseVersion, Vec3 localAnchor) {
    }

    record ClipResult(Vec3 movement, List<Contact> contacts, boolean exhausted) {
    }
}
