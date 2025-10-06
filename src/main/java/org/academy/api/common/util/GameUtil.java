package org.academy.api.common.util;

import net.neoforged.fml.loading.FMLEnvironment;
import org.academy.api.common.vanilla.EnvType;
import org.academy.api.common.vanilla.ThreadType;

public class GameUtil {
    private GameUtil() {
    }

    public static EnvType getEnvType() {
        return FMLEnvironment.getDist().isClient() ? EnvType.CLIENT : EnvType.DEDICATED_SERVER;
    }

    /**
     * 只用于区分 CS 喵
     * 调用时小心点喵
     * 其他线程请 catch 处理喵
     */
    public static ThreadType getThreadType() {
        var thread = Thread.currentThread();
        var threadName = thread.getName();
        return switch (threadName) {
            case "Server thread" -> ThreadType.SERVER;
            case "Render thread" -> ThreadType.CLIENT;
            default -> throw new IllegalArgumentException("Unknown thread type: " + threadName);
        };
    }
}