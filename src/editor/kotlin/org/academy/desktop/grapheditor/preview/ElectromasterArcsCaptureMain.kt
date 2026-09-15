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

/** Reproducible editor GPU views of the authored assets; never included in mod jars. */
private class ElectromasterArcsCaptureApp(private val root: Path) : EditorApp {
    override var title = "Electromaster VFXGraph capture"
    private val metadata = SimpleNodeRegistry()
    private val blocks = VfxBlockRegistry()
    private var renderer: VfxGraphRenderer? = null
    private var glow: EditorGlow? = null
    private var target: TextureTarget? = null
    private var index = 0
    private var warmup = 0
    private var waiting = false
    private data class Shot(val asset: String, val time: Float, val surface: Boolean = false, val frame: Int = -1)
    private val shots = if (java.lang.Boolean.getBoolean("academy.electricExtensionCapture"))
        listOf(Shot("electromagnetic_shield", 0.12f), Shot("magnetic_weapon", 0.18f), Shot("arc_generate", 0.18f))
    else listOf(Shot("railgun_charge", 8.4f), Shot("arc_generate", 0.18f),
        Shot("thunder_lance", 0.18f), Shot("sky_strike_thunderclap", 0.65f),
        Shot("sky_strike_storm", 0.65f), Shot("sky_strike_thunderclap", 0.65f, true)) +
        (0..119).map { Shot("railgun_charge", 8f + it / 30f, frame = it) }

    init { VfxBlocks.registerAll(metadata, blocks) }
    override fun onFrame(partialTick: Float) { RenderSystem.executePendingTasks() }
    override fun createRoot(): WidgetContainer = FrameLayoutWidget()
    override fun quitRequested() = index >= shots.size && !waiting

    override fun renderBackground(windowTarget: RenderTarget) {
        if (++warmup < 5 || waiting || quitRequested()) return
        if (target == null) target = TextureTarget("Electric arcs capture", 1280, 720, true, GpuFormat.RGBA8_UNORM)
        if (renderer == null) { renderer = VfxGraphRenderer(); glow = EditorGlow(renderer!!) }
        val outputTarget = target!!
        val shot = shots[index]
        val path = root.resolve("src/main/resources/assets/academy/vfxgraph/${shot.asset}.json")
        val system = JsonVfxGraphCodec(metadata).decode(JsonParser.parseString(Files.readString(path)).asJsonObject)
        val sim = VfxSystemSimulator(system, blocks, 42L, system.parameters())
        sim.setLiveParam("time", Value.of(shot.time))
        sim.step(0f)
        val specs = system.contexts().flatMap { it.blocks() }
            .filter { it.type().startsWith("vfx.block.output_") }
            .map { RenderSpec.fromOutputNode(GraphNode(it.id(), it.type(), it.properties(), it.ports(), 0f, 0f)) }
        val charge = shot.asset == "railgun_charge"
        val shield = shot.asset == "electromagnetic_shield"
        val weapon = shot.asset == "magnetic_weapon"
        val sky = shot.asset.startsWith("sky_strike")
        val length = if (shot.asset == "arc_generate") 16f else 32f
        val center = when {
            charge || shot.surface -> Vector3f()
            shield -> Vector3f(0f, 0.9f, 0f)
            weapon -> Vector3f()
            sky -> Vector3f(0f, 30f, 0f)
            else -> Vector3f(0f, length * 0.5f, 0f)
        }
        val eye = when {
            charge -> Vector3f(0.75f, 0.25f, 0.5f)
            shield || weapon -> Vector3f(3f, 2f, 4f)
            shot.surface -> Vector3f(20f, 23f, 12f)
            sky -> Vector3f(92f, 50f, 30f)
            else -> Vector3f(length, length * 0.5f, length * 0.12f)
        }
        val halfHeight = when { charge -> 0.56f; shield || weapon -> 1.5f; shot.surface -> 11f; sky -> 43f; else -> length * 0.34f }
        val up = if (charge || sky || shield || weapon) Vector3f(0f, 1f, 0f) else Vector3f(0f, 0f, 1f)
        val camera = GraphCamera(eye, Matrix4f().lookAlong(Vector3f(center).sub(eye).normalize(), up),
            Matrix4f().setOrtho(-halfHeight * 16 / 9, halfHeight * 16 / 9, -halfHeight, halfHeight, 250f, 0.01f, true))
        val world = WorldTransform.identity()
        val color = outputTarget.getColorTextureView() ?: return
        renderer!!.setArcBuffer(sim.arcBuffer())
        renderer!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), camera, true, specs, world, false)
        glow!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), sim.arcBuffer(), camera,
            specs, outputTarget.width, outputTarget.height)
        val name = "preview_${shot.asset}" + if (shot.surface) "_surface" else ""
        val output = if (shot.frame >= 0) root.resolve("build/electromaster-motion/frame_%03d.png".format(shot.frame))
            else root.resolve("docs/vfx/electromaster_arcs/$name.png")
        Files.createDirectories(output.parent)
        waiting = true
        Screenshot.takeScreenshot(outputTarget) { image ->
            image.use { it.writeToFile(output) }
            println("[electric-capture] $output arcs=${sim.arcBuffer().count()}")
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
    DesktopApplication.run(ElectromasterArcsCaptureApp(root), DesktopEnvironment(root, 1280, 720, 1f))
}
