package org.academy.internal.client.renderer.item;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.model.DefaultedItemGeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.academy.AcademyCraft;
import org.jspecify.annotations.Nullable;

public final class MisakaTowerRenderer<T extends Item & GeoAnimatable> extends GeoItemRenderer<T> {
    public MisakaTowerRenderer() {
        super(new DefaultedItemGeoModel<>(AcademyCraft.academy("misaka_tower")));
        useAlternateGuiLighting();
        withRenderLayer(OutlineBoneGeoLayer::new);
    }

    @Override
    public @Nullable RenderType getRenderType(GeoRenderState renderState, Identifier texture) {
        return MisakaTowerRenderTypes.body(texture);
    }
}
