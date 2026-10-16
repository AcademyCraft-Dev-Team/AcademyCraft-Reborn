package org.academy.api.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.academy.api.common.structure.StructureHit;
import org.academy.api.common.structure.StructureQueries;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** World-space aim points for skills, including rotated physical structures on either backend. */
public final class WorldBlockTargets {
    private WorldBlockTargets() {}

    /**
     * A cast-time position, not a block address or a permission to edit blocks. In particular,
     * callers must not turn a structure surface point into a host-world block mutation.
     */
    public record Target(Vec3 position, boolean hit) {}

    public static Vec3 aimedPoint(Entity source, double range) {
        var from = source.getEyePosition();
        return clip(source, from, from.add(source.getLookAngle().scale(range)),
                ClipContext.Block.OUTLINE).position();
    }

    /** Fluids are ignored. Long skill rays are divided into bounded structure queries. */
    public static Target clip(Entity source, Vec3 from, Vec3 to, ClipContext.Block shape) {
        if (!(source.level() instanceof ServerLevel host)) {
            throw new IllegalArgumentException("Skill world targets require a server level");
        }
        return clip(host, source, from, to, shape);
    }

    public static Target clip(ServerLevel host, @Nullable Entity source, Vec3 from, Vec3 to,
                              ClipContext.Block shape) {
        if (!host.getServer().isSameThread()) throw new IllegalStateException("World targets require the server thread");
        if (source != null && source.level() != host) throw new IllegalArgumentException("Source must belong to the host level");
        requireFinite(from);
        requireFinite(to);
        double length = from.distanceTo(to);
        if (length < 1.0E-6) return new Target(to, false);
        if (!Double.isFinite(length) || length > 4096) throw new IllegalArgumentException("Skill ray exceeds 4096 blocks");
        int segments = (int) Math.ceil(length / 255.0);
        var terrain = new HostBlocks(host);
        for (int index = 0; index < segments; index++) {
            var start = from.lerp(to, (double) index / segments);
            var end = from.lerp(to, (double) (index + 1) / segments);
            // Level.clip can return Sable's remote plot coordinates. A plain getter cannot.
            var hostHit = terrain.clip(new ClipContext(start, end, shape, ClipContext.Fluid.NONE,
                    source == null ? CollisionContext.empty() : CollisionContext.of(source)));
            boolean hostBlocked = hostHit.getType() != HitResult.Type.MISS;
            double visibleLength = hostBlocked ? start.distanceTo(hostHit.getLocation()) : start.distanceTo(end);
            // Do not require loaded data behind the first host obstacle (including an unloaded chunk).
            var queryEnd = hostBlocked
                    ? start.add(end.subtract(start).normalize().scale(Math.max(0, visibleLength - 1.0E-4))) : end;
            var structure = start.distanceToSqr(queryEnd) < 1.0E-12
                    ? Optional.<StructureHit>empty()
                    : StructureQueries.raycast(host, start, queryEnd, shape);
            var result = nearest(start, end,
                    hostHit.getType() == HitResult.Type.MISS ? null : hostHit.getLocation(),
                    structure.isEmpty() ? null : structure.get().worldPoint());
            if (result.hit()) return result;
        }
        return new Target(to, false);
    }

    static Target nearest(Vec3 from, Vec3 to, @Nullable Vec3 terrain, @Nullable Vec3 structure) {
        Vec3 nearest = onSegment(from, to, terrain) ? terrain : null;
        if (onSegment(from, to, structure)
                && (nearest == null || from.distanceToSqr(structure) < from.distanceToSqr(nearest) - 1.0E-7)) {
            nearest = structure;
        }
        return new Target(nearest == null ? to : nearest, nearest != null);
    }

    private static boolean onSegment(Vec3 from, Vec3 to, @Nullable Vec3 point) {
        if (point == null || !Double.isFinite(point.lengthSqr())) return false;
        var ray = to.subtract(from);
        double lengthSquared = ray.lengthSqr();
        if (lengthSquared < 1.0E-12) return point.distanceToSqr(from) < 1.0E-12;
        double fraction = point.subtract(from).dot(ray) / lengthSquared;
        return fraction >= -1.0E-7 && fraction <= 1.0 + 1.0E-7
                && from.add(ray.scale(fraction)).distanceToSqr(point) < 1.0E-8;
    }

    private static void requireFinite(Vec3 point) {
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || Math.abs(point.x) > 30_000_000 || Math.abs(point.z) > 30_000_000) {
            throw new IllegalArgumentException("World target coordinates must be finite and inside the world");
        }
    }

    /** Never loads host chunks. Unknown terrain stops a ray rather than becoming transparent. */
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
