package org.academy.desktop.grapheditor.preview

import com.google.gson.JsonParser
import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Screenshot
import org.academy.api.client.gui.widget.FrameLayoutWidget
import org.academy.api.client.gui.widget.WidgetContainer
import org.academy.api.client.render.graph.model.GraphNode
import org.academy.api.client.render.graph.registry.SimpleNodeRegistry
import org.academy.api.client.render.graph.type.Value
import org.academy.api.client.render.vfxgraph.nodes.VfxBlockRegistry
import org.academy.api.client.render.vfxgraph.nodes.VfxBlocks
import org.academy.api.client.render.vfxgraph.render.GraphCamera
import org.academy.api.client.render.vfxgraph.render.RenderSpec
import org.academy.api.client.render.vfxgraph.render.VfxGraphRenderer
import org.academy.api.client.render.vfxgraph.render.WorldTransform
import org.academy.api.client.render.vfxgraph.serialize.JsonVfxGraphCodec
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator
import org.academy.desktop.grapheditor.viewport.EditorGlow
import org.academy.desktop.platform.DesktopApplication
import org.academy.desktop.platform.DesktopEnvironment
import org.academy.desktop.platform.EditorApp
import org.joml.Matrix4f
import org.joml.Vector3f
import java.nio.file.Files
import java.nio.file.Path

private class AbilityVfxCaptureApp(private val root: Path, private val environment: DesktopEnvironment) : EditorApp {
    override var title = "Ability VFX / stages 4–6"
    private val metadata = SimpleNodeRegistry()
    private val blocks = VfxBlockRegistry()
    private var renderer: VfxGraphRenderer? = null
    private var glow: EditorGlow? = null
    private var target: TextureTarget? = null
    private var index = 0
    private var waiting = false
    private var warmup = 0
    private data class Shot(val asset: String, val time: Float = 0.15f, val scale: Float = 0.62f, val suffix: String = "", val baseline: Boolean = false)
    private val shots = listOf(
        Shot("vector_blast"), Shot("vector_blast", time = 0.3f, suffix = "_rolling"),
        Shot("vector_blast", time = 0.5f, suffix = "_dissipating"),
        Shot("vector_blast", suffix = "_close"),
        Shot("vector_blast", suffix = "_eye_third_person"),
        Shot("vector_blast", suffix = "_eye_first_person"),
        Shot("darkmatter_interference"), Shot("darkmatter_repair"), Shot("darkmatter_disassemble"),
        Shot("magnetic_levitation_support"),
        Shot("plasma_cannon_focus", suffix = "_3s"), Shot("plasma_cannon_projectile", suffix = "_3s"),
        Shot("plasma_cannon_focus", scale = 1f, suffix = "_12s"), Shot("plasma_cannon_projectile", scale = 1f, suffix = "_12s")
    ) + listOf("blade", "burst", "field", "ring", "stream", "vortex").flatMap {
        listOf(Shot("aeromanip_mist_$it", time = 0.15f, suffix = "_early"), Shot("aeromanip_mist_$it", time = 0.45f, suffix = "_late")) +
            if (Files.exists(root.resolve("build/ability-vfx-baseline/aeromanip_mist_$it.json")))
                listOf(Shot("aeromanip_mist_$it", time = 0.45f, suffix = "_before", baseline = true)) else emptyList()
    }

    init { VfxBlocks.registerAll(metadata, blocks) }
    override fun onFrame(partialTick: Float) { RenderSystem.executePendingTasks() }
    override fun createRoot(): WidgetContainer = FrameLayoutWidget()
    override fun quitRequested() = index >= shots.size && !waiting

