package org.academy.internal.client.render.vfx;

public enum PlatinumCosmosRenderMode {
    NORMAL,
    EXACT,
    FALLBACK;

    static PlatinumCosmosRenderMode select(boolean shaderPackInUse, boolean exactPassAvailable) {
        if (!shaderPackInUse) return NORMAL;
        return exactPassAvailable ? EXACT : FALLBACK;
    }
}
