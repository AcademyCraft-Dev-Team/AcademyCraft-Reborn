package org.academy.internal.client.renderer.item;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Shared inventory / hand renderer for items that reuse {@code geckolib/.../block/<id>} assets
 * via {@link DefaultedBlockGeoModel}.
 */
public final class GeoBlockItemRenderer<T extends Item & GeoAnimatable> extends GeoItemRenderer<T> {
    public GeoBlockItemRenderer(Identifier modelId) {
        this(modelId, 1.0F);
    }

    public GeoBlockItemRenderer(Identifier modelId, float scale) {
        super(new DefaultedBlockGeoModel<>(modelId));
        useAlternateGuiLighting();
        if (scale != 1.0F) {
            withScale(scale);
        }
    }
}
