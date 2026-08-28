package org.academy.internal.client.render.vfx;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/** Scoped access to the exact projection used by the current primary world render. */
public final class SpatialCutFrameProjectionContext {
    private static final ThreadLocal<@Nullable Matrix4f> CURRENT = new ThreadLocal<>();

    private SpatialCutFrameProjectionContext() {
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
