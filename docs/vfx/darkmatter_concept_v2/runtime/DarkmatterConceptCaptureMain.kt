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
import org.academy.api.client.render.vfxgraph.shape.SurfaceDetailSelector
import org.academy.api.client.render.vfxgraph.shape.FeatherArrayGeometry
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

/** Real editor GPU captures. Grey geometry is an explicit neutral surface fixture, not a character. */
private class DarkmatterConceptCaptureApp(private val root: Path, private val environment: DesktopEnvironment) : EditorApp {
    override var title = "Academy / Darkmatter concept v2 GPU validation"
    private val metadata = SimpleNodeRegistry()
    private val blocks = VfxBlockRegistry()
    private var renderer: VfxGraphRenderer? = null
    private var glow: EditorGlow? = null
    private var target: TextureTarget? = null
    private var index = 0
    private var waiting = false
    private var warmup = 0
    private val results = mutableListOf<Map<String, Any>>()
    private val outputDir = root.resolve(System.getProperty("academy.darkmatterCaptureOutput",
        "docs/vfx/darkmatter_concept_v2/runtime"))
    private data class Phase(val id: String, val alpha: Float, val beta: Float, val gamma: Float)
    private data class Shot(val asset: String, val phase: Phase, val time: Float = .18f,
        val progress: Float = .38f, val suffix: String = "", val spread: Float = 1f,
        val attack: Float = 0f, val reverse: Float = 0f, val yaw: Float = 0f, val firstPerson: Boolean = false,
        val grounded: Float = 1f, val blocked: Boolean = false, val penetration: Float = 0f,
        val surfaceDetail: Int = 0, val cameraDistance: Float = 0f)
    private val phases = listOf(Phase("alpha", .85f, .15f, 0f), Phase("beta", .15f, .85f, 0f), Phase("gamma", .55f, .45f, .6f))
    private val assets = listOf("generation", "elimination", "shaping", "phase_tuning", "disassemble", "cut", "interference",
        "repair", "creation_summon", "creation_dismiss", "creation_contact", "creation_trail", "feather", "light_contact", "six_wings", "wing_strike")
    private val allShots = assets.flatMap { asset -> phases.map { phase ->
        Shot("darkmatter_$asset", phase, time = if (asset == "interference") .9f else if (asset == "six_wings") .65f else .18f,
            attack = if (asset == "wing_strike") .8f else 0f)
    } } + listOf(
        Shot("darkmatter_six_wings", phases[2], .14f, .14f, "opening", spread = .35f),
        Shot("darkmatter_six_wings", phases[2], .6f, suffix = "closing", spread = .12f),
        Shot("darkmatter_six_wings", phases[2], .6f, suffix = "yaw_65", yaw = 65f),
        Shot("darkmatter_six_wings", phases[2], .6f, suffix = "first_person", firstPerson = true),
        Shot("darkmatter_cut", phases[2], .12f, suffix = "reverse", reverse = 1f),
        Shot("darkmatter_cut", phases[2], .3f, .94f, "dissipating"),
        Shot("darkmatter_wing_strike", phases[2], .08f, .25f, "early", attack = .7f),
        Shot("darkmatter_wing_strike", phases[2], .28f, .87f, "late", attack = .35f),
        Shot("darkmatter_repair", phases[0], .3f, .65f, "settled"),
        Shot("darkmatter_disassemble", phases[1], .45f, .75f, "terminal"),
        Shot("darkmatter_interference", phases[1], .9f, suffix = "night"),
        Shot("darkmatter_shaping", phases[0], .3f, .58f, "formed"),
        Shot("darkmatter_phase_tuning", phases[1], .3f, .58f, "flexible")
    ) + phases.map { Shot("darkmatter_cut", it, .08f, .25f, "peak") } +
        phases.map { Shot("darkmatter_generation", it, .3f, .58f, "condensed") } +
        phases.map { Shot("darkmatter_six_wings", it, .65f, suffix = "flight", grounded = 0f) } +
        phases.map { Shot("darkmatter_feather", it, .18f, suffix = "fit") } +
        listOf(Shot("darkmatter_six_wings", phases[2], .65f, suffix = "single_feather")) +
        listOf("generation", "shaping", "elimination").flatMap { asset -> phases.flatMap { phase ->
            listOf(.12f, .24f, .42f).map { time -> Shot("darkmatter_$asset", phase, time,
                time / if (asset == "shaping") .65f else .7f, "time_${(time * 100).toInt()}") }
        } } + listOf(
            Shot("darkmatter_six_wings", phases[2], .65f, suffix = "blocked", blocked = true),
            Shot("darkmatter_six_wings", phases[2], .65f, suffix = "partial_penetration", blocked = true, penetration = .35f),
            Shot("darkmatter_six_wings", phases[2], .65f, suffix = "penetrating", blocked = true, penetration = 1f),
            Shot("darkmatter_six_wings", phases[2], .65f, suffix = "grid_medium", surfaceDetail = 1),
            Shot("darkmatter_six_wings", phases[2], .65f, suffix = "grid_far", surfaceDetail = 2),
            Shot("darkmatter_six_wings", phases[2], .65f, suffix = "mixed_grid"),
            Shot("darkmatter_six_wings", phases[2], .65f, suffix = "lod_medium", cameraDistance = 45f),
            Shot("darkmatter_six_wings", phases[2], .65f, suffix = "lod_far", cameraDistance = 85f))

