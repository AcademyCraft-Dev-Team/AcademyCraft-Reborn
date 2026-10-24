package org.academy.internal.client.renderer.item;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.renderer.GeoItemRenderer;
import net.minecraft.world.item.Item;
import org.academy.AcademyCraft;
import org.academy.internal.client.renderer.RelayPlatformGeo;

/** Shared inventory / hand renderer for network relay satellite items. */
public final class RelaySatelliteItemRenderer<T extends Item & GeoAnimatable> extends GeoItemRenderer<T> {
    public RelaySatelliteItemRenderer() {
        super(RelayPlatformGeo.itemModel(AcademyCraft.academy("relay_satellite")));
        useAlternateGuiLighting();
        withScale(0.55F);
    }
}
