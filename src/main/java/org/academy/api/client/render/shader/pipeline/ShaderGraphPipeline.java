package org.academy.api.client.render.shader.pipeline;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;
import org.academy.api.client.render.graph.compile.CompiledGraph;
import org.academy.api.client.render.graph.model.Graph;
import org.academy.api.client.render.shader.codegen.GlslGenerator;
import org.academy.api.client.render.shader.codegen.GlslNodeRegistry;

/**
 * Shader 图管线：代码生成 → 动态着色器源注册 → RenderPipeline 构建 → 预编译。
 *
 * <p>全程仅使用图形 API 无关抽象层（PROGRAM.md R1）。预编译经
 * {@link GpuDevice#precompilePipeline(RenderPipeline, com.mojang.blaze3d.shaders.ShaderSource)} 写入管线缓存。</p>
 */
public final class ShaderGraphPipeline {
    private final GlslNodeRegistry generators;
    private final DynamicShaderSource shaderSource = new DynamicShaderSource();

    public ShaderGraphPipeline(GlslNodeRegistry generators) {
        this.generators = generators;
    }

    public ShaderGraphResult compile(Graph graph, CompiledGraph compiled) {
        var program = new GlslGenerator(generators).generate(graph, compiled);
        var vertexId = shaderSource.register(program.vertexSource());
        var fragmentId = shaderSource.register(program.fragmentSource());
        var samplers = GlslGenerator.samplePlan(graph);
        var layout = new UniformLayout(graph.parameters(), samplers);
        return new ShaderGraphResult(buildPipeline(fragmentId, vertexId, samplers.size()), layout);
    }

    /**
     * 预编译到 GPU 管线缓存；仅在有 GpuDevice 的环境（游戏/桌面编辑器）调用。
     */
    public CompiledRenderPipeline precompile(GpuDevice device, ShaderGraphResult result) {
        return device.precompilePipeline(result.pipeline(), shaderSource);
    }

    private static RenderPipeline buildPipeline(Identifier fragment, Identifier vertex, int samplerCount) {
        var builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(fragment.getNamespace(), "pipeline/" + fragment.getPath()))
                .withVertexShader(vertex)
                .withFragmentShader(fragment)
                .withBindGroupLayout(BindGroupLayout.builder()
                        .withUniform(GlslGenerator.UNIFORM_BLOCK_NAME, UniformType.UNIFORM_BUFFER)
                        .build());
        if (samplerCount > 0) {
            var samplerLayout = BindGroupLayout.builder();
            for (var i = 0; i < samplerCount; i++) {
                samplerLayout.withSampler(SamplerBinding.uniformName(i));
            }
            builder.withBindGroupLayout(samplerLayout.build());
        }
        return builder
                .withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .build();
    }
}
