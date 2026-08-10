package org.academy.internal.client.gui.debug

import net.minecraft.resources.Identifier
import org.academy.AcademyCraft
import org.academy.api.client.gui.widget.EmptyWidget
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.LinearLayoutWidget
import org.academy.api.client.gui.widget.Widget

enum class UiDebugLayoutKind {
    GUI,
    HUD
}

data class UiDebugBinding(
    val name: String,
    val widgetClass: Class<out Widget> = Widget::class.java
)

data class UiDebugLayoutDefinition(
    val id: String,
    val kind: UiDebugLayoutKind,
    val resource: Identifier = AcademyCraft.academy("ui/layout/$id.json"),
    val bindings: List<UiDebugBinding>
)

object UiDebugLayoutRegistry {
    private val definitions = listOf(
        UiDebugLayoutDefinition(
            "location_teleport", UiDebugLayoutKind.GUI,
            bindings = listOf(
                frame("panel"),
                empty("name_input"),
                empty("coordinates"),
                empty("mark_current"),
                empty("add_mark"),
                empty("marks"),
                empty("refresh"),
                empty("done")
            )
        ),
        UiDebugLayoutDefinition(
            "precision_operation_wide", UiDebugLayoutKind.GUI,
            bindings = frameBindings("panel", "palette", "canvas", "inspector")
        ),
        UiDebugLayoutDefinition(
            "precision_operation_medium", UiDebugLayoutKind.GUI,
            bindings = frameBindings("panel", "palette", "canvas", "inspector")
        ),
        UiDebugLayoutDefinition(
            "precision_operation_compact", UiDebugLayoutKind.GUI,
            bindings = frameBindings("panel", "palette", "canvas", "inspector")
        ),
        UiDebugLayoutDefinition(
            "reflection_filter_wide", UiDebugLayoutKind.GUI,
            bindings = listOf(frame("panel"), empty("left_column"), empty("middle_column"), empty("right_column"))
        ),
        UiDebugLayoutDefinition(
            "reflection_filter_compact", UiDebugLayoutKind.GUI,
            bindings = listOf(frame("panel"), empty("left_column"), empty("middle_column"), empty("right_column"))
        ),
        UiDebugLayoutDefinition(
            "ability_cp_hud", UiDebugLayoutKind.HUD,
            bindings = listOf(UiDebugBinding("cp", FrameLayoutWidget::class.java))
        ),
        UiDebugLayoutDefinition(
            "ability_skill_wheel_hud", UiDebugLayoutKind.HUD,
            bindings = listOf(UiDebugBinding("skill_wheel", FrameLayoutWidget::class.java))
        ),
        UiDebugLayoutDefinition(
            "toggle_status_hud", UiDebugLayoutKind.HUD,
            bindings = listOf(UiDebugBinding("toggle_statuses", LinearLayoutWidget::class.java))
        ),
        UiDebugLayoutDefinition(
            "mental_control_hud", UiDebugLayoutKind.HUD,
            bindings = listOf(
                UiDebugBinding("mental_control", FrameLayoutWidget::class.java),
                UiDebugBinding("content", LinearLayoutWidget::class.java)
            )
        )
    ).associateBy { it.id }

    fun all(): List<UiDebugLayoutDefinition> = definitions.values.toList()

    fun gui(): List<UiDebugLayoutDefinition> = all().filter { it.kind == UiDebugLayoutKind.GUI }

    fun hud(): List<UiDebugLayoutDefinition> = all().filter { it.kind == UiDebugLayoutKind.HUD }

    fun find(id: String): UiDebugLayoutDefinition? = definitions[id]

    fun require(id: String): UiDebugLayoutDefinition = definitions[id]
        ?: throw IllegalArgumentException("Unknown debug UI layout '$id'")

    private fun frame(name: String) = UiDebugBinding(name, FrameLayoutWidget::class.java)

    private fun empty(name: String) = UiDebugBinding(name, EmptyWidget::class.java)

    private fun frameBindings(vararg names: String) = names.map(::frame)
}
