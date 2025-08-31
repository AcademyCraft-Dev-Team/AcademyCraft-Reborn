package org.academy.api.client.compatibility;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.neoforged.fml.ModList;
import org.academy.api.client.Render;

public final class IrisCompat {
    private static final boolean HAS_IRIS = ModList.get().isLoaded("iris");

    static {
        if (HAS_IRIS) {
            IrisPipelines.assignPipeline(Render.RenderPipelines.LEVEL_POS_TEX_COLOR, ShaderKey.SKY_TEXTURED_COLOR);
        }
    }

    private IrisCompat() {
    }

    public static boolean isShaderPackInUse() {
        return HAS_IRIS && IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean isShadowRendererActive() {
        return HAS_IRIS && ShadowRenderer.ACTIVE;
    }
}