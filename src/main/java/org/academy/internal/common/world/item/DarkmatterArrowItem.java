package org.academy.internal.common.world.item;

import net.minecraft.world.item.ArrowItem;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;

public final class DarkmatterArrowItem extends ArrowItem implements DarkmatterShapedItem {
    public DarkmatterArrowItem(Properties properties) {
        super(DarkmatterNativeItemSupport.ammunitionProperties(properties));
    }

    @Override
    public DarkmatterShape darkmatterShape() {
        return DarkmatterShape.ARROW;
    }

    @Override
    public boolean usesDarkmatterIntegrity() {
        return false;
    }
}
