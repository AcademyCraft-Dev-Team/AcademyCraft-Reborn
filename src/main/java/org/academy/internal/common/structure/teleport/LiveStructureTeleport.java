package org.academy.internal.common.structure.teleport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.structure.*;
import org.academy.internal.common.structure.StructureBackendRouter;
import org.academy.internal.common.structure.upgrade.control.OrientationBackendAccess;
import org.academy.internal.common.structure.upgrade.control.TranslationSweep;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;

import static org.academy.api.common.structure.StructureTeleports.Result;

/** Loaded-only, bounded destination validation; storage addresses never enter permissions. */
public final class LiveStructureTeleport {
    public static final int MAX_CELLS = 32_768;
    private static final int MAX_PAIRS = 262_144;
    private LiveStructureTeleport() {}

    public static Result check(ServerLevel host, Entity actor, StructureTeleports.Request request) {
        return prepare(host, actor, request, Set.of(request.reference()), null).result;
    }

    public static Result teleport(ServerLevel host, Entity actor, StructureTeleports.Request request) {
        return teleportBatch(host, actor, List.of(request));
    }

    public static Result teleportBatch(ServerLevel host, Entity actor, List<StructureTeleports.Request> requests) {
        return teleportCarryingTerrain(host, actor, requests, Map.of());
    }

