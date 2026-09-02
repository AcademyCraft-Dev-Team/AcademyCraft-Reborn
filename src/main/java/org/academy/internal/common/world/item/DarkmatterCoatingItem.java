package org.academy.internal.common.world.item;

import net.minecraft.world.item.Item;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.academy.api.common.ability.darkmatter.DarkmatterShapingProfile;

/**
 * Configured consumable that transfers its phase profile onto another item in crafting.
 */
public final class DarkmatterCoatingItem extends Item implements DarkmatterShapedItem {
    public DarkmatterCoatingItem(Properties properties) {
        super(DarkmatterNativeItemSupport.enchantableProperties(properties).stacksTo(1).component(
                ItemDataComponents.DARKMATTER_SHAPING_PROFILE.get(),
                DarkmatterShapingProfile.DEFAULT));
    }

    @Override
    public DarkmatterShape darkmatterShape() {
        return DarkmatterShape.COATING;
    }

    @Override
    public boolean usesDarkmatterIntegrity() {
        return false;
    }
}
