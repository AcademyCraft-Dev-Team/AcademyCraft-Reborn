package org.academy.api.client.render.vfx.lightning;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 电弧网格计算用的共享后台执行器：单线程、守护线程、懒创建。
 */
public final class ArcExecutor {
    private static final ThreadFactory FACTORY = runnable -> {
        var thread = new Thread(runnable, "AC-Arc");
        thread.setDaemon(true);
        return thread;
    };

    private static volatile @Nullable ExecutorService executor;

    private ArcExecutor() {
    }

    public static Executor get() {
        var current = executor;
        if (current == null) {
            synchronized (ArcExecutor.class) {
                current = executor;
                if (current == null) {
                    current = Executors.newSingleThreadExecutor(FACTORY);
                    executor = current;
                }
            }
        }
        return current;
    }

    public static void shutdownAndReset() {
        synchronized (ArcExecutor.class) {
            var current = executor;
            executor = null;
            if (current != null) {
                current.shutdown();
            }
        }
    }
}
