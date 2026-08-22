package org.academy.internal.common.world.item;

import org.academy.api.common.ability.darkmatter.DarkmatterShape;

/** Marker implemented by every native item produced by the shaping editor. */
public interface DarkmatterShapedItem {
    DarkmatterShape darkmatterShape();

    default boolean usesDarkmatterIntegrity() {
        return true;
    }
}
