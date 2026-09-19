package org.academy.api.client.render.post;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.academy.api.common.vfx.DirectionalArea;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.*;

/** Actual material/geometry masks, isolated from vanilla entity outlines and the main scene. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class WorldSurfaceMasks {
    private record EntityDraw(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                              Matrix4f pose, Matrix3f normal, boolean selected) { }
    private static final List<EntityDraw> ENTITIES = new ArrayList<>();
    private static final Map<RenderPipeline, RenderPipeline> MASK_PIPELINES = new IdentityHashMap<>();
    private static List<DirectionalArea> areas = List.of();
    private static TextureTarget opaque, transparent, entities, selected;
    private static FeatureRenderDispatcher dispatcher;
    private static RenderBuffers buffers;
    private static RenderTarget replayTarget;
    private static boolean ready;
    private static final OutputTarget MASK_OUTPUT = new OutputTarget("academy_surface_mask", () -> replayTarget);
    private WorldSurfaceMasks() { }

    public static boolean active() { return !areas.isEmpty(); }
    public static boolean ready() { return ready; }
    public static TextureTarget opaque() { return opaque; }
    public static TextureTarget transparent() { return transparent; }
    public static TextureTarget entities() { return entities; }
    public static TextureTarget selected() { return selected; }

    public static void beginFrame(List<DirectionalArea> regions) {
        areas = regions; ENTITIES.clear(); ready = false;
        if (regions.isEmpty()) releaseTargets();
    }

    public static void capture(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack pose) {
        if (!active() || replayTarget != null || state.isInvisible) return;
        var center = new Vec3(state.x, state.y + state.boundingBoxHeight / 2, state.z);
        double margin = Math.max(state.boundingBoxHeight, state.boundingBoxWidth);
        if (areas.stream().noneMatch(a -> center.distanceTo(a.origin()) <= a.radius() + margin)) return;
        ENTITIES.add(new EntityDraw(state, camera, x, y, z, new Matrix4f(pose.last().pose()),
                new Matrix3f(pose.last().normal()), areas.stream().anyMatch(a -> a.contains(center))));
    }

    @SubscribeEvent public static void afterOpaque(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        if (!active()) return;
        ensureTargets();
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        opaque.copyDepthFrom(main);
        transparent.copyDepthFrom(main);
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(transparent.getColorTexture(), new Vector4f(0));
        ready = true;
    }

    private static void ensureTargets() {
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (opaque != null && opaque.width == main.width && opaque.height == main.height) return;
        releaseTargets();
        opaque = target("Opaque surface depth", main);
        transparent = target("Transparent material mask", main);
        entities = target("Visible entity mask", main);
        selected = target("Selected entity mask", main);
    }

    private static TextureTarget target(String name, RenderTarget main) {
        return new TextureTarget(name, main.width, main.height, true, GpuFormat.RGBA8_UNORM);
    }

    /** Replay only the actual translucent chunk mesh with its UVs, alpha and surface depth. */
    public static void captureTransparent(ChunkSectionsToRender chunks, GpuSampler sampler) {
        if (!active() || !ready) return;
        var groups = chunks.drawGroupsPerLayer().get(ChunkSectionLayer.TRANSLUCENT);
        if (groups == null || chunks.maxIndicesRequired() == 0) return;
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var indexBuffer = indices.getBuffer(chunks.maxIndicesRequired());
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Directional field transparent surfaces", transparent.getColorTextureView(), Optional.empty(),
                transparent.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(maskPipeline(ChunkSectionLayer.TRANSLUCENT.pipeline()));
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("Sampler0", chunks.textureView(), sampler);
            pass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : groups.values()) if (!draws.isEmpty()) {
                pass.drawMultipleIndexed(draws.reversed(), indexBuffer, indices.type(), List.of("ChunkSection"), chunks.chunkSectionInfos());
            }
        }
    }

    /** Called by the scoped RenderType mixin; retain the original vertex/fragment shaders and textures. */
    public static PreparedRenderType redirect(PreparedRenderType original) {
        if (replayTarget == null) return original;
        return new PreparedRenderType(maskPipeline(original.pipeline()), MASK_OUTPUT, original.dynamicTransforms(),
                original.scissorState(), original.textures());
    }

    private static RenderPipeline maskPipeline(RenderPipeline source) {
        return MASK_PIPELINES.computeIfAbsent(source, key -> key.toBuilder()
                .withLocation(org.academy.AcademyCraft.academy("pipeline/surface_mask/" + key.getLocation().getNamespace() + "/" + key.getLocation().getPath()))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true)).build());
    }

    public static void renderEntities() {
        if (!active() || !ready) return;
        var mc = Minecraft.getInstance();
        if (dispatcher == null) {
            buffers = new RenderBuffers(1);
            dispatcher = new FeatureRenderDispatcher(buffers, mc.getModelManager(), mc.getAtlasManager(), mc.font,
                    mc.gameRenderer.gameRenderState());
        }
        replay(entities, false);
        replay(selected, true);
        buffers.endFrame();
    }

    private static void replay(RenderTarget output, boolean onlySelected) {
        output.copyDepthFrom(opaque);
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(output.getColorTexture(), new Vector4f(0));
        var storage = new SubmitNodeStorage();
        replayTarget = output;
        try {
            for (var draw : ENTITIES) {
                if (onlySelected && !draw.selected) continue;
                var state = draw.state;
                var name = state.nameTag; var score = state.scoreText; var leashes = state.leashStates;
                var shadows = List.copyOf(state.shadowPieces);
                int outline = state.outlineColor; boolean fire = state.displayFireAnimation;
                try {
                    state.nameTag = null; state.scoreText = null; state.leashStates = null;
                    state.shadowPieces.clear(); state.outlineColor = 0; state.displayFireAnimation = false;
                    var pose = new PoseStack(); pose.last().pose().set(draw.pose); pose.last().normal().set(draw.normal);
                    Minecraft.getInstance().getEntityRenderDispatcher().submit(state, draw.camera, draw.x, draw.y, draw.z, pose, storage);
                } finally {
                    state.nameTag = name; state.scoreText = score; state.leashStates = leashes;
                    state.shadowPieces.clear(); state.shadowPieces.addAll(shadows);
                    state.outlineColor = outline; state.displayFireAnimation = fire;
                }
            }
            dispatcher.renderAllFeatures(storage);
        } finally { replayTarget = null; }
    }

    public static void reset() { areas = List.of(); ENTITIES.clear(); ready = false; releaseTargets(); }
    private static void releaseTargets() {
        for (var target : new TextureTarget[]{opaque, transparent, entities, selected}) if (target != null) target.destroyBuffers();
        opaque = transparent = entities = selected = null;
    }
    public static void close() {
        reset(); MASK_PIPELINES.clear();
        if (dispatcher != null) dispatcher.close();
        if (buffers != null) buffers.close();
        dispatcher = null; buffers = null;
    }
}