    /** Only atomic chunk payload transfer supplies these pre-exchange collision observations. */
    public static Result teleportCarryingTerrain(ServerLevel host, Entity actor, List<StructureTeleports.Request> requests,
                                                 Map<StructureRef, CarriedTerrain> carriedTerrain) {
        if (requests.isEmpty() || requests.size() > 8) return Result.BUDGET_EXCEEDED;
        var ignored = new HashSet<StructureRef>();
        for (var request : requests) if (!ignored.add(request.reference())) return Result.DENIED;
        var plans = new ArrayList<Plan>();
        for (var request : requests) {
            var plan = prepare(host, actor, request, ignored, carriedTerrain.get(request.reference()));
            if (plan.result != Result.READY) return plan.result;
            plans.add(plan);
        }
        long pairs = 0;
        for (int i = 0; i < plans.size(); i++) for (int j = i + 1; j < plans.size(); j++) {
            var first = plans.get(i); var second = plans.get(j);
            pairs += (long) first.body.localBoxes().size() * second.body.localBoxes().size();
            if (pairs > MAX_PAIRS) return Result.BUDGET_EXCEEDED;
            for (var a : first.body.localBoxes()) for (var b : second.body.localBoxes()) {
                if (TranslationSweep.firstHit(TranslationSweep.Box.of(a, first.destination), Vec3.ZERO,
                        TranslationSweep.Box.of(b, second.destination)).isPresent()) return Result.BLOCKED;
            }
        }
        var control = UUID.randomUUID();
        var acquired = new ArrayList<StructureRef>();
        var moved = new ArrayList<Plan>();
        try {
            for (var plan : plans) {
                if (StructureDirectControl.acquire(host, plan.body.reference(), actor, control) != StructureDirectControl.Status.ACTIVE) {
                    return Result.OCCUPIED;
                }
                acquired.add(plan.body.reference());
            }
            for (var plan : plans) {
                boolean applied;
                try {
                    applied = StructureDirectControl.matches(plan.body.reference(), actor.getUUID(), control, plan.body.runtimeId())
                        && plan.access.teleport(host, plan.body, plan.destination,
                        StructureTeleportMath.rotateMotion(plan.body.motion().pose(), plan.destination, plan.body.linearVelocity()),
                        StructureTeleportMath.rotateMotion(plan.body.motion().pose(), plan.destination, plan.body.motion().angularVelocity()));
                } catch (RuntimeException failure) {
                    // A foreign solver may have moved the pose before throwing. Include this attempted
                    // member, still under the exact lease, when restoring this synchronous batch.
                    moved.add(plan);
                    try { restore(host, moved); } catch (RuntimeException restoration) { failure.addSuppressed(restoration); }
                    throw failure;
                }
                if (!applied) {
                    restore(host, moved);
                    return Result.UNAVAILABLE;
                }
                moved.add(plan);
            }
            try {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new StructureTeleportedEvent(host, actor,
                        plans.stream().map(plan -> new StructureTeleportedEvent.Relocation(plan.body.reference(),
                                plan.body.runtimeId(), plan.body.motion().pose(), plan.destination)).toList()));
            } catch (RuntimeException observerFailure) {
                org.academy.AcademyCraft.LOGGER.error("Structure relocation committed but a notification observer failed", observerFailure);
            }
            return Result.TELEPORTED;
        } finally { for (var reference : acquired) StructureDirectControl.release(reference, control); }
    }

    private static void restore(ServerLevel host, List<Plan> moved) {
        for (int index = moved.size() - 1; index >= 0; index--) {
            var previous = moved.get(index);
            var current = previous.access.observe(host, previous.body.reference()).orElse(null);
            if (current == null || !current.runtimeId().equals(previous.body.runtimeId())
                    || current.contentRevision() != previous.body.contentRevision()
                    || !previous.access.teleport(host, current, previous.body.motion().pose(),
                    previous.body.linearVelocity(), previous.body.motion().angularVelocity())) {
                throw new IllegalStateException("Unable to restore partially relocated structure batch");
            }
        }
    }

    private static Plan prepare(ServerLevel host, Entity actor, StructureTeleports.Request request, Set<StructureRef> ignored,
                                CarriedTerrain carriedTerrain) {
        if (!host.getServer().isSameThread()) throw new IllegalStateException("Structure teleport requires the server thread");
        if (!actor.isAlive() || actor.isRemoved() || actor.level() != host
                || !host.dimension().identifier().equals(request.reference().dimension())) return Plan.failure(Result.UNAVAILABLE);
        if (StructureDirectControl.occupied(request.reference(), request.runtimeId())) return Plan.failure(Result.OCCUPIED);
        var probe = StructureBackendRouter.probeDirectControl(host, request.reference());
        if (probe.state() == StructureDirectControl.Probe.State.OCCUPIED) return Plan.failure(Result.OCCUPIED);
        if (probe.state() != StructureDirectControl.Probe.State.READY || !request.runtimeId().equals(probe.runtimeId())) {
            return Plan.failure(Result.UNAVAILABLE);
        }
        var access = StructureBackendRouter.orientationAccess(host, request.reference()).orElse(null);
        var body = access == null ? null : access.observe(host, request.reference()).orElse(null);
        if (body == null || !request.runtimeId().equals(body.runtimeId()) || !body.terrainReady()) return Plan.failure(Result.UNAVAILABLE);
        var destination = StructureTeleportMath.destination(body.motion().pose(), body.motion().localCenterOfMass(),
                request.destinationCenter(), request.rotation());
        var boxes = body.localBoxes().stream().map(box -> TranslationSweep.Box.of(box, destination)).toList();
        var bounds = boxes.stream().map(TranslationSweep.Box::bounds).reduce(AABB::minmax).orElseThrow();
        if (bounds.minY < host.getMinY() || bounds.maxY > host.getMaxY()
                || !host.getWorldBorder().isWithinBounds(bounds)) return Plan.failure(Result.BLOCKED);
        var sourceBounds = body.localBoxes().stream().map(box -> TranslationSweep.Box.of(box, body.motion().pose()).bounds())
                .reduce(AABB::minmax).orElseThrow();
        var sourcePermission = permission(host, actor, sourceBounds);
        if (sourcePermission != Result.READY) return Plan.failure(sourcePermission);
        var destinationPermission = permission(host, actor, bounds);
        if (destinationPermission != Result.READY) return Plan.failure(destinationPermission);
        var obstacles = new ArrayList<TranslationSweep.Box>();
        int maximumObstacles = MAX_PAIRS / boxes.size();
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(Math.nextDown(bounds.maxX), Math.nextDown(bounds.maxY), Math.nextDown(bounds.maxZ)))) {
            var state = host.getBlockState(pos);
            for (var box : state.getCollisionShape(host, pos).toAabbs()) {
                if (carriedTerrain != null && carriedTerrain.permits(body.runtimeId(), body.motion().pose(), destination, box.move(pos))) continue;
                obstacles.add(TranslationSweep.Box.axisAligned(box.move(pos)));
                if (obstacles.size() > Math.min(MAX_CELLS, maximumObstacles)) return Plan.failure(Result.BUDGET_EXCEEDED);
            }
        }
        for (var backend : StructureBackendRouter.orientationBackends(host)) {
            var nearby = backend.nearby(host, bounds, 256);
            if (!nearby.complete()) return Plan.failure(Result.UNAVAILABLE);
            for (var other : nearby.bodies()) {
                if (ignored.contains(other.reference())) continue;
                for (var local : other.localBoxes()) {
                    var obstacle = TranslationSweep.Box.of(local, other.motion().pose());
                    if (!obstacle.bounds().intersects(bounds)) continue;
                    obstacles.add(obstacle);
                    if (obstacles.size() > maximumObstacles) return Plan.failure(Result.BUDGET_EXCEEDED);
                }
            }
        }
        for (var entity : host.getEntities((Entity) null, bounds, entity -> entity.isAlive() && !entity.isSpectator()
                && entity.isPickable())) {
            obstacles.add(TranslationSweep.Box.axisAligned(entity.getBoundingBox()));
            if (obstacles.size() > maximumObstacles) return Plan.failure(Result.BUDGET_EXCEEDED);
        }
        if ((long) boxes.size() * obstacles.size() > MAX_PAIRS) return Plan.failure(Result.BUDGET_EXCEEDED);
        for (var moving : boxes) for (var obstacle : obstacles) {
            if (TranslationSweep.firstHit(moving, Vec3.ZERO, obstacle).isPresent()) return Plan.failure(Result.BLOCKED);
        }
        return new Plan(Result.READY, access, body, destination);
    }

    private static Result permission(ServerLevel host, Entity actor, AABB bounds) {
        var min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        var max = BlockPos.containing(Math.nextDown(bounds.maxX), Math.nextDown(bounds.maxY), Math.nextDown(bounds.maxZ));
        long cells = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (cells <= 0 || cells > MAX_CELLS) return Result.BUDGET_EXCEEDED;
        for (int x = (min.getX() - 1) >> 4; x <= (max.getX() + 1) >> 4; x++) {
            for (int z = (min.getZ() - 1) >> 4; z <= (max.getZ() + 1) >> 4; z++) {
                if (host.getChunkSource().getChunkNow(x, z) == null) return Result.UNAVAILABLE;
            }
        }
        for (var pos : BlockPos.betweenClosed(min, max)) {
            if (!actor.mayInteract(host, pos) || !host.mayInteract(actor, pos)) return Result.DENIED;
        }
        return Result.READY;
    }

    private record Plan(Result result, OrientationBackendAccess access, OrientationBackendAccess.Body body,
                        StructurePose destination) {
        static Plan failure(Result result) { return new Plan(result, null, null, null); }
    }

    /**
     * Existing host contact travels only with exactly the same chunk collision image and pure
     * translation. No obstacle is shrunk and no foreign target contact receives an allowance.
     */
    public record CarriedTerrain(UUID runtimeId, StructurePose source, Vec3 displacement, Set<AABB> destinationShapes) {
        public CarriedTerrain {
            destinationShapes = Set.copyOf(destinationShapes);
            if (destinationShapes.size() > MAX_CELLS) throw new IllegalArgumentException("Carried terrain exceeds budget");
        }

        public boolean permits(UUID currentRuntime, StructurePose current, StructurePose target, AABB obstacle) {
            return runtimeId.equals(currentRuntime) && source.equals(current) && destinationShapes.contains(obstacle)
                    && target.worldOrigin().distanceToSqr(source.worldOrigin().add(displacement)) < 1E-16
                    && sameRotation(source, target);
        }

        private static boolean sameRotation(StructurePose first, StructurePose second) {
            var a = first.rotation(); var b = second.rotation();
            double minus = square(a.x - b.x) + square(a.y - b.y) + square(a.z - b.z) + square(a.w - b.w);
            double plus = square(a.x + b.x) + square(a.y + b.y) + square(a.z + b.z) + square(a.w + b.w);
            return Math.min(minus, plus) < 1E-24;
        }
        private static double square(double value) { return value * value; }
    }
}
