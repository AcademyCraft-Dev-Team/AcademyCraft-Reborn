package org.academy.api.common.profiler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfilerSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger("AcademyProfiler");
    private static final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private static final ConcurrentHashMap<Long, ThreadRef> threads = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, SampledCallTree> trees = new ConcurrentHashMap<>();
    private static final int MAX_DEPTH = 128;

    private static volatile long intervalMicros = 1000L;
    private static volatile boolean running = false;
    private static volatile boolean paused = false;
    private static volatile long captureStartNanos = System.nanoTime();
    private static volatile boolean everStarted = false;

    private static volatile Thread worker = null;
    private static final Object lock = new Object();

    private ProfilerSampler() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static boolean hasData() {
        return everStarted;
    }

    public static long getIntervalMicros() {
        return intervalMicros;
    }

    public static List<ThreadRef> threadRefs() {
        List<ThreadRef> refs = new ArrayList<>(threads.values());
        refs.sort(Comparator.comparing(ThreadRef::getName));
        return refs;
    }

    public static ThreadRef registerThread(Thread thread) {
        long threadId = thread.threadId();
        ThreadRef ref = threads.computeIfAbsent(threadId, id -> new ThreadRef(id, thread.getName()));
        trees.computeIfAbsent(threadId, id -> new SampledCallTree(id, thread.getName()));
        return ref;
    }

    public static void unregisterThread(long threadId) {
        threads.remove(threadId);
        trees.remove(threadId);
    }

    public static void setThreadEnabled(long threadId, boolean enabled) {
        ThreadRef ref = threads.get(threadId);
        if (ref != null) {
            ref.enabled = enabled;
        }
    }

    public static void start(long intervalMicros) {
        synchronized (lock) {
            if (running) {
                return;
            }
            running = true;
            paused = false;
            everStarted = true;
            ProfilerSampler.intervalMicros = Math.max(100L, Math.min(1_000_000L, intervalMicros));
            captureStartNanos = System.nanoTime();
            resetInternal();
            Thread thread = new Thread(ProfilerSampler::loop, "Academy Profiler Sampler");
            thread.setDaemon(true);
            worker = thread;
            thread.start();
        }
    }

    public static void stop() {
        synchronized (lock) {
            if (!running) {
                return;
            }
            running = false;
            if (worker != null) {
                worker.interrupt();
            }
            worker = null;
        }
    }

    public static void pause() {
        paused = true;
    }

    public static void resume() {
        paused = false;
    }

    public static void reset() {
        synchronized (lock) {
            resetInternal();
        }
    }

    private static void resetInternal() {
        captureStartNanos = System.nanoTime();
        for (SampledCallTree tree : trees.values()) {
            tree.reset();
        }
    }

    public static double elapsedSeconds() {
        return (System.nanoTime() - captureStartNanos) / 1e9;
    }

    private static void loop() {
        while (running) {
            if (!paused) {
                try {
                    sampleOnce();
                } catch (Throwable t) {
                    LOGGER.warn("Sampler iteration failed", t);
                }
            }
            long us = intervalMicros;
            long sleepMs = us / 1000;
            int sleepNs = (int) ((us % 1000) * 1000);
            try {
                Thread.sleep(sleepMs, sleepNs);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static void sampleOnce() {
        List<Long> enabledIds = new ArrayList<>();
        for (ThreadRef ref : threads.values()) {
            if (ref.enabled) {
                enabledIds.add(ref.id);
            }
        }
        if (enabledIds.isEmpty()) {
            return;
        }
        long[] ids = enabledIds.stream().mapToLong(Long::longValue).toArray();
        ThreadInfo[] infos;
        try {
            infos = threadBean.getThreadInfo(ids, MAX_DEPTH);
        } catch (Throwable t) {
            return;
        }
        for (int i = 0; i < ids.length; i++) {
            ThreadInfo info = infos[i];
            if (info == null) {
                continue;
            }
            StackTraceElement[] stack = info.getStackTrace();
            if (stack.length == 0) {
                continue;
            }
            SampledCallTree tree = trees.get(ids[i]);
            if (tree != null) {
                tree.insert(stack);
            }
        }
    }

    public static SamplerSnapshot snapshot() {
        Map<Long, SampledThreadView> perThread = new LinkedHashMap<>();
        for (Map.Entry<Long, SampledCallTree> entry : trees.entrySet()) {
            SampledCallTree tree = entry.getValue();
            long total = tree.totalSamples();
            perThread.put(entry.getKey(), new SampledThreadView(
                    entry.getKey(),
                    tree.getThreadName(),
                    total,
                    cloneNode(tree.getRoot(), total)
            ));
        }
        long totalSamples = 0;
        for (SampledThreadView view : perThread.values()) {
            totalSamples += view.samples();
        }
        return new SamplerSnapshot(perThread, totalSamples, elapsedSeconds());
    }

    private static SampledNode cloneNode(SampledCallNode node, long totalSamples) {
        List<SampledCallNode> rawChildren = new ArrayList<>(node.getChildren().values());
        rawChildren.sort((a, b) -> Long.compare(b.getSamples().sum(), a.getSamples().sum()));
        List<SampledNode> children = new ArrayList<>();
        for (SampledCallNode child : rawChildren) {
            children.add(cloneNode(child, totalSamples));
        }
        return new SampledNode(
                node.getLabel(),
                node.getSamples().sum(),
                node.getSelfSamples().sum(),
                children,
                totalSamples
        );
    }

    public static class ThreadRef {
        private final long id;
        private final String name;
        public volatile boolean enabled = true;

        public ThreadRef(long id, String name) {
            this.id = id;
            this.name = name;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
