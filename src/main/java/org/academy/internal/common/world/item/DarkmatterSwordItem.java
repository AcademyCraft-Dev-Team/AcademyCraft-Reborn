package org.academy.internal.common.world.item;

import org.academy.api.common.ability.darkmatter.DarkmatterShape;

public final class DarkmatterSwordItem extends DarkmatterEquipmentItem {
    public DarkmatterSwordItem(Properties properties) {
        super(properties);
    }

    @Override
    public DarkmatterShape darkmatterShape() {
        return DarkmatterShape.SWORD;
    }
}
