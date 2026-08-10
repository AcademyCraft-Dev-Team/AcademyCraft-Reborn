package org.academy.internal.client.gui.debug

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.academy.AcademyCraft
import org.academy.Dev
import org.academy.api.client.gui.serialize.UiJson
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.internal.client.gui.SerializedUiLayout
import org.academy.internal.client.hud.HudLayoutDefaults
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*

object UiDebugSession {
    private val logger = AcademyCraft.getLogger()
    private val pretty = GsonBuilder().setPrettyPrinting().create()
    private val documents = LinkedHashMap<String, Document>()
    private val lock = Any()
    private val hostSnapshots = WeakHashMap<SerializedUiDebugHost, String>()
    private var hudDefaultsInitial: HudLayoutDefaults.Config? = null
    private var hudDefaultsDraft: HudLayoutDefaults.Config? = null
    private var hudDefaultsDirty = false
    private var hudDefaultsPendingPublish = false

    @Volatile
    var attachedLayoutId: String? = null
        private set

    @Volatile
    var hudEditorOpen: Boolean = false

    data class Document(
        val definition: UiDebugLayoutDefinition,
        var initial: JsonObject,
        var draft: JsonObject,
        var dirty: Boolean = false,
        var error: String? = null,
        var pendingPublish: Boolean = false
    )

    data class UpdateResult(val accepted: Boolean, val error: String? = null)

    data class PublishResult(
        val saved: Int,
        val sourceRoot: Path?,
        val workingDirectory: Path,
        val error: String? = null
    ) {
        val successful: Boolean get() = error == null
    }

    fun isAvailable(): Boolean {
        return Dev.HAS_IM_GUI && System.getenv("IS_DEV")?.toBooleanStrictOrNull() == true
    }

    fun document(id: String): Document = synchronized(lock) {
        documents.getOrPut(id) { loadDocument(UiDebugLayoutRegistry.require(id)) }
    }

    fun documentJson(id: String): JsonObject = synchronized(lock) {
        document(id).draft.deepCopy()
    }

    fun status(id: String): Document = synchronized(lock) {
        document(id).copy(draft = document(id).draft.deepCopy(), initial = document(id).initial.deepCopy())
    }

    fun sourceTranslationKey(id: String): String {
        val override = WidgetSerializer.layoutDir().resolve("$id.json")
        return if (Files.isRegularFile(override)) {
            "screen.academy.ui_debug.source.working"
        } else {
            "screen.academy.ui_debug.source.bundled"
        }
    }

    fun update(id: String, json: JsonObject): UpdateResult = synchronized(lock) {
        val state = document(id)
        val validation = validate(state.definition, json)
        if (validation != null) {
            state.error = validation
            return@synchronized UpdateResult(false, validation)
        }
        state.draft = normalize(json)
        state.dirty = state.draft != state.initial
        state.error = null
        UpdateResult(true)
    }

    fun capture(host: SerializedUiDebugHost) {
        if (!shouldAttach(host)) return
        val root = host.debugLayoutRoot()
        val encoded = host.sanitizeDebugCapture(WidgetSerializer.encode(root))
        val fingerprint = UiJson.GSON.toJson(encoded)
        synchronized(lock) {
            val previous = hostSnapshots.put(host, fingerprint) ?: return
            if (previous == fingerprint) return
        }
        update(host.debugLayoutId(), encoded)
    }

    fun runtimeLayout(id: String): FrameLayoutWidget? = synchronized(lock) {
        if (!isAvailable()) return@synchronized null
        val state = documents[id] ?: return@synchronized null
        runCatching { WidgetSerializer.decode(state.draft.deepCopy()) as FrameLayoutWidget }.getOrNull()
    }

    fun attach(id: String?) {
        attachedLayoutId = id
        if (id != null) document(id)
    }

    fun shouldAttach(host: SerializedUiDebugHost): Boolean {
        return isAvailable() && (host.alwaysShowDebugEditor() || attachedLayoutId == host.debugLayoutId())
    }

    fun revert(id: String) = synchronized(lock) {
        val state = document(id)
        state.draft = state.initial.deepCopy()
        state.dirty = false
        state.error = null
    }

    fun reload(id: String) = synchronized(lock) {
        documents[id] = loadDocument(UiDebugLayoutRegistry.require(id))
    }

