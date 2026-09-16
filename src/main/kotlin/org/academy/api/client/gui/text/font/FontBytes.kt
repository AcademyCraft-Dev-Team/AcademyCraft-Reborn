package org.academy.api.client.gui.text.font

import net.minecraft.resources.Identifier
import org.academy.api.client.gui.environment.UiEnvironment
import org.lwjgl.system.MemoryUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

object FontBytes {
    private val bytes = ConcurrentHashMap<Identifier, ByteArray>()
    private val buffers = ConcurrentHashMap<Identifier, ByteBuffer>()

    fun bytes(id: Identifier): ByteArray? {
        bytes[id]?.let { return it }
        val stream = UiEnvironment.get().openResource(id.namespace, id.path) ?: return null
        return try {
            stream.use { data ->
                data.readAllBytes().also { bytes[id] = it }
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to read font bytes: $id", e)
        }
    }

    fun buffer(id: Identifier): ByteBuffer? {
        buffers[id]?.let { return it }
        val data = bytes(id) ?: return null
        val buffer = MemoryUtil.memAlloc(data.size)
        buffer.put(data)
        buffer.flip()
        buffers[id] = buffer
        return buffer
    }

    fun clear() {
        buffers.values.forEach(MemoryUtil::memFree)
        buffers.clear()
        bytes.clear()
    }
}
