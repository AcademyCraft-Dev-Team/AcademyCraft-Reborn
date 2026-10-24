package org.academy.internal.common.sublevel;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.academy.api.common.structure.StructurePose;
import org.academy.api.common.structure.StructureRef;
import org.academy.api.common.structure.StructureTerrainBreakPolicy;
import org.academy.internal.common.structure.NativeStructureCollisionBoxes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded soft-terrain sweep for moving structures. The scan samples the real rotation/translation,
 * inverse-transforms each candidate block into the local grid and tests the local collision cells,
 * so a rotated hollow structure does not destroy empty space inside its world AABB.
 */
public final class SubLevelTerrainBreakPolicy implements StructureTerrainBreakPolicy {
    private static final double MAX_SOFT_HARDNESS = 0.5;
    private static final double SAMPLE_SPACING = 0.22;
    private static final int MAX_SAMPLES = 48;
    private static final int MAX_CELLS_PER_BOX = 64;

    @Override
    public Sweep scan(ServerLevel host, StructureRef reference, StructurePose previousPose,
                      StructurePose currentPose, List<AABB> localCollisionBoxes, Limits limits) {
        requireHost(host);
        requireReference(host.dimension().identifier(), reference);
        var checked = requireLimits(limits);
        if (localCollisionBoxes == null || localCollisionBoxes.isEmpty() || previousPose == null
                || currentPose == null || previousPose.equals(currentPose)) {
            return new Sweep(reference, reference.generation(), host.getGameTime(),
                    previousPose, currentPose, List.of(), false);
        }
        var pivot = localCenter(localCollisionBoxes);
        var swept = sweptContactBounds(localCollisionBoxes, previousPose, currentPose);
        var border = host.getWorldBorder();
        double minX = Math.max(swept.minX, border.getMinX());
        double maxX = Math.min(swept.maxX, border.getMaxX());
        double minZ = Math.max(swept.minZ, border.getMinZ());
        double maxZ = Math.min(swept.maxZ, border.getMaxZ());
        double minY = Math.max(swept.minY, host.getMinY());
        double maxY = Math.min(swept.maxY, host.getMaxY());
        if (!(minX <= maxX && minY <= maxY && minZ <= maxZ)) {
            return new Sweep(reference, reference.generation(), host.getGameTime(),
                    previousPose, currentPose, List.of(), true);
        }
        var from = BlockPos.containing(minX, minY, minZ);
        var to = BlockPos.containing(Math.nextDown(maxX), Math.nextDown(maxY), Math.nextDown(maxZ));
        long cells = (long) (to.getX() - from.getX() + 1) * (to.getY() - from.getY() + 1)
                * (to.getZ() - from.getZ() + 1);
        if (cells <= 0) {
            return new Sweep(reference, reference.generation(), host.getGameTime(),
                    previousPose, currentPose, List.of(), true);
        }
        var grid = new LocalGrid(localCollisionBoxes);
        int samples = sampleCount(localCollisionBoxes, previousPose, currentPose, pivot);
        var candidates = new ArrayList<Candidate>();
        int visited = 0;
        int tests = 0;
        boolean exhausted = false;
        for (var position : BlockPos.betweenClosed(from, to)) {
            if (++visited > checked.visitedCells()) {
                exhausted = true;
                break;
            }
            if (!host.hasChunkAt(position) || host.isOutsideBuildHeight(position)) continue;
            var state = host.getBlockState(position);
            if (!eligible(host, position, state)) continue;
            boolean hit = false;
            for (int sample = 0; sample <= samples; sample++) {
                var pose = previousPose.lerp(currentPose, (double) sample / samples);
                if (++tests > checked.shapeTests()) {
                    exhausted = true;
                    break;
                }
                if (grid.intersects(pose, new AABB(position))) {
                    hit = true;
                    break;
                }
            }
            if (hit) candidates.add(new Candidate(position.immutable(), state));
            if (exhausted) break;
        }
        return new Sweep(reference, reference.generation(), host.getGameTime(), previousPose, currentPose,
                List.copyOf(candidates), exhausted);
    }

