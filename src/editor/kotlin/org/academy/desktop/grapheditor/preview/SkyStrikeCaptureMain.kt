package org.academy.desktop.grapheditor.preview

import com.google.gson.JsonParser
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.GpuFormat
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

/** Deterministic GPU captures using the same simulator, renderer and bloom as the VFX editor. */
private class SkyStrikeCaptureApp(private val root: Path) : EditorApp {
    override var title = "Academy VFX / Sky strike capture"
    private val metadata = SimpleNodeRegistry()
    private val blocks = VfxBlockRegistry()
    private var renderer: VfxGraphRenderer? = null
    private var glow: EditorGlow? = null
    private var index = 0
    private var waiting = false
    private var warmup = 0
    private var capture: TextureTarget? = null
    private data class Shot(val asset: String, val view: String, val time: Float)
    private val shots = listOf(
        Shot("sky_strike_thunderclap", "front", 0.35f),
        Shot("sky_strike_thunderclap", "side", 0.35f),
        Shot("sky_strike_thunderclap", "top", 0.35f),
        Shot("sky_strike_storm", "front", 0.3f),
        Shot("sky_strike_thunderclap", "leader", 0.025f),
        Shot("sky_strike_thunderclap", "impact", 0.12f),
        Shot("sky_strike_thunderclap", "decay", 0.70f),
        Shot("sky_strike_thunderclap", "afterglow", 2.95f),
        Shot("sky_strike_thunderclap", "attachment", 1f)
    ) + (0..51).map { Shot("sky_strike_thunderclap", "motion_%03d".format(it), it / 12f) }

    init {
        VfxBlocks.registerAll(metadata, blocks)
    }

    override fun onFrame(partialTick: Float) { RenderSystem.executePendingTasks() }
    override fun createRoot(): WidgetContainer = FrameLayoutWidget()
    override fun quitRequested() = index >= shots.size && !waiting

    override fun renderBackground(windowTarget: RenderTarget) {
        if (capture == null) capture = TextureTarget("Sky strike capture", 768, 1024, true, GpuFormat.RGBA8_UNORM)
        val target = capture!!
        if (++warmup < 5 || waiting || quitRequested()) return
        if (renderer == null) {
            renderer = VfxGraphRenderer()
            glow = EditorGlow(renderer!!)
        }
        val shot = shots[index]
        val path = root.resolve("src/main/resources/assets/academy/vfxgraph/" + shot.asset + ".json")
        val system = JsonVfxGraphCodec(metadata).decode(JsonParser.parseString(Files.readString(path)).asJsonObject)
        val sim = VfxSystemSimulator(system, blocks, 42L, system.parameters())
        sim.setLiveParam("time", Value.of(shot.time))
        sim.step(0f)
        val specs = system.contexts().flatMap { it.blocks() }
            .filter { it.type().startsWith("vfx.block.output_") }
            .map { RenderSpec.fromOutputNode(GraphNode(it.id(), it.type(), it.properties(), it.ports(), 0f, 0f)) }
        val side = shot.view == "side"
        val top = shot.view == "top"
        val eye = when {
            top -> Vector3f(0f, 140f, 0f)
            side -> Vector3f(120f, 36f, 0f)
            else -> Vector3f(0f, 36f, 120f)
        }
        val direction = when {
            top -> Vector3f(0f, -1f, 0f)
            side -> Vector3f(-1f, 0f, 0f)
            else -> Vector3f(0f, 0f, -1f)
        }
        val up = if (top) Vector3f(0f, 0f, -1f) else Vector3f(0f, 1f, 0f)
        val halfY = if (top) 32f else 45f
        val halfX = halfY * target.width / target.height
        val camera = GraphCamera(eye, Matrix4f().lookAlong(direction, up),
            Matrix4f().setOrtho(-halfX, halfX, -halfY, halfY, 300f, 0.1f, true))
        renderer!!.setArcBuffer(sim.arcBuffer())
        val color = target.getColorTextureView() ?: return
        renderer!!.render(color, target.getDepthTextureView(), sim.buffer(), camera, true,
            specs, WorldTransform.identity(), false)
        glow!!.render(color, sim.buffer(), sim.arcBuffer(), camera, specs, target.width, target.height)
        val folder = if (shot.view.startsWith("motion_")) "build/sky-strike-frames/" else "docs/vfx/sky_strike/"
        val output = root.resolve(folder + shot.asset + "_" + shot.view + ".png")
        Files.createDirectories(output.parent)
        waiting = true
        Screenshot.takeScreenshot(target) { image ->
            image.use { it.writeToFile(output) }
            println("[sky-strike-capture] " + output + " arcs=" + sim.arcBuffer().count() +
                " particles=" + sim.buffer().count())
            index++
            waiting = false
        }
    }

    override fun onDispose() {
        capture?.destroyBuffers()
        glow?.destroy()
        renderer?.close()
    }
}

fun main(args: Array<String>) {
    val root = args.firstOrNull { it.startsWith("--project-root=") }
        ?.substringAfter('=')?.let(Path::of) ?: Path.of("").toAbsolutePath()
    val environment = DesktopEnvironment(root, 768, 1024, 1f)
    DesktopApplication.run(SkyStrikeCaptureApp(root), environment)
}
