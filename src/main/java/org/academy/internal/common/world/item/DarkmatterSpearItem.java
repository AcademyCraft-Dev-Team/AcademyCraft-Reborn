package org.academy.internal.common.world.item;

import org.academy.api.common.ability.darkmatter.DarkmatterShape;

/** Native shaped spear using vanilla spear left-click attacks and held right-click thrusting. */
public final class DarkmatterSpearItem extends DarkmatterEquipmentItem {
    public DarkmatterSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public DarkmatterShape darkmatterShape() {
        return DarkmatterShape.SPEAR;
    }
}
