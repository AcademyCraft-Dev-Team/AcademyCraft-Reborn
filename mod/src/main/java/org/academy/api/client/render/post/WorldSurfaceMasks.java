package org.academy.api.client.render.post;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.vfx.DirectionalArea;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class WorldSurfaceMasks {
    private record EntityDraw(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                              Matrix4f pose, Matrix3f normal, boolean selected) {
    }

    private record TranslucentDraw(ChunkSectionsToRender chunks, GpuSampler sampler, GpuTextureView atlas) {
    }

    private static final List<EntityDraw> ENTITIES = new ArrayList<>();
    private static List<DirectionalArea> areas = List.of();
    private static TextureTarget transparent, entities, selected;
    private static FeatureRenderDispatcher dispatcher;
    private static RenderBuffers buffers;
    private static @Nullable TranslucentDraw translucent;
    private static boolean ready;
    private static boolean maskPassActive;

    private WorldSurfaceMasks() {
    }

    public static boolean active() {
        return !areas.isEmpty();
    }

    public static boolean ready() {
        return ready;
    }

    public static @Nullable GpuTextureView sceneDepth() {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
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
        translucent = null;
        ready = false;
        if (regions.isEmpty()) releaseTargets();
    }

    public static void capture(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack pose) {
        if (!active() || maskPassActive || state.isInvisible) return;
        var center = new Vec3(state.x, state.y + state.boundingBoxHeight / 2, state.z);
        double margin = Math.max(state.boundingBoxHeight, state.boundingBoxWidth);
        if (areas.stream().noneMatch(a -> center.distanceTo(a.origin()) <= a.radius() + margin)) return;
        ENTITIES.add(new EntityDraw(state, camera, x, y, z, new Matrix4f(pose.last().pose()),
                new Matrix3f(pose.last().normal()), areas.stream().anyMatch(a -> a.contains(center))));
    }

    public static void recordTranslucent(ChunkSectionsToRender chunks, GpuSampler sampler, GpuTextureView atlas) {
        if (active() && !maskPassActive) translucent = new TranslucentDraw(chunks, sampler, atlas);
    }

    public static void captureMasks() {
        if (!active()) return;
        var sceneDepth = sceneDepth();
        if (sceneDepth == null) return;
        ensureTargets();
        ensureDispatcher();
        maskPassActive = true;
        try {
            captureTranslucent(sceneDepth);
            replay(entities, false, sceneDepth);
            replay(selected, true, sceneDepth);
        } finally {
            maskPassActive = false;
        }
        buffers.endFrame();
        ready = true;
    }

    private static void captureTranslucent(GpuTextureView sceneDepth) {
        var draw = translucent;
        if (draw == null) return;
        var color = transparent.getColorTextureView();
        var depth = transparent.getDepthTextureView();
        if (color == null || depth == null) return;
        seedDepth(depth, sceneDepth);
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Directional field transparent surfaces", color, Optional.of(new Vector4f(0)), depth, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            draw.chunks().renderGroup(ChunkSectionLayerGroup.TRANSLUCENT, pass, draw.sampler(), draw.atlas(), false);
        }
    }

    private static void seedDepth(GpuTextureView depth, GpuTextureView sceneDepth) {
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                RenderPassDescriptor.builder(() -> "Directional field mask depth").withDepthAttachment(depth).build())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.BLIT_DEPTH));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", sceneDepth, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void ensureTargets() {
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (transparent != null && transparent.width == main.width && transparent.height == main.height) return;
        releaseTargets();
        transparent = target("Transparent material mask", main);
        entities = target("Visible entity mask", main);
        selected = target("Selected entity mask", main);
    }

    private static TextureTarget target(String name, RenderTarget main) {
        return new TextureTarget(name, main.width, main.height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
    }

    private static void ensureDispatcher() {
        if (dispatcher != null) return;
        var mc = Minecraft.getInstance();
        buffers = new RenderBuffers(1);
        dispatcher = new FeatureRenderDispatcher(buffers, mc.getModelManager(), mc.getAtlasManager(), mc.font,
                mc.gameRenderer.gameRenderState());
    }

    private static void replay(TextureTarget output, boolean onlySelected, GpuTextureView sceneDepth) {
        var color = output.getColorTextureView();
        var depth = output.getDepthTextureView();
        if (color == null || depth == null) return;
        seedDepth(depth, sceneDepth);
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
                     () -> "Directional field entity mask", color,
                     Optional.of(new Vector4f(0)), depth, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        }
        storage.drainPhases(ignored -> {
        });
    }

    public static void reset() {
        areas = List.of();
        ENTITIES.clear();
        translucent = null;
        ready = false;
        releaseTargets();
    }

    private static void releaseTargets() {
        for (var target : new TextureTarget[]{transparent, entities, selected})
            if (target != null) target.destroyBuffers();
        transparent = entities = selected = null;
    }

    public static void close() {
        reset();
        if (dispatcher != null) dispatcher.close();
        if (buffers != null) buffers.close();
        dispatcher = null;
        buffers = null;
    }
}
