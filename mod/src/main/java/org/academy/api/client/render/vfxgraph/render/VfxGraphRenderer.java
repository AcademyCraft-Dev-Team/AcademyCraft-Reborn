package org.academy.api.client.render.vfxgraph.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.academy.api.client.gui.environment.ResolvedTexture;
import org.academy.api.client.gui.environment.UiEnvironment;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.render.vfxgraph.arc.ArcBuffer;
import org.academy.api.client.render.vfxgraph.arc.ArcCurve;
import org.academy.api.client.render.vfxgraph.arc.BlenderArcCurves;
import org.academy.api.client.render.vfxgraph.arc.CurveToMeshBuilder;
import org.academy.api.client.render.vfxgraph.block.BlockGeometryBuffer;
import org.academy.api.client.render.vfxgraph.instance.InstanceGeometryBuffer;
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
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VfxGraphRenderer {
    private static final int INSTANCE_STRIDE = (3 + 3 + 1 + 4 + 1 + 1 + 1) * 4;
    private static final int CAMERA_UBO_SIZE = 2 * 64;
    private static final int NOISE_SIZE = 256;
    private static final int ARC_LIGHTNING_UBO_SIZE = 16;
    private static final int DISTORTION_UBO_SIZE = 16;
    private static final VertexFormat DISTORTION_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .build();

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
    private static final VertexFormat SCREEN_FLASH_INSTANCE_FORMAT = VertexFormat.builder(1)
            .addAttribute("InstanceColor", GpuFormat.RGBA32_FLOAT)
            .build();
    private static final VertexFormat TEX_VERTEX_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .build();
    private static final VertexFormat INSTANCE_MAT_FORMAT = VertexFormat.builder(1)
            .addAttribute("InstanceMat", GpuFormat.RGBA32_FLOAT, 4)
            .build();
    private static final VertexFormat BLOCK_MESH_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA32_FLOAT)
            .addAttribute("UV2", DefaultVertexFormat.UV2_FORMAT)
            .build();
    private static final BindGroupLayout BLOCK_BIND_GROUP = BindGroupLayout.builder()
            .withUniform("GraphCamera", UniformType.UNIFORM_BUFFER)
            .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Sampler2", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .build();
    private static final int BLOCK_MESH_VERTEX_CAPACITY = 16384;
    private static final int INSTANCED_TEX_VERTEX_CAPACITY = 512;
    private static final int INSTANCED_TEX_INSTANCE_CAPACITY = 1024;
    private static final int INSTANCED_PARAMS_SIZE = 32;
    private static final int INSTANCED_PARAMS_CAPACITY = 32;
    private static final BindGroupLayout CAMERA_BIND_GROUP = BindGroupLayout.builder()
            .withUniform("GraphCamera", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout ARC_BIND_GROUP = BindGroupLayout.builder()
            .withUniform("GraphCamera", UniformType.UNIFORM_BUFFER)
            .withUniform("ArcLightning", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout TEX_BIND_GROUP = BindGroupLayout.builder()
            .withUniform("GraphCamera", UniformType.UNIFORM_BUFFER)
            .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Sampler1", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withUniform("InstancedParams", UniformType.UNIFORM_BUFFER)
            .build();
    private static final BindGroupLayout NOISE_BIND_GROUP = BindGroupLayout.builder()
            .withUniform("GraphCamera", UniformType.UNIFORM_BUFFER)
            .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("Sampler1", UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    private static final Vector4f CLEAR_COLOR = new Vector4f(0.07f, 0.08f, 0.1f, 1f);
    private final float[] worldScratch = new float[3];
    private final FrustumIntersection particleFrustum = new FrustumIntersection();
    private final Matrix4f particleProjection = new Matrix4f();
    private int[] selectedParticles = new int[4096];

    public record SurfaceMesh(float[] triangles, float r, float g, float b, float a) {
    }

    public record ScreenFlashSource(ParticleBuffer buffer, List<RenderSpec> specs, float cap) {
    }

    private record PreparedArc(RenderPipeline pipeline, long vertexBytes, long indexBytes, int indexCount) {
    }

    private final ConcurrentHashMap<RenderSpec, RenderPipeline> pipelines = new ConcurrentHashMap<>();
    private final GpuBuffer quadBuffer;
    private final GpuBuffer cubeBuffer;
    private final GpuBuffer cameraUbo;
    private final GpuBuffer screenFlashColorBuffer;
    private final GpuTexture noiseTexture;
    private final GpuTextureView noiseView;
    private final GpuSampler noiseSampler;
    private final SceneDepth sceneDepth = new SceneDepth();
    private final GpuTexture farTexture;
    private final GpuTextureView farView;
    private final GpuSampler depthSampler;
    private @Nullable ByteBuffer instanceData;
    private @Nullable ByteBuffer lineData;
    private ByteBuffer distortionStaging = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder());
    private final GpuBuffer distortionUbo;
    private GpuBuffer distortionVertexBuffer;
    private final GpuSampler distortionSampler;
    private final GpuBuffer arcLightningUbo;
    private static final VertexFormat ARC_TUBE_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Normal", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA32_FLOAT)
            .build();
    private final ConcurrentHashMap<String, RenderPipeline> arcTubePipelines = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RenderSpec, RenderPipeline> screenFlashPipelines = new ConcurrentHashMap<>();
    private @Nullable RenderPipeline surfacePipeline;
    private @Nullable ByteBuffer surfaceData;
    private @Nullable GpuBuffer arcTubeVertexBuffer;
    private @Nullable GpuBuffer arcTubeIndexBuffer;
    private @Nullable ByteBuffer arcVertexStaging;
    private @Nullable ByteBuffer arcIndexStaging;
    private int arcTubeVertexCapacity;
    private int arcTubeIndexCapacity;
    private @Nullable ArcBuffer arcBuffer;
    private @Nullable InstanceGeometryBuffer instanceGeometry;
    private @Nullable BlockGeometryBuffer blockGeometry;
    private @Nullable GpuBuffer blockMeshVertexBuffer;
    private int blockMeshVertexCapacity;
    private @Nullable ByteBuffer blockMeshStaging;
    private final ConcurrentHashMap<RenderSpec, RenderPipeline> blockMeshPipelines = new ConcurrentHashMap<>();
    private @Nullable GpuBuffer instancedTexVertexBuffer;
    private @Nullable GpuBuffer instancedTexInstanceBuffer;
    private int instancedTexVertexCapacity;
    private int instancedTexInstanceCapacity;
    private @Nullable ByteBuffer instancedTexInstanceStaging;
    private @Nullable GpuBuffer instancedParamsUbo;
    private @Nullable ByteBuffer instancedParamsStaging;

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
        var noiseBytes = buildNoiseTile();
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
        arcLightningUbo = device.createBuffer(
                () -> "VfxGraph Arc Lightning", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, ARC_LIGHTNING_UBO_SIZE);
        writeArcLightning(device, RenderSpec.ArcRender.DEFAULT.emission(), false);
        distortionUbo = device.createBuffer(
                () -> "VfxGraph Distortion", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, DISTORTION_UBO_SIZE);
        distortionVertexBuffer = device.createBuffer(
                () -> "VfxGraph Distortion Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) DISTORTION_FORMAT.getVertexSize() * 256);
        distortionSampler = device.createSampler(
                AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
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
        instancedTexVertexBuffer = device.createBuffer(
                () -> "VfxGraph Instanced Tex Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) TEX_VERTEX_FORMAT.getVertexSize() * INSTANCED_TEX_VERTEX_CAPACITY);
        instancedTexVertexCapacity = INSTANCED_TEX_VERTEX_CAPACITY;
        blockMeshVertexBuffer = device.createBuffer(
                () -> "VfxGraph Block Mesh Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) BLOCK_MESH_FORMAT.getVertexSize() * BLOCK_MESH_VERTEX_CAPACITY);
        blockMeshVertexCapacity = BLOCK_MESH_VERTEX_CAPACITY;
        instancedTexInstanceBuffer = device.createBuffer(
                () -> "VfxGraph Instanced Tex Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_MAT_FORMAT.getVertexSize() * INSTANCED_TEX_INSTANCE_CAPACITY);
        instancedTexInstanceCapacity = INSTANCED_TEX_INSTANCE_CAPACITY;
        screenFlashColorBuffer = device.createBuffer(
                () -> "VfxGraph Screen Flash Color",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                4L * Float.BYTES);
    }

    private RenderPipeline pipelineFor(RenderSpec spec) {
        return pipelines.computeIfAbsent(spec, s -> {
            var bindGroup = switch (s.geometry()) {
                case QUAD -> NOISE_BIND_GROUP;
                case ARC -> ARC_BIND_GROUP;
                case INSTANCED_TEX -> TEX_BIND_GROUP;
                default -> CAMERA_BIND_GROUP;
            };
            var blend = s.blend() != RenderSpec.Blend.TRANSLUCENT
                    ? BlendFunction.ADDITIVE
                    : BlendFunction.TRANSLUCENT;
            var vertexFormat = switch (s.geometry()) {
                case LINE, RIBBON -> SIMPLE_FORMAT;
                case ARC -> ARC_TUBE_FORMAT;
                case INSTANCED_TEX -> TEX_VERTEX_FORMAT;
                default -> POSITION_FORMAT;
            };
            var instanceFormat = s.geometry() == RenderSpec.Geometry.LINE || s.geometry() == RenderSpec.Geometry.RIBBON
                    || s.geometry() == RenderSpec.Geometry.ARC
                    ? null
                    : s.geometry() == RenderSpec.Geometry.INSTANCED_TEX ? INSTANCE_MAT_FORMAT : INSTANCE_FORMAT;
            var topology = s.geometry() == RenderSpec.Geometry.ARC
                    ? PrimitiveTopology.TRIANGLES
                    : s.geometry() == RenderSpec.Geometry.INSTANCED_TEX ? s.topology()
                    : PrimitiveTopology.QUADS;
            var locationName = "vfx_graph_" + s.geometry().name().toLowerCase(Locale.ROOT)
                    + "_" + s.blend().name().toLowerCase(Locale.ROOT)
                    + "_" + s.vertexShader().getPath() + "_" + s.fragmentShader().getPath()
                    + (s.geometry() == RenderSpec.Geometry.INSTANCED_TEX && topology == PrimitiveTopology.TRIANGLES
                    ? "_tri" : "");
            var pipeline = buildPipeline(locationName, s.vertexShader(), s.fragmentShader(),
                    bindGroup, vertexFormat, instanceFormat, blend, topology);
            RenderSystem.getCompiledPipeline(pipeline);
            return pipeline;
        });
    }

    private RenderPipeline arcTubePipeline(RenderSpec spec, boolean bloomPass) {
        var key = (bloomPass ? "bloom_" : "main_")
                + spec.vertexShader() + "_" + spec.fragmentShader();
        return arcTubePipelines.computeIfAbsent(key, _ -> {
            var shaderSuffix = (spec.vertexShader().getPath() + "_" + spec.fragmentShader().getPath())
                    .replace('/', '_');
            var locationName = "vfx_graph_arc_tube_" + (bloomPass ? "bloom_" : "main_") + shaderSuffix;
            var pipeline = buildPipeline(locationName, spec.vertexShader(), spec.fragmentShader(),
                    ARC_BIND_GROUP, ARC_TUBE_FORMAT, null,
                    bloomPass ? BlendFunction.ADDITIVE : BlendFunction.TRANSLUCENT,
                    PrimitiveTopology.TRIANGLES);
            RenderSystem.getCompiledPipeline(pipeline);
            return pipeline;
        });
    }

    private RenderPipeline surfacePipeline() {
        if (surfacePipeline == null) {
            surfacePipeline = buildPipeline("vfx_graph_surface",
                    R.shaders.core.vfxgraph_surface, R.shaders.core.vfxgraph_surface,
                    CAMERA_BIND_GROUP, SIMPLE_FORMAT, null,
                    BlendFunction.TRANSLUCENT, PrimitiveTopology.TRIANGLES);
            RenderSystem.getCompiledPipeline(surfacePipeline);
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

    public void setInstanceGeometry(@Nullable InstanceGeometryBuffer buffer) {
        this.instanceGeometry = buffer;
    }

    public void setBlockGeometry(@Nullable BlockGeometryBuffer buffer) {
        this.blockGeometry = buffer;
    }

    public void renderDistortionRings(GpuTextureView target, ParticleBuffer buffer, RenderSpec spec,
                                      RenderSpec.DistortionRing ring, WorldTransform transform, GraphCamera camera) {
        int count = 0;
        for (var i = 0; i < buffer.count(); i++) {
            if (spec.matchesLayer(buffer.layer(i))) count++;
        }
        if (count == 0) return;

        var vertices = buildDistortionVertices(buffer, spec, transform, camera);

        var device = RenderSystem.getDevice();
        writeCamera(device, camera);
        var aspect = Math.abs(camera.projection().m11() / camera.projection().m00());
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, DISTORTION_UBO_SIZE);
            builder.putVec4(new Vector4f(ring.strength(), ring.width(), ring.edgeBlur(), aspect));
            device.createCommandEncoder().writeToBuffer(distortionUbo.slice(), builder.get());
        }
        var scene = PostEffect.MAIN_SCENE.getColorTextureView();
        if (scene == null) return;
        var vertexCount = uploadDistortionVertices(vertices);
        var pipeline = distortionPipeline(spec);
        var encoder = device.createCommandEncoder();
        try (var pass = encoder.createRenderPass(
                () -> "VfxGraph distortion", target,
                Optional.empty(), null, OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("GraphCamera", cameraUbo.slice());
            pass.setUniform("DistortionRing", distortionUbo.slice());
            pass.setUniform("Sampler0", scene, distortionSampler);
            pass.setVertexBuffer(0, distortionVertexBuffer.slice());
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            pass.setIndexBuffer(sequential.getBuffer(vertexCount), sequential.type());
            pass.drawIndexed(vertexCount, 1, 0, 0, 0);
        }
    }

    private int uploadDistortionVertices(ByteBuffer data) {
        var vertexSize = DISTORTION_FORMAT.getVertexSize();
        int vertices = data.remaining() / vertexSize;
        if (vertices == 0) return 0;
        var needed = (long) vertexSize * vertices;
        if (needed > distortionVertexBuffer.size()) {
            distortionVertexBuffer.close();
            distortionVertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "VfxGraph Distortion Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, needed);
        }
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(distortionVertexBuffer.slice(), data);
        return vertices;
    }

    private ByteBuffer buildDistortionVertices(ParticleBuffer buffer, RenderSpec spec,
                                               WorldTransform transform, GraphCamera camera) {
        var cam = camera.position();
        var identity = transform.isIdentity();
        distortionStaging.clear();
        for (var i = 0; i < buffer.count(); i++) {
            if (!spec.matchesLayer(buffer.layer(i))) continue;
            float px = buffer.positionX(i);
            float py = buffer.positionY(i);
            float pz = buffer.positionZ(i);
            if (!identity) {
                transform.apply(px, py, pz, worldScratch);
                px = worldScratch[0];
                py = worldScratch[1];
                pz = worldScratch[2];
            }
            float half = Math.abs(buffer.size(i) * transform.scale()) * 0.5f;
            var u = new Vector3f(1f, 0f, 0f).rotate(transform.rotation());
            var v = new Vector3f(0f, 1f, 0f).rotate(transform.rotation());
            var roll = buffer.rotation(i);
            if (roll != 0f) {
                var c = (float) Math.cos(roll);
                var s = (float) Math.sin(roll);
                var nux = u.x * c + v.x * s;
                var nuy = u.y * c + v.y * s;
                var nuz = u.z * c + v.z * s;
                v.set(v.x * c - u.x * s, v.y * c - u.y * s, v.z * c - u.z * s);
                u.set(nux, nuy, nuz);
            }
            float rx = px - cam.x;
            float ry = py - cam.y;
            float rz = pz - cam.z;
            putDistortionVertex(rx - u.x * half - v.x * half, ry - u.y * half - v.y * half, rz - u.z * half - v.z * half, 0f, 0f);
            putDistortionVertex(rx + u.x * half - v.x * half, ry + u.y * half - v.y * half, rz + u.z * half - v.z * half, 1f, 0f);
            putDistortionVertex(rx + u.x * half + v.x * half, ry + u.y * half + v.y * half, rz + u.z * half + v.z * half, 1f, 1f);
            putDistortionVertex(rx - u.x * half + v.x * half, ry - u.y * half + v.y * half, rz - u.z * half + v.z * half, 0f, 1f);
        }
        distortionStaging.flip();
        return distortionStaging;
    }

    private void putDistortionVertex(float x, float y, float z, float u, float v) {
        if (distortionStaging.remaining() < 20) {
            var grown = ByteBuffer.allocateDirect(distortionStaging.capacity() * 2)
                    .order(ByteOrder.nativeOrder());
            distortionStaging.flip();
            grown.put(distortionStaging);
            distortionStaging = grown;
        }
        distortionStaging.putFloat(x).putFloat(y).putFloat(z).putFloat(u).putFloat(v);
    }

    private RenderPipeline distortionPipeline(RenderSpec spec) {
        return pipelines.computeIfAbsent(spec, s -> {
            var bindGroup = BindGroupLayout.builder()
                    .withUniform("GraphCamera", UniformType.UNIFORM_BUFFER)
                    .withUniform("DistortionRing", UniformType.UNIFORM_BUFFER)
                    .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .build();
            var pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("academy", "pipeline/vfx_graph_distortion_ring"))
                    .withVertexShader(s.vertexShader())
                    .withFragmentShader(s.fragmentShader())
                    .withBindGroupLayout(bindGroup)
                    .withCull(false)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withVertexBinding(0, DISTORTION_FORMAT)
                    .build();
            RenderSystem.getCompiledPipeline(pipeline);
            return pipeline;
        });
    }

    /**
     * 屏幕闪光（E1）：跨效果聚合 alpha 求和、按全局 cap 截断后画单个全屏 quad。
     * 语义等同旧 {@code SCREEN_SPACE_POST} 的 {@code SkyStrikeScreenFlashRenderer} 求和路径。
     * 无深度测试（全屏覆盖层），颜色取首个匹配粒子的 RGB。
     */
    public void renderScreenFlash(GpuTextureView target, List<ScreenFlashSource> sources) {
        float sum = 0f;
        float cap = 1f;
        float r = 0.80f;
        float g = 0.93f;
        float b = 1.0f;
        RenderSpec firstSpec = null;
        boolean any = false;
        for (var source : sources) {
            var buffer = source.buffer();
            if (buffer.count() == 0) continue;
            cap = Math.max(cap, source.cap());
            for (var spec : source.specs()) {
                if (spec.geometry() != RenderSpec.Geometry.SCREEN_FLASH) continue;
                if (firstSpec == null) firstSpec = spec;
                for (var i = 0; i < buffer.count(); i++) {
                    if (!spec.matchesLayer(buffer.layer(i))) continue;
                    sum += Math.max(0f, buffer.alpha(i));
                    if (!any) {
                        r = buffer.colorR(i);
                        g = buffer.colorG(i);
                        b = buffer.colorB(i);
                        any = true;
                    }
                }
            }
        }
        if (firstSpec == null) return;
        float alpha = Math.min(sum, cap);
        if (alpha <= 0.001f) return;

        var device = RenderSystem.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            var color = stack.malloc(4 * Float.BYTES);
            color.putFloat(r).putFloat(g).putFloat(b).putFloat(alpha).flip();
            device.createCommandEncoder().writeToBuffer(screenFlashColorBuffer.slice(), color);
        }
        var pipeline = screenFlashPipeline(firstSpec);
        var encoder = device.createCommandEncoder();
        try (var pass = encoder.createRenderPass(
                () -> "VfxGraph Screen Flash",
                target,
                Optional.empty(),
                null,
                OptionalDouble.empty()
        )) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            pass.setVertexBuffer(0, quadBuffer.slice());
            pass.setVertexBuffer(1, screenFlashColorBuffer.slice());
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            pass.setIndexBuffer(sequential.getBuffer(6), sequential.type());
            pass.drawIndexed(6, 1, 0, 0, 0);
        }
    }

    private RenderPipeline screenFlashPipeline(RenderSpec spec) {
        return screenFlashPipelines.computeIfAbsent(spec, s -> {
            var additive = s.blend() != RenderSpec.Blend.TRANSLUCENT;
            var pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("academy", "pipeline/vfx_graph_screen_flash"))
                    .withVertexShader(s.vertexShader())
                    .withFragmentShader(s.fragmentShader())
                    .withCull(false)
                    .withColorTargetState(new ColorTargetState(
                            additive ? BlendFunction.ADDITIVE : BlendFunction.TRANSLUCENT))
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withVertexBinding(0, POSITION_FORMAT)
                    .withVertexBinding(1, SCREEN_FLASH_INSTANCE_FORMAT)
                    .build();
            RenderSystem.getCompiledPipeline(pipeline);
            return pipeline;
        });
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
        var particleSpecs = specs.stream()
                .filter(s -> s.geometry() != RenderSpec.Geometry.DISTORTION_RING
                        && s.geometry() != RenderSpec.Geometry.SCREEN_FLASH).toList();
        var count = buffer.count();
        var hasSurfaces = !surfaces.isEmpty();
        var hasInstancedTex = specs.stream().anyMatch(s -> s.geometry() == RenderSpec.Geometry.INSTANCED_TEX);
        var hasBlockMesh = specs.stream().anyMatch(s -> s.geometry() == RenderSpec.Geometry.BLOCK_QUADS);

        if (count == 0 && (arcBuffer == null || arcBuffer.count() == 0) && !hasSurfaces
                && !(hasInstancedTex && instanceGeometry != null && !instanceGeometry.isEmpty())
                && !(hasBlockMesh && blockGeometry != null && !blockGeometry.isEmpty())) {
            if (clear) clearTarget(device, target, depth);
            return;
        }

        particleFrustum.set(particleProjection.set(camera.projection()).mul(camera.viewRotation()));
        writeCamera(device, camera);
        var encoder = device.createCommandEncoder();
        var preClearedDepth = false;
        var anyBillboard = particleSpecs.stream().anyMatch(s -> s.geometry() == RenderSpec.Geometry.QUAD);
        var useSceneDepth = anyBillboard && sceneDepthUsable(RenderSpec.Geometry.QUAD);
        if (useSceneDepth && depth != null) {
            if (clear) {
                encoder.clearDepthTexture(depth.texture(), 0.0);
                preClearedDepth = true;
            }
            sceneDepth.copyFrom(depth);
        }
        var clearDepth = depth != null && clear && !preClearedDepth ? OptionalDouble.of(0.0) : OptionalDouble.empty();

        var preparedArc = prepareArcPass(arcBuffer, camera, transform, bloomPass, particleSpecs);
        var specTextures = resolveSpecTextures(particleSpecs, bloomPass);
        var preparedInstancedTex = prepareInstancedTexPass(camera, transform, particleSpecs);
        var instancedTexTextures = resolveInstancedTexTextures(particleSpecs, bloomPass);
        var preparedBlockMesh = prepareBlockMeshPass(camera, particleSpecs);
        writeInstancedParams(particleSpecs);

        try (var renderPass = encoder.createRenderPass(
                () -> "VfxGraph " + particleSpecs, target,
                clear ? Optional.of(CLEAR_COLOR) : Optional.empty(),
                depth, clearDepth
        )) {
            if (hasSurfaces) {
                drawSurfaces(renderPass, surfaces, camera);
            }
            var arcsDrawn = false;
            for (var index = 0; index < particleSpecs.size(); index++) {
                var spec = particleSpecs.get(index);
                if (bloomPass ? !spec.feedsBloom() : spec.bloomOnly()) continue;
                switch (spec.geometry()) {
                    case MESH -> drawInstanced(renderPass, buffer, camera, cubeBuffer, transform, spec, null);
                    case LINE -> drawTrail(renderPass, buffer, camera, spec, PrimitiveTopology.LINES, transform);
                    case RIBBON -> drawTrail(renderPass, buffer, camera, spec, PrimitiveTopology.QUADS, transform);
                    case ARC -> {
                        if (!arcsDrawn && preparedArc != null) {
                            drawArcTubes(renderPass, preparedArc);
                            arcsDrawn = true;
                        }
                    }
                    case INSTANCED_TEX -> {
                        if (preparedInstancedTex != null) {
                            drawInstancedTex(renderPass, preparedInstancedTex, spec,
                                    instancedTexTextures.get(spec), instancedParamsSlice(index));
                        }
                    }
                    case BLOCK_QUADS -> {
                        if (preparedBlockMesh != null) {
                            drawBlockMesh(renderPass, preparedBlockMesh, spec);
                        }
                    }
                    default ->
                            drawInstanced(renderPass, buffer, camera, quadBuffer, transform, spec, specTextures.get(spec));
                }
            }
        }
    }

    private @Nullable PreparedArc prepareArcPass(@Nullable ArcBuffer arcs, GraphCamera camera,
                                                 WorldTransform transform, boolean bloomPass,
                                                 List<RenderSpec> specs) {
        if (arcs == null || arcs.count() == 0) return null;
        for (var spec : specs) {
            if (bloomPass ? !spec.feedsBloom() : spec.bloomOnly()) continue;
            if (spec.geometry() == RenderSpec.Geometry.ARC) {
                return prepareArcTubes(arcs, camera, transform, bloomPass, spec);
            }
        }
        return null;
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

    private @Nullable PreparedArc prepareArcTubes(
            ArcBuffer arcBuffer,
            GraphCamera camera, WorldTransform transform, boolean bloomPass,
            RenderSpec spec
    ) {
        if (arcBuffer.count() == 0) return null;

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
        if (totalVerts == 0) return null;
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

        return new PreparedArc(arcTubePipeline(spec, bloomPass), vertexBytes, indexBytes, totalIndices);
    }

    private void drawArcTubes(RenderPass pass, PreparedArc prepared) {
        pass.setPipeline(RenderSystem.getCompiledPipeline(prepared.pipeline()));
        pass.setUniform("GraphCamera", cameraUbo.slice());
        pass.setUniform("ArcLightning", arcLightningUbo.slice());
        pass.setVertexBuffer(0, Objects.requireNonNull(arcTubeVertexBuffer).slice(0, prepared.vertexBytes()));
        pass.setVertexBuffer(1, null);
        pass.setIndexBuffer(Objects.requireNonNull(arcTubeIndexBuffer), IndexType.INT);
        pass.drawIndexed(prepared.indexCount(), 1, 0, 0, 0);
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

    private record PreparedInstancedTex(long vertexBytes, long instanceBytes,
                                        int vertexCount, int instanceCount) {
    }

    private @Nullable PreparedInstancedTex prepareInstancedTexPass(GraphCamera camera,
                                                                   WorldTransform transform,
                                                                   List<RenderSpec> specs) {
        var geometry = instanceGeometry;
        if (geometry == null || geometry.isEmpty()) return null;
        if (specs.stream().noneMatch(s -> s.geometry() == RenderSpec.Geometry.INSTANCED_TEX)) return null;
        var vertexCount = geometry.vertexCount();
        var instanceCount = geometry.instanceCount();
        var vertexBytes = (long) TEX_VERTEX_FORMAT.getVertexSize() * vertexCount;
        var instanceBytes = (long) INSTANCE_MAT_FORMAT.getVertexSize() * instanceCount;
        if (instancedTexVertexBuffer == null || vertexBytes > instancedTexVertexBuffer.size())
            growInstancedTexVertexBuffer(vertexCount);
        if (instancedTexInstanceBuffer == null || instanceBytes > instancedTexInstanceBuffer.size())
            growInstancedTexInstanceBuffer(instanceCount);
        var vertexBuffer = Objects.requireNonNull(instancedTexVertexBuffer);
        var instanceBuffer = Objects.requireNonNull(instancedTexInstanceBuffer);
        instancedTexInstanceStaging = ensureInstancedTexStaging(
                instancedTexInstanceStaging, Math.toIntExact(instanceBytes));
        var staging = instancedTexInstanceStaging;
        transformInstanceMatrices(Objects.requireNonNull(geometry.instances()), staging, instanceCount,
                camera.position(), transform);
        staging.flip();
        var vertices = Objects.requireNonNull(geometry.vertices()).duplicate();
        vertices.position(0);
        vertices.limit((int) vertexBytes);
        var writeEncoder = RenderSystem.getDevice().createCommandEncoder();
        writeEncoder.writeToBuffer(vertexBuffer.slice(0, vertexBytes), vertices);
        writeEncoder.writeToBuffer(instanceBuffer.slice(0, instanceBytes), staging);
        return new PreparedInstancedTex(vertexBytes, instanceBytes, vertexCount, instanceCount);
    }

    private Map<RenderSpec, @Nullable ResolvedTexture[]> resolveInstancedTexTextures(List<RenderSpec> specs, boolean bloomPass) {
        var textures = new IdentityHashMap<RenderSpec, @Nullable ResolvedTexture[]>();
        var environment = UiEnvironment.get();
        for (var spec : specs) {
            if (bloomPass ? !spec.feedsBloom() : spec.bloomOnly()) continue;
            if (spec.geometry() != RenderSpec.Geometry.INSTANCED_TEX) continue;
            ResolvedTexture primary = null;
            ResolvedTexture secondary = null;
            if (spec.texture() != null) {
                primary = environment.resolveTexture(spec.texture());
            }
            if (spec.texture1() != null) {
                secondary = environment.resolveTexture(spec.texture1());
            }
            @Nullable ResolvedTexture[] resolved = {primary, secondary};
            textures.put(spec, resolved);
        }
        return textures;
    }

    private void drawInstancedTex(RenderPass pass, PreparedInstancedTex prepared, RenderSpec spec,
                                  @Nullable ResolvedTexture @Nullable [] textures, GpuBufferSlice params) {
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipelineFor(spec)));
        pass.setUniform("GraphCamera", cameraUbo.slice());
        pass.setUniform("InstancedParams", params);
        var primary = textures == null ? null : textures[0];
        if (primary != null) {
            pass.setUniform("Sampler0", primary.getView(), primary.getSampler());
        } else {
            pass.setUniform("Sampler0", noiseView, noiseSampler);
        }
        var secondary = textures == null ? null : textures[1];
        if (secondary != null) {
            pass.setUniform("Sampler1", secondary.getView(), secondary.getSampler());
        } else {
            pass.setUniform("Sampler1", noiseView, noiseSampler);
        }
        RenderSystem.bindDefaultUniforms(pass);
        pass.setVertexBuffer(0, Objects.requireNonNull(instancedTexVertexBuffer).slice(0, prepared.vertexBytes()));
        pass.setVertexBuffer(1, Objects.requireNonNull(instancedTexInstanceBuffer).slice(0, prepared.instanceBytes()));
        var topology = spec.topology();
        var sequential = RenderSystem.getSequentialBuffer(topology);
        var indexCount = topology == PrimitiveTopology.TRIANGLES
                ? prepared.vertexCount() : prepared.vertexCount() * 3 / 2;
        pass.setIndexBuffer(sequential.getBuffer(indexCount), sequential.type());
        pass.drawIndexed(indexCount, prepared.instanceCount(), 0, 0, 0);
    }

    private void transformInstanceMatrices(ByteBuffer source, ByteBuffer target, int instanceCount,
                                           Vector3f camPos, WorldTransform transform) {
        var identity = transform.isIdentity();
        var scratch = new float[3];
        for (var i = 0; i < instanceCount; i++) {
            var base = i * 64;
            for (var column = 0; column < 3; column++) {
                var offset = base + column * 16;
                var x = source.getFloat(offset);
                var y = source.getFloat(offset + 4);
                var z = source.getFloat(offset + 8);
                var w = source.getFloat(offset + 12);
                if (identity) {
                    target.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
                } else {
                    transform.applyDirection(x, y, z, scratch);
                    target.putFloat(scratch[0]).putFloat(scratch[1]).putFloat(scratch[2]).putFloat(w);
                }
            }
            var tx = source.getFloat(base + 48);
            var ty = source.getFloat(base + 52);
            var tz = source.getFloat(base + 56);
            var tw = source.getFloat(base + 60);
            if (identity) {
                target.putFloat(tx - camPos.x).putFloat(ty - camPos.y)
                        .putFloat(tz - camPos.z).putFloat(tw);
            } else {
                transform.apply(tx, ty, tz, scratch);
                target.putFloat(scratch[0] - camPos.x).putFloat(scratch[1] - camPos.y)
                        .putFloat(scratch[2] - camPos.z).putFloat(tw);
            }
        }
    }

    private void growInstancedTexVertexBuffer(int requiredVertices) {
        var newCapacity = Math.max(requiredVertices, instancedTexVertexCapacity * 2);
        var old = instancedTexVertexBuffer;
        instancedTexVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VfxGraph Instanced Tex Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) TEX_VERTEX_FORMAT.getVertexSize() * newCapacity);
        instancedTexVertexCapacity = newCapacity;
        if (old != null) old.close();
    }

    private void growInstancedTexInstanceBuffer(int requiredInstances) {
        var newCapacity = Math.max(requiredInstances, instancedTexInstanceCapacity * 2);
        var old = instancedTexInstanceBuffer;
        instancedTexInstanceBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VfxGraph Instanced Tex Instances",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) INSTANCE_MAT_FORMAT.getVertexSize() * newCapacity);
        instancedTexInstanceCapacity = newCapacity;
        if (old != null) old.close();
    }

    private static ByteBuffer ensureInstancedTexStaging(@Nullable ByteBuffer buffer, int needed) {
        if (buffer == null || buffer.capacity() < needed) {
            int capacity = Math.max(needed, buffer == null ? 65536 : Math.multiplyExact(buffer.capacity(), 2));
            var replacement = MemoryUtil.memAlloc(capacity);
            if (buffer != null) MemoryUtil.memFree(buffer);
            buffer = replacement;
        }
        buffer.clear();
        return buffer;
    }

    private void writeInstancedParams(List<RenderSpec> specs) {
        if (specs.stream().noneMatch(s -> s.geometry() == RenderSpec.Geometry.INSTANCED_TEX)) return;
        var count = specs.size();
        var bytes = (long) INSTANCED_PARAMS_SIZE * count;
        if (instancedParamsUbo == null || instancedParamsUbo.size() < bytes) {
            if (instancedParamsUbo != null) instancedParamsUbo.close();
            var capacity = Math.max(count, INSTANCED_PARAMS_CAPACITY);
            instancedParamsUbo = RenderSystem.getDevice().createBuffer(
                    () -> "VfxGraph Instanced Params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    (long) INSTANCED_PARAMS_SIZE * capacity);
        }
        instancedParamsStaging = ensureInstancedParamsStaging(
                instancedParamsStaging, Math.toIntExact(bytes));
        var staging = instancedParamsStaging;
        for (var spec : specs) {
            var scale = spec.scale();
            staging.putFloat(scale.x()).putFloat(scale.y()).putFloat(scale.z()).putFloat(1f);
            var color = spec.color();
            staging.putFloat(color.r()).putFloat(color.g()).putFloat(color.b()).putFloat(color.a());
        }
        staging.flip();
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(Objects.requireNonNull(instancedParamsUbo).slice(0, bytes), staging);
    }

    private static ByteBuffer ensureInstancedParamsStaging(@Nullable ByteBuffer buffer, int needed) {
        if (buffer == null || buffer.capacity() < needed) {
            return BufferUtils.createByteBuffer(
                    Math.max(needed, INSTANCED_PARAMS_SIZE * INSTANCED_PARAMS_CAPACITY));
        }
        buffer.clear();
        return buffer;
    }

    private GpuBufferSlice instancedParamsSlice(int index) {
        return Objects.requireNonNull(instancedParamsUbo)
                .slice((long) index * INSTANCED_PARAMS_SIZE, INSTANCED_PARAMS_SIZE);
    }

    private record PreparedBlockMesh(long vertexBytes, int vertexCount) {
    }

    private @Nullable PreparedBlockMesh prepareBlockMeshPass(GraphCamera camera,
                                                             List<RenderSpec> specs) {
        var geometry = blockGeometry;
        if (geometry == null || geometry.isEmpty()) return null;
        if (specs.stream().noneMatch(s -> s.geometry() == RenderSpec.Geometry.BLOCK_QUADS)) return null;
        var vertexCount = geometry.vertexCount();
        var vertexBytes = (long) BLOCK_MESH_FORMAT.getVertexSize() * vertexCount;
        if (blockMeshVertexBuffer == null || vertexBytes > (long) blockMeshVertexCapacity * BLOCK_MESH_FORMAT.getVertexSize()) {
            growBlockMeshVertexBuffer(vertexCount);
        }
        blockMeshStaging = ensureBlockMeshStaging(blockMeshStaging, Math.toIntExact(vertexBytes));
        var staging = blockMeshStaging;
        compactBlockMeshVertices(geometry, staging, camera.position());
        staging.flip();
        var writeEncoder = RenderSystem.getDevice().createCommandEncoder();
        writeEncoder.writeToBuffer(Objects.requireNonNull(blockMeshVertexBuffer).slice(0, vertexBytes), staging);
        return new PreparedBlockMesh(vertexBytes, vertexCount);
    }

    private static void compactBlockMeshVertices(BlockGeometryBuffer geometry, ByteBuffer staging,
                                                 Vector3f camPos) {
        staging.clear();
        var vertices = geometry.layerVertices();
        var counts = geometry.layerVertexCounts();
        if (vertices == null || counts == null) return;
        for (var layer = 0; layer < counts.length; layer++) {
            var count = counts[layer];
            if (count == 0) continue;
            var source = vertices[layer].duplicate();
            for (var i = 0; i < count; i++) {
                staging.putFloat(source.getFloat() - camPos.x);
                staging.putFloat(source.getFloat() - camPos.y);
                staging.putFloat(source.getFloat() - camPos.z);
                staging.putFloat(source.getFloat());
                staging.putFloat(source.getFloat());
                staging.putFloat(source.getFloat());
                staging.putFloat(source.getFloat());
                staging.putFloat(source.getFloat());
                staging.putFloat(source.getFloat());
                staging.putShort(source.getShort());
                staging.putShort(source.getShort());
                source.position(source.position() + 4 * Byte.BYTES);
            }
        }
    }

    private void growBlockMeshVertexBuffer(int requiredVertices) {
        var newCapacity = Math.max(requiredVertices, blockMeshVertexCapacity * 2);
        var old = blockMeshVertexBuffer;
        blockMeshVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "VfxGraph Block Mesh Vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                (long) BLOCK_MESH_FORMAT.getVertexSize() * newCapacity);
        blockMeshVertexCapacity = newCapacity;
        if (old != null) old.close();
    }

    private static ByteBuffer ensureBlockMeshStaging(@Nullable ByteBuffer buffer, int needed) {
        if (buffer == null || buffer.capacity() < needed) {
            return BufferUtils.createByteBuffer(Math.max(needed, 4096));
        }
        buffer.clear();
        return buffer;
    }

    private void drawBlockMesh(RenderPass pass, PreparedBlockMesh prepared, RenderSpec spec) {
        pass.setPipeline(RenderSystem.getCompiledPipeline(blockMeshPipeline(spec)));
        pass.setUniform("GraphCamera", cameraUbo.slice());
        var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        pass.setUniform("Sampler0", atlas.getTextureView(), atlas.getSampler());
        pass.setUniform("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        RenderSystem.bindDefaultUniforms(pass);
        pass.setVertexBuffer(0, Objects.requireNonNull(blockMeshVertexBuffer).slice(0, prepared.vertexBytes()));
        var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var indexCount = prepared.vertexCount() * 3 / 2;
        pass.setIndexBuffer(sequential.getBuffer(indexCount), sequential.type());
        pass.drawIndexed(indexCount, 1, 0, 0, 0);
    }

    private RenderPipeline blockMeshPipeline(RenderSpec spec) {
        return blockMeshPipelines.computeIfAbsent(spec, s -> {
            var shaderSuffix = (s.vertexShader().getPath() + "_" + s.fragmentShader().getPath())
                    .replace('/', '_');
            var additive = s.blend() != RenderSpec.Blend.TRANSLUCENT;
            var pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("academy",
                            "pipeline/vfx_graph_block_mesh_" + shaderSuffix))
                    .withVertexShader(s.vertexShader())
                    .withFragmentShader(s.fragmentShader())
                    .withBindGroupLayout(BLOCK_BIND_GROUP)
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                    .withColorTargetState(new ColorTargetState(
                            additive ? BlendFunction.ADDITIVE : BlendFunction.TRANSLUCENT))
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withVertexBinding(0, BLOCK_MESH_FORMAT)
                    .build();
            RenderSystem.getCompiledPipeline(pipeline);
            return pipeline;
        });
    }

    private Map<RenderSpec, ResolvedTexture> resolveSpecTextures(List<RenderSpec> specs, boolean bloomPass) {
        var textures = new IdentityHashMap<RenderSpec, ResolvedTexture>();
        var environment = UiEnvironment.get();
        for (var spec : specs) {
            if (bloomPass ? !spec.feedsBloom() : spec.bloomOnly()) continue;
            if (spec.geometry() != RenderSpec.Geometry.QUAD) continue;
            var textureId = spec.texture();
            if (textureId != null) {
                var texture = environment.resolveTexture(textureId);
                if (texture != null) textures.put(spec, texture);
            }
        }
        return textures;
    }

    private void drawInstanced(
            RenderPass pass,
            ParticleBuffer buffer, GraphCamera camera, GpuBuffer shapeBuffer, WorldTransform transform,
            RenderSpec spec, @Nullable ResolvedTexture texture
    ) {
        var count = buffer.count();
        var matched = 0;
        if (selectedParticles.length < count)
            selectedParticles = new int[Math.max(count, selectedParticles.length * 2)];
        for (var i = 0; i < count; i++) {
            if (spec.matchesLayer(buffer.layer(i)) && particleVisible(buffer, i, camera, transform, spec)) {
                selectedParticles[matched++] = i;
            }
        }
        if (matched == 0) return;

        var bytes = (long) INSTANCE_STRIDE * matched;
        instanceData = ensureScratch(instanceData, Math.toIntExact(bytes));
        var camPos = camera.position();
        var identity = transform.isIdentity();
        for (var selected = 0; selected < matched; selected++) {
            writeInstance(buffer, selectedParticles[selected], camPos, identity, transform, instanceData);
        }
        instanceData.flip();
        var instances = RenderSystem.getDevice().createCommandEncoder().transientMemory()
                .uploadGpu(instanceData, 1L, GpuBuffer.USAGE_VERTEX);

        var depthView = sceneDepthUsable(spec.geometry())
                && sceneDepth.view() != null ? sceneDepth.view() : farView;
        var billboard = spec.geometry() == RenderSpec.Geometry.QUAD;
        drawInstancedPass(pass, pipelineFor(spec), shapeBuffer, cameraUbo.slice(),
                instances, matched, billboard, depthView, texture);
    }

    private static ByteBuffer ensureScratch(@Nullable ByteBuffer buffer, int needed) {
        if (buffer == null || buffer.capacity() < needed) {
            return BufferUtils.createByteBuffer(Math.max(needed, 4096));
        }
        buffer.clear();
        return buffer;
    }

    private void drawInstancedPass(
            RenderPass pass, RenderPipeline pipeline, GpuBuffer shapeBuffer,
            GpuBufferSlice cameraSlice, GpuBufferSlice instanceSlice, int count,
            boolean billboard, GpuTextureView depthView, @Nullable ResolvedTexture texture
    ) {
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        pass.setUniform("GraphCamera", cameraSlice);
        if (billboard) {
            if (texture == null) {
                pass.setUniform("Sampler0", noiseView, noiseSampler);
            } else {
                pass.setUniform("Sampler0", texture.getView(), texture.getSampler());
            }
            pass.setUniform("Sampler1", depthView, depthSampler);
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
        lineData = ensureScratch(lineData, Math.toIntExact(neededBytes));
        if (line) {
            buildLineVertices(buffer, camera, lineData, transform, spec);
        } else {
            buildRibbonVertices(buffer, camera, lineData, transform, spec);
        }
        lineData.flip();
        var vertices = RenderSystem.getDevice().createCommandEncoder().transientMemory()
                .uploadGpu(lineData, 1L, GpuBuffer.USAGE_VERTEX);

        pass.setPipeline(RenderSystem.getCompiledPipeline(pipelineFor(spec)));
        pass.setUniform("GraphCamera", cameraUbo.slice());
        pass.setVertexBuffer(0, vertices);
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
        screenFlashColorBuffer.close();
        noiseSampler.close();
        noiseView.close();
        noiseTexture.close();
        depthSampler.close();
        farView.close();
        farTexture.close();
        sceneDepth.close();
        if (arcVertexStaging != null) {
            MemoryUtil.memFree(arcVertexStaging);
            arcVertexStaging = null;
        }
        if (arcIndexStaging != null) {
            MemoryUtil.memFree(arcIndexStaging);
            arcIndexStaging = null;
        }
        if (arcTubeVertexBuffer != null) arcTubeVertexBuffer.close();
        if (arcTubeIndexBuffer != null) arcTubeIndexBuffer.close();
        if (instancedTexVertexBuffer != null) instancedTexVertexBuffer.close();
        if (instancedTexInstanceBuffer != null) instancedTexInstanceBuffer.close();
        if (instancedTexInstanceStaging != null) {
            MemoryUtil.memFree(instancedTexInstanceStaging);
            instancedTexInstanceStaging = null;
        }
        if (instancedParamsUbo != null) instancedParamsUbo.close();
        instancedParamsStaging = null;
        if (blockMeshVertexBuffer != null) blockMeshVertexBuffer.close();
        blockMeshStaging = null;
        arcLightningUbo.close();
        distortionUbo.close();
        distortionVertexBuffer.close();
        distortionSampler.close();
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

    static boolean sceneDepthUsable(
            RenderSpec.Geometry geometry
    ) {
        return geometry == RenderSpec.Geometry.QUAD;
    }

    private static void clearTarget(GpuDevice device, GpuTextureView target, @Nullable GpuTextureView depth) {
        var encoder = device.createCommandEncoder();
        encoder.clearColorTexture(target.texture(), CLEAR_COLOR);
        if (depth != null) encoder.clearDepthTexture(depth.texture(), 0.0);
    }

    private void drawSurfaces(RenderPass pass, List<SurfaceMesh> surfaces, GraphCamera camera) {
        var totalVerts = 0;
        for (var sm : surfaces) {
            totalVerts += sm.triangles().length / 3;
        }
        if (totalVerts == 0) return;

        var bytes = (long) SIMPLE_FORMAT.getVertexSize() * totalVerts;
        surfaceData = ensureScratch(surfaceData, Math.toIntExact(bytes));
        var camPos = camera.position();
        for (var sm : surfaces) {
            var tris = sm.triangles();
            for (var i = 0; i + 2 < tris.length; i += 3) {
                putVertex(surfaceData, tris[i] - camPos.x, tris[i + 1] - camPos.y, tris[i + 2] - camPos.z,
                        sm.r(), sm.g(), sm.b(), sm.a());
            }
        }
        surfaceData.flip();
        var vertices = RenderSystem.getDevice().createCommandEncoder().transientMemory()
                .uploadGpu(surfaceData, 1L, GpuBuffer.USAGE_VERTEX);

        pass.setPipeline(RenderSystem.getCompiledPipeline(surfacePipeline()));
        pass.setUniform("GraphCamera", cameraUbo.slice());
        pass.setVertexBuffer(0, vertices);
        pass.setVertexBuffer(1, null);
        var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
        var indices = sequential.getBuffer(totalVerts);
        pass.setIndexBuffer(indices, sequential.type());
        pass.drawIndexed(totalVerts, 1, 0, 0, 0);
    }

    private static ByteBuffer buildNoiseTile() {
        var rng = new Random(0xC0FFEEL);
        var grid = new float[NOISE_SIZE + 1][NOISE_SIZE + 1];
        for (var row : grid) {
            for (var x = 0; x <= NOISE_SIZE; x++) {
                row[x] = rng.nextFloat();
            }
        }
        var data = new float[NOISE_SIZE * NOISE_SIZE];
        var min = Float.MAX_VALUE;
        var max = -Float.MAX_VALUE;
        for (var y = 0; y < NOISE_SIZE; y++) {
            for (var x = 0; x < NOISE_SIZE; x++) {
                var sum = 0f;
                var amp = 0.5f;
                for (var o = 0; o < 3; o++) {
                    var freq = (float) (1 << o);
                    sum += amp * valueNoise(x * freq, y * freq, grid);
                    amp *= 0.5f;
                }
                data[y * NOISE_SIZE + x] = sum;
                min = Math.min(min, sum);
                max = Math.max(max, sum);
            }
        }
        var out = BufferUtils.createByteBuffer(NOISE_SIZE * NOISE_SIZE * 4);
        var range = Math.max(max - min, 1e-6f);
        for (var value : data) {
            var b = (int) ((value - min) / range * 255f);
            var channel = (byte) b;
            out.put(channel).put(channel).put(channel).put((byte) 255);
        }
        out.flip();
        return out;
    }

    private static float valueNoise(float x, float y, float[][] grid) {
        var xi = Math.floorMod((int) Math.floor(x), NOISE_SIZE);
        var yi = Math.floorMod((int) Math.floor(y), NOISE_SIZE);
        var xf = x - (float) Math.floor(x);
        var yf = y - (float) Math.floor(y);
        var x1 = (xi + 1) % NOISE_SIZE;
        var y1 = (yi + 1) % NOISE_SIZE;
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
