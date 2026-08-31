package org.academy.desktop.grapheditor.bridge

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class VfxGraphMcpBridgeTest {
    private val gson = Gson()

    @Test
    fun processesRequestAndPublishesHeartbeat() {
        val root = Files.createTempDirectory("vfxgraph-mcp-bridge")
        val bridge = VfxGraphMcpBridge(root, { request ->
            JsonObject().apply { addProperty("action", request.get("action").asString) }
        }, heartbeatIntervalNanos = 0L)

        Files.createDirectories(root.resolve("inbox"))
        Files.writeString(root.resolve("inbox/request-1.json"), """
            {"id":"request-1","action":"status"}
        """.trimIndent())

        bridge.poll()

        assertFalse(Files.exists(root.resolve("inbox/request-1.json")))
        val response = gson.fromJson(
            Files.readString(root.resolve("outbox/request-1.json")), JsonObject::class.java
        )
        assertTrue(response.get("ok").asBoolean)
        assertEquals("status", response.getAsJsonObject("result").get("action").asString)
        val status = gson.fromJson(Files.readString(root.resolve("status.json")), JsonObject::class.java)
        assertTrue(status.get("running").asBoolean)

        bridge.close()
        val stopped = gson.fromJson(Files.readString(root.resolve("status.json")), JsonObject::class.java)
        assertFalse(stopped.get("running").asBoolean)
    }

    @Test
    fun reportsMalformedRequestWithoutStoppingQueue() {
        val root = Files.createTempDirectory("vfxgraph-mcp-bridge-error")
        val bridge = VfxGraphMcpBridge(root, { JsonObject() }, heartbeatIntervalNanos = Long.MAX_VALUE)
        Files.writeString(root.resolve("inbox/bad.json"), "{")

        bridge.poll()

        val response = gson.fromJson(Files.readString(root.resolve("outbox/bad.json")), JsonObject::class.java)
        assertFalse(response.get("ok").asBoolean)
        assertTrue(response.has("error"))
        bridge.close()
    }
}
