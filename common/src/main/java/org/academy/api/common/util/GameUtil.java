package org.academy.api.common.util;

import net.minecraft.client.Minecraft;
import org.academy.api.common.vanilla.EnvType;

public class GameUtil {
    private GameUtil() {
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static EnvType getEnvType() {
        try {
            Minecraft.class.getClass();
            return EnvType.CLIENT;
        } catch (Throwable ignored) {
            return EnvType.SERVER;
        }
    }
}