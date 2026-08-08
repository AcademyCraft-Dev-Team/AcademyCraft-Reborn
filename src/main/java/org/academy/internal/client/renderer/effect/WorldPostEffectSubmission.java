package org.academy.internal.client.renderer.effect;

/**
 * Marks the vanilla main-world entity submission scope.
 *
 * <p>Entity renderers that write directly to a global post-effect buffer must not run during
 * auxiliary entity passes (for example a shader shadow pass), because those passes use a
 * different view matrix while sharing the main post-effect buffer.</p>
 */
public final class WorldPostEffectSubmission {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static void begin() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void end() {
        var depth = DEPTH.get();
        if (depth <= 1) DEPTH.remove();
        else DEPTH.set(depth - 1);
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    private WorldPostEffectSubmission() {
    }
}
