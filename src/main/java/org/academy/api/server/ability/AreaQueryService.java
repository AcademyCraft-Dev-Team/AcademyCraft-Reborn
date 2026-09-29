package org.academy.api.server.ability;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.academy.AcademyCraft;

import java.util.*;

/** Shared per-level cooperative budget. All world access and callbacks stay on the server thread. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class AreaQueryService {
    public static final int MAX_CANDIDATES = 256, MAX_EFFECTS = 96, MAX_PROBES = 4096, MAX_TRACKED = 8192;
    public static final long SOFT_BUDGET_NANOS = 1_000_000;
    private static final Map<ServerLevel, State> LEVELS = new IdentityHashMap<>();
    private static final boolean PROFILE_INDEX = Boolean.getBoolean("academy.areaQueryProfile");
    public enum Point { ORIGIN, BOUNDS_CENTER }

    public interface Effect {
        boolean matches(LivingEntity target);
        void apply(LivingEntity target, long tick);
        default void removed(LivingEntity target) { }
        default void afterTick(long tick) { }
        default void closed() { }
    }

    public record Metrics(int candidates, int effects, int probes, long nanos, long longestCallbackNanos) { }
    public static Metrics metrics(ServerLevel level) { return state(level).metrics; }
    public static long indexNanos(ServerLevel level) { return state(level).indexNanos; }
    private static State state(ServerLevel level) { return LEVELS.computeIfAbsent(level, _ -> new State()); }

    public static Session open(ServerLevel level, LivingEntity owner, Point point, Effect effect) {
        var session = new Session(level, owner, point, effect);
        state(level).sessions.addLast(session);
        return session;
    }

    private static final class State {
        final SectionEntityIndex<LivingEntity> origins = new SectionEntityIndex<>(), centers = new SectionEntityIndex<>();
        final ArrayDeque<Session> sessions = new ArrayDeque<>();
        Metrics metrics = new Metrics(0, 0, 0, 0, 0);
        long indexNanos;
    }
    private static final class Target {
        final LivingEntity entity;
        long due;
        Target(LivingEntity entity, long due) { this.entity = entity; this.due = due; }
    }

    public static final class Session implements AutoCloseable {
        private final ServerLevel level;
        private final LivingEntity owner;
        private final Point point;
        private final Effect effect;
        private final Int2ObjectOpenHashMap<Target> known = new Int2ObjectOpenHashMap<>();
        private final ArrayDeque<Target> queue = new ArrayDeque<>();
        private SectionEntityIndex<LivingEntity>.Cursor cursor;
        private Vec3 center = Vec3.ZERO;
        private Vec3 scanCenter = Vec3.ZERO;
        private double radius;
        private int interval = 1, maximum = 96, served, turn;
        private long refreshed, nextScan, lastWorkTick = Long.MIN_VALUE, maximumDelay;
        private boolean closed;

        private Session(ServerLevel level, LivingEntity owner, Point point, Effect effect) {
            this.level = level; this.owner = owner; this.point = point; this.effect = effect;
            refreshed = level.getGameTime();
        }
        public boolean isClosed() { return closed; }
        public int trackedCount() { return known.size(); }
        public long maximumDelayTicks() { return maximumDelay; }
        public void update(Vec3 center, double radius, int interval, int maximum) {
            if (closed) return;
            if (!Double.isFinite(radius) || radius <= 0 || radius > 512
                    || !Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
                close(); return;
            }
            // Preserve an in-progress outer pass while walking; eligibility always uses the current center.
            if (this.radius != radius || scanCenter.distanceToSqr(center) > 32 * 32) { cursor = null; nextScan = 0; scanCenter = center; }
            this.center = center; this.radius = radius;
            this.interval = Math.max(1, interval); this.maximum = Math.clamp(maximum, 1, MAX_EFFECTS);
            refreshed = level.getGameTime();
        }

        private boolean eligible(LivingEntity target) {
            return target.level() == level && target.isAlive() && !target.isRemoved()
                    && AreaEffectTargets.contains(center, point == Point.ORIGIN ? target.position()
                    : target.getBoundingBox().getCenter(), radius) && effect.matches(target);
        }

        private boolean step(long tick, Budget budget) {
            if (closed) return false;
            if (tick - refreshed > 1 || owner.isRemoved() || !owner.isAlive() || owner.level() != level) {
                close(); return true;
            }
            if (radius <= 0) return false;
            if (lastWorkTick != tick) { lastWorkTick = tick; served = 0; }
            boolean due = !queue.isEmpty() && queue.peekFirst().due <= tick && served < maximum && budget.effects < MAX_EFFECTS;
            if (due && (++turn % 4 != 0 || nextScan > tick)) {
                if (budget.candidates >= MAX_CANDIDATES) return false;
                var target = queue.removeFirst();
                budget.candidates++;
                if (known.get(target.entity.getId()) != target || !eligible(target.entity)) {
                    known.remove(target.entity.getId()); effect.removed(target.entity);
                } else {
                    maximumDelay = Math.max(maximumDelay, tick - target.due);
                    target.due = tick + interval;
                    queue.addLast(target);
                    served++; budget.effects++;
                    long start = System.nanoTime();
                    effect.apply(target.entity, tick);
                    budget.longestCallback = Math.max(budget.longestCallback, System.nanoTime() - start);
                }
                return true;
            }
            if (nextScan > tick || budget.probes >= MAX_PROBES || budget.candidates >= MAX_CANDIDATES) return false;
            var index = point == Point.ORIGIN ? state(level).origins : state(level).centers;
            if (cursor == null) { cursor = index.cursor(center.x, center.y, center.z, radius); scanCenter = center; }
            budget.probes++;
            var candidate = cursor.next();
            if (candidate != null) {
                budget.candidates++;
                if (!known.containsKey(candidate.getId()) && eligible(candidate)) {
                    if (known.size() >= MAX_TRACKED) {
                        var oldest = queue.removeFirst();
                        known.remove(oldest.entity.getId()); effect.removed(oldest.entity);
                    }
                    var target = new Target(candidate, tick);
                    known.put(candidate.getId(), target); queue.addLast(target);
                }
            }
            if (cursor.finished()) { cursor = null; nextScan = tick + interval; }
            return true;
        }

        @Override public void close() {
            if (closed) return;
            closed = true; known.clear(); queue.clear(); cursor = null;
            effect.closed();
        }
    }
    private static final class Budget {
        int candidates, effects, probes;
        long longestCallback;
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var state = LEVELS.get(level);
        if (state == null || state.sessions.isEmpty()) return;
        long start = System.nanoTime(), tick = level.getGameTime();
        var budget = new Budget();
        var touched = new LinkedHashSet<Session>();
        int idle = 0;
        while (!state.sessions.isEmpty() && idle < state.sessions.size() && System.nanoTime() - start < SOFT_BUDGET_NANOS) {
            var session = state.sessions.removeFirst();
            if (session.closed) continue;
            if (session.step(tick, budget)) { touched.add(session); idle = 0; } else idle++;
            if (!session.closed) state.sessions.addLast(session);
        }
        for (var session : touched) if (!session.closed) session.effect.afterTick(tick);
        state.metrics = new Metrics(budget.candidates, budget.effects, budget.probes,
                System.nanoTime() - start, budget.longestCallback);
    }

    private static void index(LivingEntity entity, boolean includeOrigin) {
        if (!(entity.level() instanceof ServerLevel level) || entity.isRemoved()) return;
        long start = PROFILE_INDEX ? System.nanoTime() : 0;
        var state = state(level);
        if (includeOrigin) state.origins.update(entity.getId(), entity.getX(), entity.getY(), entity.getZ(), entity);
        var box = entity.getBoundingBox();
        state.centers.update(entity.getId(), (box.minX + box.maxX) * .5, (box.minY + box.maxY) * .5,
                (box.minZ + box.maxZ) * .5, entity);
        if (PROFILE_INDEX) state.indexNanos += System.nanoTime() - start;
    }
    @SubscribeEvent public static void beforeTick(LevelTickEvent.Pre event) {
        if (PROFILE_INDEX && event.getLevel() instanceof ServerLevel level) state(level).indexNanos = 0;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void joined(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof LivingEntity entity) index(entity, true);
    }
    @SubscribeEvent public static void moved(EntityEvent.EnteringSection event) {
        if (event.getEntity() instanceof LivingEntity entity) index(entity, true);
    }
    @SubscribeEvent public static void movedWithinSection(EntityTickEvent.Post event) {
        // Also covers a bounding-box center crossing a section before the entity's feet do, or a size change.
        if (event.getEntity() instanceof LivingEntity entity) index(entity, false);
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var state = LEVELS.get(level);
        if (state == null) return;
        state.origins.remove(event.getEntity().getId()); state.centers.remove(event.getEntity().getId());
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        var state = LEVELS.remove(event.getLevel());
        if (state != null) state.sessions.forEach(Session::close);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        for (var state : LEVELS.values()) state.sessions.forEach(Session::close);
        LEVELS.clear();
    }
}
