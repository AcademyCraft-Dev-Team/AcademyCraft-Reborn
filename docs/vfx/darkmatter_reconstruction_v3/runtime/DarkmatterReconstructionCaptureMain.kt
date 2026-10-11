package org.academy.desktop.grapheditor.preview

import com.google.gson.GsonBuilder
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
import org.academy.api.client.render.vfxgraph.shape.SurfaceSampler
import org.academy.api.client.render.vfxgraph.sim.VfxSystemSimulator
import org.academy.desktop.grapheditor.viewport.EditorGlow
import org.academy.desktop.platform.DesktopApplication
import org.academy.desktop.platform.DesktopEnvironment
import org.academy.desktop.platform.EditorApp
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.nio.file.Files
import java.nio.file.Path

/** Production graphs and shaders rendered by the real editor GPU. Fixtures are explicitly neutral proxies. */
private class DarkmatterReconstructionCaptureApp(private val root: Path, private val environment: DesktopEnvironment) : EditorApp {
    override var title = "Academy / reconstruction v3 GPU validation"
    private val registry = SimpleNodeRegistry()
    private val blocks = VfxBlockRegistry()
    private var renderer: VfxGraphRenderer? = null
    private var glow: EditorGlow? = null
    private var target: TextureTarget? = null
    private var index = 0
    private var waiting = false
    private var warmup = 0
    private val results = mutableListOf<Map<String, Any>>()
    private val output = Path.of(System.getProperty("academy.reconstructionOutput",
        root.resolve("docs/vfx/darkmatter_reconstruction_v3/runtime/editor").toString()))
    private data class Shot(val asset: String, val id: String, val time: Float, val progress: Float,
        val side: Boolean = false, val night: Boolean = false, val posed: Boolean = false,
        val block: Boolean = false, val beta: Float = .45f)
    private val shots = listOf("disassemble", "repair").flatMap { asset ->
        val samples = if (asset == "repair") listOf(.057f, .19f, .3344f) else listOf(.065f, .26f, .494f)
        samples.flatMapIndexed { stage, time -> listOf(false, true).map { side ->
            Shot(asset, "stage_${stage + 1}_${if (side) "side" else "front"}", time,
                time / if (asset == "repair") .38f else .65f, side = side)
        } } + listOf(
            Shot(asset, "posed", samples[1], samples[1] / if (asset == "repair") .38f else .65f, posed = true),
            Shot(asset, "night", samples[1], samples[1] / if (asset == "repair") .38f else .65f, night = true),
            Shot(asset, "beta", samples[1], samples[1] / if (asset == "repair") .38f else .65f, beta = .85f))
    } + listOf(.065f, .25f, .49f, .64f).mapIndexed { stage, time ->
        Shot("disassemble", "removed_block_${stage + 1}", time, time / .65f, block = true)
    } + listOf(.3f, .55f, .8f).map { time ->
        Shot("repair", "continuous_${(time * 100).toInt()}", time, .26f / .38f)
    }

    init { VfxBlocks.registerAll(registry, blocks) }
    override fun onFrame(partialTick: Float) { RenderSystem.executePendingTasks() }
    override fun createRoot(): WidgetContainer = FrameLayoutWidget()
    override fun quitRequested() = index >= shots.size && !waiting

