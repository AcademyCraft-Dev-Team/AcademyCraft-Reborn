package org.academy.internal.client.renderer.effect;

enum PlatinumCosmosRenderMode {
    NORMAL,
    EXACT,
    FALLBACK;

    static PlatinumCosmosRenderMode select(boolean shaderPackInUse, boolean exactPassAvailable) {
        if (!shaderPackInUse) return NORMAL;
        return exactPassAvailable ? EXACT : FALLBACK;
    }
}
