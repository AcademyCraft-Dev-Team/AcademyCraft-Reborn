package org.academy.api.common.entitycontrol;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.*;
import java.util.stream.Stream;

/** Resumable, server-thread navigation. A deferred query is never an unreachable destination. */
public final class GroupControlNavigation {
    public enum Status { READY, DEFERRED, NO_PATH, UNLOADED, INVALID }
    public record Result(Status status, @Nullable Vec3 position, @Nullable Path path) {}
    private static final int[][] SEARCH_OFFSETS = {
            {0, 0, 0}, {0, 1, 0}, {0, 2, 0}, {0, -1, 0},
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
            {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}
    };
    private static final Map<MinecraftServer, QueueState> QUEUES = new WeakHashMap<>();
    private static final Map<LivingEntity, PreparedPath> PREPARED = new WeakHashMap<>();
    private static final Map<LivingEntity, Search> LEGACY = new WeakHashMap<>();
    private record PreparedPath(Vec3 destination, Path path, long tick) {}
    private GroupControlNavigation() {}

    public static Optional<Vec3> findNearestOccupablePosition(LivingEntity subject, Vec3 preferred) {
        if (!isFinite(preferred)) return Optional.empty();
        for (var offset : SEARCH_OFFSETS) {
            var candidate = preferred.add(offset[0], offset[1], offset[2]);
            if (canOccupy(subject, candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public static Search positionSearch(LivingEntity subject, Vec3 preferred) {
        return new Search(subject, preferred, isFinite(preferred) ? Arrays.stream(SEARCH_OFFSETS)
                .map(offset -> preferred.add(offset[0], offset[1], offset[2]))
                .sorted(Comparator.comparingDouble((Vec3 p) -> p.distanceToSqr(preferred))
                        .thenComparingDouble(subject::distanceToSqr)).toList() : List.of(), 0, true);
    }

    public static Search workSearch(LivingEntity subject, BlockPos block) {
        var candidates = Stream.of(block.above(), block.north(), block.south(), block.west(), block.east(),
                        block.north().west(), block.north().east(), block.south().west(), block.south().east(),
                        block.north().above(), block.south().above(), block.west().above(), block.east().above(), block.above(2))
                .map(Vec3::atBottomCenterOf).sorted(Comparator.comparingDouble(subject::distanceToSqr)).toList();
        return new Search(subject, Vec3.atCenterOf(block), candidates, 0, true);
    }

    /** Execution navigation can retain a partial vanilla path, matching its existing arrival handling. */
    public static Search pathSearch(Mob subject, Vec3 destination, int reachRange) {
        return new Search(subject, destination, isFinite(destination) ? List.of(destination) : List.of(), reachRange, false);
    }

    /** One-shot transfer; a mutable Path must never be shared by different workers. */
    public static @Nullable Path takePreparedPath(Mob subject, Vec3 destination) {
        var prepared = PREPARED.remove(subject);
        if (prepared == null || subject.level().getGameTime() - prepared.tick > 20
                || prepared.destination.distanceToSqr(destination) > 0.01 || !canOccupy(subject, destination)) return null;
        return prepared.path;
    }

    public static void cancel(LivingEntity subject) {
        PREPARED.remove(subject);
        var legacy = LEGACY.remove(subject);
        if (legacy != null) legacy.close();
    }

    public static boolean waitingForPath(LivingEntity subject) {
        if (!(subject.level() instanceof ServerLevel level)) return false;
        var queue = QUEUES.get(level.getServer());
        return queue != null && queue.pendingSubjects.containsKey(subject.getUUID());
    }

    public static void clear(MinecraftServer server) {
        var queue = QUEUES.remove(server);
        if (queue != null) for (var search : List.copyOf(queue.pending)) search.close();
        PREPARED.clear();
        LEGACY.clear();
        WorkScheduling.clear(server);
    }

    /** Compatibility helpers retain their cursor; new callers should own and close a Search explicitly. */
    public static Optional<Vec3> findNearestReachablePosition(LivingEntity subject, Vec3 preferred) {
        var search = LEGACY.get(subject);
        if (search == null || !search.preferred.equals(preferred)) {
            if (search != null) search.close();
            search = positionSearch(subject, preferred);
            LEGACY.put(subject, search);
        }
        var result = search.poll();
        if (result.status != Status.DEFERRED) LEGACY.remove(subject);
        return Optional.ofNullable(result.position);
    }

    public static Optional<Vec3> findNearestWorkPosition(LivingEntity subject, BlockPos block) {
        var search = LEGACY.get(subject);
        if (search == null || !search.preferred.equals(Vec3.atCenterOf(block))) {
            if (search != null) search.close();
            search = workSearch(subject, block);
            LEGACY.put(subject, search);
        }
        var result = search.poll();
        if (result.status != Status.DEFERRED) LEGACY.remove(subject);
        return Optional.ofNullable(result.position);
    }

    public static boolean pathBudgetExhausted(Level level) {
        return level instanceof ServerLevel serverLevel
                && !WorkScheduling.budget(serverLevel).available(WorkBudget.Operation.PATH);
    }

    public static boolean canOccupy(LivingEntity subject, Vec3 candidate) {
        if (!isFinite(candidate)) return false;
        var level = subject.level();
        var block = BlockPos.containing(candidate);
        if (block.getY() < level.getMinY() || block.getY() >= level.getMaxY() || !level.hasChunkAt(block)) return false;
        var moved = subject.getBoundingBox().move(candidate.subtract(subject.position()));
        return level.getWorldBorder().isWithinBounds(moved) && level.noCollision(subject, moved);
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    public static final class Search implements AutoCloseable {
        private final LivingEntity subject;
        private final Level level;
        private final Vec3 preferred;
        private final List<Vec3> candidates;
        private final int reachRange;
        private final boolean requireReachable;
        private int cursor;
        private boolean unloaded, closed;
        private long attemptedTick = Long.MIN_VALUE, requestedTick;
        private Result result = new Result(Status.DEFERRED, null, null);
        private @Nullable RuntimeException failure;

        private Search(LivingEntity subject, Vec3 preferred, List<Vec3> candidates, int reachRange, boolean requireReachable) {
            this.subject = subject;
            this.level = subject.level();
            this.preferred = preferred;
            this.candidates = candidates;
            this.reachRange = reachRange;
            this.requireReachable = requireReachable;
        }

        public Result poll() {
            if (failure != null) throw failure;
            if (closed) return new Result(Status.INVALID, null, null);
            if (result.status != Status.DEFERRED) return result;
            if (!(level instanceof ServerLevel serverLevel) || subject.level() != level || subject.isRemoved()) {
                close(); return new Result(Status.INVALID, null, null);
            }
            var queue = QUEUES.computeIfAbsent(serverLevel.getServer(), ignored -> new QueueState());
            requestedTick = Integer.toUnsignedLong(serverLevel.getServer().getTickCount());
            if (queue.pending.add(this)) queue.pendingSubjects.merge(subject.getUUID(), 1, Integer::sum);
            queue.drain(serverLevel.getServer());
            if (failure != null) throw failure;
            return result;
        }

        private void attempt(WorkBudget budget) {
            while (cursor < candidates.size()) {
                var candidate = candidates.get(cursor);
                if (!level.hasChunkAt(BlockPos.containing(candidate))) { unloaded = true; cursor++; continue; }
                if (requireReachable && !canOccupy(subject, candidate)) { cursor++; continue; }
                if (!(subject instanceof Mob mob)) { result = new Result(Status.READY, candidate, null); return; }
                if (!budget.spend(WorkBudget.Operation.PATH)) return;
                cursor++;
                var path = mob.getNavigation().createPath(BlockPos.containing(candidate), reachRange);
                if (path != null && (!requireReachable || path.canReach())) {
                    result = new Result(Status.READY, candidate, path);
                    if (requireReachable) PREPARED.put(subject, new PreparedPath(candidate, path, level.getGameTime()));
                    return;
                }
                // One expensive attempt per request per tick, including fallback candidates.
                break;
            }
            if (cursor == candidates.size()) result = new Result(unloaded ? Status.UNLOADED : Status.NO_PATH, null, null);
        }

        @Override public void close() {
            closed = true;
            if (level instanceof ServerLevel serverLevel) {
                var queue = QUEUES.get(serverLevel.getServer());
                if (queue != null) queue.remove(this);
            }
        }
    }

    private static final class QueueState {
        private final LinkedHashSet<Search> pending = new LinkedHashSet<>();
        private final Map<UUID, Integer> pendingSubjects = new HashMap<>();
        private final Map<Level, Integer> levelAttempts = new HashMap<>();
        private long tick = Long.MIN_VALUE;
        private int inspections;
        private void remove(Search search) {
            if (pending.remove(search)) pendingSubjects.computeIfPresent(search.subject.getUUID(),
                    (id, count) -> count == 1 ? null : count - 1);
        }
        private void drain(MinecraftServer server) {
            long now = Integer.toUnsignedLong(server.getTickCount());
            if (tick != now) { tick = now; levelAttempts.clear(); inspections = 0; }
            var budget = WorkScheduling.budget(server);
            int remaining = pending.size();
            while (remaining-- > 0 && inspections < 256 && !pending.isEmpty() && budget.available(WorkBudget.Operation.PATH)) {
                inspections++;
                var search = pending.getFirst();
                if (search.closed || search.subject.isRemoved() || search.subject.level() != search.level) { search.close(); continue; }
                // An unscheduled worker can re-enqueue later; inactivity is not a path failure.
                if (now - search.requestedTick > 200) { remove(search); continue; }
                pending.remove(search);
                pending.add(search);
                if (search.attemptedTick == now || levelAttempts.getOrDefault(search.level, 0) >= 4) continue;
                search.attemptedTick = now;
                levelAttempts.merge(search.level, 1, Integer::sum);
                try (var ignored = budget.measure()) { search.attempt(budget); }
                catch (RuntimeException failure) {
                    search.failure = failure;
                    search.result = new Result(Status.INVALID, null, null);
                }
                if (search.result.status != Status.DEFERRED) remove(search);
            }
        }
    }
}
