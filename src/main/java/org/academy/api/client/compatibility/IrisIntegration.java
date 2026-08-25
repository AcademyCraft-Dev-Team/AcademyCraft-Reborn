package org.academy.api.client.compatibility;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.vertices.ImmediateState;
import net.neoforged.fml.loading.FMLLoader;
import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/** Centralized Iris render integration. */
public final class IrisIntegration {
    private static final ThreadLocal<ArrayDeque<Boolean>> BYPASS_STATES =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final AtomicBoolean HAND_BRIDGE_WARNING_LOGGED = new AtomicBoolean();
    private static boolean hasIris;
    private static volatile boolean handBridgeMounted;

    private IrisIntegration() {
    }

    public static void init() {
        hasIris = FMLLoader.getCurrent().getLoadingModList().getModFileById("iris") != null;
    }

    public static boolean hasIris() {
        return hasIris;
    }

    public static boolean isShaderPackInUse() {
        return hasIris() && IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean isShadowRendererActive() {
        return hasIris() && IrisApi.getInstance().isRenderingShadowPass();
    }

    public static void markHandBridgeMounted() {
        handBridgeMounted = true;
    }

    public static boolean isHandBridgeMounted() {
        return handBridgeMounted;
    }

    public static void warnHandBridgeFallback() {
        warnHandBridgeFallback(null);
    }

    private static void warnHandBridgeFallback(@Nullable Throwable throwable) {
        if (!HAND_BRIDGE_WARNING_LOGGED.compareAndSet(false, true)) return;
        if (throwable == null) {
            AcademyCraft.getLogger().warn(
                    "Iris platinum-wing hand bridge is unavailable; using the visible textured fallback."
            );
        } else {
            AcademyCraft.getLogger().warn(
                    "Iris platinum-wing hand bridge failed; using the visible textured fallback.",
                    throwable
            );
        }
    }
}
