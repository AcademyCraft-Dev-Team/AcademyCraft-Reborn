package org.academy.api.common.profiler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class ZoneNode {
    final String path;
    final String name;
    public final LongAdder totalNs = new LongAdder();
    public final LongAdder count = new LongAdder();
    public final AtomicLong maxNs = new AtomicLong(0);
    public final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();

    ZoneNode(String path, String name) {
        this.path = path;
        this.name = name;
    }
}
