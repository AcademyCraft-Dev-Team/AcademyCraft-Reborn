package org.academy.internal.client.renderer.item;

import net.minecraft.util.Util;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/**
 * Misaka Tower–only render types. Does not alter shared {@link Render} / vanilla defaults.
 */
final class MisakaTowerRenderTypes {
    private static final Function<Identifier, RenderType> OUTLINE_CUTOUT_CULL_Z_OFFSET = Util.memoize(
            texture -> RenderType.create(
                    "academy_misaka_tower_outline",
                    RenderSetup.builder(RenderPipelines.ENTITY_CUTOUT_CULL)
                            .withTexture("Sampler0", texture)
                            .useLightmap()
                            .useOverlay()
                            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                            .affectsCrumbling()
                            .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                            .createRenderSetup()
            )
    );

    private MisakaTowerRenderTypes() {
    }

    static RenderType body(Identifier texture) {
        return RenderTypes.entityCutoutCull(texture);
    }

    static RenderType outline(Identifier texture) {
        return OUTLINE_CUTOUT_CULL_Z_OFFSET.apply(texture);
    }
}
