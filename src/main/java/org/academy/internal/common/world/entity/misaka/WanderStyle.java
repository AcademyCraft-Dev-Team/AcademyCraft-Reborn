package org.academy.internal.common.world.entity.misaka;

import com.mojang.serialization.Codec;
import org.academy.internal.common.world.entity.misaka.MisakaSisterEntity;

public enum WanderStyle {
    WAITING,
    FREE_MOVE,
    FOLLOW;

    public static final Codec<WanderStyle> CODEC = Codec.STRING.xmap(
            WanderStyle::byName,
            style -> style.name().toLowerCase()
    );

    public static WanderStyle fromOrdinal(int ordinal) {
        var values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return FREE_MOVE;
        }
        return values[ordinal];
    }

    public void apply(MisakaSisterEntity sister) {
        sister.getNavigation().stop();
        var followGoal = sister.followGoal();
        var wanderGoal = sister.networkWanderGoal();
        if (followGoal != null) {
            followGoal.setEnabled(this == FOLLOW);
        }
        if (wanderGoal != null) {
            wanderGoal.setEnabled(this == FREE_MOVE);
        }
    }

    private static WanderStyle byName(String value) {
        for (var style : values()) {
            if (style.name().equalsIgnoreCase(value)) {
                return style;
            }
        }
        throw new IllegalArgumentException("Unknown wander style: " + value);
    }
}
