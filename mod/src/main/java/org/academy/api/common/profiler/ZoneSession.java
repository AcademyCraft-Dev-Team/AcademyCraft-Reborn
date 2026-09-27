package org.academy.api.common.profiler;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class ZoneSession {
    private final long threadId;
    private final String name;
    private final ArrayDeque<ZoneNode> pathStack = new ArrayDeque<>();
    private final ArrayDeque<Long> startTimes = new ArrayDeque<>();
    private final ConcurrentHashMap<String, ZoneNode> nodes = new ConcurrentHashMap<>();
    private int mismatchPops = 0;

    ZoneSession(long threadId, String name) {
        this.threadId = threadId;
        this.name = name;
        nodes.computeIfAbsent(ZoneProfiler.ROOT, path -> new ZoneNode(path, ZoneProfiler.ROOT));
    }

    public long getThreadId() {
        return threadId;
    }

    public String getName() {
        return name;
    }

    public void push(String zoneName) {
        var current = currentNode();
        var path = childPath(current.path, zoneName);
        var node = nodes.computeIfAbsent(path, p -> new ZoneNode(p, zoneName));
        pathStack.addLast(node);
        startTimes.addLast(System.nanoTime());
    }

    public void pop() {
        if (startTimes.isEmpty()) {
            if (mismatchPops++ < 5) {
                ZoneProfiler.logUnbalancedPop(name);
            }
            return;
        }
        long start = startTimes.removeLast();
        var node = pathStack.removeLast();
        var elapsed = System.nanoTime() - start;
        node.totalNs.add(elapsed);
        node.count.increment();
        node.maxNs.accumulateAndGet(elapsed, Math::max);
    }

    public void incrementCounter(String counterName, int amount) {
        if (amount == 0) {
            return;
        }
        currentNode().counters
                .computeIfAbsent(counterName, key -> new LongAdder())
                .add(amount);
    }

    private ZoneNode currentNode() {
        var last = pathStack.peekLast();
        return last != null ? last : nodes.get(ZoneProfiler.ROOT);
    }

    private String childPath(String parentPath, String childName) {
        if (parentPath.equals(ZoneProfiler.ROOT)) {
            return ZoneProfiler.ROOT + ZoneProfiler.PATH_SEPARATOR + childName;
        }
        return parentPath + ZoneProfiler.PATH_SEPARATOR + childName;
    }

    public void reset() {
        nodes.clear();
        nodes.computeIfAbsent(ZoneProfiler.ROOT, path -> new ZoneNode(path, ZoneProfiler.ROOT));
        pathStack.clear();
        startTimes.clear();
        mismatchPops = 0;
    }

    public ZoneSnapshot snapshot() {
        var rootNode = nodes.get(ZoneProfiler.ROOT);
        var rootTotal = rootNode != null ? rootNode.totalNs.sum() : 0L;
        Map<String, ZoneSlice> slices = new LinkedHashMap<>();
        for (var entry : nodes.entrySet()) {
            var path = entry.getKey();
            var node = entry.getValue();
            var self = selfNs(node);
            Map<String, Long> counters = new LinkedHashMap<>();
            for (var counter : node.counters.entrySet()) {
                counters.put(counter.getKey(), counter.getValue().sum());
            }
            slices.put(path, new ZoneSlice(
                    node.name,
                    path,
                    node.totalNs.sum(),
                    self,
                    node.count.sum(),
                    node.maxNs.get(),
                    rootTotal,
                    ZoneProfiler.colorOf(node.name),
                    counters
            ));
        }
        return new ZoneSnapshot(name, slices, rootTotal);
    }

    private long selfNs(ZoneNode node) {
        var childSum = 0L;
        var prefix = node.path + ZoneProfiler.PATH_SEPARATOR;
        for (var entry : nodes.entrySet()) {
            var path = entry.getKey();
            if (path.length() > node.path.length()
                    && path.startsWith(prefix)
                    && path.indexOf(ZoneProfiler.PATH_SEPARATOR, node.path.length() + 1) < 0) {
                childSum += entry.getValue().totalNs.sum();
            }
        }
        return Math.max(0L, node.totalNs.sum() - childSum);
    }
}