    @Override
    public BreakPlan authorize(ServerLevel host, Entity actor, Sweep sweep, boolean dropItems, Limits limits) {
        requireHost(host);
        var checked = requireLimits(limits);
        var permitted = new ArrayList<Candidate>();
        if (sweep == null || actor == null || !SubLevelTerrainBreaker.actorAllowed(host, actor)
                || !host.dimension().identifier().equals(sweep.reference().dimension())) {
            return new BreakPlan(sweep, actor == null ? null : actor.getUUID(), List.of(), dropItems);
        }
        for (var candidate : sweep.candidates()) {
            if (permitted.size() >= checked.destroyedBlocks()) break;
            var position = candidate.position();
            var state = host.getBlockState(position);
            if (!state.equals(candidate.observedState()) || !eligible(host, position, state)) continue;
            var player = playerOrNull(actor);
            if (!mayBreak(host, actor, position)) continue;
            permitted.add(new Candidate(position, state));
        }
        return new BreakPlan(sweep, actor.getUUID(), List.copyOf(permitted), dropItems);
    }

    @Override
    public BreakResult apply(ServerLevel host, Entity actor, BreakPlan plan, Limits limits) {
        requireHost(host);
        var checked = requireLimits(limits);
        if (plan == null || actor == null || plan.sweep() == null
                || !actor.getUUID().equals(plan.actorId())
                || !host.dimension().identifier().equals(plan.sweep().reference().dimension())
                || !SubLevelTerrainBreaker.actorAllowed(host, actor)) {
            return new BreakResult(List.of(), List.of(), false);
        }
        return applyCandidates(host, actor, plan.permitted(), plan.dropItems(), checked, plan.sweep().budgetExhausted());
    }

    /** Explicit skill clearance uses the same permissions, events and drops as moving bodies. */
    public static BreakResult applyCandidates(ServerLevel host, Entity actor, List<Candidate> candidates,
                                              boolean dropItems, Limits limits, boolean budgetExhausted) {
        requireHost(host);
        var checked = requireLimits(limits);
        if (!SubLevelTerrainBreaker.actorAllowed(host, actor)) return new BreakResult(List.of(), List.of(), false);
        var destroyed = new ArrayList<BlockPos>();
        var rejected = new ArrayList<BlockPos>();
        int visited = 0;
        for (var candidate : candidates) {
            if (++visited > checked.visitedCells()) { budgetExhausted = true; break; }
            if (destroyed.size() >= checked.destroyedBlocks()) {
                rejected.add(candidate.position());
                continue;
            }
            var position = candidate.position();
            if (!host.hasChunkAt(position) || host.isOutsideBuildHeight(position)) {
                rejected.add(position);
                continue;
            }
            var state = host.getBlockState(position);
            var player = playerOrNull(actor);
            if (!state.equals(candidate.observedState()) || !eligible(host, position, state)
                    || !mayBreak(host, actor, position)) {
                rejected.add(position);
                continue;
            }
            if (player != null) {
                var event = new BreakBlockEvent(host, position, state, player);
                NeoForge.EVENT_BUS.post(event);
                if (event.isCanceled()) {
                    rejected.add(position);
                    continue;
                }
            }
            // A protection listener may replace the cell or revoke the actor's permission.
            if (!host.getBlockState(position).equals(state)
                    || !SubLevelTerrainBreaker.actorAllowed(host, actor)
                    || !mayBreak(host, actor, position)) {
                rejected.add(position);
                continue;
            }
            if (!host.destroyBlock(position, dropItems, actor)) {
                rejected.add(position);
                continue;
            }
            destroyed.add(position.immutable());
            SubLevelServer.onBlockChanged(host, position);
        }
        return new BreakResult(List.copyOf(destroyed), List.copyOf(rejected),
                budgetExhausted || destroyed.size() >= checked.destroyedBlocks());
    }

    static boolean eligible(ServerLevel host, BlockPos position, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (state.is(SubLevelEnhancementBootstrap.MOTION_PROTECTED)) return false;
        if (state.hasBlockEntity()) return false;
        float hardness = state.getDestroySpeed(host, position);
        if (!Float.isFinite(hardness) || hardness < 0.0f || hardness >= MAX_SOFT_HARDNESS) return false;
        return state.is(SubLevelEnhancementBootstrap.MOTION_BREAKABLE) || defaultSoft(state);
    }

    private static boolean mayBreak(ServerLevel host, Entity actor, BlockPos position) {
        if (!SubLevelTerrainBreaker.actorAllowed(host, actor) || !host.isInWorldBounds(position)
                || !host.getWorldBorder().isWithinBounds(position) || !actor.mayInteract(host, position)
                || !host.mayInteract(actor, position)) return false;
        return !(actor instanceof net.minecraft.server.level.ServerPlayer player)
                || !player.isSpectator() && player.getAbilities().mayBuild;
    }

