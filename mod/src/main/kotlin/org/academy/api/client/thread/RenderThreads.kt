package org.academy.api.client.thread

import org.academy.api.client.gui.environment.UiEnvironment
import java.util.concurrent.CompletableFuture

internal fun <T> runOnRenderThread(task: () -> T): T {
    val environment = UiEnvironment.get()
    if (environment.isOnRenderThread()) return task()
    val future = CompletableFuture<T>()
    environment.runOnRenderThread {
        try {
            future.complete(task())
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }
    return future.join()
}
