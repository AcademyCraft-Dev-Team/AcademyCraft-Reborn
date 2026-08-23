package org.academy.desktop.grapheditor.preview

import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.resources.Identifier
import org.academy.api.client.render.Render
import org.academy.api.client.render.graph.compile.DefaultGraphCompiler
import org.academy.api.client.render.graph.registry.NodeRegistry
import org.academy.api.client.render.shader.codegen.GlslGenerator
import org.academy.api.client.render.shader.codegen.GlslNodeRegistry
import org.academy.api.client.render.shader.pipeline.GraphMaterial
import org.academy.api.client.render.shader.pipeline.ShaderGraphPipeline
import org.academy.api.client.render.shader.pipeline.ShaderGraphResult
import org.academy.desktop.grapheditor.canvas.GraphEditorModel
import org.joml.Vector4f
import org.lwjgl.system.MemoryStack
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Optional
import java.util.OptionalDouble

/**
 * Shader 实时预览：把当前图编译为管线，渲染为全窗口背景（ImGui 编辑器浮层其上）。
 * 纹理绑定（A1，ADR-021）：按 [org.academy.api.client.render.shader.pipeline.UniformLayout.samplers]
 * 逐槽位加载真实资产纹理；未指定/加载失败的标识绑兜底品红贴图。
 */
class ShaderPreview(
    private val modelRef: org.academy.desktop.grapheditor.canvas.GraphEditorModelRef,
    private val registry: NodeRegistry,
    private val glslRegistry: GlslNodeRegistry,
    private val loadTexture: (Identifier) -> GpuTextureView,
) {
    private val model: GraphEditorModel get() = modelRef.model
    private val pipeline = ShaderGraphPipeline(glslRegistry)
    private var compiled: ShaderGraphResult? = null
    private var material: GraphMaterial? = null
    private var uniformBuffer: GpuBuffer? = null
    private val textureBindings = LinkedHashMap<String, Pair<GpuTextureView, GpuSampler>>()
    private var previewSampler: GpuSampler? = null
    private var fallbackTexture: GpuTextureView? = null
    private var lastModel: GraphEditorModel? = null
    private var lastVersion = -1
    private val startNanos = System.nanoTime()

    /** 子图注册表（M12-05）；为空则不展开子图节点。 */
    var subGraphs: org.academy.api.client.render.graph.subgraph.SubGraphRegistry? = null

    var error: String? = null
        private set
    var fragmentSource: String? = null
        private set

    /** 若模型变更（含标签页切换替换模型）则重编译。 */
    fun sync() {
        if (model === lastModel && model.version == lastVersion) return
        recompile()
    }

    private fun recompile() {
        error = null
        fragmentSource = null
        compiled = null
        material = null
        uniformBuffer?.close()
        uniformBuffer = null
        try {
            val graph = org.academy.api.client.render.graph.subgraph.SubGraphFlattener.flatten(model.toGraph(), subGraphs)
            // 空图（新建空白文档）不算错误：无输出可预览，但不弹错误条
            if (graph.nodes().isEmpty()) {
                lastModel = model
                lastVersion = model.version
                return
            }
            val compiledGraph = DefaultGraphCompiler(registry).compile(graph)
            val result = pipeline.compile(graph, compiledGraph)
            val device = RenderSystem.getDevice()
            pipeline.precompile(device, result)

            fragmentSource = GlslGenerator(glslRegistry).generate(graph, compiledGraph).fragmentSource()
            material = GraphMaterial(result.layout(), graph.parameters())
            uniformBuffer = device.createBuffer(
                { "Graph Uniforms" },
                GpuBuffer.USAGE_UNIFORM or GpuBuffer.USAGE_COPY_DST,
                result.layout().totalSize().toLong()
            )
            rebuildTextureBindings(result)
            compiled = result
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
        lastModel = model
        lastVersion = model.version
    }

    fun render(target: RenderTarget) {
        sync()
        val result = compiled ?: return
        val mat = material ?: return
        val buffer = uniformBuffer ?: return
        val color = target.getColorTextureView() ?: return

        val time = (System.nanoTime() - startNanos) / 1e9f
        writeUniforms(mat, buffer, result, time)

        val device = RenderSystem.getDevice()
        val encoder = device.createCommandEncoder()
        try {
            val renderPass = encoder.createRenderPass({ "Shader Preview" }, color, Optional.of(Vector4f(0f)))
            renderPass.use {
                it.setPipeline(result.pipeline())
                it.setUniform(GlslGenerator.UNIFORM_BLOCK_NAME, buffer.slice())
                for ((name, binding) in textureBindings) {
                    it.bindTexture(name, binding.first, binding.second)
                }
                it.setVertexBuffer(0, Render.Buffers.getInstance().getFSQuadVBNDC().slice())
                val seq = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
                it.setIndexBuffer(seq.getBuffer(6), seq.type())
                it.drawIndexed(6, 1, 0, 0, 0)
            }
        } finally {
            encoder.submit()
        }
    }

    /** 按图绑定的 sampler 槽位加载真实资产纹理；标识空/非法/加载失败时绑兜底品红。 */
    private fun rebuildTextureBindings(result: ShaderGraphResult) {
        textureBindings.clear()
        if (result.layout().samplers().isEmpty()) return
        val device = RenderSystem.getDevice()
        previewSampler = previewSampler ?: device.createSampler(
            AddressMode.REPEAT, AddressMode.REPEAT,
            FilterMode.LINEAR, FilterMode.LINEAR,
            1, OptionalDouble.empty()
        )
        val sam = previewSampler!!
        for (binding in result.layout().samplers()) {
            val view = try {
                if (binding.identifier().isBlank()) null
                else loadTexture(Identifier.parse(binding.identifier()))
            } catch (_: Exception) {
                null
            }
            textureBindings[binding.uniformName()] = (view ?: ensureFallbackTexture()) to sam
        }
    }

    /** 1x1 品红兜底纹理（标识未指定或加载失败时可见）。 */
    private fun ensureFallbackTexture(): GpuTextureView {
        fallbackTexture?.let { return it }
        val device = RenderSystem.getDevice()
        val texture = device.createTexture(
            { "Graph Texture Fallback" },
            GpuTexture.USAGE_TEXTURE_BINDING or GpuTexture.USAGE_COPY_DST,
            GpuFormat.RGBA8_UNORM,
            1, 1, 1, 1
        )
        val bytes: ByteBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        bytes.put(0xFF.toByte()).put(0x00.toByte()).put(0xFF.toByte()).put(0xFF.toByte())
        bytes.flip()
        device.createCommandEncoder().writeToTexture(texture, bytes, 0, 0, 0, 0, 1, 1)
        fallbackTexture = device.createTextureView(texture)
        return fallbackTexture!!
    }

    private fun writeUniforms(mat: GraphMaterial, buffer: GpuBuffer, result: ShaderGraphResult, time: Float) {
        try {
            MemoryStack.stackPush().use { stack ->
                val builder = Std140Builder.onStack(stack, result.layout().totalSize())
                mat.write(builder, time)
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), builder.get())
            }
        } catch (_: Exception) {
            // 忽略逐帧写入错误
        }
    }
}
