package org.academy.api.common.profiler;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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
        ZoneNode current = currentNode();
        String path = childPath(current.path, zoneName);
        ZoneNode node = nodes.computeIfAbsent(path, p -> new ZoneNode(p, zoneName));
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
        ZoneNode node = pathStack.removeLast();
        long elapsed = System.nanoTime() - start;
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
        ZoneNode last = pathStack.peekLast();
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
        ZoneNode rootNode = nodes.get(ZoneProfiler.ROOT);
        long rootTotal = rootNode != null ? rootNode.totalNs.sum() : 0L;
        Map<String, ZoneSlice> slices = new LinkedHashMap<>();
        for (Map.Entry<String, ZoneNode> entry : nodes.entrySet()) {
            String path = entry.getKey();
            ZoneNode node = entry.getValue();
            long self = selfNs(node);
            Map<String, Long> counters = new LinkedHashMap<>();
            for (Map.Entry<String, LongAdder> counter : node.counters.entrySet()) {
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
        long childSum = 0L;
        String prefix = node.path + ZoneProfiler.PATH_SEPARATOR;
        for (Map.Entry<String, ZoneNode> entry : nodes.entrySet()) {
            String path = entry.getKey();
            if (path.length() > node.path.length()
                    && path.startsWith(prefix)
                    && path.indexOf(ZoneProfiler.PATH_SEPARATOR, node.path.length() + 1) < 0) {
                childSum += entry.getValue().totalNs.sum();
            }
        }
        return Math.max(0L, node.totalNs.sum() - childSum);
    }
}
