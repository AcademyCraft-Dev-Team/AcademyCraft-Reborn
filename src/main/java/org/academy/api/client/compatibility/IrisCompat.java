package org.academy.api.client.compatibility;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.neoforged.fml.loading.FMLLoader;
import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public final class IrisCompat {
    private static final AtomicBoolean HAND_BRIDGE_WARNING_LOGGED = new AtomicBoolean();
    private static boolean hasIris = false;
    private static volatile boolean handBridgeMounted;

    private IrisCompat() {
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
        return hasIris() && ShadowRenderer.ACTIVE;
    }

    public static void markHandBridgeMounted() {
        handBridgeMounted = true;
    }

    public static void markHandBridgeFailed(Throwable throwable) {
        handBridgeMounted = false;
        warnHandBridgeFallback(throwable);
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