    /** Default eligibility mirrors low-hardness vegetation; the allow tag widens it. */
    private static boolean defaultSoft(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LEAVES)
                || state.is(net.minecraft.tags.BlockTags.FLOWERS)
                || state.is(net.minecraft.tags.BlockTags.CROPS)
                || state.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock
                || state.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)
                || state.is(net.minecraft.world.level.block.Blocks.TALL_GRASS)
                || state.is(net.minecraft.world.level.block.Blocks.FERN)
                || state.is(net.minecraft.world.level.block.Blocks.LARGE_FERN)
                || state.is(net.minecraft.world.level.block.Blocks.DEAD_BUSH)
                || state.is(net.minecraft.world.level.block.Blocks.VINE)
                || state.is(net.minecraft.world.level.block.Blocks.SNOW)
                || state.is(net.minecraft.world.level.block.Blocks.SUGAR_CANE)
                || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                || state.is(net.minecraft.world.level.block.Blocks.GLOW_LICHEN);
    }

    private static net.minecraft.world.entity.player.@Nullable Player playerOrNull(Entity actor) {
        return actor instanceof net.minecraft.world.entity.player.Player player ? player : null;
    }

    /**
     * Endpoint shapes plus a conservative rotation arc. A sphere around the grid origin can
     * consume the entire cell budget below/beside even a small translating platform, forever
     * starving the leaves directly ahead. Pure translation needs only the endpoint union.
     */
    static AABB sweptContactBounds(List<AABB> boxes, StructurePose previous, StructurePose current) {
        AABB result = null;
        double radius = 0.0;
        for (var box : boxes) {
            var endpoints = NativeStructureCollisionBoxes.transformBox(box, previous)
                    .minmax(NativeStructureCollisionBoxes.transformBox(box, current));
            result = result == null ? endpoints : result.minmax(endpoints);
            double x = Math.max(Math.abs(box.minX), Math.abs(box.maxX));
            double y = Math.max(Math.abs(box.minY), Math.abs(box.maxY));
            double z = Math.max(Math.abs(box.minZ), Math.abs(box.maxZ));
            radius = Math.max(radius, Math.sqrt(x * x + y * y + z * z));
        }
        double dot = Math.abs(previous.rotationX() * current.rotationX()
                + previous.rotationY() * current.rotationY() + previous.rotationZ() * current.rotationZ()
                + previous.rotationW() * current.rotationW());
        double angle = 2.0 * Math.acos(Math.min(1.0, dot));
        // At interpolation t, the rotation differs from the endpoint chord by at most
        // (1-t)*r*angle*t + t*r*angle*(1-t) <= r*angle/2. Origin translation is linear.
        return java.util.Objects.requireNonNull(result).inflate(radius * angle * 0.5);
    }

    private static int sampleCount(List<AABB> boxes, StructurePose previous, StructurePose current, Vec3 pivot) {
        double travel = previous.worldOrigin().distanceTo(current.worldOrigin());
        double radius = 0.0;
        for (var box : boxes) {
            double x = Math.max(Math.abs(box.minX - pivot.x), Math.abs(box.maxX - pivot.x));
            double y = Math.max(Math.abs(box.minY - pivot.y), Math.abs(box.maxY - pivot.y));
            double z = Math.max(Math.abs(box.minZ - pivot.z), Math.abs(box.maxZ - pivot.z));
            radius = Math.max(radius, Math.sqrt(x * x + y * y + z * z));
        }
        double dot = Math.abs(previous.rotationX() * current.rotationX() + previous.rotationY() * current.rotationY()
                + previous.rotationZ() * current.rotationZ() + previous.rotationW() * current.rotationW());
        double arc = 2.0 * Math.acos(Math.min(1.0, dot)) * radius;
        return Math.max(1, Math.min(MAX_SAMPLES, (int) Math.ceil((travel + arc) / SAMPLE_SPACING)));
    }

    private static Vec3 localCenter(List<AABB> boxes) {
        double x = 0, y = 0, z = 0;
        for (var box : boxes) {
            var center = box.getCenter();
            x += center.x;
            y += center.y;
            z += center.z;
        }
        return new Vec3(x / boxes.size(), y / boxes.size(), z / boxes.size());
    }

    private static Limits requireLimits(Limits limits) {
        if (limits == null) return new Limits(4_096, 16_384, 48);
        if (limits.visitedCells() <= 0 || limits.shapeTests() <= 0 || limits.destroyedBlocks() <= 0) {
            throw new IllegalArgumentException("Terrain break limits must be positive");
        }
        return limits;
    }

    static void requireReference(Identifier dimension, StructureRef reference) {
        // Geometry is supplied by the authoritative backend adapter. The host-terrain policy
        // does not resolve or decode its identity; Sable and local bodies share this event path.
        if (reference == null || !reference.dimension().equals(dimension)) {
            throw new IllegalArgumentException("Terrain sweep requires a structure reference in this host");
        }
    }

    private static void requireHost(ServerLevel host) {
        if (host == null || host.getServer() == null || !host.getServer().isSameThread()) {
            throw new IllegalStateException("Terrain break policy requires the owning server thread");
        }
    }

    /** Local cell grid used for exact inverse-transformed block tests. */
    static final class LocalGrid {
        private final List<AABB> boxes;
        private final Map<Long, IntList> cells = new HashMap<>();
        private final int[] marks;
        private final IntList oversized = new IntList();
        private int stamp;
        private final boolean linear;

        LocalGrid(List<AABB> boxes) {
            this.boxes = boxes;
            this.linear = boxes.size() > 32_768;
            this.marks = new int[linear ? 0 : boxes.size()];
            if (linear) return;
            for (int index = 0; index < boxes.size(); index++) {
                var box = boxes.get(index);
                int minX = (int) Math.floor(box.minX), minY = (int) Math.floor(box.minY), minZ = (int) Math.floor(box.minZ);
                int maxX = (int) Math.floor(box.maxX), maxY = (int) Math.floor(box.maxY), maxZ = (int) Math.floor(box.maxZ);
                long count = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
                if (count <= 0 || count > MAX_CELLS_PER_BOX) {
                    // Merged dense bodies commonly exceed the cell-index budget; retain them
                    // in the bounded fallback instead of silently losing their contacts.
                    oversized.add(index);
                    continue;
                }
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            cells.computeIfAbsent(key(x, y, z), ignored -> new IntList()).add(index);
                        }
                    }
                }
            }
        }

        boolean intersects(StructurePose pose, AABB worldBlock) {
            var local = inverse(pose, worldBlock);
            if (linear) {
                for (var box : boxes) if (hits(box, local, pose, worldBlock)) return true;
                return false;
            }
            for (int index = 0; index < oversized.size; index++) {
                if (hits(boxes.get(oversized.values[index]), local, pose, worldBlock)) return true;
            }
            int minX = (int) Math.floor(local.minX), minY = (int) Math.floor(local.minY), minZ = (int) Math.floor(local.minZ);
            int maxX = (int) Math.floor(local.maxX), maxY = (int) Math.floor(local.maxY), maxZ = (int) Math.floor(local.maxZ);
            if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > MAX_CELLS_PER_BOX * 4L) {
                for (var box : boxes) if (hits(box, local, pose, worldBlock)) return true;
                return false;
            }
            int current = ++stamp;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        var list = cells.get(key(x, y, z));
                        if (list == null) continue;
                        for (int i = 0; i < list.size; i++) {
                            int index = list.values[i];
                            if (marks[index] == current) continue;
                            marks[index] = current;
                            if (hits(boxes.get(index), local, pose, worldBlock)) return true;
                        }
                    }
                }
            }
            return false;
        }

        private static boolean hits(AABB box, AABB local, StructurePose pose, AABB worldBlock) {
            return box.intersects(local)
                    && SubLevelOrientedCollision.intersects(SubLevelOrientedCollision.Box.of(box, pose), worldBlock);
        }

        private static AABB inverse(StructurePose pose, AABB world) {
            AABB result = null;
            for (int corner = 0; corner < 8; corner++) {
                var point = pose.worldToLocalPoint(new Vec3(
                        (corner & 1) == 0 ? world.minX : world.maxX,
                        (corner & 2) == 0 ? world.minY : world.maxY,
                        (corner & 4) == 0 ? world.minZ : world.maxZ));
                var unit = new AABB(point, point);
                result = result == null ? unit : result.minmax(unit);
            }
            return java.util.Objects.requireNonNull(result);
        }

        private static long key(int x, int y, int z) {
            return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0x3FFFFF) << 21) | (z & 0x3FFFFF);
        }
    }

    private static final class IntList {
        private int[] values = new int[4];
        private int size;

        private void add(int value) {
            if (size == values.length) values = java.util.Arrays.copyOf(values, size * 2);
            values[size++] = value;
        }
    }
}
