package org.academy.api.client.render.vfxgraph.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import net.minecraft.resources.Identifier;
import org.academy.api.client.compatibility.IrisIntegration;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.resources.R;
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

/**
 * 自持 VFX 粒子渲染器（M13-07）：数据驱动，按 {@link RenderSpec} 动态构建/缓存管线。
 * <ul>
 *   <li>{@link RenderSpec.Geometry#QUAD}：面向相机软边粒子四边形（含旋转/速度拉伸）。</li>
 *   <li>{@link RenderSpec.Geometry#MESH}：实例化单位立方体（旋转绕 Y 轴）。</li>
 *   <li>{@link RenderSpec.Geometry#LINE}：每粒子 trail 折线（LINES）。</li>
 *   <li>{@link RenderSpec.Geometry#RIBBON}：trail 四边形条带（QUADS，垂直相机偏移）。</li>
 * </ul>
 * 片元着色器与混合模式来自图数据（输出节点 {@code shader}/{@code blend} 属性），不在此枚举；
 * 几何（quad/mesh/line/ribbon）是结构性的，决定顶点着色器/顶点缓冲/图元。
 * 一次渲染可带**多个输出规格**（M21n）：逐 spec 按自身 {@code layer} 过滤粒子绘制（多输出分层，无 smoke 概念）；
 * {@code bloomPass} 只画 GLOW 规格（translucent 层不参与 bloom）。
 * 仅使用 Blaze3D 抽象层，不依赖现有 VFX 数据/管理器/管线。
 */
public final class VfxGraphRenderer {
    private static final int INSTANCE_STRIDE = (3 + 3 + 1 + 4 + 1 + 1 + 1) * 4;
    private static final int CAMERA_UBO_SIZE = 2 * 64;
    private static final int INITIAL_INSTANCES = 4096;
    private static final int INITIAL_VERTICES = 16384;
    private static final int NOISE_SIZE = 256;
    /** 旧 vfx 电弧管环分辨率（同 LightningRenderer/ArcTube 的 SEGMENT_RESOLUTION）。 */
    private static final int ARC_SEGMENT_RESOLUTION = 4;
    private static final int ARC_LIGHTNING_UBO_SIZE = 16;

