package org.academy.internal.common.world.entity.misaka;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class MisakaDayTime {
    private MisakaDayTime() {
    }

    public static long dayTime(Level level) {
        return level.getOverworldClockTime();
    }

    public static int dayIndex(Level level) {
        return (int) (dayTime(level) / 24000L);
    }

    public static boolean isNight(Level level) {
        return level instanceof ServerLevel serverLevel && serverLevel.isDarkOutside();
    }
}