    fun hasUnsavedChanges(): Boolean = synchronized(lock) {
        documents.values.any { it.dirty || it.pendingPublish } || hudDefaultsDirty || hudDefaultsPendingPublish
    }

    fun hudDefaults(): HudLayoutDefaults.Config = synchronized(lock) {
        ensureHudDefaults()
        hudDefaultsDraft!!.copyDeep()
    }

    fun updateHudDefaults(config: HudLayoutDefaults.Config): UpdateResult = synchronized(lock) {
        ensureHudDefaults()
        for ((name, value) in config.regions) {
            if (!value.offsetX.isFinite() || !value.offsetY.isFinite() || !value.scale.isFinite()) {
                return@synchronized UpdateResult(
                    false,
                    tr("screen.academy.ui_debug.error.invalid_hud_defaults", name)
                )
            }
            value.scale = value.scale.coerceIn(0.5f, 2.0f)
        }
        hudDefaultsDraft = config.copyDeep()
        hudDefaultsDirty =
            HudLayoutDefaults.toJson(hudDefaultsDraft!!) != HudLayoutDefaults.toJson(hudDefaultsInitial!!)
        HudLayoutDefaults.replace(hudDefaultsDraft!!)
        UpdateResult(true)
    }

    fun revertHudDefaults() = synchronized(lock) {
        ensureHudDefaults()
        hudDefaultsDraft = hudDefaultsInitial!!.copyDeep()
        hudDefaultsDirty = false
        HudLayoutDefaults.replace(hudDefaultsDraft!!)
    }

    fun publish(): PublishResult = synchronized(lock) {
        val dirty = documents.values.filter { it.dirty || it.pendingPublish }
        ensureHudDefaults()
        val publishHudDefaults = hudDefaultsDirty || hudDefaultsPendingPublish
        val workingDir = WidgetSerializer.layoutDir()
        if (dirty.isEmpty() && !publishHudDefaults) {
            return@synchronized PublishResult(0, findProjectRoot(), workingDir)
        }
        for (state in dirty) {
            val error = validate(state.definition, state.draft)
            if (error != null) {
                state.error = error
                return@synchronized PublishResult(0, findProjectRoot(), workingDir, "${state.definition.id}: $error")
            }
        }

        val projectRoot = findProjectRoot()
        val sourceDir = projectRoot?.resolve("src/main/resources/assets/academy/ui/layout")
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(Date())
        val backupDir = workingDir.resolve("backup").resolve(stamp)
        val plannedCount = dirty.size + if (publishHudDefaults) 1 else 0
        var workingPublished = false
        return@synchronized try {
            Files.createDirectories(workingDir)
            Files.createDirectories(backupDir)
            val workingFiles = linkedMapOf<String, String>()
            for (state in dirty) {
                val name = "${state.definition.id}.json"
                workingFiles[name] = canonicalText(state.draft)
            }
            if (publishHudDefaults) {
                workingFiles[HudLayoutDefaults.FILE_NAME] =
                    canonicalText(HudLayoutDefaults.toJson(hudDefaultsDraft!!))
            }
            UiDebugFilePublisher.writeBatch(workingDir, workingFiles, backupDir, "working-")
            workingPublished = true
            if (sourceDir != null && Files.isDirectory(projectRoot.resolve("src/main/resources"))) {
                Files.createDirectories(sourceDir)
                UiDebugFilePublisher.writeBatch(sourceDir, workingFiles, backupDir, "source-")
                dirty.forEach {
                    it.initial = it.draft.deepCopy()
                    it.dirty = false
                    it.pendingPublish = false
                }
                if (publishHudDefaults) {
                    hudDefaultsInitial = hudDefaultsDraft!!.copyDeep()
                    hudDefaultsDirty = false
                    hudDefaultsPendingPublish = false
                }
                PublishResult(plannedCount, projectRoot, workingDir)
            } else {
                dirty.forEach { it.pendingPublish = true }
                if (publishHudDefaults) hudDefaultsPendingPublish = true
                logger.warn(
                    "[UiDebug] Saved working copies to {}, but the project source root could not be located",
                    workingDir
                )
                PublishResult(
                    plannedCount, null, workingDir,
                    tr("screen.academy.ui_debug.error.project_root_missing")
                )
            }
        } catch (exception: Exception) {
            logger.error("[UiDebug] Failed to publish layouts", exception)
            dirty.forEach { it.pendingPublish = true }
            if (publishHudDefaults) hudDefaultsPendingPublish = true
            PublishResult(
                if (workingPublished) plannedCount else 0,
                projectRoot,
                workingDir,
                exception.message ?: exception.javaClass.simpleName
            )
        }
    }