    /** trail 顶点格式（M16-02：缓存避免每帧重建 VertexFormat）。 */
    private static final VertexFormat TRAIL_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA32_FLOAT)
            .build();
    /** 实例化顶点格式（billboard/mesh 共用；M21 增 InstanceSeed/InstanceAge）。 */
    private static final VertexFormat INSTANCE_FORMAT = VertexFormat.builder(1)
            .addAttribute("InstancePos", GpuFormat.RGB32_FLOAT)
            .addAttribute("InstanceVel", GpuFormat.RGB32_FLOAT)
            .addAttribute("InstanceSize", GpuFormat.R32_FLOAT)
            .addAttribute("InstanceColor", GpuFormat.RGBA32_FLOAT)
            .addAttribute("InstanceRot", GpuFormat.R32_FLOAT)
            .addAttribute("InstanceSeed", GpuFormat.R32_FLOAT)
            .addAttribute("InstanceAge", GpuFormat.R32_FLOAT)
            .build();
    private static final VertexFormat POSITION_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .build();
    private static final VertexFormat SIMPLE_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA32_FLOAT)
            .build();
    private static final BindGroupLayout CAMERA_BIND_GROUP = BindGroupLayout.builder()
            .withUniform("GraphCamera", UniformType.UNIFORM_BUFFER)
            .build();
    /** 旧 vfx 电弧管绑定组：GraphCamera + ArcLightning（基色/发射/参数，复刻 LightningRenderer）。 */
    private static final BindGroupLayout ARC_BIND_GROUP = BindGroupLayout.builder()
            .withUniform("GraphCamera", UniformType.UNIFORM_BUFFER)
            .withUniform("ArcLightning", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout NOISE_BIND_GROUP = BindGroupLayout.builder()
            .withUniform("GraphCamera", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .build();
    /** 编辑器预览清屏：不透明深色，便于确认视口在渲染（运行时 clear=false 不受影响）。 */
    private static final Vector4f CLEAR_COLOR = new Vector4f(0.07f, 0.08f, 0.1f, 1f);
    /** 复用的世界变换 scratch（M16-02：避免每粒子分配 float[3]）。 */
    private final float[] worldScratch = new float[3];

    /**
     * 视口表面网格（M29b-03）：编辑器预览中的 plane/sphere 三角面（Blender 场景复刻）。
     *
     * <p>{@code triangles} 为世界坐标三角面数组（xyz*3/三角形，已含 origin 位移），
     * {@code r/g/b/a} 为半透明材质色。仅在编辑器预览中传入（VfxPreview），运行时传空列表。</p>
     */
    public record SurfaceMesh(float[] triangles, float r, float g, float b, float a) {
    }

    /** 按 RenderSpec 缓存的管线（数据驱动，M21l）：着色器来自图数据，渲染器零着色器引用。 */
    private final java.util.concurrent.ConcurrentHashMap<RenderSpec, RenderPipeline> pipelines = new java.util.concurrent.ConcurrentHashMap<>();
    private final GpuBuffer quadBuffer;
    private final GpuBuffer cubeBuffer;
    private final GpuBuffer cameraUbo;
    private final GpuTexture noiseTexture;
    private final GpuTextureView noiseView;
    private final GpuSampler noiseSampler;
    private final SceneDepth sceneDepth = new SceneDepth();
    private final GpuTexture farTexture;
    private final GpuTextureView farView;
    private final GpuSampler depthSampler;
    private GpuBuffer instanceBuffer;
    private ByteBuffer instanceData;
    private int instanceCapacity;
    private GpuBuffer lineBuffer;
    private ByteBuffer lineData;
    private int lineCapacity;
    private GpuBuffer arcLightningUbo;
    // --- M22-Rev2 Blender 式电弧渲染 ---
    /** 电弧管顶点格式（Position+Normal+UV+Color）：匹配 vfxgraph_arc 着色器。 */
    private static final VertexFormat ARC_TUBE_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Normal", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA32_FLOAT)
            .build();
    /** 电弧管管线缓存。 */
    private final java.util.concurrent.ConcurrentHashMap<String, RenderPipeline> arcTubePipelines = new java.util.concurrent.ConcurrentHashMap<>();
    /** 表面网格管线（M29b-03）：半透明平面/球三角面，编辑器预览场景。 */
    private RenderPipeline surfacePipeline;
    private GpuBuffer surfaceBuffer;
    private ByteBuffer surfaceData;
    private int surfaceCapacity;
    /** 电弧管顶点/索引缓冲（growable）。 */
    private GpuBuffer arcTubeVertexBuffer;
    private GpuBuffer arcTubeIndexBuffer;
    private int arcTubeVertexCapacity;
    private int arcTubeIndexCapacity;
    /** Blender 式电弧缓冲（由调用方在 render 前设置）。 */
    private org.academy.api.client.render.vfxgraph.arc.ArcBuffer arcBuffer;

    public VfxGraphRenderer() {
        var device = RenderSystem.getDevice();

        quadBuffer = device.createBuffer(() -> "VfxGraph Quad", GpuBuffer.USAGE_VERTEX, buildQuad());
        cubeBuffer = device.createBuffer(() -> "VfxGraph Cube", GpuBuffer.USAGE_VERTEX, buildCube());
        cameraUbo = device.createBuffer(
                () -> "VfxGraph Camera", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CAMERA_UBO_SIZE);
        noiseTexture = device.createTexture(
                () -> "VfxGraph Noise", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM, NOISE_SIZE, NOISE_SIZE, 1, 1);
        noiseView = device.createTextureView(noiseTexture);
        noiseSampler = device.createSampler(
                AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
        var noiseBytes = buildNoiseTile(NOISE_SIZE);
        device.createCommandEncoder().writeToTexture(noiseTexture, noiseBytes, 0, 0, 0, 0, NOISE_SIZE, NOISE_SIZE);
        // soft particles 兜底：1x1 远平面（反向 Z 远=0）→ 无深度时火焰/烟始终可见
        farTexture = device.createTexture(
                () -> "VfxGraph FarDepth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.R32_FLOAT, 1, 1, 1, 1);
        farView = device.createTextureView(farTexture);
        var farBytes = BufferUtils.createByteBuffer(4);
        farBytes.putFloat(0f).flip();
        device.createCommandEncoder().writeToTexture(farTexture, farBytes, 0, 0, 0, 0, 1, 1);
        depthSampler = device.createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
        instanceBuffer = device.createBuffer(
                () -> "VfxGraph Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * INITIAL_INSTANCES);
        instanceCapacity = INITIAL_INSTANCES;
        lineBuffer = device.createBuffer(
                () -> "VfxGraph Line",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) SIMPLE_FORMAT.getVertexSize() * INITIAL_VERTICES);
        lineCapacity = INITIAL_VERTICES;
        // 电弧：发射 UBO
        arcLightningUbo = device.createBuffer(
                () -> "VfxGraph Arc Lightning", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, ARC_LIGHTNING_UBO_SIZE);
        writeArcLightning(device, RenderSpec.ArcRender.DEFAULT.emission());
        // 电弧管缓冲
        arcTubeVertexBuffer = device.createBuffer(
                () -> "VfxGraph Arc Tube Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) ARC_TUBE_FORMAT.getVertexSize() * 512);
        arcTubeVertexCapacity = 512;
        arcTubeIndexBuffer = device.createBuffer(
                () -> "VfxGraph Arc Tube Indices",
                GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                1024L * 4);
        arcTubeIndexCapacity = 1024;
        // 表面网格缓冲（M29b-03，编辑器预览 scene）
        surfaceBuffer = device.createBuffer(
                () -> "VfxGraph Surface",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) SIMPLE_FORMAT.getVertexSize() * 1024);
        surfaceCapacity = 1024;
    }

    /**
     * 按 RenderSpec 构建/取用管线（数据驱动，M21l）。顶点/片元着色器与混合全部来自 spec（图数据），
     * 渲染器不引用任何具体着色器 id；几何决定顶点缓冲/图元（结构性）。
     */
    private RenderPipeline pipelineFor(RenderSpec spec) {
        return pipelines.computeIfAbsent(spec, s -> {
            // QUAD 采样噪声/深度（Sampler0/Sampler1）；ARC 走旧式管绑定组（GraphCamera + ArcLightning）
            var bindGroup = switch (s.geometry()) {
                case QUAD -> NOISE_BIND_GROUP;
                case ARC -> ARC_BIND_GROUP;
                default -> CAMERA_BIND_GROUP;
            };
            var blend = s.blend() == RenderSpec.Blend.ADDITIVE || s.blend() == RenderSpec.Blend.GLOW
                    ? BlendFunction.ADDITIVE
                    : BlendFunction.TRANSLUCENT;
            var vertexFormat = switch (s.geometry()) {
                case LINE, RIBBON -> SIMPLE_FORMAT;
                case ARC -> ARC_TUBE_FORMAT;
                default -> POSITION_FORMAT;
            };
            var instanceFormat = s.geometry() == RenderSpec.Geometry.LINE || s.geometry() == RenderSpec.Geometry.RIBBON
                    || s.geometry() == RenderSpec.Geometry.ARC
                    ? null
                    : INSTANCE_FORMAT;
            // ARC 管为 TRIANGLES（ring 网格）；其余 QUADS
            var topology = s.geometry() == RenderSpec.Geometry.ARC
                    ? PrimitiveTopology.TRIANGLES
                    : PrimitiveTopology.QUADS;
            // pipeline location 需是合法 Identifier（[a-z0-9/._-]）：用安全字段拼名，不用 record toString
            var locationName = "vfx_graph_" + s.geometry().name().toLowerCase(java.util.Locale.ROOT)
                    + "_" + s.blend().name().toLowerCase(java.util.Locale.ROOT)
                    + "_" + s.vertexShader().getPath() + "_" + s.fragmentShader().getPath();
            var pipeline = buildPipeline(locationName, s.vertexShader(), s.fragmentShader(),
                    bindGroup, vertexFormat, instanceFormat, blend, topology);
            RenderSystem.getDevice().precompilePipeline(pipeline);
            return pipeline;
        });
    }

    /** 电弧管管线：透明主 pass / additive bloom pass。 */
    private RenderPipeline arcTubePipeline(RenderSpec spec, boolean bloomPass) {
        var key = (bloomPass ? "bloom_" : "main_")
                + spec.vertexShader() + "_" + spec.fragmentShader();
        return arcTubePipelines.computeIfAbsent(key, k -> {
            var shaderSuffix = (spec.vertexShader().getPath() + "_" + spec.fragmentShader().getPath())
                    .replace('/', '_');
            var locationName = "vfx_graph_arc_tube_" + (bloomPass ? "bloom_" : "main_") + shaderSuffix;
            var pipeline = buildPipeline(locationName, spec.vertexShader(), spec.fragmentShader(),
                    ARC_BIND_GROUP, ARC_TUBE_FORMAT, null,
                    bloomPass ? BlendFunction.ADDITIVE : BlendFunction.TRANSLUCENT,
                    PrimitiveTopology.TRIANGLES);
            RenderSystem.getDevice().precompilePipeline(pipeline);
            return pipeline;
        });
    }

    /** 表面网格管线（M29b-03）：SIMPLE_FORMAT（Position+Color）+ CAMERA_BIND_GROUP + TRIANGLES，半透明。 */
    private RenderPipeline surfacePipeline() {
        if (surfacePipeline == null) {
            surfacePipeline = buildPipeline("vfx_graph_surface",
                    R.shaders.core.vfxgraph_surface, R.shaders.core.vfxgraph_surface,
                    CAMERA_BIND_GROUP, SIMPLE_FORMAT, null,
                    BlendFunction.TRANSLUCENT, PrimitiveTopology.TRIANGLES);
            RenderSystem.getDevice().precompilePipeline(surfacePipeline);
        }
        return surfacePipeline;
    }

    /** 写入旧式电弧渲染参数 UBO：仅渲染标量（aces 开关、发射增强），**无任何颜色常量**——电弧颜色全由图数据顶点色驱动。 */
    private void writeArcLightning(GpuDevice device, float emission) {
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, ARC_LIGHTNING_UBO_SIZE);
            builder.putVec4(new Vector4f(0f, 0f, emission, 0f)); // LightningParams(aces=0, unused, 发射增强 emission)
            device.createCommandEncoder().writeToBuffer(arcLightningUbo.slice(), builder.get());
        }
    }

    private static RenderPipeline buildPipeline(
            String name, Identifier vs, Identifier fs,
            BindGroupLayout bindGroup, VertexFormat vertexFormat, VertexFormat instanceFormat,
            BlendFunction blend, PrimitiveTopology topology
    ) {
        var builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("academy", "pipeline/" + name))
                .withVertexShader(vs)
                .withFragmentShader(fs)
                .withBindGroupLayout(bindGroup)
                .withCull(false)
                // 深度测试：与 Minecraft 主渲染一致的反向 Z（近=1.0/远=0.0，GEQUAL 通过），
                // 粒子与场景正确遮挡（不写深度——半透明粒子彼此不遮挡）
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withColorTargetState(new ColorTargetState(blend))
                .withPrimitiveTopology(topology)
                .withVertexBinding(0, vertexFormat);
        if (instanceFormat != null) {
            builder.withVertexBinding(1, instanceFormat);
        }
        return builder.build();
    }

    /** 设置 M22-Rev2 Blender 式电弧缓冲（render 前调用；null = 不渲染 arc2）。 */
    public void setArcBuffer(org.academy.api.client.render.vfxgraph.arc.ArcBuffer buffer) {
        this.arcBuffer = buffer;
    }

    public void render(GpuTextureView target, @Nullable GpuTextureView depth, ParticleBuffer buffer, GraphCamera camera, boolean clear) {
        render(target, depth, buffer, camera, clear, List.of(RenderSpec.DEFAULT), WorldTransform.identity(), false, List.of());
    }

    public void render(GpuTextureView target, @Nullable GpuTextureView depth, ParticleBuffer buffer, GraphCamera camera, boolean clear, RenderSpec spec) {
        render(target, depth, buffer, camera, clear, List.of(spec), WorldTransform.identity(), false, List.of());
    }

    public void render(GpuTextureView target, @Nullable GpuTextureView depth, ParticleBuffer buffer, GraphCamera camera, boolean clear,
                       List<RenderSpec> specs) {
        render(target, depth, buffer, camera, clear, specs, WorldTransform.identity(), false, List.of());
    }

    public void render(GpuTextureView target, @Nullable GpuTextureView depth, ParticleBuffer buffer, GraphCamera camera, boolean clear,
                       List<RenderSpec> specs, WorldTransform transform) {
        render(target, depth, buffer, camera, clear, specs, transform, false, List.of());
    }

    public void render(GpuTextureView target, @Nullable GpuTextureView depth, ParticleBuffer buffer,
                       GraphCamera camera, boolean clear, List<RenderSpec> specs, WorldTransform transform, boolean bloomPass) {
        render(target, depth, buffer, camera, clear, specs, transform, bloomPass, List.of());
    }

    /**
     * 数据驱动多输出渲染：对 {@code specs} 逐规格绘制，ARC 规格用 drawArcTubes 渲染；
     * {@code surfaces} 为编辑器预览场景表面网格（plane/sphere，M29b-03），先于粒子/电弧绘制。
     */
    public void render(GpuTextureView target, @Nullable GpuTextureView depth, ParticleBuffer buffer,
                       GraphCamera camera, boolean clear, List<RenderSpec> specs, WorldTransform transform, boolean bloomPass,
                       List<SurfaceMesh> surfaces) {
        var device = RenderSystem.getDevice();
        var count = buffer.count();
        boolean hasSurfaces = surfaces != null && !surfaces.isEmpty();

        if (count == 0 && (arcBuffer == null || arcBuffer.count() == 0) && !hasSurfaces) {
            if (clear) clearTarget(device, target, depth);
            return;
        }

        writeCamera(device, camera);
        var encoder = device.createCommandEncoder();
        // soft particles（仅 quad 系）：先把深度附件拷到可采样纹理（must be outside render pass）。
        // 清屏时先清深度到远平面 0.0（反向 Z），再拷贝，保证编辑器视口采样到"无遮挡"。
        // Iris shader pack 下主目标深度不是场景深度（世界深度在 Iris 内部 gbuffer），
        // 采样它会使 depthDiff<0 → 粒子被 discard/alpha 灭掉（不可见），改用 farView（0.0）兜底。
        boolean preClearedDepth = false;
        // soft particles（仅 quad 系）：任一输出规格为 quad（billboard）才拷深度；Iris shader pack 下退回 farView。
        boolean anyBillboard = specs.stream().anyMatch(s -> s.geometry() == RenderSpec.Geometry.QUAD);
        boolean useSceneDepth = anyBillboard && sceneDepthUsable(IrisIntegration.isShaderPackInUse(), RenderSpec.Geometry.QUAD);
        if (useSceneDepth && depth != null) {
            if (clear) {
                encoder.clearDepthTexture(depth.texture(), 0.0);
                preClearedDepth = true;
            }
            sceneDepth.copyFrom(depth);
        }
        var clearDepth = depth != null && clear && !preClearedDepth ? OptionalDouble.of(0.0) : OptionalDouble.empty();
        try (var renderPass = encoder.createRenderPass(
                () -> "VfxGraph " + specs, target,
                clear ? Optional.of(CLEAR_COLOR) : Optional.empty(),
                depth, clearDepth
        )) {
            // 编辑器预览场景表面网格（先画，深度 GEQUAL，半透明材质色）
            if (hasSurfaces) {
                drawSurfaces(renderPass, surfaces, camera);
            }
            // 电弧无 layer 语义：整批电弧只画一次（首个 ARC 规格），避免多 ARC 输出重复叠加过曝。
            // bloom 输入（bloomPass=true）只画 GLOW 规格，同样只画一次。
            boolean arcsDrawn = false;
            for (var spec : specs) {
                if (bloomPass && !spec.feedsBloom()) continue;
                switch (spec.geometry()) {
                    case MESH -> drawInstanced(renderPass, buffer, camera, cubeBuffer, transform, spec);
                    case LINE -> drawTrail(renderPass, buffer, camera, spec, PrimitiveTopology.LINES, transform);
                    case RIBBON -> drawTrail(renderPass, buffer, camera, spec, PrimitiveTopology.QUADS, transform);
                    case ARC -> {
                        if (!arcsDrawn && arcBuffer != null && arcBuffer.count() > 0) {
                            drawArcTubes(renderPass, arcBuffer, camera, transform, bloomPass, spec);
                            arcsDrawn = true;
                        }
                    }
                    default -> drawInstanced(renderPass, buffer, camera, quadBuffer, transform, spec);
                }
            }
        }
    }

    // --- M22-Rev2 Blender 式电弧渲染 ---

    /**
     * M30 age 亮度闪烁（复刻 Blender 材质 Emission）：
     * 表面弧 {@code Light = FloatCurve.004(age/寿命)×亮度 + 0.33×亮度}（先亮后灭）；
     * 接触弧 {@code TLight = FloatCurve.009(生命系数)}（直接，无 ×6）；自由弧无闪烁返回 1。
     * 返回 {rgb 乘数, alpha 乘数}，烘焙进管顶点色（UBO emission 仍由图数据 {@code output_arc} 驱动）。
     */
    private static float[] arcLight(org.academy.api.client.render.vfxgraph.arc.ArcCurve arc) {
        float lifetime = Math.max(1e-3f, arc.lifetime());
        float ageFrac = Math.max(0f, Math.min(1f, arc.age() / lifetime));
        // 粒子火花（Blender PLight = FloatCurve.003(生命系数)×粒子亮度 ×6）：随生命衰减熄灭
        if (arc.sparkVelocity() != null) {
            float f = org.academy.api.client.render.vfxgraph.arc.BlenderArcCurves.sample(
                    org.academy.api.client.render.vfxgraph.arc.BlenderArcCurves.PARTICLE_LIFE, ageFrac);
            return new float[]{f, 1f};
        }
        if (arc.flatRadius()) {
            float f = org.academy.api.client.render.vfxgraph.arc.BlenderArcCurves.sample(
                    org.academy.api.client.render.vfxgraph.arc.BlenderArcCurves.CONTACT_RADIUS_AGE, ageFrac);
            return new float[]{f, 1f};
        }
        if (arc.hasArchBase()) {
            float f = org.academy.api.client.render.vfxgraph.arc.BlenderArcCurves.sample(
                    org.academy.api.client.render.vfxgraph.arc.BlenderArcCurves.LIGHT, ageFrac) + 0.33f;
            return new float[]{f, 1f};
        }
        return new float[]{1f, 1f};
    }

    /**
     * M22-Rev2 电弧渲染入口：遍历 ArcBuffer，对每条弧线构建管网格并绘制。
     * Blender 对应：Curve to Mesh(Circle) → Set Material → 输出到 Join Geometry。
     */
    public void drawArcTubes(
            com.mojang.blaze3d.systems.RenderPass pass,
            org.academy.api.client.render.vfxgraph.arc.ArcBuffer arcBuffer,
            GraphCamera camera, WorldTransform transform, boolean bloomPass,
            RenderSpec spec
    ) {
        if (arcBuffer == null || arcBuffer.count() == 0) return;

        var device = RenderSystem.getDevice();
        var arcRender = spec.arc();

        // 收集所有弧线的管网格数据
        int totalVerts = 0;
        int totalIndices = 0;
        var meshDataList = new java.util.ArrayList<org.academy.api.client.render.vfxgraph.arc.CurveToMeshBuilder.MeshData>();

        for (int a = 0; a < arcBuffer.count(); a++) {
            var arc = arcBuffer.arc(a);
            int segRes = Math.max(3, Math.min(16, arcRender.segments()));
            // M30 age 亮度闪烁（Blender 材质 Emission）：表面弧 Light = FloatCurve.004(age)×亮度+0.33×亮度；
            // 接触弧 TLight = FloatCurve.009(生命系数)。烘焙进顶点色（UBO emission 保持图数据驱动）。
            float[] light = arcLight(arc);
            var meshData = org.academy.api.client.render.vfxgraph.arc.CurveToMeshBuilder.build(
                    arc, segRes,
                    arc.r() * light[0], arc.g() * light[0], arc.b() * light[0], arc.a() * light[1],
                    arcRender.branchBrightnessScale());
            if (meshData.vertexCount() > 0) {
                meshDataList.add(meshData);
                totalVerts += meshData.vertexCount();
                totalIndices += meshData.indexCount();
            }
        }

        if (totalVerts == 0) return;

        // 确保缓冲区足够大
        long vertexBytes = (long) totalVerts * ARC_TUBE_FORMAT.getVertexSize();
        long indexBytes = (long) totalIndices * 4;
        if (vertexBytes > arcTubeVertexBuffer.size()) {
            growArc2TubeBuffer(totalVerts);
        }
        if (indexBytes > arcTubeIndexBuffer.size()) {
            growArc2TubeIndexBuffer(totalIndices);
        }

        // 合并所有网格数据到单个顶点/索引缓冲
        var vertexData = BufferUtils.createByteBuffer(Math.toIntExact(vertexBytes));
        var indexData = BufferUtils.createByteBuffer(Math.toIntExact(indexBytes));
        int vertexOffset = 0;
        int indexOffset = 0;
        for (var meshData : meshDataList) {
            // 顶点数据：直接拷贝（已经是 Position+Normal+UV+Color 格式）
            var srcVert = meshData.vertexBuffer().duplicate();
            vertexData.put(srcVert);
            // 索引数据：偏移后拷贝
            var srcIndices = meshData.indices();
            for (int i = 0; i < srcIndices.length; i++) {
                indexData.putInt(srcIndices[i] + vertexOffset);
            }
            vertexOffset += meshData.vertexCount();
            indexOffset += meshData.indexCount();
        }
        vertexData.flip();
        indexData.flip();

        // 顶点烘焙世界变换（旋转/缩放/平移）并转为相机相对坐标（视图为纯旋转矩阵，平移必须写进顶点）；
        // overall_scale 为图数据驱动（output_arc 块属性）的整体缩放。与粒子/轨迹路径一致。
        transformArcTubeVertices(vertexData, totalVerts, camera.position(), transform, arcRender.overallScale());

        // 上传 GPU
        var writeEncoder = device.createCommandEncoder();
        writeEncoder.writeToBuffer(arcTubeVertexBuffer.slice(0, vertexBytes), vertexData);
        writeEncoder.writeToBuffer(arcTubeIndexBuffer.slice(0, indexBytes), indexData);

        // 写入 UBO
        writeArcLightning(device, arcRender.emission());

        // 绘制
        var pipeline = arcTubePipeline(spec, bloomPass);
        pass.setPipeline(pipeline);
        pass.setUniform("GraphCamera", cameraUbo.slice());
        pass.setUniform("ArcLightning", arcLightningUbo.slice());
        pass.setVertexBuffer(0, arcTubeVertexBuffer.slice(0, vertexBytes));
        // 同一 RenderPass 中较早的 billboard/mesh 输出会在槽 1 留下实例缓冲；
        // ARC 管线只有槽 0，必须显式解绑，否则 OpenGL VAO 缓存会用 null VertexFormat 解读槽 1。
        pass.setVertexBuffer(1, null);
        pass.setIndexBuffer(arcTubeIndexBuffer, IndexType.INT);
        pass.drawIndexed(totalIndices, 1, 0, 0, 0);
    }

    /**
     * 电弧管顶点烘焙（包内静态，可单测）：overall_scale（图数据整体缩放）→ WorldTransform（局部→世界）→
     * 相机相对坐标（视图为纯旋转矩阵，平移必须写进顶点）。法线仅旋转（均匀缩放在归一化后无影响）。
     *
     * <p>顶点布局与 {@link CurveToMeshBuilder} 一致：Position(3) + Normal(3) + UV(2) + Color(4)。</p>
     */
    static void transformArcTubeVertices(java.nio.ByteBuffer vertexData, int vertexCount,
                                         Vector3f camPos, WorldTransform transform, float overallScale) {
        boolean identity = transform.isIdentity();
        float[] s = new float[3];
        for (int v = 0; v < vertexCount; v++) {
            int base = v * org.academy.api.client.render.vfxgraph.arc.CurveToMeshBuilder.FLOATS_PER_VERTEX * 4;
            float x = vertexData.getFloat(base);
            float y = vertexData.getFloat(base + 4);
            float z = vertexData.getFloat(base + 8);
            if (overallScale != 1f) {
                x *= overallScale;
                y *= overallScale;
                z *= overallScale;
            }
            if (!identity) {
                transform.apply(x, y, z, s);
                x = s[0];
                y = s[1];
                z = s[2];
            }
            vertexData.putFloat(base, x - camPos.x);
            vertexData.putFloat(base + 4, y - camPos.y);
            vertexData.putFloat(base + 8, z - camPos.z);
            if (!identity) {
                float nx = vertexData.getFloat(base + 12);
                float ny = vertexData.getFloat(base + 16);
                float nz = vertexData.getFloat(base + 20);
                transform.applyDirection(nx, ny, nz, s);
                float len = (float) Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
                if (len > 1e-6f) {
                    vertexData.putFloat(base + 12, s[0] / len);
                    vertexData.putFloat(base + 16, s[1] / len);
                    vertexData.putFloat(base + 20, s[2] / len);
                }
            }
        }
    }

    /** 新式电弧管顶点缓冲扩容。 */
    private void growArc2TubeBuffer(int requiredVertices) {
        var newCapacity = Math.max(requiredVertices, arcTubeVertexCapacity * 2);
        var old = arcTubeVertexBuffer;
        arcTubeVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VfxGraph Arc Tube Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) ARC_TUBE_FORMAT.getVertexSize() * newCapacity);
        arcTubeVertexCapacity = newCapacity;
        old.close();
    }

    /** 新式电弧管索引缓冲扩容。 */
    private void growArc2TubeIndexBuffer(int requiredIndices) {
        var newCapacity = Math.max(requiredIndices, arcTubeIndexCapacity * 2);
        var old = arcTubeIndexBuffer;
        arcTubeIndexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VfxGraph Arc Tube Indices",
                GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                (long) newCapacity * 4);
        arcTubeIndexCapacity = newCapacity;
        old.close();
    }

    private void drawInstanced(
            com.mojang.blaze3d.systems.RenderPass pass,
            ParticleBuffer buffer, GraphCamera camera, GpuBuffer shapeBuffer, WorldTransform transform,
            RenderSpec spec
    ) {
        var count = buffer.count();
        // 只写该 spec 负责的层（layer 过滤，数据驱动）：分层外观由图上多输出节点表达，无 fire/smoke 硬编码。
        int matched = 0;
        for (int i = 0; i < count; i++) {
            if (spec.matchesLayer(buffer.layer(i))) matched++;
        }
        if (matched == 0) return;

        var bytes = (long) INSTANCE_STRIDE * matched;
        if (bytes > instanceBuffer.size()) {
            growInstances(matched);
        }
        if (instanceData == null || instanceData.capacity() < bytes) {
            instanceData = BufferUtils.createByteBuffer(Math.toIntExact(bytes));
        }
        var camPos = camera.position();
        var identity = transform.isIdentity();
        instanceData.clear();
        for (int i = 0; i < count; i++) {
            if (!spec.matchesLayer(buffer.layer(i))) continue;
            writeInstance(buffer, i, camPos, identity, transform, instanceData);
        }
        instanceData.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(instanceBuffer.slice(0, bytes), instanceData);

        var depthView = sceneDepthUsable(IrisIntegration.isShaderPackInUse(), spec.geometry())
                && sceneDepth.view() != null ? sceneDepth.view() : farView;
        boolean billboard = spec.geometry() == RenderSpec.Geometry.QUAD;
        drawInstancedPass(pass, pipelineFor(spec), shapeBuffer, cameraUbo.slice(),
                instanceBuffer.slice(0, bytes), matched, billboard, depthView);
    }

    private void drawInstancedPass(
            com.mojang.blaze3d.systems.RenderPass pass, RenderPipeline pipeline, GpuBuffer shapeBuffer,
            GpuBufferSlice cameraSlice, GpuBufferSlice instanceSlice, int count,
            boolean billboard, GpuTextureView depthView
    ) {
        pass.setPipeline(pipeline);
        pass.setUniform("GraphCamera", cameraSlice);
        if (billboard) {
            pass.bindTexture("Sampler0", noiseView, noiseSampler);
            pass.bindTexture("Sampler1", depthView, depthSampler);
        }
        pass.setVertexBuffer(0, shapeBuffer.slice());
        pass.setVertexBuffer(1, instanceSlice);
        var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        pass.setIndexBuffer(sequential.getBuffer(6), sequential.type());
        pass.drawIndexed(6, count, 0, 0, 0);
    }

    private void writeInstance(ParticleBuffer buffer, int i, Vector3f camPos, boolean identity, WorldTransform transform, ByteBuffer out) {
        float px = buffer.positionX(i);
        float py = buffer.positionY(i);
        float pz = buffer.positionZ(i);
        if (!identity) {
            transform.apply(px, py, pz, worldScratch);
            px = worldScratch[0];
            py = worldScratch[1];
            pz = worldScratch[2];
        }
        out.putFloat(px - camPos.x);
        out.putFloat(py - camPos.y);
        out.putFloat(pz - camPos.z);
        float vx = buffer.velocityX(i);
        float vy = buffer.velocityY(i);
        float vz = buffer.velocityZ(i);
        if (!identity) {
            transform.applyDirection(vx, vy, vz, worldScratch);
            vx = worldScratch[0];
            vy = worldScratch[1];
            vz = worldScratch[2];
        }
        out.putFloat(vx);
        out.putFloat(vy);
        out.putFloat(vz);
        out.putFloat(buffer.size(i));
        out.putFloat(buffer.colorR(i));
        out.putFloat(buffer.colorG(i));
        out.putFloat(buffer.colorB(i));
        out.putFloat(buffer.alpha(i));
        out.putFloat(buffer.rotation(i));
        out.putFloat(buffer.seed(i));
        float life = buffer.lifetime(i);
        out.putFloat(life > 0f ? Math.min(1f, buffer.age(i) / life) : 0f);
    }

    private void drawTrail(
            com.mojang.blaze3d.systems.RenderPass pass,
            ParticleBuffer buffer, GraphCamera camera, RenderSpec spec, PrimitiveTopology primitive,
            WorldTransform transform
    ) {
        boolean line = spec.geometry() == RenderSpec.Geometry.LINE;
        var vertexCount = line
                ? countLineVertices(buffer, spec)
                : countRibbonVertices(buffer, spec);
        if (vertexCount == 0) return;

        var neededBytes = (long) TRAIL_FORMAT.getVertexSize() * vertexCount;
        if (neededBytes > lineBuffer.size()) {
            growLine(vertexCount);
        }
        if (lineData == null || lineData.capacity() < neededBytes) {
            lineData = BufferUtils.createByteBuffer(Math.toIntExact(neededBytes));
        }
        lineData.clear();
        if (line) {
            buildLineVertices(buffer, camera, lineData, transform, spec);
        } else {
            buildRibbonVertices(buffer, camera, lineData, transform, spec);
        }
        lineData.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(lineBuffer.slice(0, neededBytes), lineData);

        pass.setPipeline(pipelineFor(spec));
        pass.setUniform("GraphCamera", cameraUbo.slice());
        pass.setVertexBuffer(0, lineBuffer.slice(0, neededBytes));
        // LINE/RIBBON 同样是单缓冲管线，清理前一个实例化输出留下的槽 1。
        pass.setVertexBuffer(1, null);
        var sequential = RenderSystem.getSequentialBuffer(primitive);
        var indices = sequential.getBuffer(vertexCount);
        pass.setIndexBuffer(indices, sequential.type());
        pass.drawIndexed(vertexCount, 1, 0, 0, 0);
    }

    private static int countLineVertices(ParticleBuffer buffer, RenderSpec spec) {
        int total = 0;
        for (int i = 0; i < buffer.count(); i++) {
            if (!spec.matchesLayer(buffer.layer(i))) continue;
            int size = buffer.trailSize(i);
            if (size >= 2) total += (size - 1) * 2;
        }
        return total;
    }

    private static int countRibbonVertices(ParticleBuffer buffer, RenderSpec spec) {
        int total = 0;
        for (int i = 0; i < buffer.count(); i++) {
            if (!spec.matchesLayer(buffer.layer(i))) continue;
            int size = buffer.trailSize(i);
            if (size >= 2) total += (size - 1) * 4;
        }
        return total;
    }

    private void buildLineVertices(ParticleBuffer buffer, GraphCamera camera, ByteBuffer out,
                                   WorldTransform transform, RenderSpec spec) {
        var camPos = camera.position();
        var identity = transform.isIdentity();
        for (int i = 0; i < buffer.count(); i++) {
            if (!spec.matchesLayer(buffer.layer(i))) continue;
            int size = buffer.trailSize(i);
            for (int k = 0; k < size - 1; k++) {
                float ax = buffer.trailX(i, k);
                float ay = buffer.trailY(i, k);
                float az = buffer.trailZ(i, k);
                float bx = buffer.trailX(i, k + 1);
                float by = buffer.trailY(i, k + 1);
                float bz = buffer.trailZ(i, k + 1);
                if (!identity) {
                    transform.apply(ax, ay, az, worldScratch);
                    ax = worldScratch[0];
                    ay = worldScratch[1];
                    az = worldScratch[2];
                    transform.apply(bx, by, bz, worldScratch);
                    bx = worldScratch[0];
                    by = worldScratch[1];
                    bz = worldScratch[2];
                }
                putVertex(out, ax - camPos.x, ay - camPos.y, az - camPos.z,
                        buffer.colorR(i), buffer.colorG(i), buffer.colorB(i), buffer.alpha(i));
                putVertex(out, bx - camPos.x, by - camPos.y, bz - camPos.z,
                        buffer.colorR(i), buffer.colorG(i), buffer.colorB(i), buffer.alpha(i));
            }
        }
    }

    private void buildRibbonVertices(ParticleBuffer buffer, GraphCamera camera, ByteBuffer out,
                                     WorldTransform transform, RenderSpec spec) {
        var camPos = camera.position();
        var identity = transform.isIdentity();
        var view = camera.viewRotation();
        float rx = view.m00();
        float ry = view.m01();
        float rz = view.m02();
        for (int i = 0; i < buffer.count(); i++) {
            if (!spec.matchesLayer(buffer.layer(i))) continue;
            int size = buffer.trailSize(i);
            float half = buffer.size(i) * 0.5f;
            for (int k = 0; k < size - 1; k++) {
                float ax = buffer.trailX(i, k);
                float ay = buffer.trailY(i, k);
                float az = buffer.trailZ(i, k);
                float bx = buffer.trailX(i, k + 1);
                float by = buffer.trailY(i, k + 1);
                float bz = buffer.trailZ(i, k + 1);
                if (!identity) {
                    transform.apply(ax, ay, az, worldScratch);
                    ax = worldScratch[0];
                    ay = worldScratch[1];
                    az = worldScratch[2];
                    transform.apply(bx, by, bz, worldScratch);
                    bx = worldScratch[0];
                    by = worldScratch[1];
                    bz = worldScratch[2];
                }
                putVertex(out, ax - rx * half, ay - ry * half, az - rz * half, buffer.colorR(i), buffer.colorG(i), buffer.colorB(i), buffer.alpha(i));
                putVertex(out, ax + rx * half, ay + ry * half, az + rz * half, buffer.colorR(i), buffer.colorG(i), buffer.colorB(i), buffer.alpha(i));
                putVertex(out, bx + rx * half, by + ry * half, bz + rz * half, buffer.colorR(i), buffer.colorG(i), buffer.colorB(i), buffer.alpha(i));
                putVertex(out, bx - rx * half, by - ry * half, bz - rz * half, buffer.colorR(i), buffer.colorG(i), buffer.colorB(i), buffer.alpha(i));
            }
        }
    }

    private static void putVertex(ByteBuffer out, float x, float y, float z, float r, float g, float b, float a) {
        out.putFloat(x).putFloat(y).putFloat(z);
        out.putFloat(r).putFloat(g).putFloat(b).putFloat(a);
    }

    public void close() {
        quadBuffer.close();
        cubeBuffer.close();
        cameraUbo.close();
        noiseSampler.close();
        noiseView.close();
        noiseTexture.close();
        depthSampler.close();
        farView.close();
        farTexture.close();
        sceneDepth.close();
        instanceBuffer.close();
        lineBuffer.close();
        arcTubeVertexBuffer.close();
        arcTubeIndexBuffer.close();
        arcLightningUbo.close();
        surfaceBuffer.close();
        instanceData = null;
        lineData = null;
        surfaceData = null;
    }

    private void writeCamera(GpuDevice device, GraphCamera camera) {
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, CAMERA_UBO_SIZE);
            builder.putMat4f(camera.viewRotation());
            builder.putMat4f(camera.projection());
            device.createCommandEncoder().writeToBuffer(cameraUbo.slice(), builder.get());
        }
    }

    /** 场景深度（soft particles 采样）仅在无 Iris shader pack 的 billboard 系下可用：
     *  Iris shader pack 时主目标深度不是场景深度（世界深度在 Iris 内部 gbuffer），采样会令
     *  depthDiff<0 → 粒子被 discard/alpha 灭掉，退回 farView（0.0）保证可见。 */
    static boolean sceneDepthUsable(boolean shaderPackInUse, RenderSpec.Geometry geometry) {
        return geometry == RenderSpec.Geometry.QUAD && !shaderPackInUse;
    }

    private static void clearTarget(GpuDevice device, GpuTextureView target, @Nullable GpuTextureView depth) {
        var encoder = device.createCommandEncoder();
        try (var pass = encoder.createRenderPass(() -> "VfxGraph Clear", target, Optional.of(CLEAR_COLOR), depth, OptionalDouble.of(0.0))) {
            // 空 pass 仅清屏（反向 Z：深度清到远平面 0.0）
        }
    }

    private void growInstances(int requiredInstances) {
        var newCapacity = Math.max(requiredInstances, instanceCapacity * 2);
        var old = instanceBuffer;
        instanceBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VfxGraph Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_STRIDE * newCapacity);
        instanceCapacity = newCapacity;
        old.close();
    }

    private void growLine(int requiredVertices) {
        var newCapacity = Math.max(requiredVertices, lineCapacity * 2);
        var old = lineBuffer;
        lineBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VfxGraph Line",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) (3 + 4) * 4 * newCapacity);
        lineCapacity = newCapacity;
        old.close();
    }

    /**
     * 编辑器预览表面网格（M29b-03）：把 plane/sphere 三角面烘焙为相机相对坐标顶点
     * （视图纯旋转，平移写进顶点，同电弧/粒子），半透明材质色，TRIANGLES 绘制。
     */
    private void drawSurfaces(com.mojang.blaze3d.systems.RenderPass pass, List<SurfaceMesh> surfaces, GraphCamera camera) {
        int totalVerts = 0;
        for (var sm : surfaces) {
            totalVerts += sm.triangles().length / 3;
        }
        if (totalVerts == 0) return;

        var bytes = (long) SIMPLE_FORMAT.getVertexSize() * totalVerts;
        if (bytes > surfaceBuffer.size()) {
            growSurface(totalVerts);
        }
        if (surfaceData == null || surfaceData.capacity() < bytes) {
            surfaceData = BufferUtils.createByteBuffer(Math.toIntExact(bytes));
        }
        var camPos = camera.position();
        surfaceData.clear();
        for (var sm : surfaces) {
            float[] tris = sm.triangles();
            for (int i = 0; i + 2 < tris.length; i += 3) {
                putVertex(surfaceData, tris[i] - camPos.x, tris[i + 1] - camPos.y, tris[i + 2] - camPos.z,
                        sm.r(), sm.g(), sm.b(), sm.a());
            }
        }
        surfaceData.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(surfaceBuffer.slice(0, bytes), surfaceData);

        pass.setPipeline(surfacePipeline());
        pass.setUniform("GraphCamera", cameraUbo.slice());
        pass.setVertexBuffer(0, surfaceBuffer.slice(0, bytes));
        pass.setVertexBuffer(1, null);
        var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
        var indices = sequential.getBuffer(totalVerts);
        pass.setIndexBuffer(indices, sequential.type());
        pass.drawIndexed(totalVerts, 1, 0, 0, 0);
    }

    private void growSurface(int requiredVertices) {
        var newCapacity = Math.max(requiredVertices, surfaceCapacity * 2);
        var old = surfaceBuffer;
        surfaceBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VfxGraph Surface",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) SIMPLE_FORMAT.getVertexSize() * newCapacity);
        surfaceCapacity = newCapacity;
        old.close();
    }

    /**
     * 生成可平铺的 fBm value-noise 灰度瓦片（RGBA8，四通道同值），供火焰片元着色器采样
     * （被啃轮廓 / 参差边缘 / 摆动 / 闪烁）。
     */
    private static ByteBuffer buildNoiseTile(int size) {
        var rng = new Random(0xC0FFEEL);
        var grid = new float[size + 1][size + 1];
        for (var row : grid) {
            for (int x = 0; x <= size; x++) {
                row[x] = rng.nextFloat();
            }
        }
        var data = new float[size * size];
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float sum = 0f;
                float amp = 0.5f;
                // 3 octaves（freq 1/2/4）：去掉最细 octave（32px 特征），避免小尺度采样混叠成边缘锯齿
                for (int o = 0; o < 3; o++) {
                    float freq = (float) (1 << o);
                    sum += amp * valueNoise(x * freq, y * freq, size, grid);
                    amp *= 0.5f;
                }
                data[y * size + x] = sum;
                min = Math.min(min, sum);
                max = Math.max(max, sum);
            }
        }
        var out = BufferUtils.createByteBuffer(size * size * 4);
        float range = Math.max(max - min, 1e-6f);
        for (float value : data) {
            int b = (int) ((value - min) / range * 255f);
            byte channel = (byte) b;
            out.put(channel).put(channel).put(channel).put((byte) 255);
        }
        out.flip();
        return out;
    }

    /** 周期 = size 的双线性 value noise（可平铺）。 */
    private static float valueNoise(float x, float y, int size, float[][] grid) {
        int xi = Math.floorMod((int) Math.floor(x), size);
        int yi = Math.floorMod((int) Math.floor(y), size);
        float xf = x - (float) Math.floor(x);
        float yf = y - (float) Math.floor(y);
        int x1 = (xi + 1) % size;
        int y1 = (yi + 1) % size;
        float a = grid[yi][xi];
        float b = grid[yi][x1];
        float c = grid[y1][xi];
        float d = grid[y1][x1];
        float u = xf * xf * (3f - 2f * xf);
        float v = yf * yf * (3f - 2f * yf);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    private static ByteBuffer buildQuad() {
        var format = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT).build();
        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(format.getVertexSize() * 4)) {
            var builder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, format);
            builder.addVertex(0.0f, 0.0f, 0.0f);
            builder.addVertex(1.0f, 0.0f, 0.0f);
            builder.addVertex(1.0f, 1.0f, 0.0f);
            builder.addVertex(0.0f, 1.0f, 0.0f);
            try (var meshData = builder.buildOrThrow()) {
                return meshData.vertexBuffer();
            }
        }
    }

    private static ByteBuffer buildCube() {
        var format = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RGB32_FLOAT).build();
        try (var byteBufferBuilder = ByteBufferBuilder.exactlySized(format.getVertexSize() * 24)) {
            var builder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, format);
            // +X, -X, +Y, -Y, +Z, -Z 六个面（单位立方体 0..1）
            builder.addVertex(1, 0, 0).addVertex(1, 1, 0).addVertex(1, 1, 1).addVertex(1, 0, 1);
            builder.addVertex(0, 0, 1).addVertex(0, 1, 1).addVertex(0, 1, 0).addVertex(0, 0, 0);
            builder.addVertex(0, 1, 0).addVertex(1, 1, 0).addVertex(1, 1, 1).addVertex(0, 1, 1);
            builder.addVertex(0, 0, 1).addVertex(1, 0, 1).addVertex(1, 0, 0).addVertex(0, 0, 0);
            builder.addVertex(0, 0, 1).addVertex(1, 0, 1).addVertex(1, 1, 1).addVertex(0, 1, 1);
            builder.addVertex(1, 0, 0).addVertex(0, 0, 0).addVertex(0, 1, 0).addVertex(1, 1, 0);
            try (var meshData = builder.buildOrThrow()) {
                return meshData.vertexBuffer();
            }
        }
    }
}
