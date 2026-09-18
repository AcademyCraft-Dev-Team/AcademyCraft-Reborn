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

/** Deterministic GPU review through the VFXGraph editor simulator, renderer and bloom. */
private class IronSandCaptureApp(private val root: Path, private val environment: DesktopEnvironment) : EditorApp {
    override var title = "Iron Sand / VFXGraph review"
    private val metadata = SimpleNodeRegistry()
    private val blocks = VfxBlockRegistry()
    private var renderer: VfxGraphRenderer? = null
    private var glow: EditorGlow? = null
    private var target: TextureTarget? = null
    private var index = 0
    private var waiting = false
    private var warmup = 0
    private data class Shot(val asset: String, val time: Float, val frame: Int = -1)
    private val shots = listOf(Shot("defense", 1f), Shot("guard", 0.12f), Shot("whip", 0.25f),
        Shot("cloud", 1f), Shot("intercept", 0.08f)) +
        (0..29).map { Shot("guard", it / 40f, it) }

    init { VfxBlocks.registerAll(metadata, blocks) }
    override fun onFrame(partialTick: Float) { RenderSystem.executePendingTasks() }
    override fun createRoot(): WidgetContainer = FrameLayoutWidget()
    override fun quitRequested() = index >= shots.size && !waiting

    override fun renderBackground(windowTarget: RenderTarget) {
        if (++warmup < 5 || waiting || quitRequested()) return
        if (target == null) target = TextureTarget("Iron sand capture", 1280, 720, true, GpuFormat.RGBA8_UNORM)
        if (renderer == null) { renderer = VfxGraphRenderer(environment::loadTexture); glow = EditorGlow(renderer!!) }
        val outputTarget = target!!
        val shot = shots[index]
        val system = JsonVfxGraphCodec(metadata).decode(JsonParser.parseString(Files.readString(root.resolve(
            "src/main/resources/assets/academy/vfxgraph/iron_sand_${shot.asset}.json"))).asJsonObject)
        val sim = VfxSystemSimulator(system, blocks, 42L, system.parameters())
        sim.setLiveParam("time", Value.of(shot.time))
        sim.step(0f)
        val specs = system.contexts().flatMap { it.blocks() }.filter { it.type().startsWith("vfx.block.output_") }
            .map { RenderSpec.fromOutputNode(GraphNode(it.id(), it.type(), it.properties(), it.ports(), 0f, 0f)) }
        val large = shot.asset == "whip" || shot.asset == "cloud"
        val center = Vector3f(0f, 0.6f, if (shot.asset == "whip") 4f else 0f)
        val eye = if (large) Vector3f(22f, 20f, 28f) else Vector3f(4f, 3.5f, 6f)
        val halfHeight = if (large) 12f else 2.5f
        val camera = GraphCamera(eye, Matrix4f().lookAlong(Vector3f(center).sub(eye).normalize(), Vector3f(0f, 1f, 0f)),
            Matrix4f().setOrtho(-halfHeight * 16 / 9, halfHeight * 16 / 9, -halfHeight, halfHeight, 150f, 0.01f, true))
        val world = WorldTransform.identity()
        val color = outputTarget.getColorTextureView() ?: return
        val floor = floatArrayOf(-32f, -0.02f, -32f, -32f, -0.02f, 32f, 32f, -0.02f, 32f,
            -32f, -0.02f, -32f, 32f, -0.02f, 32f, 32f, -0.02f, -32f)
        renderer!!.setArcBuffer(sim.arcBuffer())
        renderer!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), camera, true, specs, world, false,
            listOf(VfxGraphRenderer.SurfaceMesh(floor, 0.42f, 0.45f, 0.48f, 1f)))
        glow!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), sim.arcBuffer(), camera, specs,
            outputTarget.width, outputTarget.height)
        val output = root.resolve("build/iron-sand-editor-captures/" +
            if (shot.frame < 0) "${shot.asset}.png" else "guard/frame_%03d.png".format(shot.frame))
        Files.createDirectories(output.parent)
        waiting = true
        Screenshot.takeScreenshot(outputTarget) { image ->
            image.use { it.writeToFile(output) }
            println("[iron-sand-capture] $output grains=${sim.buffer().count()} arcs=${sim.arcBuffer().count()}")
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
    DesktopApplication.run(IronSandCaptureApp(root, environment), environment)
}
