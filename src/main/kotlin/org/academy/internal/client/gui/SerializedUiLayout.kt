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
import java.util.function.Supplier

/** Loads editable widget layouts while keeping code-owned behavior and a safe fallback tree. */
object SerializedUiLayout {
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
                return validate(identifier, draft, requiredNames)
            }
        }
        val overrideFile = WidgetSerializer.layoutDir().resolve(overrideName)
        if (Files.isRegularFile(overrideFile)) {
            try {
                return validate(
                    identifier,
                    WidgetSerializer.import(overrideFile),
                    requiredNames
                ).also {
                    AcademyCraft.getLogger().info("[UiLayout] Loaded override {}", overrideFile)
                }
            } catch (exception: Exception) {
                AcademyCraft.getLogger().warn(
                    "[UiLayout] Invalid override {}; trying bundled layout {}",
                    overrideFile,
                    identifier,
                    exception
                )
            }
        }
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
