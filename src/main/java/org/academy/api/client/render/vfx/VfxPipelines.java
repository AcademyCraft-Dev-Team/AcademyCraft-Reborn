package org.academy.api.client.render.vfx;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.academy.AcademyCraft;
import org.academy.api.client.resources.R;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.Optional;

import static com.mojang.blaze3d.pipeline.RenderPipeline.builder;
import static org.academy.AcademyCraft.academy;

@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class VfxPipelines {
    public static final RenderPipeline PARTICLE_ADDITIVE = builder()
            .withLocation(academy("pipeline/vfx_particle"))
            .withVertexShader(R.shaders.core.vfx_particle)
            .withFragmentShader(R.shaders.core.vfx_particle)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexBinding(0, VertexFormat.builder(0)
                    .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                    .build())
            .withVertexBinding(1, VertexFormat.builder(1)
                    .addAttribute("InstancePos", GpuFormat.RGB32_FLOAT)
                    .addAttribute("InstanceSize", GpuFormat.R32_FLOAT)
                    .addAttribute("InstanceColor", GpuFormat.RGBA32_FLOAT)
                    .build())
            .build();

    public static final RenderPipeline BEAM_BALL = builder()
            .withLocation(academy("pipeline/vfx_beam_ball"))
            .withVertexShader(R.shaders.core.vfx_beam)
            .withFragmentShader(R.shaders.core.vfx_beam)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withVertexBinding(1, VertexFormat.builder(1)
                    .addAttribute("InstanceMat", GpuFormat.RGBA32_FLOAT, 4)
                    .build())
            .build();

    public static final RenderPipeline BEAM_BOX = builder()
            .withLocation(academy("pipeline/vfx_beam_box"))
            .withVertexShader(R.shaders.core.vfx_beam)
            .withFragmentShader(R.shaders.core.vfx_beam)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withVertexBinding(1, VertexFormat.builder(1)
                    .addAttribute("InstanceMat", GpuFormat.RGBA32_FLOAT, 4)
                    .build())
            .build();

    public static final RenderPipeline BEAM_CORE_BALL = builder()
            .withLocation(academy("pipeline/vfx_beam_core_ball"))
            .withVertexShader(R.shaders.core.vfx_beam)
            .withFragmentShader(R.shaders.core.vfx_beam)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withVertexBinding(1, VertexFormat.builder(1)
                    .addAttribute("InstanceMat", GpuFormat.RGBA32_FLOAT, 4)
                    .build())
            .build();

    public static final RenderPipeline BEAM_CORE_BOX = builder()
            .withLocation(academy("pipeline/vfx_beam_core_box"))
            .withVertexShader(R.shaders.core.vfx_beam)
            .withFragmentShader(R.shaders.core.vfx_beam)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withVertexBinding(1, VertexFormat.builder(1)
                    .addAttribute("InstanceMat", GpuFormat.RGBA32_FLOAT, 4)
                    .build())
            .build();

    public static final RenderPipeline TEX_BILLBOARD_TRANSLUCENT = builder()
            .withLocation(academy("pipeline/vfx_tex_billboard_translucent"))
            .withVertexShader(R.shaders.core.vfx_tex_billboard)
            .withFragmentShader(R.shaders.core.vfx_tex_billboard)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withVertexBinding(1, VertexFormat.builder(1)
                    .addAttribute("InstancePos", GpuFormat.RGB32_FLOAT)
                    .addAttribute("InstanceSize", GpuFormat.R32_FLOAT)
                    .addAttribute("InstanceAlpha", GpuFormat.R32_FLOAT)
                    .addAttribute("InstanceUVRect", GpuFormat.RGBA32_FLOAT)
                    .build())
            .build();

    public static final RenderPipeline TEX_RING_TRANSLUCENT = builder()
            .withLocation(academy("pipeline/vfx_tex_ring_translucent"))
            .withVertexShader(R.shaders.core.vfx_ring)
            .withFragmentShader(R.shaders.core.vfx_ring)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withVertexBinding(1, VertexFormat.builder(1)
                    .addAttribute("InstanceMat", GpuFormat.RGBA32_FLOAT, 4)
                    .build())
            .build();

    public static final RenderPipeline LIGHTNING_TUBE = lightningTube(
            academy("pipeline/vfx_lightning"), null, GpuFormat.RGBA8_UNORM
    );
    public static final RenderPipeline SCREEN_FLASH = builder()
            .withLocation(academy("pipeline/vfx_screen_flash"))
            .withVertexShader(R.shaders.core.vfx_screen_flash)
            .withFragmentShader(R.shaders.core.vfx_screen_flash)
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withVertexBinding(0, DefaultVertexFormat.POSITION)
            .withVertexBinding(1, VertexFormat.builder(1)
                    .addAttribute("InstanceColor", GpuFormat.RGBA32_FLOAT)
                    .build())
            .build();
    public static final RenderPipeline LIGHTNING_TUBE_BLOOM = lightningTube(
            academy("pipeline/vfx_lightning_bloom"), BlendFunction.ADDITIVE, GpuFormat.RGBA8_UNORM
    );
    private static final VertexFormat SKY_STRIKE_QUAD_INSTANCE_FORMAT = VertexFormat.builder(1)
            .addAttribute("Corner0Pos", GpuFormat.RGB32_FLOAT)
            .addAttribute("Corner0Uv", GpuFormat.RG32_FLOAT)
            .addAttribute("Corner0Color", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Corner1Pos", GpuFormat.RGB32_FLOAT)
            .addAttribute("Corner1Uv", GpuFormat.RG32_FLOAT)
            .addAttribute("Corner1Color", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Corner2Pos", GpuFormat.RGB32_FLOAT)
            .addAttribute("Corner2Uv", GpuFormat.RG32_FLOAT)
            .addAttribute("Corner2Color", GpuFormat.RGBA32_FLOAT)
            .addAttribute("Corner3Pos", GpuFormat.RGB32_FLOAT)
            .addAttribute("Corner3Uv", GpuFormat.RG32_FLOAT)
            .addAttribute("Corner3Color", GpuFormat.RGBA32_FLOAT)
            .build();
    public static final RenderPipeline SKY_STRIKE_QUAD_TRANSLUCENT = skyStrikeQuad(
            "pipeline/vfx_sky_strike_quad_translucent", BlendFunction.TRANSLUCENT).build();
    public static final RenderPipeline SKY_STRIKE_QUAD_ADDITIVE = skyStrikeQuad(
            "pipeline/vfx_sky_strike_quad_additive", BlendFunction.ADDITIVE).build();

    private VfxPipelines() {
    }

    private static RenderPipeline lightningTube(
            Identifier location,
            @Nullable BlendFunction blend,
            GpuFormat colorFormat
    ) {
        return builder()
                .withLocation(location)
                .withVertexShader(R.shaders.core.vfx_lightning)
                .withFragmentShader(R.shaders.core.vfx_lightning)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform("LightningUniforms", UniformType.UNIFORM_BUFFER)
                        .build())
                .withCull(false)
                .withColorTargetState(new ColorTargetState(
                        blend == null ? Optional.empty() : Optional.of(blend),
                        colorFormat,
                        ColorTargetState.WRITE_ALL
                ))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .build();
    }

    private static RenderPipeline.Builder skyStrikeQuad(String location, BlendFunction blend) {
        return builder()
                .withLocation(academy(location))
                .withVertexShader(R.shaders.core.vfx_sky_strike_quad)
                .withFragmentShader(R.shaders.core.vfx_sky_strike_quad)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withCull(false)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .withVertexBinding(1, SKY_STRIKE_QUAD_INSTANCE_FORMAT);
    }

    @SubscribeEvent
    public static void onRegisterRenderPipelinesEvent(RegisterRenderPipelinesEvent event) {
        for (var field : VfxPipelines.class.getDeclaredFields()) {
            var modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }
            if (field.getType() != RenderPipeline.class) continue;
            try {
                event.registerPipeline((RenderPipeline) field.get(null));
            } catch (IllegalAccessException e) {
                AcademyCraft.getLogger().warn(e.getMessage());
            }
        }
    }
}
