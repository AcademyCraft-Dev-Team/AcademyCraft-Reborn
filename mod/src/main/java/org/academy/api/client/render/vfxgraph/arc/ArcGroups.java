package org.academy.api.client.render.vfxgraph.arc;

import java.util.concurrent.atomic.AtomicLong;

public final class ArcGroups {
    private static final AtomicLong NEXT_GROUP = new AtomicLong(-1);

    private ArcGroups() {
    }

    public static long next() {
        return NEXT_GROUP.getAndDecrement();
    }
}
