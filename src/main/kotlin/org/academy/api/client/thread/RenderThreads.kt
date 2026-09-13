package org.academy.api.client.thread

import com.mojang.blaze3d.systems.RenderSystem
import org.academy.api.client.gui.environment.UiEnvironment
import java.util.concurrent.CompletableFuture

internal fun <T> runOnRenderThread(task: () -> T): T {
    if (RenderSystem.isOnRenderThread()) return task()
    val future = CompletableFuture<T>()
    UiEnvironment.get().runOnMainThread {
        try {
            future.complete(task())
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }
    return future.join()
}