    private val selectedAssets = System.getProperty("academy.darkmatterCaptureAssets", "")
        .split(',').filter { it.isNotBlank() }.toSet()
    private val shots = allShots.filter { selectedAssets.isEmpty() || it.asset.removePrefix("darkmatter_") in selectedAssets }

    init { VfxBlocks.registerAll(metadata, blocks) }
    override fun onFrame(partialTick: Float) { RenderSystem.executePendingTasks() }
    override fun createRoot(): WidgetContainer = FrameLayoutWidget()
    override fun quitRequested() = index >= shots.size && !waiting

    override fun renderBackground(windowTarget: RenderTarget) {
        if (++warmup < 5 || waiting || quitRequested()) return
        if (target == null) target = TextureTarget("Darkmatter concept v2 capture", 1280, 720, true, GpuFormat.RGBA8_UNORM)
        if (renderer == null) { renderer = VfxGraphRenderer(environment::loadTexture); glow = EditorGlow(renderer!!) }
        val shot = shots[index]
        val system = JsonVfxGraphCodec(metadata).decode(JsonParser.parseString(Files.readString(
            root.resolve("src/main/resources/assets/academy/vfxgraph/${shot.asset}.json"))).asJsonObject)
        val sim = VfxSystemSimulator(system, blocks, 42L, system.parameters())
        val wings = shot.asset.endsWith("six_wings")
        val study = shot.suffix == "single_feather"
        val fit = shot.suffix == "fit"
        val cut = shot.asset.endsWith("cut") || shot.asset.endsWith("wing_strike")
        val wingStrike = shot.asset.endsWith("wing_strike")
        val fanRadius = if (wingStrike) 2.2f else 4.4f
        val field = shot.asset.endsWith("interference")
        val local = listOf("generation", "elimination", "shaping", "phase_tuning", "feather", "creation_trail").any { shot.asset.endsWith(it) }
        val center = if (study) Vector3f(3.5f, 3.65f, -.45f) else if (field) Vector3f(0f, 2.2f, 7f) else if (cut) Vector3f(0f, 1.2f, 3f)
            else if (local) Vector3f() else Vector3f(0f, 1.2f, 0f)
        val eye = if (study) Vector3f(center).add(0f, 0f, -7f) else if (shot.firstPerson) Vector3f(0f, 1.62f, .08f) else if (wings) Vector3f(0f, 2.1f, -12f)
            else if (field) Vector3f(11f, 7f, -8f) else if (cut) Vector3f(6f, 5f, -8f)
            else if (local) Vector3f(1.8f, 1.1f, -4f) else Vector3f(2.3f, 2f, -5f)
        val halfHeight = if (study) .95f else if (fit) 1.8f else if (wings) 4.3f else if (field) 4.4f else if (cut) 3.6f else if (local) .75f else 1.3f
        if (shot.cameraDistance > 0) eye.set(0f, 2.1f, -shot.cameraDistance)
        var camera = GraphCamera(eye, Matrix4f().lookAlong(if (shot.firstPerson) Vector3f(0f, 0f, 1f)
            else Vector3f(center).sub(eye).normalize(), Vector3f(0f, 1f, 0f)),
            if (shot.firstPerson || shot.cameraDistance > 0) Matrix4f().setPerspective(Math.toRadians(70.0).toFloat(), 16f / 9, 250f, .01f, true)
            else Matrix4f().setOrtho(-halfHeight * 16 / 9, halfHeight * 16 / 9, -halfHeight, halfHeight, 250f, .01f, true))
        val rotation = Quaternionf().rotateY(Math.toRadians(shot.yaw.toDouble()).toFloat())
        val live = mapOf("time" to shot.time, "progress" to shot.progress, "alpha" to shot.phase.alpha,
            "beta" to shot.phase.beta, "gamma" to shot.phase.gamma, "density" to shot.phase.alpha,
            "flexibility" to .08f + shot.phase.beta * .18f, "edge_activity" to .15f + shot.phase.beta * .6f,
            "light" to if (shot.suffix == "night") .12f else 1f, "spread" to shot.spread, "strength" to 1f,
            "grounded" to shot.grounded, "minimum_height" to .12f,
            "block_penetration" to shot.penetration,
            "lod" to if (shot.cameraDistance > 0) (1 - (eye.length() - 16) / 64).coerceIn(.2f, 1f) else 1f,
            "attack" to shot.attack, "reverse" to shot.reverse, "view_first_person" to if (shot.firstPerson) 1f else 0f,
            "radius" to if (cut) fanRadius else 1f,
            "width" to if (wingStrike) fanRadius * .42f else if (cut) fanRadius * (.38f + shot.phase.alpha * .12f) else .6f,
            "height" to if (cut) 1.2f else 1.8f, "sheet_scale" to 1f, "finished" to if (shot.suffix == "terminal") 1f else 0f)
        live.forEach { (key, value) -> sim.setLiveParam(key, Value.of(value)) }
        val detail = if (shot.cameraDistance > 0) SurfaceDetailSelector().update(1.8f,
            FeatherArrayGeometry.boundsRadius(3.6f, 1.25f, -.2f), eye.length(), camera.projection()) else shot.surfaceDetail
        sim.setLiveParam("surface_detail", Value.of(detail))
        sim.setLiveParam("camera", Value.of(Quaternionf(rotation).conjugate().transform(Vector3f(eye))))
        sim.setLiveParam("camera_forward", Value.of(Vector3f(center).sub(eye).normalize()))
        sim.setSurfaceSampler("surface", sampler(shot))
        if (shot.blocked) sim.setSurfaceOcclusionSampler("occlusion") { group, _, _, _, _, plane ->
            if (group == 3 || group == 5) { plane.set(-1f, 0f, 0f, 2.2f); true } else false
        }
        repeat((shot.time * 60).toInt().coerceAtLeast(1)) { sim.step(1f / 60) }
        if (shot.suffix == "mixed_grid") for (particle in 0 until sim.buffer().count())
            sim.buffer().setSurfaceDetail(particle, particle / 44)
        // Isolate an existing outer primary for close material inspection. The actual graph,
        // simulation, skeleton and shader are unchanged; no synthetic feather is substituted.
        if (study) {
            center.set(sim.buffer().positionX(35) + sim.buffer().velocityX(35) * .5f,
                sim.buffer().positionY(35) + sim.buffer().velocityY(35) * .5f,
                sim.buffer().positionZ(35) + sim.buffer().velocityZ(35) * .5f)
            eye.set(center).add(0f, 0f, -7f)
            camera = GraphCamera(eye, Matrix4f().lookAlong(Vector3f(0f, 0f, 1f), Vector3f(0f, 1f, 0f)),
                Matrix4f().setOrtho(-halfHeight * 16 / 9, halfHeight * 16 / 9, -halfHeight, halfHeight, 250f, .01f, true))
            for (particle in 0 until sim.buffer().count()) if (particle != 35) sim.buffer().setAlpha(particle, 0f)
        }
        val specs = system.contexts().flatMap { it.blocks() }.filter { it.type().startsWith("vfx.block.output_") }
            .map { RenderSpec.fromOutputNode(GraphNode(it.id(), it.type(), it.properties(), it.ports(), 0f, 0f)) }
        val outputTarget = target!!
        val color = outputTarget.getColorTextureView() ?: return
        renderer!!.setArcBuffer(sim.arcBuffer())
        val fixture = if (local || cut || field || shot.firstPerson || study) emptyList() else humanoidFixture(shot.blocked)
        val beforeSubmission = renderer!!.instancingStatistics()
        renderer!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), camera, true, specs,
            WorldTransform(Vector3f(), rotation, 1f), false, fixture)
        glow!!.render(color, outputTarget.getDepthTextureView(), sim.buffer(), sim.arcBuffer(), camera, specs, outputTarget.width, outputTarget.height)
        val submitted = renderer!!.instancingStatistics()
        Files.createDirectories(outputDir)
        val name = "gpu_${shot.asset}_${shot.phase.id}${if (shot.suffix.isEmpty()) "" else "_${shot.suffix}"}.png"
        val output = outputDir.resolve(name)
        waiting = true
        Screenshot.takeScreenshot(outputTarget) { image ->
            image.use { it.writeToFile(output) }
            results += mapOf("file" to name, "asset" to shot.asset, "phase" to shot.phase.id, "parameters" to live,
                "particles" to sim.buffer().count(), "arcs" to sim.arcBuffer().count(), "geometry" to specs.map { it.geometry().name },
                "shaders" to specs.map { it.fragmentShader().toString() }, "surfaceFixture" to if (fixture.isEmpty()) "none"
                    else if (shot.blocked) "neutral humanoid boxes + solid block proxy at local x=2.2" else "neutral humanoid boxes",
                "photography" to mapOf("eye" to listOf(eye.x, eye.y, eye.z), "center" to listOf(center.x, center.y, center.z),
                    "orthoHalfHeight" to halfHeight, "isolatedParticle" to if (study) 35 else -1),
                "surfaceDetail" to detail, "submission" to mapOf("drawCalls" to submitted.drawCalls() - beforeSubmission.drawCalls(),
                    "instances" to submitted.instances() - beforeSubmission.instances(),
                    "materialTriangles" to submitted.materialTriangles() - beforeSubmission.materialTriangles(),
                    "uploadedBytes" to submitted.uploadedBytes() - beforeSubmission.uploadedBytes(),
                    "zeroAlphaSkipped" to submitted.zeroAlphaSkipped() - beforeSubmission.zeroAlphaSkipped()),
                "directionalSceneOutput" to "requires gameplay client; this capture verifies graph surfaces")
            Files.writeString(outputDir.resolve("gpu_capture_manifest.json"), GsonBuilder().setPrettyPrinting().create().toJson(results))
            println("[darkmatter-concept-v2-capture] $output particles=${sim.buffer().count()} arcs=${sim.arcBuffer().count()}")
            index++
            waiting = false
        }
    }

    private fun sampler(shot: Shot): SurfaceSampler = SurfaceSampler { index, u, v, position, normal ->
        val formation = listOf("generation", "elimination", "shaping", "phase_tuning", "creation_trail").any { shot.asset.endsWith(it) }
        val min = if (shot.asset.endsWith("shaping")) Vector3f(-.028f, -.34f, -.018f)
            else if (shot.asset.endsWith("phase_tuning")) Vector3f(-.12f, -.16f, -.025f - shot.phase.alpha * .05f)
            else if (formation) Vector3f(-.08f) else Vector3f(-.3f, 0f, -.15f)
        val max = if (shot.asset.endsWith("shaping")) Vector3f(.028f, .34f, .018f)
            else if (shot.asset.endsWith("phase_tuning")) Vector3f(.12f, .16f, .025f + shot.phase.alpha * .05f)
            else if (formation) Vector3f(.08f) else Vector3f(.3f, 1.8f, .15f)
        SurfaceSampler.box(index, u, v, min, max, Matrix4f(), position, normal)
        if (shot.asset.endsWith("generation") || shot.asset.endsWith("elimination"))
            position.fma((if (shot.asset.endsWith("elimination")) shot.progress else 1 - shot.progress) * .3f, normal)
        else if (shot.asset.endsWith("shaping")) position.mul(.15f + shot.progress * .85f)
        true
    }

    private fun humanoidFixture(blocked: Boolean = false): List<VfxGraphRenderer.SurfaceMesh> {
        val vertices = mutableListOf<Float>()
        fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
            val c = arrayOf(floatArrayOf(x0,y0,z0), floatArrayOf(x1,y0,z0), floatArrayOf(x1,y1,z0), floatArrayOf(x0,y1,z0),
                floatArrayOf(x0,y0,z1), floatArrayOf(x1,y0,z1), floatArrayOf(x1,y1,z1), floatArrayOf(x0,y1,z1))
            for (f in arrayOf(intArrayOf(0,1,2,3), intArrayOf(4,7,6,5), intArrayOf(0,4,5,1), intArrayOf(3,2,6,7), intArrayOf(0,3,7,4), intArrayOf(1,5,6,2)))
                for (corner in intArrayOf(f[0],f[1],f[2],f[0],f[2],f[3])) c[corner].forEach { vertices += it }
        }
        box(-.25f,.65f,-.13f,.25f,1.35f,.13f); box(-.22f,1.35f,-.22f,.22f,1.8f,.22f)
        box(-.49f,.65f,-.11f,-.26f,1.35f,.11f); box(.26f,.65f,-.11f,.49f,1.35f,.11f)
        box(-.23f,0f,-.11f,-.01f,.65f,.11f); box(.01f,0f,-.11f,.23f,.65f,.11f)
        if (blocked) box(2.2f, 0f, -1.2f, 2.65f, 1.8f, .3f)
        return listOf(VfxGraphRenderer.SurfaceMesh(vertices.toFloatArray(), .16f,.18f,.22f,1f))
    }

    override fun onDispose() { target?.destroyBuffers(); glow?.destroy(); renderer?.close() }
}

fun main(args: Array<String>) {
    val root = args.firstOrNull { it.startsWith("--project-root=") }?.substringAfter('=')?.let(Path::of) ?: Path.of("").toAbsolutePath()
    val environment = DesktopEnvironment(root, 1280, 720, 1f)
    DesktopApplication.run(DarkmatterConceptCaptureApp(root, environment), environment)
}
