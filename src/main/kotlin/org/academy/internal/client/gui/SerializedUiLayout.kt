package org.academy.internal.client.gui

import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import org.academy.AcademyCraftClient
import org.academy.api.client.gui.serialize.WidgetSerializer
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.Widget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.internal.client.gui.debug.UiDebugSession
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

/** Loads editable widget layouts while keeping code-owned behavior and a safe fallback tree. */
object SerializedUiLayout {
    private val rejectedOverrideTimestamps = mutableMapOf<Path, Long>()

    @JvmStatic
    fun load(identifier: Identifier, fallback: Supplier<FrameLayoutWidget>): FrameLayoutWidget {
        return load(identifier, emptyList(), fallback)
    }

    @JvmStatic
    fun load(
        identifier: Identifier,
        requiredNames: List<String>,
        fallback: Supplier<FrameLayoutWidget>
    ): FrameLayoutWidget {
        val overrideName = identifier.path.substringAfterLast('/')
        val layoutId = overrideName.removeSuffix(".json")
        if (AcademyCraftClient.isUiDebugEnvironment()) {
            UiDebugSession.runtimeLayout(layoutId)?.let { draft ->
                validateOrNull(draft, requiredNames)?.let { return it }
                AcademyCraft.getLogger().warn(
                    "[UiLayout] Incompatible runtime draft {}; trying saved or bundled layout {}",
                    layoutId,
                    identifier
                )
            }
        }
        val overrideFile = WidgetSerializer.layoutDir().resolve(overrideName)
        if (Files.isRegularFile(overrideFile)) {
            val modifiedAt = runCatching {
                Files.getLastModifiedTime(overrideFile).toMillis()
            }.getOrDefault(Long.MIN_VALUE)
            if (rejectedOverrideTimestamps[overrideFile] != modifiedAt) {
                try {
                    val loaded = WidgetSerializer.import(overrideFile)
                    validateOrNull(loaded, requiredNames)?.let {
                        rejectedOverrideTimestamps.remove(overrideFile)
                        AcademyCraft.getLogger().info("[UiLayout] Loaded override {}", overrideFile)
                        return it
                    }
                    rejectedOverrideTimestamps[overrideFile] = modifiedAt
                    AcademyCraft.getLogger().warn(
                        "[UiLayout] Incompatible override {}; trying bundled layout {}",
                        overrideFile,
                        identifier
                    )
                } catch (exception: Exception) {
                    rejectedOverrideTimestamps[overrideFile] = modifiedAt
                    AcademyCraft.getLogger().warn(
                        "[UiLayout] Invalid override {}; trying bundled layout {}",
                        overrideFile,
                        identifier,
                        exception
                    )
                }
            }
        }
        return loadBundled(identifier, requiredNames, fallback)
    }

    /** Loads only the packaged layout, bypassing editable runtime and disk overrides. */
    @JvmStatic
    fun loadBundled(
        identifier: Identifier,
        requiredNames: List<String>,
        fallback: Supplier<FrameLayoutWidget>
    ): FrameLayoutWidget {
        return try {
            validate(identifier, WidgetSerializer.loadLayout(identifier), requiredNames)
        } catch (exception: Exception) {
            AcademyCraft.getLogger().error(
                "[UiLayout] Failed to load {}; using the built-in fallback",
                identifier,
                exception
            )
            fallback.get()
        }
    }

    private fun validateOrNull(
        loaded: Widget,
        requiredNames: List<String>
    ): FrameLayoutWidget? {
        if (loaded !is FrameLayoutWidget) return null
        for (name in requiredNames) {
            if (find(loaded, name) == null) return null
        }
        return loaded
    }

    private fun validate(
        identifier: Identifier,
        loaded: Widget,
        requiredNames: List<String>
    ): FrameLayoutWidget {
        require(loaded is FrameLayoutWidget) {
            "Layout '$identifier' must use frame_layout as its root"
        }
        for (name in requiredNames) {
            require(find(loaded, name) != null) {
                "Layout '$identifier' is missing widget '$name'"
            }
        }
        return loaded
    }

    @JvmStatic
    fun find(root: Widget, name: String): Widget? {
        if (root.name == name) return root
        if (root !is WidgetContainer) return null
        for (child in root.children.values) {
            find(child, name)?.let { return it }
        }
        return null
    }

    @JvmStatic
    fun require(root: Widget, name: String): Widget {
        return find(root, name)
            ?: throw IllegalArgumentException("Serialized UI is missing widget '$name'")
    }
}
