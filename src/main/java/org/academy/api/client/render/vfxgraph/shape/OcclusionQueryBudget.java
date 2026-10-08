package org.academy.api.client.render.vfxgraph.shape;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.WeakHashMap;

/** Shared world-tick budget; visibility samplers cannot multiply ray work by render FPS. */
public final class OcclusionQueryBudget {
    private final int limit;
    private WeakReference<Object> world = new WeakReference<>(null);
    private final WeakHashMap<Object, Request> waiting = new WeakHashMap<>();
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    private long tick = Long.MIN_VALUE;
    private long samplingEpoch;
    private int used;
    private long total;

    private static final class Request {
        final WeakReference<Object> requester;
        long lastRequestedEpoch;
        Request(Object requester, long epoch) {
            this.requester = new WeakReference<>(requester);
            lastRequestedEpoch = epoch;
        }
    }

    public OcclusionQueryBudget(int limit) {
        if (limit < 1) throw new IllegalArgumentException("Occlusion query budget must be positive");
        this.limit = limit;
    }

    /** Reserves a complete group; rejected groups reuse their previous boundary. */
    public boolean tryAcquire(Object world, long tick, int cost) {
        if (cost < 1 || cost > limit) return false;
        beginTick(world, tick);
        return reserve(cost);
    }

    /**
     * Fair admission for persistent groups. Deferred requests keep their place across render frames;
     * removed or invisible callers expire after one unrequested sampling batch and are weakly referenced.
     * Batches count observed ticks, so skipped ticks at low FPS cannot repeatedly discard deferred groups.
     */
    public boolean tryAcquire(Object world, long tick, int cost, Object requester) {
        if (cost < 1 || cost > limit) return false;
        Objects.requireNonNull(requester);
        beginTick(world, tick);
        while (!queue.isEmpty()) {
            var front = queue.getFirst();
            var key = front.requester.get();
            if (key != null && front.lastRequestedEpoch >= samplingEpoch - 1) break;
            queue.removeFirst();
            if (key != null) waiting.remove(key);
        }
        var request = waiting.get(requester);
        if (request == null) {
            request = new Request(requester, samplingEpoch);
            waiting.put(requester, request);
            queue.addLast(request);
        }
        request.lastRequestedEpoch = samplingEpoch;
        if (queue.getFirst() != request || !reserve(cost)) return false;
        queue.removeFirst();
        waiting.remove(requester);
        return true;
    }

    private void beginTick(Object world, long tick) {
        boolean newWorld = this.world.get() != world;
        if (newWorld || tick < this.tick) {
            waiting.clear(); queue.clear();
            samplingEpoch = 0;
        }
        if (newWorld) this.world = new WeakReference<>(world);
        if (newWorld || this.tick != tick) {
            this.tick = tick; used = 0;
            samplingEpoch++;
        }
    }

    private boolean reserve(int cost) {
        if (used > limit - cost) return false;
        used += cost;
        total += cost;
        return true;
    }

    public int limit() { return limit; }
    public int used() { return used; }
    public long total() { return total; }
}
