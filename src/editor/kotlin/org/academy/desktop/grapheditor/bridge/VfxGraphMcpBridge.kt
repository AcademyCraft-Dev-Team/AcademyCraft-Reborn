package org.academy.desktop.grapheditor.bridge

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID

/**
 * VFXGraph 编辑器与本地 MCP 进程之间的文件队列桥。
 *
 * MCP 将命令写入 [root]/inbox，编辑器在渲染线程调用 [poll]，处理后把结果原子写入
 * [root]/outbox。所有模型操作因此都留在渲染线程，避免 HTTP/后台线程直接触碰 ImGui
 * 或预览状态。桥只使用项目 run 目录，不监听端口，也不接受项目外路径。
 */
class VfxGraphMcpBridge(
    private val root: Path,
    private val handler: (JsonObject) -> JsonObject,
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
    private val heartbeatIntervalNanos: Long = 500_000_000L,
) : AutoCloseable {
    private val inbox = root.resolve("inbox")
    private val outbox = root.resolve("outbox")
    private val statusFile = root.resolve("status.json")
    private var lastHeartbeatNanos = 0L

    init {
        Files.createDirectories(inbox)
        Files.createDirectories(outbox)
    }

    /** 在编辑器渲染线程调用；单帧最多处理 32 个请求，防止异常队列拖垮帧时间。 */
    fun poll() {
        try {
            Files.createDirectories(inbox)
            Files.createDirectories(outbox)
            Files.list(inbox).use { files ->
                files.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                    .sorted()
                    .limit(MAX_REQUESTS_PER_POLL)
                    .forEach(::process)
            }
        } catch (_: Exception) {
            // MCP 是开发辅助能力；文件队列暂时不可用时不能拖垮编辑器渲染循环。
        }
        try {
            writeHeartbeatIfDue()
        } catch (_: Exception) {
            // 同上，下一帧会自动重试。
        }
    }

    private fun process(requestFile: Path) {
        val fallbackId = requestFile.fileName.toString().removeSuffix(".json")
        var id = fallbackId
        val response = JsonObject()
        try {
            val request = gson.fromJson(Files.readString(requestFile), JsonObject::class.java)
                ?: error("empty request")
            id = request.get("id")?.asString?.takeIf { it.isNotBlank() } ?: fallbackId
            response.addProperty("ok", true)
            response.add("result", handler(request))
        } catch (e: Exception) {
            response.addProperty("ok", false)
            response.addProperty("error", e.message ?: e.javaClass.simpleName)
        }
        response.addProperty("id", id)
        response.addProperty("protocolVersion", PROTOCOL_VERSION)
        response.addProperty("timestamp", Instant.now().toString())
        atomicWrite(outbox.resolve("$id.json"), gson.toJson(response))
        Files.deleteIfExists(requestFile)
    }

    private fun writeHeartbeatIfDue() {
        val now = System.nanoTime()
        if (now - lastHeartbeatNanos < heartbeatIntervalNanos) return
        lastHeartbeatNanos = now
        val status = JsonObject()
        status.addProperty("running", true)
        status.addProperty("pid", ProcessHandle.current().pid())
        status.addProperty("protocolVersion", PROTOCOL_VERSION)
        status.addProperty("timestamp", Instant.now().toString())
        try {
            status.add("editor", handler(JsonObject().apply { addProperty("action", "status") }))
        } catch (e: Exception) {
            status.addProperty("error", e.message ?: e.javaClass.simpleName)
        }
        atomicWrite(statusFile, gson.toJson(status))
    }

    override fun close() {
        val status = JsonObject()
        status.addProperty("running", false)
        status.addProperty("protocolVersion", PROTOCOL_VERSION)
        status.addProperty("timestamp", Instant.now().toString())
        try {
            atomicWrite(statusFile, gson.toJson(status))
        } catch (_: Exception) {
        }
    }

    private fun atomicWrite(target: Path, content: String) {
        Files.createDirectories(target.parent)
        val temp = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        Files.writeString(
            temp,
            content,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val PROTOCOL_VERSION = 1
        private const val MAX_REQUESTS_PER_POLL = 32L
    }
}
