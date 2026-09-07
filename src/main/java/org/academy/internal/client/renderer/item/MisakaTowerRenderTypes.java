package org.academy.internal.client.renderer.item;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.academy.AcademyCraft;

import java.util.function.Function;

/**
 * Misaka Tower–only render types. Does not alter shared {@link org.academy.api.client.render.Render} /
 * vanilla defaults used by other models.
 */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID, value = Dist.CLIENT)
public final class MisakaTowerRenderTypes {
    /**
     * Cutout + cull, no lightmap and no cardinal lighting ({@code EMISSIVE} +
     * {@code NO_CARDINAL_LIGHTING}) for {@code *_outline} bones.
     */
    public static final RenderPipeline OUTLINE_UNLIT = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation(AcademyCraft.academy("pipeline/misaka_tower_outline"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .build();

    private static final Function<Identifier, RenderType> OUTLINE_CUTOUT_CULL_Z_OFFSET = Util.memoize(
            texture -> RenderType.create(
                    "academy_misaka_tower_outline",
                    RenderSetup.builder(OUTLINE_UNLIT)
                            .withTexture("Sampler0", texture)
                            .useOverlay()
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .affectsCrumbling()
                            .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                            .createRenderSetup()
            )
    );

    private MisakaTowerRenderTypes() {
    }

    @SubscribeEvent
    public static void onRegisterRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(OUTLINE_UNLIT);
    }

    static RenderType body(Identifier texture) {
        return RenderTypes.entityCutoutCull(texture);
    }

    static RenderType outline(Identifier texture) {
        return OUTLINE_CUTOUT_CULL_Z_OFFSET.apply(texture);
    }
}