    override fun renderBackground(windowTarget: RenderTarget) {
        if (++warmup < 5 || waiting || quitRequested()) return
        if (target == null) target = TextureTarget("Ability VFX capture", 1280, 720, true, GpuFormat.RGBA8_UNORM)
        if (renderer == null) { renderer = VfxGraphRenderer(environment::loadTexture); glow = EditorGlow(renderer!!) }
        val shot = shots[index]
        val assetPath = if (shot.baseline) "build/ability-vfx-baseline/${shot.asset}.json"
            else "src/main/resources/assets/academy/vfxgraph/${shot.asset}.json"
        val graph = JsonVfxGraphCodec(metadata).decode(JsonParser.parseString(Files.readString(root.resolve(assetPath))).asJsonObject)
        val sim = VfxSystemSimulator(graph, blocks, 42L, graph.parameters())
        sim.setLiveParam("time", Value.of(shot.time))
        sim.setLiveParam("formation_progress", Value.of(shot.scale))
        sim.setLiveParam("convergence_progress", Value.of(1f))
        sim.setLiveParam("launch_scale", Value.of(shot.scale))
        sim.setLiveParam("view_first_person", Value.of(if (shot.suffix == "_eye_first_person") 1f else 0f))
        repeat((shot.time * 60).toInt().coerceAtLeast(1)) { sim.step(1f / 60) }
        val specs = graph.contexts().flatMap { it.blocks() }.filter { it.type().startsWith("vfx.block.output_") }
            .map { RenderSpec.fromOutputNode(GraphNode(it.id(), it.type(), it.properties(), it.ports(), 0f, 0f)) }
        val axial = shot.asset == "vector_blast" || shot.asset == "darkmatter_interference"
        val plasma = shot.asset.startsWith("plasma")
        val stream = shot.asset.endsWith("stream") || shot.asset.endsWith("blade")
        val length = if (shot.asset == "vector_blast") 64f else 16f
        val close = shot.asset == "vector_blast" && shot.suffix == "_close"
        val eyeView = shot.suffix.startsWith("_eye_")
        val center = Vector3f(0f, if (close) 46f else if (axial) length * 0.5f else if (plasma) 0f else if (stream) 2.7f else 0.8f, 0f)
        val eye = if (eyeView) Vector3f(0f, -3f, 0f) else if (axial) Vector3f(length, center.y, length * 0.15f) else Vector3f(20f, 12f, 30f)
        val halfHeight = if (close) 8f else if (axial) length * 0.30f else if (plasma) 16f else if (stream) 4f else 2.4f
        val up = if (axial) Vector3f(0f, 0f, 1f) else Vector3f(0f, 1f, 0f)
        val camera = GraphCamera(eye, Matrix4f().lookAlong(Vector3f(center).sub(eye).normalize(), up),
            if (eyeView) Matrix4f().setPerspective(Math.toRadians(70.0).toFloat(), 16f / 9, 250f, 0.01f, true)
            else Matrix4f().setOrtho(-halfHeight * 16 / 9, halfHeight * 16 / 9, -halfHeight, halfHeight, 250f, 0.01f, true))
        val outputTarget = target!!
        val color = outputTarget.getColorTextureView() ?: return
        renderer!!.setArcBuffer(sim.arcBuffer())
        renderer!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), camera, true, specs, WorldTransform.identity(), false)
        glow!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), sim.arcBuffer(), camera, specs,
            outputTarget.width, outputTarget.height)
        val output = root.resolve("docs/vfx/ability_update/preview_${shot.asset}${shot.suffix}.png")
        Files.createDirectories(output.parent)
        waiting = true
        Screenshot.takeScreenshot(outputTarget) { image ->
            image.use { it.writeToFile(output) }
            println("[ability-vfx-capture] $output particles=${sim.buffer().count()} arcs=${sim.arcBuffer().count()}")
            index++
            waiting = false
        }
    }
    override fun onDispose() { target?.destroyBuffers(); glow?.destroy(); renderer?.close() }
}

fun main(args: Array<String>) {
    val root = args.firstOrNull { it.startsWith("--project-root=") }?.substringAfter('=')?.let(Path::of)
        ?: Path.of("").toAbsolutePath()
    val environment = DesktopEnvironment(root, 1280, 720, 1f)
    DesktopApplication.run(AbilityVfxCaptureApp(root, environment), environment)
}