    override fun renderBackground(windowTarget: RenderTarget) {
        if (++warmup < 5 || waiting || quitRequested()) return
        if (target == null) target = TextureTarget("Reconstruction v3 capture", 1280, 720, true, GpuFormat.RGBA8_UNORM)
        if (renderer == null) { renderer = VfxGraphRenderer(environment::loadTexture); glow = EditorGlow(renderer!!) }
        val shot = shots[index]
        val system = JsonVfxGraphCodec(registry).decode(JsonParser.parseString(Files.readString(
            root.resolve("src/main/resources/assets/academy/vfxgraph/darkmatter_${shot.asset}.json"))).asJsonObject)
        val sim = VfxSystemSimulator(system, blocks, 42L, system.parameters())
        val pose = Matrix4f().rotateZ(if (shot.posed) -.55f else 0f).rotateY(if (shot.posed) .3f else 0f)
        val min = if (shot.block) Vector3f(-.5f, 0f, -.5f) else Vector3f(-.125f, 0f, -.125f)
        val max = if (shot.block) Vector3f(.5f, 1f, .5f) else Vector3f(.125f, .75f, .125f)
        val center = pose.transformPosition(Vector3f(0f, if (shot.block) .5f else .36f, 0f))
        val eye = Vector3f(center).add(if (shot.side) 2.2f else .72f, .3f, if (shot.side) -1.8f else -3f)
        val half = if (shot.block) .87f else .58f
        val camera = GraphCamera(eye,
            Matrix4f().lookAlong(Vector3f(center).sub(eye).normalize(), Vector3f(0f, 1f, 0f)),
            Matrix4f().setOrtho(-half * 16 / 9, half * 16 / 9, -half, half, 250f, .01f, true))
        val live = mutableMapOf("time" to shot.time, "progress" to shot.progress,
            "alpha" to 1 - shot.beta, "beta" to shot.beta, "gamma" to 0f,
            "density" to 1 - shot.beta, "flexibility" to shot.beta, "edge_activity" to .15f + shot.beta * .7f,
            "light" to if (shot.night) .12f else 1f, "strength" to 1f,
            "width" to if (shot.block) 1f else .6f, "height" to if (shot.block) 1f else 1.8f,
            "sheet_scale" to 1f, "finished" to if (shot.block) 1f else 0f,
            "view_first_person" to 0f, "lod" to 1f,
            "surface_patches" to if (shot.block || shot.asset == "repair") 6f else 1f)
        repeat(6) { part -> live["repair_progress_$part"] = shot.progress }
        live.forEach { (key, value) -> sim.setLiveParam(key, Value.of(value)) }
        sim.setLiveParam("surface_detail", Value.of(0))
        sim.setLiveParam("camera", Value.of(eye))
        sim.setLiveParam("camera_forward", Value.of(Vector3f(center).sub(eye).normalize()))
        sim.setLiveParam("sun_direction", Value.of(Vector3f(-.4f, .8f, -.5f).normalize()))
        sim.setSurfaceSampler("surface", SurfaceSampler { sample, u, v, position, normal ->
            if (shot.asset == "repair" && sample % 6 != 2) false
            else {
                SurfaceSampler.box(if (shot.block) sample % 6 else 4,
                    if (shot.block) .12f + .76f * u else if (shot.asset == "repair") .18f + .64f * u else .5f + (u - .5f) * .64f,
                    if (shot.block) .12f + .76f * v else if (shot.asset == "repair") .36f + .56f * v else .5f + (v - .5f) * .64f,
                    min, max, pose, position, normal)
                true
            }
        })
        repeat((shot.time * 60).toInt().coerceAtLeast(1)) { sim.step(1f / 60) }
        val specs = system.contexts().flatMap { it.blocks() }.filter { it.type().startsWith("vfx.block.output_") }
            .map { RenderSpec.fromOutputNode(GraphNode(it.id(), it.type(), it.properties(), it.ports(), 0f, 0f)) }
        val frame = target!!
        val color = frame.getColorTextureView() ?: return
        val before = renderer!!.instancingStatistics()
        val fixture = if (shot.block) emptyList() else listOf(boxMesh(min, max, pose))
        renderer!!.setArcBuffer(sim.arcBuffer())
        renderer!!.render(color, frame.getDepthTextureView(), sim.buffer(), camera, true, specs,
            WorldTransform(Vector3f(), Quaternionf(), 1f), false, fixture)
        glow!!.render(color, frame.getDepthTextureView(), sim.buffer(), sim.arcBuffer(), camera, specs, frame.width, frame.height)
        val after = renderer!!.instancingStatistics()
        Files.createDirectories(output)
        val name = "gpu_${shot.asset}_${shot.id}.png"
        waiting = true
        Screenshot.takeScreenshot(frame) { image ->
            image.use { it.writeToFile(output.resolve(name)) }
            results += mapOf("file" to name, "asset" to "darkmatter_${shot.asset}", "parameters" to live,
                "fixture" to if (shot.block) "removed unit block, six exposed faces, no replacement solid geometry"
                    else "neutral posed cuboid forearm proxy; not an in-game player model",
                "source" to "real production graph / editor VfxGraphRenderer / GPU screenshot readback",
                "continuation" to if (shot.id.startsWith("continuous")) "fixed retained progress fixture; network continuity requires gameplay probe" else "single pulse",
                "particleSlots" to sim.buffer().count(), "geometry" to specs.map { it.geometry().name },
                "shaders" to specs.map { it.fragmentShader().toString() },
                "drawCalls" to after.drawCalls() - before.drawCalls(),
                "instances" to after.instances() - before.instances(),
                "materialTriangles" to after.materialTriangles() - before.materialTriangles(),
                "uploadedBytes" to after.uploadedBytes() - before.uploadedBytes(),
                "camera" to listOf(eye.x, eye.y, eye.z), "orthoHalfHeight" to half)
            Files.writeString(output.resolve("gpu_capture_manifest.json"), GsonBuilder().setPrettyPrinting().create().toJson(results))
            println("[reconstruction-v3-gpu] $name slots=${sim.buffer().count()}")
            index++
            waiting = false
        }
    }

    private fun boxMesh(min: Vector3f, max: Vector3f, transform: Matrix4f): VfxGraphRenderer.SurfaceMesh {
        val corners = arrayOf(Vector3f(min.x,min.y,min.z), Vector3f(max.x,min.y,min.z),
            Vector3f(max.x,max.y,min.z), Vector3f(min.x,max.y,min.z), Vector3f(min.x,min.y,max.z),
            Vector3f(max.x,min.y,max.z), Vector3f(max.x,max.y,max.z), Vector3f(min.x,max.y,max.z))
        corners.forEach { transform.transformPosition(it) }
        val vertices = mutableListOf<Float>()
        for (face in arrayOf(intArrayOf(0,1,2,3), intArrayOf(4,7,6,5), intArrayOf(0,4,5,1),
            intArrayOf(3,2,6,7), intArrayOf(0,3,7,4), intArrayOf(1,5,6,2)))
            for (corner in intArrayOf(face[0],face[1],face[2],face[0],face[2],face[3])) {
                vertices += corners[corner].x; vertices += corners[corner].y; vertices += corners[corner].z
            }
        return VfxGraphRenderer.SurfaceMesh(vertices.toFloatArray(), .20f, .24f, .29f, 1f)
    }

    override fun onDispose() { target?.destroyBuffers(); glow?.destroy(); renderer?.close() }
}

fun main(args: Array<String>) {
    val root = args.firstOrNull { it.startsWith("--project-root=") }?.substringAfter('=')?.let(Path::of) ?: Path.of("").toAbsolutePath()
    val environment = DesktopEnvironment(root, 1280, 720, 1f)
    DesktopApplication.run(DarkmatterReconstructionCaptureApp(root, environment), environment)
}
