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

/** Reproducible GPU preview, using the editor's real graph, materials and bloom. */
private class RailgunCaptureApp(private val root: Path) : EditorApp {
    override var title = "Railgun VFX capture"
    private val metadata = SimpleNodeRegistry()
    private val blocks = VfxBlockRegistry()
    private var renderer: VfxGraphRenderer? = null
    private var glow: EditorGlow? = null
    private var target: TextureTarget? = null
    private var index = 0
    private var warmup = 0
    private var waiting = false
    private data class Shot(val name: String, val time: Float)
    private val shots = listOf(Shot("small", 0.012f), Shot("peak", 0.077f),
        Shot("medium", 0.3f), Shot("afterglow", 1.35f), Shot("cleared", 1.7f), Shot("top", 0.15f),
        Shot("end_cap", 0.3f)) +
        (0..101).map { Shot("motion_%03d".format(it), it / 60f) }

    init { VfxBlocks.registerAll(metadata, blocks) }
    override fun onFrame(partialTick: Float) { RenderSystem.executePendingTasks() }
    override fun createRoot(): WidgetContainer = FrameLayoutWidget()
    override fun quitRequested() = index >= shots.size && !waiting

    override fun renderBackground(windowTarget: RenderTarget) {
        if (++warmup < 5 || waiting || quitRequested()) return
        if (target == null) target = TextureTarget("Railgun capture", 1280, 720, true, GpuFormat.RGBA8_UNORM)
        if (renderer == null) { renderer = VfxGraphRenderer(); glow = EditorGlow(renderer!!) }
        val outputTarget = target!!
        val shot = shots[index]
        val path = root.resolve("src/main/resources/assets/academy/vfxgraph/railgun_shot.json")
        val system = JsonVfxGraphCodec(metadata).decode(JsonParser.parseString(Files.readString(path)).asJsonObject)
        val sim = VfxSystemSimulator(system, blocks, 42L, system.parameters())
        sim.setLiveParam("time", Value.of(shot.time))
        sim.step(0f)
        val specs = system.contexts().flatMap { it.blocks() }
            .filter { it.type().startsWith("vfx.block.output_") }
            .map { RenderSpec.fromOutputNode(GraphNode(it.id(), it.type(), it.properties(), it.ports(), 0f, 0f)) }
        val top = shot.name == "top"
        val eye = when (shot.name) {
            "top" -> Vector3f(0f, 70f, 25f)
            "end_cap" -> Vector3f(40f, 12f, 75f)
            else -> Vector3f(70f, 8f, 25f)
        }
        val direction = Vector3f(0f, 0f, 25f).sub(eye).normalize()
        val up = if (top) Vector3f(1f, 0f, 0f) else Vector3f(0f, 1f, 0f)
        val camera = GraphCamera(eye, Matrix4f().lookAlong(direction, up),
            Matrix4f().setOrtho(-28f, 28f, -15.75f, 15.75f, 200f, 0.1f, true))
        val color = outputTarget.getColorTextureView() ?: return
        renderer!!.setArcBuffer(sim.arcBuffer())
        renderer!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), camera, true,
            specs, WorldTransform.identity(), false)
        glow!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), sim.arcBuffer(), camera,
            specs, outputTarget.width, outputTarget.height)
        val directory = if (shot.name.startsWith("motion_")) "build/railgun-frames" else "docs/vfx/railgun"
        val output = root.resolve("$directory/${shot.name}.png")
        Files.createDirectories(output.parent)
        waiting = true
        Screenshot.takeScreenshot(outputTarget) { image ->
            image.use { it.writeToFile(output) }
            println("[railgun-capture] $output arcs=${sim.arcBuffer().count()}")
            index++
            waiting = false
        }
    }

    override fun onDispose() {
        target?.destroyBuffers()
        glow?.destroy()
        renderer?.close()
    }
}

fun main(args: Array<String>) {
    val root = args.firstOrNull { it.startsWith("--project-root=") }
        ?.substringAfter('=')?.let(Path::of) ?: Path.of("").toAbsolutePath()
    DesktopApplication.run(RailgunCaptureApp(root), DesktopEnvironment(root, 1280, 720, 1f))
}
