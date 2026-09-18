package org.academy.api.client.render.vfxgraph.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.util.function.Function;
import org.academy.api.client.compatibility.IrisIntegration;
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer;
import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.arc.BlenderArcCurves;
import org.academy.api.client.render.vfxgraph.arc.CurveToMeshBuilder;
import org.academy.api.client.render.vfxgraph.sim.ParticleBuffer;
import org.academy.api.client.resources.R;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VfxGraphRenderer {
    private static final int INSTANCE_STRIDE = (3 + 3 + 1 + 4 + 1 + 1 + 1) * 4;
    private static final int CAMERA_UBO_SIZE = 2 * 64;
    private static final int INITIAL_INSTANCES = 4096;
    private static final int INITIAL_VERTICES = 16384;
    private static final int NOISE_SIZE = 256;
    private static final int ARC_SEGMENT_RESOLUTION = 4;
    private static final int ARC_LIGHTNING_UBO_SIZE = 16;

    private static final VertexFormat TRAIL_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA32_FLOAT)
            .build();
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
    private static final BindGroupLayout ARC_BIND_GROUP = BindGroupLayout.builder()
            .withUniform("GraphCamera", UniformType.UNIFORM_BUFFER)
            .withUniform("ArcLightning", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout NOISE_BIND_GROUP = BindGroupLayout.builder()
            .withUniform("GraphCamera", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .build();
    private static final Vector4f CLEAR_COLOR = new Vector4f(0.07f, 0.08f, 0.1f, 1f);
    private final float[] worldScratch = new float[3];
    private final FrustumIntersection particleFrustum = new FrustumIntersection();
    private final Matrix4f particleProjection = new Matrix4f();
    private int[] selectedParticles = new int[4096];

    public record SurfaceMesh(float[] triangles, float r, float g, float b, float a) {
    }

    private final ConcurrentHashMap<RenderSpec, RenderPipeline> pipelines = new ConcurrentHashMap<>();
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
    private @Nullable GpuBuffer instanceBuffer;
    private @Nullable ByteBuffer instanceData;
    private int instanceCapacity;
    private @Nullable GpuBuffer lineBuffer;
    private @Nullable ByteBuffer lineData;
    private int lineCapacity;
    private final GpuBuffer arcLightningUbo;
    private static final VertexFormat ARC_TUBE_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Normal", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA32_FLOAT)
            .build();
    private final ConcurrentHashMap<String, RenderPipeline> arcTubePipelines = new ConcurrentHashMap<>();
    private @Nullable RenderPipeline surfacePipeline;
    private @Nullable GpuBuffer surfaceBuffer;
    private @Nullable ByteBuffer surfaceData;
    private int surfaceCapacity;
    private @Nullable GpuBuffer arcTubeVertexBuffer;
    private @Nullable GpuBuffer arcTubeIndexBuffer;
    private @Nullable ByteBuffer arcVertexStaging;
    private @Nullable ByteBuffer arcIndexStaging;
    private int arcTubeVertexCapacity;
    private int arcTubeIndexCapacity;
    private @Nullable ArcBuffer arcBuffer;
    private final @Nullable Function<Identifier, GpuTextureView> textureLoader;
    private final Map<Identifier, GpuTextureView> loadedTextures = new java.util.HashMap<>();

    public VfxGraphRenderer() {
        this(null);
    }

    /** Standalone hosts supply their texture cache; returned views remain owned by the host. */
    public VfxGraphRenderer(@Nullable Function<Identifier, GpuTextureView> textureLoader) {
        this.textureLoader = textureLoader;
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
        arcLightningUbo = device.createBuffer(
                () -> "VfxGraph Arc Lightning", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, ARC_LIGHTNING_UBO_SIZE);
        writeArcLightning(device, RenderSpec.ArcRender.DEFAULT.emission(), false);
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
        surfaceBuffer = device.createBuffer(
                () -> "VfxGraph Surface",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) SIMPLE_FORMAT.getVertexSize() * 1024);
        surfaceCapacity = 1024;
    }

    private RenderPipeline pipelineFor(RenderSpec spec) {
        return pipelines.computeIfAbsent(spec, s -> {
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
            var topology = s.geometry() == RenderSpec.Geometry.ARC
                    ? PrimitiveTopology.TRIANGLES
                    : PrimitiveTopology.QUADS;
            var locationName = "vfx_graph_" + s.geometry().name().toLowerCase(Locale.ROOT)
                    + "_" + s.blend().name().toLowerCase(Locale.ROOT)
                    + "_" + s.vertexShader().getPath() + "_" + s.fragmentShader().getPath();
            var pipeline = buildPipeline(locationName, s.vertexShader(), s.fragmentShader(),
                    bindGroup, vertexFormat, instanceFormat, blend, topology);
            RenderSystem.getDevice().precompilePipeline(pipeline);
            return pipeline;
        });
    }

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

    private void writeArcLightning(GpuDevice device, float emission, boolean bloomPass) {
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, ARC_LIGHTNING_UBO_SIZE);
            builder.putVec4(new Vector4f(0f, bloomPass ? 1f : 0f, emission, 0f));
            device.createCommandEncoder().writeToBuffer(arcLightningUbo.slice(), builder.get());
        }
    }

    private static RenderPipeline buildPipeline(
            String name, Identifier vs, Identifier fs,
            BindGroupLayout bindGroup, VertexFormat vertexFormat, @Nullable VertexFormat instanceFormat,
            BlendFunction blend, PrimitiveTopology topology
    ) {
        var builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("academy", "pipeline/" + name))
                .withVertexShader(vs)
                .withFragmentShader(fs)
                .withBindGroupLayout(bindGroup)
                .withCull(false)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withColorTargetState(new ColorTargetState(blend))
                .withPrimitiveTopology(topology)
                .withVertexBinding(0, vertexFormat);
        if (instanceFormat != null) {
            builder.withVertexBinding(1, instanceFormat);
        }
        return builder.build();
    }

    public void setArcBuffer(@Nullable ArcBuffer buffer) {
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

    public void render(GpuTextureView target, @Nullable GpuTextureView depth, ParticleBuffer buffer,
                       GraphCamera camera, boolean clear, List<RenderSpec> specs, WorldTransform transform, boolean bloomPass,
                       List<SurfaceMesh> surfaces) {
        var device = RenderSystem.getDevice();
        var count = buffer.count();
        var hasSurfaces = surfaces != null && !surfaces.isEmpty();

        if (count == 0 && (arcBuffer == null || arcBuffer.count() == 0) && !hasSurfaces) {
            if (clear) clearTarget(device, target, depth);
            return;
        }

        particleFrustum.set(particleProjection.set(camera.projection()).mul(camera.viewRotation()));
        writeCamera(device, camera);
        var encoder = device.createCommandEncoder();
        var preClearedDepth = false;
        var anyBillboard = specs.stream().anyMatch(s -> s.geometry() == RenderSpec.Geometry.QUAD);
        var useSceneDepth = anyBillboard && sceneDepthUsable(RenderSpec.Geometry.QUAD);
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
            if (hasSurfaces) {
                drawSurfaces(renderPass, surfaces, camera);
            }
            for (var spec : specs) {
                if (bloomPass && !spec.feedsBloom()) continue;
                switch (spec.geometry()) {
                    case MESH -> drawInstanced(renderPass, buffer, camera, cubeBuffer, transform, spec);
                    case LINE -> drawTrail(renderPass, buffer, camera, spec, PrimitiveTopology.LINES, transform);
                    case RIBBON -> drawTrail(renderPass, buffer, camera, spec, PrimitiveTopology.QUADS, transform);
                    case ARC -> {
                        if (arcBuffer != null && arcBuffer.count() > 0) {
                            drawArcTubes(renderPass, arcBuffer, camera, transform, bloomPass, spec);
                        }
                    }
                    default -> drawInstanced(renderPass, buffer, camera, quadBuffer, transform, spec);
                }
            }
        }
    }


    private static float[] arcLight(ArcCurve arc) {
        var lifetime = Math.max(1e-3f, arc.lifetime());
        var ageFrac = Math.clamp(arc.age() / lifetime, 0f, 1f);
        if (arc.sparkVelocity() != null) {
            var f = BlenderArcCurves.sample(
                    BlenderArcCurves.PARTICLE_LIFE, ageFrac);
            return new float[]{f, 1f};
        }
        if (arc.flatRadius()) {
            var f = BlenderArcCurves.sample(
                    BlenderArcCurves.CONTACT_RADIUS_AGE, ageFrac);
            return new float[]{f, 1f};
        }
        if (arc.hasArchBase()) {
            var f = BlenderArcCurves.sample(
                    BlenderArcCurves.LIGHT, ageFrac) + 0.33f;
            return new float[]{f, 1f};
        }
        var f = BlenderArcCurves.sample(BlenderArcCurves.FLICKER, ageFrac);
        return new float[]{f, 1f};
    }

    public void drawArcTubes(
            RenderPass pass,
            ArcBuffer arcBuffer,
            GraphCamera camera, WorldTransform transform, boolean bloomPass,
            RenderSpec spec
    ) {
        if (arcBuffer == null || arcBuffer.count() == 0) return;

        var device = RenderSystem.getDevice();
        var arcRender = spec.arc();

        int totalVerts = 0, totalIndices = 0;
        int segRes = Math.clamp(arcRender.segments(), 3, 16);
        for (int a = 0; a < arcBuffer.count(); a++) {
            if (!spec.matchesArcLayer(arcBuffer.arc(a).layer())) continue;
            var size = CurveToMeshBuilder.measure(arcBuffer.arc(a), segRes);
            totalVerts += size.vertices();
            totalIndices += size.indices();
        }
        if (totalVerts == 0) return;
        long vertexBytes = (long) totalVerts * ARC_TUBE_FORMAT.getVertexSize();
        long indexBytes = (long) totalIndices * 4;
        if (arcTubeVertexBuffer == null || vertexBytes > arcTubeVertexBuffer.size()) growArc2TubeBuffer(totalVerts);
        if (arcTubeIndexBuffer == null || indexBytes > arcTubeIndexBuffer.size()) growArc2TubeIndexBuffer(totalIndices);
        arcVertexStaging = ensureArcStaging(arcVertexStaging, Math.toIntExact(vertexBytes));
        arcIndexStaging = ensureArcStaging(arcIndexStaging, Math.toIntExact(indexBytes));
        var vertexData = arcVertexStaging;
        var indexData = arcIndexStaging;
        int vertexOffset = 0;
        for (int a = 0; a < arcBuffer.count(); a++) {
            var arc = arcBuffer.arc(a);
            if (!spec.matchesArcLayer(arc.layer())) continue;
            var light = arcLight(arc);
            vertexOffset += CurveToMeshBuilder.append(arc, segRes,
                    arc.r() * light[0], arc.g() * light[0], arc.b() * light[0], arc.a() * light[1],
                    arcRender.branchBrightnessScale(), vertexData, indexData, vertexOffset);
        }
        vertexData.flip();
        indexData.flip();

        transformArcTubeVertices(vertexData, totalVerts, camera.position(), transform, arcRender.overallScale());

        var writeEncoder = device.createCommandEncoder();
        writeEncoder.writeToBuffer(Objects.requireNonNull(arcTubeVertexBuffer).slice(0, vertexBytes), vertexData);
        writeEncoder.writeToBuffer(Objects.requireNonNull(arcTubeIndexBuffer).slice(0, indexBytes), indexData);

        writeArcLightning(device, arcRender.emission(), bloomPass);

        var pipeline = arcTubePipeline(spec, bloomPass);
        pass.setPipeline(pipeline);
        pass.setUniform("GraphCamera", cameraUbo.slice());
        pass.setUniform("ArcLightning", arcLightningUbo.slice());
        pass.setVertexBuffer(0, Objects.requireNonNull(arcTubeVertexBuffer).slice(0, vertexBytes));
        pass.setVertexBuffer(1, null);
        pass.setIndexBuffer(Objects.requireNonNull(arcTubeIndexBuffer), IndexType.INT);
        pass.drawIndexed(totalIndices, 1, 0, 0, 0);
    }

    private static ByteBuffer ensureArcStaging(@Nullable ByteBuffer buffer, int needed) {
        if (buffer == null || buffer.capacity() < needed) {
            int capacity = Math.max(needed, buffer == null ? 65536 : Math.multiplyExact(buffer.capacity(), 2));
            var replacement = MemoryUtil.memAlloc(capacity);
            if (buffer != null) MemoryUtil.memFree(buffer);
            buffer = replacement;
        }
        buffer.clear();
        return buffer;
    }

    static void transformArcTubeVertices(ByteBuffer vertexData, int vertexCount,
                                         Vector3f camPos, WorldTransform transform, float overallScale) {
        var identity = transform.isIdentity();
        var s = new float[3];
        for (var v = 0; v < vertexCount; v++) {
            var base = v * CurveToMeshBuilder.FLOATS_PER_VERTEX * 4;
            var x = vertexData.getFloat(base);
            var y = vertexData.getFloat(base + 4);
            var z = vertexData.getFloat(base + 8);
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
                var nx = vertexData.getFloat(base + 12);
                var ny = vertexData.getFloat(base + 16);
                var nz = vertexData.getFloat(base + 20);
                transform.applyDirection(nx, ny, nz, s);
                var len = (float) Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
                if (len > 1e-6f) {
                    vertexData.putFloat(base + 12, s[0] / len);
                    vertexData.putFloat(base + 16, s[1] / len);
                    vertexData.putFloat(base + 20, s[2] / len);
                }
            }
        }
    }

    private void growArc2TubeBuffer(int requiredVertices) {
        var newCapacity = Math.max(requiredVertices, arcTubeVertexCapacity * 2);
        var old = arcTubeVertexBuffer;
        arcTubeVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VfxGraph Arc Tube Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) ARC_TUBE_FORMAT.getVertexSize() * newCapacity);
        arcTubeVertexCapacity = newCapacity;
        if (old != null) old.close();
    }

    private void growArc2TubeIndexBuffer(int requiredIndices) {
        var newCapacity = Math.max(requiredIndices, arcTubeIndexCapacity * 2);
        var old = arcTubeIndexBuffer;
        arcTubeIndexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VfxGraph Arc Tube Indices",
                GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                (long) newCapacity * 4);
        arcTubeIndexCapacity = newCapacity;
        if (old != null) old.close();
    }

    private void drawInstanced(
            RenderPass pass,
            ParticleBuffer buffer, GraphCamera camera, GpuBuffer shapeBuffer, WorldTransform transform,
            RenderSpec spec
    ) {
        var count = buffer.count();
        var matched = 0;
        if (selectedParticles.length < count) selectedParticles = new int[Math.max(count, selectedParticles.length * 2)];
        for (var i = 0; i < count; i++) {
            if (spec.matchesLayer(buffer.layer(i)) && particleVisible(buffer, i, camera, transform, spec)) {
                selectedParticles[matched++] = i;
            }
        }
        if (matched == 0) return;

        var bytes = (long) INSTANCE_STRIDE * matched;
        if (instanceBuffer == null || bytes > instanceBuffer.size()) {
            growInstances(matched);
        }
        if (instanceData == null || instanceData.capacity() < bytes) {
            instanceData = BufferUtils.createByteBuffer(Math.toIntExact(Objects.requireNonNull(instanceBuffer).size()));
        }
        var camPos = camera.position();
        var identity = transform.isIdentity();
        instanceData.clear();
        for (var selected = 0; selected < matched; selected++) {
            writeInstance(buffer, selectedParticles[selected], camPos, identity, transform, instanceData);
        }
        instanceData.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(instanceBuffer.slice(0, bytes), instanceData);

        var depthView = sceneDepthUsable(spec.geometry())
                && sceneDepth.view() != null ? sceneDepth.view() : farView;
        var billboard = spec.geometry() == RenderSpec.Geometry.QUAD;
        drawInstancedPass(pass, pipelineFor(spec), shapeBuffer, cameraUbo.slice(),
                instanceBuffer.slice(0, bytes), matched, billboard, depthView, spec);
    }

    private void drawInstancedPass(
            RenderPass pass, RenderPipeline pipeline, GpuBuffer shapeBuffer,
            GpuBufferSlice cameraSlice, GpuBufferSlice instanceSlice, int count,
            boolean billboard, GpuTextureView depthView, RenderSpec spec
    ) {
        pass.setPipeline(pipeline);
        pass.setUniform("GraphCamera", cameraSlice);
        if (billboard) {
            var textureId = spec.texture();
            if (textureId == null) {
                pass.bindTexture("Sampler0", noiseView, noiseSampler);
            } else if (textureLoader != null) {
                pass.bindTexture("Sampler0", loadedTextures.computeIfAbsent(textureId, textureLoader),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            } else {
                var texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);
                pass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            }
            pass.bindTexture("Sampler1", depthView, depthSampler);
        }
        pass.setVertexBuffer(0, shapeBuffer.slice());
        pass.setVertexBuffer(1, instanceSlice);
        var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        pass.setIndexBuffer(sequential.getBuffer(6), sequential.type());
        pass.drawIndexed(6, count, 0, 0, 0);
    }

    private boolean particleVisible(ParticleBuffer buffer, int i, GraphCamera camera,
                                    WorldTransform transform, RenderSpec spec) {
        if (!(spec.particleBoundsScale() > 0f) || spec.geometry() != RenderSpec.Geometry.QUAD) return true;
        transform.apply(buffer.positionX(i), buffer.positionY(i), buffer.positionZ(i), worldScratch);
        float radius = Math.abs(buffer.size(i)) * spec.particleBoundsScale()
                * Math.max(1f, Math.abs(transform.scale())) + 0.25f;
        var c = camera.position();
        return particleFrustum.testSphere(worldScratch[0] - c.x, worldScratch[1] - c.y,
                worldScratch[2] - c.z, radius);
    }

    private void writeInstance(ParticleBuffer buffer, int i, Vector3f camPos, boolean identity, WorldTransform transform, ByteBuffer out) {
        var px = buffer.positionX(i);
        var py = buffer.positionY(i);
        var pz = buffer.positionZ(i);
        if (!identity) {
            transform.apply(px, py, pz, worldScratch);
            px = worldScratch[0];
            py = worldScratch[1];
            pz = worldScratch[2];
        }
        out.putFloat(px - camPos.x);
        out.putFloat(py - camPos.y);
        out.putFloat(pz - camPos.z);
        var vx = buffer.velocityX(i);
        var vy = buffer.velocityY(i);
        var vz = buffer.velocityZ(i);
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
        var life = buffer.lifetime(i);
        out.putFloat(life > 0f ? Math.min(1f, buffer.age(i) / life) : 0f);
    }

    private void drawTrail(
            RenderPass pass,
            ParticleBuffer buffer, GraphCamera camera, RenderSpec spec, PrimitiveTopology primitive,
            WorldTransform transform
    ) {
        var line = spec.geometry() == RenderSpec.Geometry.LINE;
        var vertexCount = line
                ? countLineVertices(buffer, spec)
                : countRibbonVertices(buffer, spec);
        if (vertexCount == 0) return;

        var neededBytes = (long) TRAIL_FORMAT.getVertexSize() * vertexCount;
        if (lineBuffer == null || neededBytes > lineBuffer.size()) {
            growLine(vertexCount);
        }
        if (lineData == null || lineData.capacity() < neededBytes) {
            lineData = BufferUtils.createByteBuffer(Math.toIntExact(Objects.requireNonNull(lineBuffer).size()));
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
        if (instanceBuffer != null) instanceBuffer.close();
        if (lineBuffer != null) lineBuffer.close();
        if (arcVertexStaging != null) { MemoryUtil.memFree(arcVertexStaging); arcVertexStaging = null; }
        if (arcIndexStaging != null) { MemoryUtil.memFree(arcIndexStaging); arcIndexStaging = null; }
        if (arcTubeVertexBuffer != null) arcTubeVertexBuffer.close();
        if (arcTubeIndexBuffer != null) arcTubeIndexBuffer.close();
        arcLightningUbo.close();
        if (surfaceBuffer != null) surfaceBuffer.close();
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

    static boolean sceneDepthUsable(RenderSpec.Geometry geometry) {
        return sceneDepthUsable(geometry, IrisIntegration.isShaderPackInUse());
    }

    static boolean sceneDepthUsable(
            RenderSpec.Geometry geometry,
            boolean shaderPackInUse
    ) {
        return geometry == RenderSpec.Geometry.QUAD && !shaderPackInUse;
    }

    private static void clearTarget(GpuDevice device, GpuTextureView target, @Nullable GpuTextureView depth) {
        var encoder = device.createCommandEncoder();
        try (var pass = encoder.createRenderPass(() -> "VfxGraph Clear", target, Optional.of(CLEAR_COLOR), depth, OptionalDouble.of(0.0))) {
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
        if (old != null) old.close();
    }

    private void growLine(int requiredVertices) {
        var newCapacity = Math.max(requiredVertices, lineCapacity * 2);
        var old = lineBuffer;
        lineBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VfxGraph Line",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) (3 + 4) * 4 * newCapacity);
        lineCapacity = newCapacity;
        if (old != null) old.close();
    }

    private void drawSurfaces(RenderPass pass, List<SurfaceMesh> surfaces, GraphCamera camera) {
        var totalVerts = 0;
        for (var sm : surfaces) {
            totalVerts += sm.triangles().length / 3;
        }
        if (totalVerts == 0) return;

        var bytes = (long) SIMPLE_FORMAT.getVertexSize() * totalVerts;
        if (surfaceBuffer == null || bytes > surfaceBuffer.size()) {
            growSurface(totalVerts);
        }
        if (surfaceData == null || surfaceData.capacity() < bytes) {
            surfaceData = BufferUtils.createByteBuffer(Math.toIntExact(bytes));
        }
        var camPos = camera.position();
        surfaceData.clear();
        for (var sm : surfaces) {
            var tris = sm.triangles();
            for (var i = 0; i + 2 < tris.length; i += 3) {
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
        if (old != null) old.close();
    }

    private static ByteBuffer buildNoiseTile(int size) {
        var rng = new Random(0xC0FFEEL);
        var grid = new float[size + 1][size + 1];
        for (var row : grid) {
            for (var x = 0; x <= size; x++) {
                row[x] = rng.nextFloat();
            }
        }
        var data = new float[size * size];
        var min = Float.MAX_VALUE;
        var max = -Float.MAX_VALUE;
        for (var y = 0; y < size; y++) {
            for (var x = 0; x < size; x++) {
                var sum = 0f;
                var amp = 0.5f;
                for (var o = 0; o < 3; o++) {
                    var freq = (float) (1 << o);
                    sum += amp * valueNoise(x * freq, y * freq, size, grid);
                    amp *= 0.5f;
                }
                data[y * size + x] = sum;
                min = Math.min(min, sum);
                max = Math.max(max, sum);
            }
        }
        var out = BufferUtils.createByteBuffer(size * size * 4);
        var range = Math.max(max - min, 1e-6f);
        for (var value : data) {
            var b = (int) ((value - min) / range * 255f);
            var channel = (byte) b;
            out.put(channel).put(channel).put(channel).put((byte) 255);
        }
        out.flip();
        return out;
    }

    private static float valueNoise(float x, float y, int size, float[][] grid) {
        var xi = Math.floorMod((int) Math.floor(x), size);
        var yi = Math.floorMod((int) Math.floor(y), size);
        var xf = x - (float) Math.floor(x);
        var yf = y - (float) Math.floor(y);
        var x1 = (xi + 1) % size;
        var y1 = (yi + 1) % size;
        var a = grid[yi][xi];
        var b = grid[yi][x1];
        var c = grid[y1][xi];
        var d = grid[y1][x1];
        var u = xf * xf * (3f - 2f * xf);
        var v = yf * yf * (3f - 2f * yf);
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
