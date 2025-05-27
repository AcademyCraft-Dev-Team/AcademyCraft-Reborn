package org.academy.internal.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.academy.AcademyCraft;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Shaders {
    public static final List<Function<ResourceProvider, ShaderInstance>> SHADERS = new ArrayList<>();
    public static ShaderInstance glowCircle;
    public static ShaderInstance positionColorShader;

    static {
        SHADERS.add(new Function<>() {
            @Override
            public ShaderInstance apply(ResourceProvider resourceProvider) {
                try {
                    ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, "glow_circle");
                    ShaderInstance shaderInstance = new ShaderInstance(resourceProvider, resourceLocation.toString(), DefaultVertexFormat.POSITION_TEX);
                    glowCircle = shaderInstance;
                    return shaderInstance;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        SHADERS.add(new Function<>() {
            @Override
            public ShaderInstance apply(ResourceProvider resourceProvider) {
                try {
                    ResourceLocation resourceLocation = new ResourceLocation(AcademyCraft.MOD_ID, "position_color_tex");
                    ShaderInstance shaderInstance = new ShaderInstance(resourceProvider, resourceLocation.toString(), DefaultVertexFormat.POSITION_COLOR_TEX);
                    positionColorShader = shaderInstance;
                    return shaderInstance;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private Shaders() {
    }
}