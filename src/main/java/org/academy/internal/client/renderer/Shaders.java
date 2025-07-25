package org.academy.internal.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.academy.AcademyCraft;

import java.io.IOException;

import static org.academy.AcademyCraft.getResourceLocation;

@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public class Shaders {
    public static ShaderInstance GLOW_CIRCLE;
    public static ShaderInstance POSITION_COLOR_TEX;
    public static ShaderInstance SDF_CIRCLE_GLOW;
    public static ShaderInstance SDF_SHARP_QUAD_WITH_MARGIN;
    public static ShaderInstance SCREEN_BLIT;
    public static ShaderInstance GAUSSIAN_BLUR;
    public static ShaderInstance BLOOM_BLEND;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), getResourceLocation("screen_blit"), DefaultVertexFormat.POSITION),
                    shader -> SCREEN_BLIT = shader);

            event.registerShader(new ShaderInstance(event.getResourceProvider(), getResourceLocation("glow_circle"), DefaultVertexFormat.POSITION_TEX),
                    shader -> GLOW_CIRCLE = shader);

            event.registerShader(new ShaderInstance(event.getResourceProvider(), getResourceLocation("position_color_tex"), VertexFormat.builder()
                            .add("Position", VertexFormatElement.POSITION)
                            .add("Color", VertexFormatElement.COLOR)
                            .add("UV0", VertexFormatElement.UV0)
                            .build()),
                    shader -> POSITION_COLOR_TEX = shader);

            event.registerShader(new ShaderInstance(event.getResourceProvider(), getResourceLocation("sdf_circle_glow"), DefaultVertexFormat.POSITION_TEX),
                    shader -> SDF_CIRCLE_GLOW = shader);

            event.registerShader(new ShaderInstance(event.getResourceProvider(), getResourceLocation("sdf_sharp_quad_with_margin"), DefaultVertexFormat.POSITION_TEX),
                    shader -> SDF_SHARP_QUAD_WITH_MARGIN = shader);

            event.registerShader(new ShaderInstance(event.getResourceProvider(), getResourceLocation("gaussian_blur"), DefaultVertexFormat.POSITION),
                    shader -> GAUSSIAN_BLUR = shader);

            event.registerShader(new ShaderInstance(event.getResourceProvider(), getResourceLocation("bloom_blend"), DefaultVertexFormat.POSITION),
                    shader -> BLOOM_BLEND = shader);

        } catch (IOException e) {
            throw new RuntimeException("Failed to register shaders for " + AcademyCraft.MODID, e);
        }
    }

    private Shaders() {
    }
}