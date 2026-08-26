package org.academy.api.common.profiler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ZoneProfiler {
    public static final String ROOT = "root";
    public static final char PATH_SEPARATOR = '\u001e';

    private static final Logger LOGGER = LoggerFactory.getLogger("AcademyProfiler");

    private static volatile boolean enabled = false;

    private static final ConcurrentHashMap<Long, ZoneSession> sessions = new ConcurrentHashMap<>();

    private static final ThreadLocal<ZoneSession> threadLocalSession = ThreadLocal.withInitial(() -> {
        Thread thread = Thread.currentThread();
        return sessions.computeIfAbsent(
                thread.threadId(),
                id -> new ZoneSession(id, thread.getName())
        );
    });

    private ZoneProfiler() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            reset();
        }
    }

    public static void push(String name) {
        if (!enabled) {
            return;
        }
        threadLocalSession.get().push(name);
    }

    public static void pop() {
        if (!enabled) {
            return;
        }
        threadLocalSession.get().pop();
    }

    public static void popPush(String name) {
        if (!enabled) {
            return;
        }
        ZoneSession session = threadLocalSession.get();
        session.pop();
        session.push(name);
    }

    public static void incrementCounter(String name, int amount) {
        if (!enabled) {
            return;
        }
        threadLocalSession.get().incrementCounter(name, amount);
    }

    public static void reset() {
        for (ZoneSession session : sessions.values()) {
            session.reset();
        }
    }

    public static List<String> threadNames() {
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ZoneSession session : sessions.values()) {
            if (seen.add(session.getName())) {
                names.add(session.getName());
            }
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    public static Map<String, ZoneSnapshot> snapshot() {
        Map<String, ZoneSnapshot> result = new LinkedHashMap<>();
        for (ZoneSession session : sessions.values()) {
            if (!result.containsKey(session.getName())) {
                result.put(session.getName(), session.snapshot());
            }
        }
        return result;
    }

    static void logUnbalancedPop(String threadName) {
        LOGGER.warn("ZoneProfiler: unbalanced pop() on thread '{}' (missing push)", threadName);
    }

    static int colorOf(String name) {
        return (name.hashCode() & 11184810) + -12303292;
    }
}
