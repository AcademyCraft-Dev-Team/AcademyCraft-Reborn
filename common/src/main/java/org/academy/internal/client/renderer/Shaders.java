package org.academy.internal.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Shaders {
    public static final List<Function<ResourceProvider, ShaderInstance>> SHADERS = new ArrayList<>();
    public static final ShaderInstance GLOW_CIRCLE;
    public static final ShaderInstance POSITION_COLOR_TEX;
    public static final ShaderInstance SDF_CIRCLE_GLOW;
    public static final ShaderInstance SDF_SHARP_QUAD_WITH_MARGIN;
    public static final ShaderInstance SCREEN_BLIT;
    public static final ShaderInstance GAUSSIAN_BLUR;
    public static final ShaderInstance BLOOM_BLEND;

    static {
        try {
            var resourceManager = Minecraft.getInstance().getResourceManager();
            SCREEN_BLIT = new ShaderInstance(resourceManager, "academy:screen_blit", DefaultVertexFormat.POSITION);
            GLOW_CIRCLE = new ShaderInstance(resourceManager, "academy:glow_circle", DefaultVertexFormat.POSITION_TEX);
            POSITION_COLOR_TEX = new ShaderInstance(resourceManager, "academy:position_color_tex", DefaultVertexFormat.POSITION_COLOR_TEX);
            SDF_CIRCLE_GLOW = new ShaderInstance(resourceManager, "academy:sdf_circle_glow", DefaultVertexFormat.POSITION_TEX);
            SDF_SHARP_QUAD_WITH_MARGIN = new ShaderInstance(resourceManager, "academy:sdf_sharp_quad_with_margin", DefaultVertexFormat.POSITION_TEX);
            GAUSSIAN_BLUR = new ShaderInstance(resourceManager, "academy:gaussian_blur", DefaultVertexFormat.POSITION);
            BLOOM_BLEND = new ShaderInstance(resourceManager, "academy:bloom_blend", DefaultVertexFormat.POSITION);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Shaders() {
    }
}