    fun close() {
        if (hasUnsavedChanges()) logger.warn("[UiDebug] Client stopped with unpublished UI layout changes")
        documents.clear()
        hostSnapshots.clear()
        hudDefaultsInitial = null
        hudDefaultsDraft = null
        hudDefaultsDirty = false
        hudDefaultsPendingPublish = false
        attachedLayoutId = null
        hudEditorOpen = false
    }

    private fun loadDocument(definition: UiDebugLayoutDefinition): Document {
        val override = WidgetSerializer.layoutDir().resolve("${definition.id}.json")
        val json = runCatching {
            if (Files.isRegularFile(override)) {
                JsonParser.parseString(Files.readString(override)).asJsonObject
            } else {
                Minecraft.getInstance().resourceManager.open(definition.resource).use {
                    JsonParser.parseReader(it.reader()).asJsonObject
                }
            }
        }.getOrElse { exception ->
            throw IllegalStateException("Unable to load debug layout '${definition.id}'", exception)
        }
        val normalized = normalize(json)
        validate(definition, normalized)?.let { throw IllegalArgumentException("${definition.id}: $it") }
        return Document(definition, normalized.deepCopy(), normalized.deepCopy())
    }

    private fun ensureHudDefaults() {
        if (hudDefaultsDraft != null) return
        val loaded = HudLayoutDefaults.loadJson(HudLayoutDefaults.loadSourceJson())
        hudDefaultsInitial = loaded.copyDeep()
        hudDefaultsDraft = loaded.copyDeep()
        HudLayoutDefaults.replace(loaded)
    }

    private fun validate(definition: UiDebugLayoutDefinition, json: JsonObject): String? {
        val decoded = try {
            WidgetSerializer.decode(json.deepCopy())
        } catch (exception: Exception) {
            return tr("screen.academy.ui_debug.error.decode_failed", exception.message ?: "")
        }
        if (decoded !is FrameLayoutWidget) return tr("screen.academy.ui_debug.error.root_type")
        for (binding in definition.bindings) {
            val widget = SerializedUiLayout.find(decoded, binding.name)
                ?: return tr("screen.academy.ui_debug.error.missing_widget", binding.name)
            if (!binding.widgetClass.isInstance(widget)) {
                return tr(
                    "screen.academy.ui_debug.error.widget_type",
                    binding.name,
                    binding.widgetClass.simpleName
                )
            }
        }
        return validateFinite(json)
    }

    private fun validateFinite(element: JsonElement): String? {
        if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
            val value = runCatching { element.asDouble }.getOrNull()
                ?: return tr("screen.academy.ui_debug.error.invalid_number")
            if (!value.isFinite()) return tr("screen.academy.ui_debug.error.non_finite")
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { validateFinite(it)?.let { error -> return error } }
        } else if (element.isJsonObject) {
            element.asJsonObject.entrySet().forEach { (_, value) ->
                validateFinite(value)?.let { error -> return error }
            }
        }
        return null
    }

    private fun normalize(json: JsonObject): JsonObject {
        return UiJson.GSON.fromJson(UiJson.GSON.toJson(json), JsonObject::class.java)
    }

    private fun canonicalText(json: JsonObject): String = pretty.toJson(json) + "\n"

    private fun tr(key: String, vararg args: Any): String = Component.translatable(key, *args).string

    private fun findProjectRoot(): Path? {
        System.getProperty("academy.ui.debug.project_root")?.takeIf { it.isNotBlank() }?.let {
            val path = Path.of(it).toAbsolutePath().normalize()
            if (isProjectRoot(path)) return path
        }
        var current: Path? = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize()
        repeat(6) {
            val candidate = current ?: return null
            if (isProjectRoot(candidate)) return candidate
            current = candidate.parent
        }
        return null
    }

    private fun isProjectRoot(path: Path): Boolean {
        return Files.isRegularFile(path.resolve("settings.gradle"))
                && Files.isDirectory(path.resolve("src/main/resources"))
    }

}
