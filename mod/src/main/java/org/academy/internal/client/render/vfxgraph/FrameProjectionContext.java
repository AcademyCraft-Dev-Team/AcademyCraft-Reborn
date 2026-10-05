package org.academy.internal.client.render.vfxgraph;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/**
 * Current-frame projection scope shared by every frame consumer.
 */
public final class FrameProjectionContext {
    private static final ThreadLocal<@Nullable Matrix4f> CURRENT = new ThreadLocal<>();

    private FrameProjectionContext() {
    }

    public static Scope push(Matrix4fc projection) {
        var previous = CURRENT.get();
        CURRENT.set(new Matrix4f(projection));
        return new Scope(previous);
    }

    public static @Nullable Matrix4f currentCopy() {
        var current = CURRENT.get();
        return current == null ? null : new Matrix4f(current);
    }

    public static final class Scope implements AutoCloseable {
        private final @Nullable Matrix4f previous;
        private boolean closed;

        private Scope(@Nullable Matrix4f previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
