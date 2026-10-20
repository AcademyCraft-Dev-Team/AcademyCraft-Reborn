package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.api.common.structure.*;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Shared world-space queries for skills, programs, and other server-side actors. */
public final class SubLevelQueries {
    private SubLevelQueries() {}

    public static Optional<StructureHandle> resolve(MinecraftServer server, StructureRef reference) {
        if (!server.isSameThread()) throw new IllegalStateException("SubLevel queries require the server thread");
        var host = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, reference.dimension()));
        var runtime = host == null ? null : SubLevelServer.runtime(host).orElse(null);
        var id = SubLevelStructureRefs.id(reference).orElse(null);
        if (runtime == null || id == null
                || !runtime.host().dimension().identifier().equals(reference.dimension())) return Optional.empty();
        var subLevel = runtime.get(id).orElse(null);
        var activation = SubLevelActivationRegistry.find(id).orElse(null);
        if (subLevel == null || activation == null || activation.generation() != reference.generation()
                || !active(subLevel)) return Optional.empty();
        return Optional.of(new SubLevelStructureHandle(subLevel, runtime.storage(), reference.dimension()));
    }

    public static Optional<StructureDynamics.State> motion(ServerLevel host, StructureRef reference) {
        var handle = resolve(host.getServer(), reference).orElse(null);
        var runtime = SubLevelServer.runtime(host).orElse(null);
        if (handle == null || runtime == null || runtime.host() != host) return Optional.empty();
        return runtime.universe().motion(new SubLevelId(reference.id())).map(motion ->
                new StructureDynamics.State(handle.pose(),
                        new Vec3(motion.linearVelocity().x(), motion.linearVelocity().y(), motion.linearVelocity().z()),
                        new Vec3(motion.angularVelocity().x(), motion.angularVelocity().y(), motion.angularVelocity().z()),
                        new Vec3(motion.localCenterOfMass().x(), motion.localCenterOfMass().y(), motion.localCenterOfMass().z()),
                        motion.mass()));
    }

    /** Caller validates the bounded ray; missing host chunks conservatively occlude it. */
    public static Optional<StructureHit> raycast(ServerLevel host, Vec3 from, Vec3 to, ClipContext.Block shape) {
        var runtime = SubLevelServer.runtime(host).orElse(null);
        if (runtime == null || runtime.host() != host) return Optional.empty();
        var terrain = new HostBlocks(host).clip(new ClipContext(from, to, shape,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        double nearestDistance = terrain.getType() == HitResult.Type.MISS
                ? Double.POSITIVE_INFINITY : from.distanceToSqr(terrain.getLocation());
        StructureHit nearest = null;
        for (var subLevel : runtime.subLevels()) {
            if (!active(subLevel)) continue;
            var pose = subLevel.pose();
            var hit = runtime.view(subLevel.id()).clip(new ClipContext(pose.worldToLocalPoint(from),
                    pose.worldToLocalPoint(to), shape, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.BLOCK) continue;
            var world = pose.localToWorldPoint(hit.getLocation());
            double distance = from.distanceToSqr(world);
            if (distance >= nearestDistance - 1.0E-7) continue;
            var activation = SubLevelActivationRegistry.activation(subLevel.id());
            var ref = SubLevelStructureRefs.of(subLevel, host.dimension().identifier(), activation.generation());
            nearest = new StructureHit(new SpaceBlockRef(ref, hit.getBlockPos()), activation.runtimeId(),
                    subLevel.geometryRevision(), pose, hit.getDirection(), hit.getLocation(), world, hit.isInside());
            nearestDistance = distance;
        }
        return Optional.ofNullable(nearest);
    }

    public static StructureHitValidation validate(ServerLevel host, Vec3 from, Vec3 to, StructureHit proposed) {
        return validate(host, from, to, proposed, ClipContext.Block.OUTLINE);
    }

    public static StructureHitValidation validate(ServerLevel host, Vec3 from, Vec3 to, StructureHit proposed,
                                                   ClipContext.Block shape) {
        if (!host.dimension().identifier().equals(proposed.block().structure().dimension())) {
            return StructureHitValidation.failure(StructureHitValidation.Status.NOT_AVAILABLE);
        }
        var handle = resolve(host.getServer(), proposed.block().structure()).orElse(null);
        if (handle == null) return StructureHitValidation.failure(StructureHitValidation.Status.NOT_AVAILABLE);
        if (!handle.runtimeId().equals(proposed.runtimeId()) || handle.geometryRevision() != proposed.geometryRevision()
                || !handle.pose().equals(proposed.pose())) {
            return StructureHitValidation.failure(StructureHitValidation.Status.STALE);
        }
        var current = StructureQueries.raycast(host, from, to, shape).orElse(null);
        if (current == null || !current.block().equals(proposed.block())
                || current.localFace() != proposed.localFace() || current.inside() != proposed.inside()
                || current.localPoint().distanceToSqr(proposed.localPoint()) > 1.0 / 4096
                || current.worldPoint().distanceToSqr(proposed.worldPoint()) > 1.0 / 4096) {
            return StructureHitValidation.failure(StructureHitValidation.Status.NOT_VISIBLE);
        }
        return StructureHitValidation.valid(current);
    }

    private static boolean active(SubLevel subLevel) {
        return subLevel.state() == SubLevelState.ACTIVE || subLevel.state() == SubLevelState.SLEEPING;
    }

    private record HostBlocks(ServerLevel host) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos pos) {
            if (pos.getY() < getMinY() || pos.getY() >= getMinY() + getHeight()) return Blocks.AIR.defaultBlockState();
            var chunk = host.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk == null ? Blocks.BARRIER.defaultBlockState() : chunk.getBlockState(pos);
        }
        @Override public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            var chunk = host.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            return chunk == null ? null : chunk.getBlockEntities().get(pos);
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return host.getHeight(); }
        @Override public int getMinY() { return host.getMinY(); }
    }
}
