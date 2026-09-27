package org.academy.api.client.render.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.academy.api.common.vfx.DirectionalArea;
import org.academy.mixin.client.ChunkSectionsToRenderInvoker;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.*;

/**
 * Actual material/geometry masks, isolated from vanilla entity outlines and the main scene.
 *
 * <p>26.3 note: the legacy {@code OutputTarget}/scoped {@code PreparedRenderType} redirect and
 * {@code ChunkSectionsToRender#drawGroupsPerLayer} primitives were removed from the renderer.
 * Masks are now captured by opening explicit render passes on dedicated targets and replaying
 * entity features through {@link FeatureRenderDispatcher#renderAllFeatures}. Mask pipelines reuse
 * the source pipelines because 26.3 no longer exposes a {@code RenderPipeline#toBuilder}.</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public final class WorldSurfaceMasks {
    private record EntityDraw(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                              Matrix4f pose, Matrix3f normal, boolean selected) {
    }

    private static final List<EntityDraw> ENTITIES = new ArrayList<>();
    private static final Map<RenderPipeline, RenderPipeline> MASK_PIPELINES = new IdentityHashMap<>();
    private static final int MASK_INDEX_COUNT = 6 * 65536;
    private static List<DirectionalArea> areas = List.of();
    private static TextureTarget opaque, transparent, entities, selected;
    private static FeatureRenderDispatcher dispatcher;
    private static RenderBuffers buffers;
    private static boolean ready;

    private WorldSurfaceMasks() {
    }

    public static boolean active() {
        return !areas.isEmpty();
    }

    public static boolean ready() {
        return ready;
    }

    public static TextureTarget opaque() {
        return opaque;
    }

    public static TextureTarget transparent() {
        return transparent;
    }

    public static TextureTarget entities() {
        return entities;
    }

    public static TextureTarget selected() {
        return selected;
    }

    public static void beginFrame(List<DirectionalArea> regions) {
        areas = regions;
        ENTITIES.clear();
        ready = false;
        if (regions.isEmpty()) releaseTargets();
    }

    public static void capture(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack pose) {
        if (!active() || state.isInvisible) return;
        var center = new Vec3(state.x, state.y + state.boundingBoxHeight / 2, state.z);
        double margin = Math.max(state.boundingBoxHeight, state.boundingBoxWidth);
        if (areas.stream().noneMatch(a -> center.distanceTo(a.origin()) <= a.radius() + margin)) return;
        ENTITIES.add(new EntityDraw(state, camera, x, y, z, new Matrix4f(pose.last().pose()),
                new Matrix3f(pose.last().normal()), areas.stream().anyMatch(a -> a.contains(center))));
    }

    @SubscribeEvent
    public static void afterOpaque(RenderLevelStageEvent.AfterOpaqueFeatures event) {
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
        return new TextureTarget(name, main.width, main.height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
    }

    /**
     * Replay the actual translucent chunk mesh into the transparent mask target.
     */
    public static void captureTransparent(ChunkSectionsToRender chunks, GpuSampler sampler) {
        if (!active() || !ready) return;
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        indices.requestIndexCount(MASK_INDEX_COUNT);
        indices.resizeToRequestedIndexCount();
        var indexBuffer = indices.getBuffer();
        var pipeline = maskPipeline(ChunkSectionLayer.TRANSLUCENT.pipeline(true));
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Directional field transparent surfaces", transparent.getColorTextureView(), Optional.empty(),
                transparent.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            ((ChunkSectionsToRenderInvoker) chunks).academy$render(
                    ChunkSectionLayer.TRANSLUCENT, pass, indexBuffer, indices.type(), pipeline, pipeline);
        }
    }

    private static RenderPipeline maskPipeline(RenderPipeline source) {
        // 26.3 removed RenderPipeline#toBuilder, so the source pipeline is reused as-is.
        return MASK_PIPELINES.computeIfAbsent(source, key -> key);
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

    private static void replay(TextureTarget output, boolean onlySelected) {
        output.copyDepthFrom(opaque);
        var storage = new SubmitNodeStorage();
        for (var draw : ENTITIES) {
            if (onlySelected && !draw.selected) continue;
            var state = draw.state;
            var name = state.nameTag;
            var score = state.scoreText;
            var leashes = state.leashStates;
            var shadows = List.copyOf(state.shadowPieces);
            int outline = state.outlineColor;
            boolean fire = state.displayFireAnimation;
            try {
                state.nameTag = null;
                state.scoreText = null;
                state.leashStates = null;
                state.shadowPieces.clear();
                state.outlineColor = 0;
                state.displayFireAnimation = false;
                var pose = new PoseStack();
                pose.last().pose().set(draw.pose);
                pose.last().normal().set(draw.normal);
                Minecraft.getInstance().getEntityRenderDispatcher().submit(state, draw.camera, draw.x, draw.y, draw.z, pose, storage);
            } finally {
                state.nameTag = name;
                state.scoreText = score;
                state.leashStates = leashes;
                state.shadowPieces.clear();
                state.shadowPieces.addAll(shadows);
                state.outlineColor = outline;
                state.displayFireAnimation = fire;
            }
        }
        try (var frame = dispatcher.prepareFrame(storage);
             var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                     () -> "Directional field entity mask", output.getColorTextureView(),
                     Optional.of(new Vector4f(0)), output.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        }
        storage.drainPhases(ignored -> {
        });
    }

    public static void reset() {
        areas = List.of();
        ENTITIES.clear();
        ready = false;
        releaseTargets();
    }

    private static void releaseTargets() {
        for (var target : new TextureTarget[]{opaque, transparent, entities, selected})
            if (target != null) target.destroyBuffers();
        opaque = transparent = entities = selected = null;
    }

    public static void close() {
        reset();
        MASK_PIPELINES.clear();
        if (dispatcher != null) dispatcher.close();
        if (buffers != null) buffers.close();
        dispatcher = null;
        buffers = null;
    }
}
