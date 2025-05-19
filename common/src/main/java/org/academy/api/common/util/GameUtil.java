package org.academy.api.common.util;

import net.minecraft.client.Minecraft;
import org.academy.api.common.vanilla.EnvType;
import org.academy.api.common.vanilla.ThreadType;

public class GameUtil {
    private GameUtil() {
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static EnvType getEnvType() {
        try {
            Minecraft.class.getClass();
            return EnvType.CLIENT;
        } catch (Throwable ignored) {
            return EnvType.DEDICATED_SERVER;
        }
    }

    public static ThreadType getThreadType() {
        Thread thread = Thread.currentThread();
        String threadName = thread.getName();
        return switch (threadName) {
            case "Server thread" -> ThreadType.SERVER;
            case "Render thread" -> ThreadType.CLIENT;
            default -> throw new IllegalArgumentException("Unknown thread type: " + threadName);
        };
    }
}