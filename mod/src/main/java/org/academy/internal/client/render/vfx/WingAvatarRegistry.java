package org.academy.internal.client.render.vfx;

import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

public final class WingAvatarRegistry {
    private static final Map<Integer, Matrix4f> ROOTS = new HashMap<>();

    private WingAvatarRegistry() {
    }

    public static void beginFrame() {
        ROOTS.clear();
    }

    public static void capture(int entityId, Matrix4f root) {
        ROOTS.put(entityId, root);
    }

    public static Map<Integer, Matrix4f> entries() {
        return Map.copyOf(ROOTS);
    }
}
