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

private class DarkmatterVfxCaptureApp(private val root: Path, private val environment: DesktopEnvironment) : EditorApp {
    override var title = "Darkmatter / material surface study"
    private val metadata = SimpleNodeRegistry()
    private val blocks = VfxBlockRegistry()
    private var renderer: VfxGraphRenderer? = null
    private var glow: EditorGlow? = null
    private var target: TextureTarget? = null
    private var index = 0
    private var waiting = false
    private var warmup = 0
    private data class Shot(val asset: String, val time: Float = 0.15f, val suffix: String = "")
    private val shots = listOf(
        Shot("darkmatter_feather", time = 0.2f),
        Shot("darkmatter_interference", time = 0.9f, suffix = "_day"),
        Shot("darkmatter_interference", time = 0.9f, suffix = "_night"),
        Shot("darkmatter_light_contact", time = 0.22f),
        Shot("darkmatter_repair", time = 0.15f, suffix = "_growth"),
        Shot("darkmatter_repair", time = 0.30f, suffix = "_settled"),
        Shot("darkmatter_disassemble", time = 0.18f, suffix = "_peel"),
        Shot("darkmatter_disassemble", time = 0.43f, suffix = "_eroded")
    )

    init { VfxBlocks.registerAll(metadata, blocks) }
    override fun onFrame(partialTick: Float) { RenderSystem.executePendingTasks() }
    override fun createRoot(): WidgetContainer = FrameLayoutWidget()
    override fun quitRequested() = index >= shots.size && !waiting

    override fun renderBackground(windowTarget: RenderTarget) {
        if (++warmup < 5 || waiting || quitRequested()) return
        if (target == null) target = TextureTarget("Darkmatter VFX capture", 1280, 720, true, GpuFormat.RGBA8_UNORM)
        if (renderer == null) { renderer = VfxGraphRenderer(environment::loadTexture); glow = EditorGlow(renderer!!) }
        val shot = shots[index]
        val assetPath = "src/main/resources/assets/academy/vfxgraph/${shot.asset}.json"
        val graph = JsonVfxGraphCodec(metadata).decode(JsonParser.parseString(Files.readString(root.resolve(assetPath))).asJsonObject)
        val sim = VfxSystemSimulator(graph, blocks, 42L, graph.parameters())
        sim.setLiveParam("time", Value.of(shot.time))
        sim.setLiveParam("light", Value.of(if (shot.suffix == "_night") 0.12f else 1f))
        repeat((shot.time * 60).toInt().coerceAtLeast(1)) { sim.step(1f / 60) }
        val specs = graph.contexts().flatMap { it.blocks() }.filter { it.type().startsWith("vfx.block.output_") }
            .map { RenderSpec.fromOutputNode(GraphNode(it.id(), it.type(), it.properties(), it.ports(), 0f, 0f)) }
        val field = shot.asset.endsWith("interference")
        val center = if (field) Vector3f(0f, 1.5f, 7f) else Vector3f(0f, if (shot.asset.endsWith("feather")) -0.15f else 0.9f, 0f)
        val eye = if (field) Vector3f(10f, 7f, -8f) else Vector3f(3f, 2f, -6f)
        val halfHeight = if (field) 3.5f else 1.15f
        val up = Vector3f(0f, 1f, 0f)
        val camera = GraphCamera(eye, Matrix4f().lookAlong(Vector3f(center).sub(eye).normalize(), up),
            Matrix4f().setOrtho(-halfHeight * 16 / 9, halfHeight * 16 / 9, -halfHeight, halfHeight, 250f, 0.01f, true))
        val outputTarget = target!!
        val color = outputTarget.getColorTextureView() ?: return
        renderer!!.setArcBuffer(sim.arcBuffer())
        renderer!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), camera, true, specs, WorldTransform.identity(), false)
        glow!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), sim.arcBuffer(), camera, specs,
            outputTarget.width, outputTarget.height)
        val output = root.resolve("docs/vfx/darkmatter_redesign/preview_${shot.asset}${shot.suffix}.png")
        Files.createDirectories(output.parent)
        waiting = true
        Screenshot.takeScreenshot(outputTarget) { image ->
            image.use { it.writeToFile(output) }
            println("[darkmatter-vfx-capture] $output particles=${sim.buffer().count()} arcs=${sim.arcBuffer().count()}")
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
    DesktopApplication.run(DarkmatterVfxCaptureApp(root, environment), environment)
}
