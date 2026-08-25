package org.academy.desktop.grapheditor.preview

import com.mojang.blaze3d.pipeline.RenderTarget
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry
import org.academy.api.client.render.vfxgraph.nodes.VfxNodeRegistry
import org.academy.api.client.render.vfxgraph.operator.VfxOperatorRegistry
import org.academy.api.client.render.vfxgraph.render.GraphCamera
import org.academy.api.client.render.vfxgraph.render.RenderSpec
import org.academy.api.client.render.vfxgraph.render.VfxGraphRenderer
import org.academy.api.client.render.vfxgraph.render.VfxGraphRenderer.SurfaceMesh
import org.academy.api.client.render.vfxgraph.render.WorldTransform
import org.academy.api.client.render.vfxgraph.shape.MeshAssets
import org.academy.api.client.render.vfxgraph.sim.VfxSimulator
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator
import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.academy.desktop.grapheditor.canvas.GraphEditorModelRef
import org.academy.desktop.grapheditor.container.VfxContainerModel
import org.academy.desktop.grapheditor.container.VfxContainerModelRef
import org.academy.desktop.grapheditor.viewport.EditorGlow

/**
 * VFX 实时预览：支持两种模型——扁平（[GraphEditorModel] → [VfxSimulator]，过渡期兼容）与
 * 容器（[VfxContainerModel] → [VfxSystemSimulator]，M26 容器编辑器主路径）。经 [VfxGraphRenderer]
 * 渲染到视口 target。
 */
