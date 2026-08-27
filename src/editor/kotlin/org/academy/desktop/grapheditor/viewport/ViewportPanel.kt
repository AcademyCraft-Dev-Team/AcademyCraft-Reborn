package org.academy.desktop.grapheditor.viewport

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import imgui.ImGui
import imgui.extension.imguizmo.ImGuizmo
import imgui.extension.imguizmo.flag.Mode
import imgui.extension.imguizmo.flag.Operation
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiWindowFlags
import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.academy.desktop.grapheditor.canvas.GraphEditorModelRef
import org.academy.desktop.platform.DesktopEnvironment
import java.util.*

/**
 * 独立 docked 视口（M14）：离屏 [TextureTarget] 渲染预览，经 [ImGuiBackend] 多纹理 ID
 * 注册后用 [ImGui.image] 显示；叠加轨道相机输入、ImGuizmo 网格/gizmo 与统计 overlay。
 */
class ViewportPanel(
    private val environment: DesktopEnvironment,
    private val orbit: OrbitCamera,
    private val modelRef: GraphEditorModelRef,
) {
    private val model: GraphEditorModel get() = modelRef.model
    private var target: TextureTarget? = null
    private var textureId = 0L
    private var registeredView: GpuTextureView? = null
    private var sampler: GpuSampler? = null

    /** 质量档位：离屏分辨率缩放（M14-06）。 */
    var resolutionScale = 1f

    /** 宿主提供的 dock id（在 DockHost 窗口内计算，跨窗口上下文不可用 getID 重算）。 */
    var dockId: Int = 0

    /** 是否启用轨道相机（VFX 视口）。 */
    var orbitEnabled = false

    /** 是否显示网格地面 / 统计。 */
    var showGrid = true
    var showStats = true

    /** 发射器 gizmo 目标节点（选中 spawn 节点），空则不显示。 */
    var emitterNodeId: String? = null

    /** 视口内可交互（gizmo/轨道输入）的门槛。 */
    private var interactive = false
    private var wasUsingGizmo = false

    // 统计
    private var lastNanos = System.nanoTime()
    var frameNanos = 0L
        private set
    var particleCount = 0
        private set

    /** 渲染预览的离屏目标（GraphEditorApp.renderBackground 调用）。带深度缓冲（useDepth=true）供粒子深度测试。 */
    fun renderTarget(): TextureTarget {
        if (target == null) {
            target = TextureTarget("Graph Viewport", 1280, 720, true, GpuFormat.RGBA8_UNORM)
        }
        return target!!
    }

    /** 视口 ImGui 窗口：显示纹理 + 输入 + 网格/gizmo/统计。 */
    fun render() {
        val now = System.nanoTime()
        frameNanos = now - lastNanos
        lastNanos = now

        ImGui.begin("Viewport", WINDOW_FLAGS)
        val winX = ImGui.getWindowPosX()
        val winY = ImGui.getWindowPosY()
        val winW = ImGui.getWindowSizeX()
        val winH = ImGui.getWindowSizeY()
        interactive = ImGui.isWindowHovered()

        resizeTarget(winW, winH)
        val texId = ensureTexture()

        handleOrbitInput(winW, winH)
        if (texId != 0L) {
            ImGui.image(texId, winW, winH, 0f, 1f, 1f, 0f)
        }

        val aspect = winH.takeIf { it > 0f }?.let { winW / it } ?: 1f
        if (showGrid) renderGrid(aspect, winX, winY, winW, winH)
        renderGizmo(aspect, winX, winY, winW, winH)
        renderStats(winX, winY, winW, winH)
        ImGui.end()
    }

    private fun resizeTarget(winW: Float, winH: Float) {
        val t = renderTarget()
        val w = (winW * resolutionScale).toInt().coerceAtLeast(64)
        val h = (winH * resolutionScale).toInt().coerceAtLeast(64)
        if (t.width != w || t.height != h) {
            t.resize(w, h)
        }
    }

    private fun ensureTexture(): Long {
        val backend = environment.imguiBackend ?: return 0L
        val t = target ?: return 0L
        val view = t.getColorTextureView() ?: return 0L
        if (sampler == null) {
            sampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty()
            )
        }
        if (view !== registeredView) {
            if (textureId != 0L) backend.unregisterTexture(textureId)
            textureId = backend.registerTexture(view, sampler!!)
            registeredView = view
        }
        return textureId
    }

    // ---- 轨道相机输入（M14-02） ----

    private fun handleOrbitInput(winW: Float, winH: Float) {
        if (!orbitEnabled) return
        if (ImGui.isWindowHovered()) interactive = true
        if (!interactive) return
        val io = ImGui.getIO()
        if (ImGuizmo.isUsing()) return

        if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
            orbit.yaw -= io.mouseDeltaX * 0.01f
            orbit.pitch = (orbit.pitch - io.mouseDeltaY * 0.01f).coerceIn(-1.4f, 1.4f)
        }
        if (ImGui.isMouseDragging(ImGuiMouseButton.Right) || ImGui.isMouseDragging(ImGuiMouseButton.Middle)) {
            val view = orbit.viewRotation()
            val scale = 2f * orbit.distance * kotlin.math.tan(orbit.fov / 2f) / winH
            val rightX = view.m00()
            val rightY = view.m10()
            val rightZ = view.m20()
            val upX = view.m01()
            val upY = view.m11()
            val upZ = view.m21()
            orbit.targetX -= (rightX * io.mouseDeltaX - upX * io.mouseDeltaY) * scale
            orbit.targetY -= (rightY * io.mouseDeltaX - upY * io.mouseDeltaY) * scale
            orbit.targetZ -= (rightZ * io.mouseDeltaX - upZ * io.mouseDeltaY) * scale
        }
        val wheel = io.mouseWheel
        if (wheel != 0f) {
            orbit.distance = (orbit.distance * (if (wheel > 0) 0.9f else 1.1f)).coerceIn(0.5f, 500f)
        }
    }

    // ---- 网格地面（M14-03） ----

    private fun renderGrid(aspect: Float, winX: Float, winY: Float, winW: Float, winH: Float) {
        if (!orbitEnabled) return
        val view = orbitViewFull()
        val proj = orbit.projection(aspect).get(projArray)
        ImGuizmo.setDrawList()
        ImGuizmo.beginFrame()
        ImGuizmo.setRect(winX, winY, winW, winH)
        ImGuizmo.drawGrid(view, proj, identityArray, 10f)
    }

    // ---- 发射器 gizmo（M14-03） ----

    private fun renderGizmo(aspect: Float, winX: Float, winY: Float, winW: Float, winH: Float) {
        val nodeId = emitterNodeId ?: return
        val node = model.nodes[nodeId] ?: return
        if (!node.typeId.startsWith("vfx.spawn") && !node.typeId.startsWith("vfx.init_position")) return
        if (!orbitEnabled) return

        val x = propFloat(node, "origin_x")
        val y = propFloat(node, "origin_y")
        val z = propFloat(node, "origin_z")
        translationToMatrix(x, y, z, modelMatrix)

        orbitViewFull()
        orbit.projection(aspect).get(projArray)
        ImGuizmo.setDrawList()
        ImGuizmo.beginFrame()
        ImGuizmo.setRect(winX, winY, winW, winH)
        val using = ImGuizmo.isUsing()
        ImGuizmo.manipulate(viewFullArray, projArray, Operation.TRANSLATE, Mode.WORLD, modelMatrix)
        if (wasUsingGizmo && !using) {
            val t = matrixToTranslation(modelMatrix)
            model.setProperty(nodeId, "origin_x", t[0].toString())
            model.setProperty(nodeId, "origin_y", t[1].toString())
            model.setProperty(nodeId, "origin_z", t[2].toString())
        }
        wasUsingGizmo = using
    }

    // ---- 统计 overlay（M14-05） ----

    private fun renderStats(winX: Float, winY: Float, winW: Float, winH: Float) {
        if (!showStats) return
        val ms = frameNanos / 1e6f
        val fps = if (frameNanos > 0) 1e9f / frameNanos else 0f
        val tw = (winW * resolutionScale).toInt()
        val th = (winH * resolutionScale).toInt()
        val text = "FPS %.0f  %.1f ms  particles %d  %dx%d".format(fps, ms, particleCount, tw, th)
        ImGui.getWindowDrawList().addText(
            winX + 8f, winY + 8f,
            ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f), text
        )
    }

    /** 供 GraphEditorApp 每帧更新粒子数。 */
    fun updateParticleCount(count: Int) {
        particleCount = count
    }

    // ---- 矩阵辅助 ----

    private fun orbitViewFull(): FloatArray {
        val eye = orbit.eyePosition()
        val view = orbit.viewRotation().translate(-eye.x, -eye.y, -eye.z)
        return view.get(viewFullArray)
    }

    private val viewFullArray = FloatArray(16)
    private val projArray = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val identityArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f
    )

    private fun translationToMatrix(x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = 1f; out[1] = 0f; out[2] = 0f; out[3] = 0f
        out[4] = 0f; out[5] = 1f; out[6] = 0f; out[7] = 0f
        out[8] = 0f; out[9] = 0f; out[10] = 1f; out[11] = 0f
        out[12] = x; out[13] = y; out[14] = z; out[15] = 1f
    }

    private fun matrixToTranslation(m: FloatArray): FloatArray =
        floatArrayOf(m[12], m[13], m[14])

    private fun propFloat(node: GraphEditorModel.EdNode, id: String): Float =
        node.properties[id]?.toFloatOrNull() ?: 0f

    companion object {
        private val WINDOW_FLAGS = ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse
    }
}
