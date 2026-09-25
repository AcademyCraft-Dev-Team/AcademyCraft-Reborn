package org.academy.api.client.compatibility;

import net.irisshaders.iris.api.v0.IrisApi;
import net.neoforged.fml.loading.FMLLoader;

public final class IrisIntegration {
    private static boolean hasIris;

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
}