class VfxPreview(
    private val modelRef: GraphEditorModelRef,
    private val vfxRegistry: VfxNodeRegistry,
    private val containerRef: VfxContainerModelRef,
    private val blockRegistry: VfxBlockRegistry,
    private val operatorRegistry: VfxOperatorRegistry,
) {
    private val model: GraphEditorModel get() = modelRef.model
    private val containerModel: VfxContainerModel get() = containerRef.model
    private var simulator: VfxSimulator? = null
    private var systemSimulator: VfxSystemSimulator? = null
    private var renderer: VfxGraphRenderer? = null
    private var glow: EditorGlow? = null
    private var specs: List<RenderSpec> = listOf(RenderSpec.DEFAULT)
    private var lastModel: GraphEditorModel? = null
    private var lastVersion = -1
    private var lastContainerModel: VfxContainerModel? = null
    private var lastContainerVersion = -1
    private var lastContainerSimVersion = -1
    private var lastNanos = System.nanoTime()
    private var lastLoopRestartNanos = 0L

    var playing = true
    var loop = true
    var error: String? = null
        private set

    /** 当前粒子数 + 电弧数（供统计 overlay）。 */
    val particleCount: Int get() = (systemSimulator?.buffer()?.count() ?: simulator?.buffer()?.count() ?: 0) +
        (systemSimulator?.arcBuffer()?.count() ?: 0)

    /** 模拟已运行时间（供时间轴显示）。 */
    val time: Float get() = systemSimulator?.time() ?: simulator?.time() ?: 0f

    fun sync() {
        // 版本守卫必须在清空模拟器之前：版本未变则保留现有模拟器（否则每帧清空但不重建 → 画面冻结第一帧）。
        // 容器路径用 simVersion（仅影响模拟的变更递增）：移动节点不重建，loop 播放不被打断。
        if (containerModel.contexts.isNotEmpty() || containerModel.operators.isNotEmpty()) {
            if (containerModel === lastContainerModel && containerModel.simVersion == lastContainerSimVersion) {
                return
            }
        } else if (model === lastModel && model.version == lastVersion) {
            return
        }
        error = null
        simulator = null
        systemSimulator = null
        try {
            // 容器模型优先（M26）：有 context 则走容器执行器
            if (containerModel.contexts.isNotEmpty() || containerModel.operators.isNotEmpty()) {
                systemSimulator = VfxSystemSimulator(
                    containerModel.toSystem(), blockRegistry, operatorRegistry, 42L, containerModel.parameters)
                specs = systemSpecs(containerModel)
                lastContainerModel = containerModel
                lastContainerVersion = containerModel.version
                lastContainerSimVersion = containerModel.simVersion
                return
            }
            val graph = model.toGraph()
            simulator = VfxSimulator(graph.nodes(), vfxRegistry, 42L, graph.parameters())
            specs = graph.nodes().filter { it.type().startsWith("vfx.output_") }
                .map { RenderSpec.fromOutputNode(it) }
                .ifEmpty { listOf(RenderSpec.DEFAULT) }
            lastModel = model
            lastVersion = model.version
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    /** 容器系统全部输出块 → RenderSpec 列表（M26/M21n：几何由 output 块类型派生，着色器/混合/层由图数据，多输出分层）。 */
    private fun systemSpecs(containerModel: VfxContainerModel): List<RenderSpec> {
        val out = mutableListOf<RenderSpec>()
        for (ctx in containerModel.contexts.values) {
            for (block in ctx.blocks.values) {
                if (block.typeId.startsWith("vfx.block.output_")) {
                    out += RenderSpec.fromOutputNode(org.academy.api.client.render.graph.model.GraphNode(
                        block.id, block.typeId, block.properties.toMap(), containerModel.portsFor(block.id), 0f, 0f))
                }
            }
        }
        return out.ifEmpty { listOf(RenderSpec.DEFAULT) }
    }

    /**
     * 收集编辑器预览的场景表面网格（M29b-03，Blender 场景复刻）：扫描容器模型 SPAWN 块
     * {@code arc_surface}/{@code arc_contact} 的 {@code mesh}/{@code contact_mesh} 属性 +
     * origin 位移 → {@link MeshAssets} 解析三角面 → 半透明材质色。运行时无此可视化（空列表）。
     */
    private fun collectSurfaces(containerModel: VfxContainerModel): List<SurfaceMesh> {
        val out = mutableListOf<SurfaceMesh>()
        for (ctx in containerModel.contexts.values) {
            for (block in ctx.blocks.values) {
                when (block.typeId) {
                    "vfx.block.arc_surface" -> {
                        meshSurface(block, "mesh", "origin_", BLENDER_PLANE_COLOR)?.let(out::add)
                    }
                    "vfx.block.arc_contact" -> {
                        meshSurface(block, "mesh", "origin_", BLENDER_PLANE_COLOR)?.let(out::add)
                        meshSurface(block, "contact_mesh", "contact_origin_", BLENDER_SPHERE_COLOR)?.let(out::add)
                    }
                }
            }
        }
        return out
    }

    private fun meshSurface(block: VfxContainerModel.EdBlock, meshProp: String, originPrefix: String, color: FloatArray): SurfaceMesh? {
        val id = block.properties[meshProp] ?: return null
        val tris = MeshAssets.resolve(id) ?: return null
        val ox = block.properties["${originPrefix}x"]?.toFloatOrNull() ?: 0f
        val oy = block.properties["${originPrefix}y"]?.toFloatOrNull() ?: 0f
        val oz = block.properties["${originPrefix}z"]?.toFloatOrNull() ?: 0f
        if (ox == 0f && oy == 0f && oz == 0f) return SurfaceMesh(tris, color[0], color[1], color[2], color[3])
        val shifted = tris.clone()
        for (i in 0 until shifted.size step 3) {
            shifted[i] += ox
            shifted[i + 1] += oy
            shifted[i + 2] += oz
        }
        return SurfaceMesh(shifted, color[0], color[1], color[2], color[3])
    }

    fun render(target: RenderTarget, camera: GraphCamera) {
        sync()
        val color = target.getColorTextureView() ?: return
        val depth = target.getDepthTextureView()

        if (renderer == null) {
            renderer = VfxGraphRenderer()
            glow = EditorGlow(renderer!!)
        }

        val now = System.nanoTime()
        val dt = ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        lastNanos = now

        val container = systemSimulator
        if (container != null) {
            if (playing) {
                container.step(dt)
                val arcsEmpty = (container.arcBuffer()?.count() ?: 0) == 0
                if (loop && container.buffer().count() == 0 && arcsEmpty && container.time() > 0f && canLoopRestart()) {
                    // 重播但延续 time（避免编辑后粒子为 0 时每帧重建导致 time 归零、t 冻结）。
                    // 节流：避免每次重建把 spawn 累加器归零导致永远 spawn 不出粒子（渲染不可见）
                    val continued = container.time()
                    reset()
                    sync()
                    val next = systemSimulator ?: return
                    next.setTime(continued)
                    next.step(dt)
                }
            }
            val active = systemSimulator ?: return
            renderer!!.setArcBuffer(active.arcBuffer())
            val surfaces = collectSurfaces(containerModel)
            renderer!!.render(color, depth, active.buffer(), camera, true, specs, WorldTransform.identity(), false, surfaces)
            if (specs.any { it.blend() == RenderSpec.Blend.GLOW }) {
                glow!!.render(color, active.buffer(), active.arcBuffer(), camera, specs, target.width, target.height)
            }
            return
        }

        var sim = simulator ?: return
        if (playing) {
            sim.step(dt)
            if (loop && sim.buffer().count() == 0 && sim.time() > 0f && canLoopRestart()) {
                val continued = sim.time()
                reset()
                sync()
                sim = simulator ?: return
                sim.setTime(continued)
                sim.step(dt)
            }
        }
        renderer!!.render(color, depth, sim.buffer(), camera, true, specs)
        if (specs.any { it.blend() == RenderSpec.Blend.GLOW }) {
            glow!!.render(color, sim.buffer(), null, camera, specs, target.width, target.height)
        }
    }

    /** loop 重启节流：至少间隔 [LOOP_RESTART_INTERVAL_NS] 才允许再次重启，避免每帧重建。 */
    private fun canLoopRestart(): Boolean {
        val now = System.nanoTime()
        if (now - lastLoopRestartNanos < LOOP_RESTART_INTERVAL_NS) return false
        lastLoopRestartNanos = now
        return true
    }

    /** 释放 GPU 资源（编辑器销毁时调用，防泄漏）。 */
    fun close() {
        glow?.destroy()
        glow = null
        renderer?.close()
        renderer = null
    }

    fun stepOnce() {
        systemSimulator?.step(1f / 60f)
        simulator?.step(1f / 60f)
    }

    fun reset() {
        lastModel = null
        lastVersion = -1
        lastContainerModel = null
        lastContainerVersion = -1
        lastContainerSimVersion = -1
        lastNanos = System.nanoTime()
    }

    companion object {
        /** loop 重启最小间隔：避免每帧重建（把 spawn 累加器归零 → 渲染不可见）。 */
        private const val LOOP_RESTART_INTERVAL_NS = 250_000_000L // 250ms

        /** 场景表面材质色（M29b-03）：地面平面 / 悬浮球，半透明暖灰蓝（参考 Blender 视口材质）。 */
        private val BLENDER_PLANE_COLOR = floatArrayOf(0.45f, 0.48f, 0.58f, 0.45f)
        private val BLENDER_SPHERE_COLOR = floatArrayOf(0.32f, 0.36f, 0.5f, 0.55f)
    }
}
