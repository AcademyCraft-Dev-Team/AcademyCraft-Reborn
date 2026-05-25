package org.academy.api.client.compatibility;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.vertices.ImmediateState;
import net.neoforged.fml.loading.FMLLoader;

public final class IrisCompat {
    private static boolean hasIris = false;
    private static boolean bypass;

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

    public static void enableBypass() {
        if (hasIris()) {
            bypass = ImmediateState.bypass;
            ImmediateState.bypass = true;
        }
    }

    public static void resetBypass() {
        if (hasIris()) ImmediateState.bypass = bypass;
    }

    private IrisCompat() {
    }
}