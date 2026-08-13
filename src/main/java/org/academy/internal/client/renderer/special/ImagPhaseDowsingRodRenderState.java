package org.academy.internal.client.renderer.special;

import net.minecraft.world.item.ItemDisplayContext;

public record ImagPhaseDowsingRodRenderState(
        boolean showMap,
        ItemDisplayContext displayContext
) {
}
