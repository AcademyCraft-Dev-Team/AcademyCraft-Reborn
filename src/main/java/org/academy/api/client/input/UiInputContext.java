package org.academy.api.client.input;

/**
 * Declares a synchronous GUI/HUD input operation. Registered exclusive HUD bindings enter this
 * scope automatically. Custom HUD integrations may use it when consuming input or changing bindings.
 */
public final class UiInputContext {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private UiInputContext() {}

    public static void run(Runnable action) {
        var previous = DEPTH.get();
        DEPTH.set(previous == null ? 1 : previous + 1);
        try { action.run(); }
        finally {
            if (previous == null) DEPTH.remove();
            else DEPTH.set(previous);
        }
    }

    public static boolean isActive() {
        if (DEPTH.get() != null) return true;
        return WALKER.walk(frames -> frames.anyMatch(frame -> isUiType(frame.getDeclaringClass())));
    }

    private static boolean isUiType(Class<?> type) {
        for (var current = type; current != null; current = current.getSuperclass()) {
            var name = current.getName();
            if (name.equals("net.minecraft.client.gui.screens.Screen")
                    || name.equals("net.minecraft.client.gui.components.AbstractWidget")) return true;
        }
        return false;
    }
}
