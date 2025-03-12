package org.academy.api.common.util;

import net.minecraft.client.Minecraft;

public class GameUtil {
    public static EnvType getEnvType() {
        try {
            Minecraft.class.getClasses();
            return EnvType.CLIENT;
        } catch (Throwable throwable) {
            return EnvType.SERVER;
        }
    }

    private GameUtil() {
    }

    public enum EnvType {
        CLIENT,
        SERVER,
    }
}