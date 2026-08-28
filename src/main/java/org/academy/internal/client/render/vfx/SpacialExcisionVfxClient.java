package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.render.Render;
import org.academy.api.client.render.TextureBinding;
import org.academy.api.client.render.UniformBinding;
import org.academy.api.client.render.post.PostEffect;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxCamera;
import org.academy.api.client.render.vfx.VfxManager;
import org.academy.api.client.render.vfx.VfxPhase;
import org.academy.api.client.render.vfx.VfxPipelines;
import org.academy.api.client.render.vfx.VfxRenderContext;
import org.academy.api.client.render.vfx.VfxRenderData;
import org.academy.api.client.render.vfx.VfxRenderer;
import org.academy.api.client.render.vfx.VfxRegistry;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.internal.common.ability.teleport.SpacialExcisionMath;
import org.academy.internal.client.render.vfx.SpacialExcisionRenderMath.AffineCutMapping;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/** Client state and renderer for the world-space spatial cut. */
public final class SpacialExcisionVfxClient {
    private static final int VERTEX_STRIDE = 7 * Float.BYTES;
    private static final int LINE_VERTEX_STRIDE = VfxPipelines.SPATIAL_CUT_LINE_FORMAT.getVertexSize();
    // The crack and its immediate surroundings move as one rigid slab. Only
    // the short region outside that slab rapidly returns to the untouched scene.
    private static final float DISPLACEMENT_RIGID_HALF_WIDTH = 12.0f;
    private static final float MIN_DISPLACEMENT_FALLOFF_WIDTH = 6.0f;
    private static final float HORIZONTAL_SLIDE_DISTANCE_BLOCKS = 6.0f;
    private static final float VERTICAL_CONVERGENCE_DISTANCE_BLOCKS = 0.5f;
    static final int MAX_SOURCE_VALIDATION_CUTS = 5;
    private static final float PORTAL_MAX_HALF_WIDTH = 0.32f;
    private static final double MAX_RENDER_DISTANCE = 512.0;
    private static final double TOMBSTONE_TICKS = 1200.0;
    private static final int MAX_ENDED_SESSION_TOMBSTONES = 4096;
    private static final int INITIAL_VERTICES = 256;
    private static final int LINE_LONGITUDINAL_SECTIONS = 24;
    private static final int MASK_FALLOFF_STEPS = 6;
    private static final int MASK_CROSS_SECTIONS = MASK_FALLOFF_STEPS + 2;
    private static final int MASK_LONGITUDINAL_SECTIONS =
            MASK_FALLOFF_STEPS * 2 + LINE_LONGITUDINAL_SECTIONS + 1;
    // Clipping one triangle against the near plane can turn it into a quad (six vertices).
    private static final int MASK_MAX_VERTICES_PER_SEGMENT =
            (MASK_LONGITUDINAL_SECTIONS - 1)
                    * (MASK_CROSS_SECTIONS - 1) * 2 * 2 * 6;
    private static final int LINE_MAX_VERTICES_PER_SEGMENT =
            LINE_LONGITUDINAL_SECTIONS * 2 * 6;
    private static final float NEAR_CLIP_MIN_W = 5.0e-2f;
    static final int LINE_CLIP_VERTEX_COMPONENTS = 8;
    static final int LINE_CLIP_MAX_VERTICES = 4;
    static final int LINE_CLIP_SCRATCH_COMPONENTS =
            LINE_CLIP_VERTEX_COMPONENTS * LINE_CLIP_MAX_VERTICES;
    private static final int SOURCE_VALIDATION_IDLE_FRAME_LIMIT = 120;
    private static final int FLOW_UNIFORM_SIZE = new Std140SizeCalculator().putVec4().get();
    private static final int POST_UNIFORM_SIZE = postUniformSize();
    private static final Identifier END_SKY_TEXTURE =
            Identifier.withDefaultNamespace("textures/environment/end_sky.png");
    private static final Identifier END_PORTAL_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/end_portal/end_portal.png");
    // Launch with -Dacademy.spatialCut.debugClassification=true to color the
    // post path by selected-depth, mask, near-guard, UV, and source-plane result.
    private static final boolean DEBUG_POST_CLASSIFICATION =
            Boolean.getBoolean("academy.spatialCut.debugClassification");
    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();
    private static final Map<UUID, Double> ENDED_SESSIONS =
            new LinkedHashMap<>(16, 0.75f, true);
    private static final CutVfx VFX = new CutVfx();
    private static final CutRenderer RENDERER = new CutRenderer();
    private static final CutLineRenderer<CutMaterialData> MATERIAL_RENDERER =
            new CutLineRenderer<>(LineKind.MATERIAL);
    private static final CutLineRenderer<CutGlowData> GLOW_RENDERER =
            new CutLineRenderer<>(LineKind.GLOW);

    private static boolean registered;
    private static final RenderTarget[] sourceValidationDepthTargets =
            new RenderTarget[MAX_SOURCE_VALIDATION_CUTS];
    private static final RenderTarget[] sourceValidationMaskTargets =
            new RenderTarget[MAX_SOURCE_VALIDATION_CUTS];
    private static final boolean[] sourceValidationReady =
            new boolean[MAX_SOURCE_VALIDATION_CUTS];
    private static final int[] sourceValidationIdleFrames =
            new int[MAX_SOURCE_VALIDATION_CUTS];
    private static final Vector4f[] sourceValidationPlanes = newVector4Array();
    private static final Vector4f[] sourceValidationOffsets = newVector4Array();
    private static final Map<Long, Integer> sourceValidationSides = new HashMap<>();
    private static final Matrix4f sourceValidationInverseProjection = new Matrix4f();
    private static @Nullable GpuBuffer postUniforms;

    private static Vector4f[] newVector4Array() {
        var values = new Vector4f[MAX_SOURCE_VALIDATION_CUTS];
        for (var i = 0; i < values.length; i++) values[i] = new Vector4f();
        return values;
    }

    private static int postUniformSize() {
        var calculator = new Std140SizeCalculator().putVec4();
        for (var i = 0; i < MAX_SOURCE_VALIDATION_CUTS * 2; i++) {
            calculator.putVec4();
        }
        return calculator.putMat4f().get();
    }

    private SpacialExcisionVfxClient() {
    }

    static double localSessionStartTick(
            double clientNow,
            long serverStartTick,
            long firstCreatedTick
    ) {
        if (!Double.isFinite(clientNow) || firstCreatedTick < serverStartTick) {
            return Double.NaN;
        }
        return clientNow - ((double) firstCreatedTick - (double) serverStartTick);
    }

    static double localSessionTick(
            double localStartTick,
            long serverStartTick,
            long serverTick
    ) {
        if (!Double.isFinite(localStartTick) || serverTick < serverStartTick) {
            return Double.NaN;
        }
        return localStartTick + ((double) serverTick - (double) serverStartTick);
    }

    static boolean validTimeline(long sessionStartTick, long createdTick, long endTick) {
        if (createdTick < sessionStartTick || createdTick >= endTick) return false;
        try {
            var duration = Math.subtractExact(endTick, sessionStartTick);
            return duration > 0L && duration <= 600L;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    static void retainSourceValidationSides(
            Map<Long, Integer> sides,
            Set<Long> activeIds
    ) {
        if (sides == null) return;
        if (activeIds == null || activeIds.isEmpty()) {
            sides.clear();
            return;
        }
        sides.keySet().retainAll(activeIds);
    }

    static int lineGrowthVertexRequirement(int writtenVertices) {
        return growthVertexRequirement(writtenVertices, LINE_MAX_VERTICES_PER_SEGMENT, 64);
    }

    /**
     * Clips one triangle-derived convex polygon against {@code clipW >= minimumW} without
     * allocating. Input and output must be distinct fixed scratch arrays. A triangle clipped by
     * one plane has at most four vertices; anything outside that contract fails closed.
     */
    static int clipLineVertices(
            double[] input,
            int inputCount,
            float minimumW,
            double[] output
    ) {
        if (input == null || output == null || input == output
                || inputCount < 3 || inputCount > LINE_CLIP_MAX_VERTICES
                || input.length < inputCount * LINE_CLIP_VERTEX_COMPONENTS
                || output.length < LINE_CLIP_SCRATCH_COMPONENTS
                || !Float.isFinite(minimumW)) {
            return 0;
        }
        for (var component = 0;
             component < inputCount * LINE_CLIP_VERTEX_COMPONENTS;
             component++) {
            if (!Double.isFinite(input[component])) return 0;
        }

        var outputCount = 0;
        var previous = (inputCount - 1) * LINE_CLIP_VERTEX_COMPONENTS;
        var previousInside = input[previous + 7] >= minimumW;
        for (var vertex = 0; vertex < inputCount; vertex++) {
            var current = vertex * LINE_CLIP_VERTEX_COMPONENTS;
            var currentInside = input[current + 7] >= minimumW;
            if (currentInside != previousInside) {
                if (outputCount >= LINE_CLIP_MAX_VERTICES
                        || !interpolateLineClip(
                        input, previous, current, minimumW,
                        output, outputCount * LINE_CLIP_VERTEX_COMPONENTS)) {
                    return 0;
                }
                outputCount++;
            }
            if (currentInside) {
                if (outputCount >= LINE_CLIP_MAX_VERTICES) return 0;
                System.arraycopy(input, current, output,
                        outputCount * LINE_CLIP_VERTEX_COMPONENTS,
                        LINE_CLIP_VERTEX_COMPONENTS);
                outputCount++;
            }
            previous = current;
            previousInside = currentInside;
        }
        return outputCount >= 3 ? outputCount : 0;
    }

    private static boolean interpolateLineClip(
            double[] input,
            int from,
            int to,
            float minimumW,
            double[] output,
            int destination
    ) {
        var t = SpacialExcisionMath.frontClipInterpolation(
                (float) input[from + 7], (float) input[to + 7], minimumW);
        if (!Float.isFinite(t)) return false;
        for (var component = 0; component < 3; component++) {
            var value = input[from + component]
                    + (input[to + component] - input[from + component]) * t;
            if (!Double.isFinite(value)) return false;
            output[destination + component] = value;
        }
        for (var component = 3; component < 7; component++) {
            var value = lerpLineAttribute(
                    (float) input[from + component], (float) input[to + component], t);
            if (!Float.isFinite(value)) return false;
            output[destination + component] = value;
        }
        output[destination + 7] = minimumW;
        return true;
    }

    private static float lerpLineAttribute(float from, float to, float t) {
        return from + (to - from) * t;
    }

    static int growthVertexRequirement(int writtenVertices, int additionalVertices, int paddingVertices) {
        var required = (long) writtenVertices + additionalVertices + paddingVertices;
        return writtenVertices < 0 || additionalVertices < 0 || paddingVertices < 0
                || required > Integer.MAX_VALUE ? -1 : (int) required;
    }

    static int growthByteCapacity(int oldByteCapacity, int requiredVertices, int stride) {
        if (oldByteCapacity < 0 || requiredVertices < 0 || stride <= 0) return -1;
        var requiredBytes = (long) requiredVertices * stride;
        var doubledBytes = (long) oldByteCapacity * 2L;
        var capacity = Math.max(requiredBytes, doubledBytes);
        return capacity > Integer.MAX_VALUE ? -1 : (int) capacity;
    }

    static @Nullable ByteBuffer grow(ByteBuffer old, int requiredVertices, int stride) {
        var byteCapacity = old == null ? -1
                : growthByteCapacity(old.capacity(), requiredVertices, stride);
        if (byteCapacity < 0) return null;
        var replacement = BufferUtils.createByteBuffer(byteCapacity);
        old.flip();
        replacement.put(old);
        return replacement;
    }

    /** Called during skill client initialization, before VfxManager.init(). */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        // The material renderer is initialized by the registry but receives no
        // frame data: its geometry is drawn after the scene displacement.
        VfxRegistry.register(CutMaterialData.class, VfxPhase.WORLD_TRANSLUCENT, MATERIAL_RENDERER);
        VfxRegistry.register(CutGlowData.class, VfxPhase.WORLD_GLOW, GLOW_RENDERER);
        VfxManager.INSTANCE.spawn(VFX);
    }

    public static void addSegment(
            UUID sessionId,
            long sessionStartTick,
            long createdTick,
            long endTick,
            Identifier dimension,
            Vec3 start,
            Vec3 end,
            float preTeleportYaw,
            long seed
    ) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (sessionId == null || dimension == null || start == null || end == null
                || !validTimeline(sessionStartTick, createdTick, endTick)
                || !SpacialExcisionMath.isFinite(start)
                || !SpacialExcisionMath.isFinite(end)
                || !Float.isFinite(preTeleportYaw)) {
            return;
        }
        var basis = SpacialExcisionMath.planeBasis(start, end, preTeleportYaw).orElse(null);
        if (basis == null) return;
        var delta = end.subtract(start);
        var length = delta.length();
        if (!SpacialExcisionMath.isFinite(delta) || !Double.isFinite(length) || length <= 0.001) {
            return;
        }
        var center = start.add(end).scale(0.5);
        var requestedFalloffWidth = CutVfx.displacementFalloffWidth(basis);
        var clientNow = (double) level.getGameTime()
                + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        synchronized (SESSIONS) {
            pruneEndedSessions(clientNow);
            if (ENDED_SESSIONS.containsKey(sessionId)) return;
            var session = SESSIONS.get(sessionId);
            if (session == null) {
                var localStartTick = localSessionStartTick(
                        clientNow, sessionStartTick, createdTick);
                if (!Double.isFinite(localStartTick)) return;
                session = new Session(sessionStartTick, endTick, localStartTick);
                SESSIONS.put(sessionId, session);
            }
            if (session.endTick != endTick || session.startTick != sessionStartTick) return;
            var localCreatedTick = localSessionTick(
                    session.localStartTick, sessionStartTick, createdTick);
            if (!Double.isFinite(localCreatedTick)) return;
            session.segments.add(new Segment(
                    localCreatedTick, dimension, start, end, delta, length, center,
                    requestedFalloffWidth, seed,
                    basis, SpacialExcisionRenderMath.flowParams(seed),
                    SpacialExcisionRenderMath.breathParams(seed)));
        }
    }

    public static void endSession(UUID sessionId) {
        if (sessionId == null) return;
        synchronized (SESSIONS) {
            SESSIONS.remove(sessionId);
            var level = Minecraft.getInstance().level;
            var now = level == null ? 0.0 : (double) level.getGameTime();
            rememberEndedSession(sessionId, now + TOMBSTONE_TICKS);
            pruneEndedSessions(now);
        }
    }

    public static void clear() {
        synchronized (SESSIONS) {
            SESSIONS.clear();
            ENDED_SESSIONS.clear();
        }
        VFX.clearLatestFrame();
        for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
            sourceValidationReady[slot] = false;
            sourceValidationIdleFrames[slot] = 0;
            sourceValidationPlanes[slot].zero();
            sourceValidationOffsets[slot].zero();
        }
    }

    private static void pruneEndedSessions(double now) {
        ENDED_SESSIONS.entrySet().removeIf(entry -> now >= entry.getValue());
    }

    private static void rememberEndedSession(UUID sessionId, double expiryTick) {
        ENDED_SESSIONS.remove(sessionId);
        ENDED_SESSIONS.put(sessionId, expiryTick);
        while (ENDED_SESSIONS.size() > MAX_ENDED_SESSION_TOMBSTONES) {
            var iterator = ENDED_SESSIONS.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    /** Releases frame-sized targets after a world/resource lifecycle boundary. */
    public static void releaseTransientResources() {
        clear();
        for (var i = 0; i < MAX_SOURCE_VALIDATION_CUTS; i++) {
            destroySourceValidationSlot(i);
        }
        sourceValidationInverseProjection.identity();
        sourceValidationSides.clear();
    }

    private static void destroySourceValidationSlot(int slot) {
        if (sourceValidationDepthTargets[slot] != null) {
            sourceValidationDepthTargets[slot].destroyBuffers();
        }
        if (sourceValidationMaskTargets[slot] != null) {
            sourceValidationMaskTargets[slot].destroyBuffers();
        }
        sourceValidationDepthTargets[slot] = null;
        sourceValidationMaskTargets[slot] = null;
        sourceValidationReady[slot] = false;
        sourceValidationIdleFrames[slot] = 0;
        sourceValidationPlanes[slot].zero();
        sourceValidationOffsets[slot].zero();
    }

    private static void ageSourceValidationSlots() {
        for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
            if (sourceValidationReady[slot]) {
                sourceValidationIdleFrames[slot] = 0;
            } else if (sourceValidationDepthTargets[slot] != null
                    || sourceValidationMaskTargets[slot] != null) {
                if (++sourceValidationIdleFrames[slot] >= SOURCE_VALIDATION_IDLE_FRAME_LIMIT) {
                    destroySourceValidationSlot(slot);
                }
            }
        }
    }

    public static void close() {
        releaseTransientResources();
        registered = false;
    }

    /**
     * Selects at most five cuts for source-coordinate validation. Each slot keeps
     * one segment's mask, plane depth and projected basis together; this never
     * re-enters LevelRenderer or replaces its targets.
     */
    public static void prepareSourceValidation() {
        for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
            sourceValidationReady[slot] = false;
            sourceValidationPlanes[slot].zero();
            sourceValidationOffsets[slot].zero();
        }
        sourceValidationInverseProjection.identity();
        var camera = VFX.latestCamera;
        var projection = SpatialCutFrameProjectionContext.currentCopy();
        if (camera == null || projection == null || !projection.isFinite()) {
            ageSourceValidationSlots();
            return;
        }
        sourceValidationInverseProjection.set(projection).invert();
        if (!sourceValidationInverseProjection.isFinite()) {
            sourceValidationInverseProjection.identity();
            ageSourceValidationSlots();
            return;
        }
        var inverseTranspose = new Matrix4f(camera.viewRotationMatrix()).invert().transpose();
        if (!inverseTranspose.isFinite()) {
            ageSourceValidationSlots();
            return;
        }

        var cameraPosition = new Vec3(camera.pos());
        var candidates = new ArrayList<SpacialExcisionRenderMath.BackgroundCandidate>();
        var byId = new LinkedHashMap<Long, VisibleCut>();
        for (var visible : VFX.latestVisibleCuts) {
            var segment = visible.segment();
            var area = projectedArea(camera, visible);
            var distanceSquared = segment.center().distanceToSqr(cameraPosition);
            var stableId = segment.seed();
            while (byId.containsKey(stableId)) stableId++;
            byId.put(stableId, visible);
            candidates.add(new SpacialExcisionRenderMath.BackgroundCandidate(
                    stableId, area, distanceSquared));
        }
        retainSourceValidationSides(sourceValidationSides, byId.keySet());

        var selectedIds = SpacialExcisionRenderMath.selectBackgroundIds(
                candidates, MAX_SOURCE_VALIDATION_CUTS);
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var readySlot = 0;
        for (var selectedId : selectedIds) {
            if (readySlot >= MAX_SOURCE_VALIDATION_CUTS) break;
            var visible = byId.get(selectedId);
            if (visible == null) continue;
            var depthTarget = ensureSourceValidationDepthTarget(
                    readySlot, main.width, main.height);
            var maskTarget = ensureSourceValidationMaskTarget(
                    readySlot, main.width, main.height);
            var plane = sourceValidationPlane(
                    camera, visible, selectedId, inverseTranspose);
            var mapping = affineCutMapping(
                    camera, projection, visible, visible.worldDisplacement(),
                    main.width, main.height);
            if (depthTarget == null || maskTarget == null || plane == null || mapping == null
                    || RENDERER.renderSelectedGeometry(
                            readySlot, camera, visible, maskTarget, depthTarget) < 0.0f) {
                continue;
            }
            sourceValidationPlanes[readySlot].set(plane);
            sourceValidationOffsets[readySlot].set(
                    mapping.positiveUvOffset().x,
                    mapping.positiveUvOffset().y,
                    mapping.negativeUvOffset().x,
                    mapping.negativeUvOffset().y);
            sourceValidationReady[readySlot] = true;
            readySlot++;
        }
        ageSourceValidationSlots();
    }

    private static Vec3 requestedWorldDisplacement(
            SpacialExcisionMath.PlaneBasis basis, float progress
    ) {
        return new Vec3(SpacialExcisionRenderMath.worldDisplacement(
                new Vector3f(
                        (float) basis.tangent().x,
                        (float) basis.tangent().y,
                        (float) basis.tangent().z),
                new Vector3f(
                        (float) basis.planeUp().x,
                        (float) basis.planeUp().y,
                        (float) basis.planeUp().z),
                HORIZONTAL_SLIDE_DISTANCE_BLOCKS,
                VERTICAL_CONVERGENCE_DISTANCE_BLOCKS,
                progress));
    }

    private static @Nullable AffineCutMapping affineCutMapping(
            VfxCamera camera,
            Matrix4f projection,
            VisibleCut visible,
            Vec3 worldDisplacement,
            int viewportWidth,
            int viewportHeight
    ) {
        var footprint = CutVfx.footprintPoints(visible, camera);
        if (footprint.isEmpty() || !SpacialExcisionMath.isFinite(worldDisplacement)) {
            return null;
        }
        var supportWorld = Vec3.ZERO;
        for (var point : footprint) supportWorld = supportWorld.add(point);
        supportWorld = supportWorld.scale(1.0 / footprint.size());

        var supportView = new Vector4f(
                (float) (supportWorld.x - camera.pos().x),
                (float) (supportWorld.y - camera.pos().y),
                (float) (supportWorld.z - camera.pos().z),
                1.0f).mul(camera.viewRotationMatrix());
        var displacementView = new Vector4f(
                (float) worldDisplacement.x,
                (float) worldDisplacement.y,
                (float) worldDisplacement.z,
                0.0f).mul(camera.viewRotationMatrix());
        if (!supportView.isFinite() || !displacementView.isFinite()) return null;
        return SpacialExcisionRenderMath.regularizedAffineCutMapping(
                new Vector3f(supportView.x, supportView.y, supportView.z),
                new Vector3f(displacementView.x, displacementView.y, displacementView.z),
                projection, viewportWidth, viewportHeight);
    }

    private static double projectedArea(VfxCamera camera, VisibleCut visible) {
        var clipPoints = new ArrayList<SpacialExcisionRenderMath.ClipPoint>(4);
        for (var point : CutVfx.footprintPoints(visible, camera)) {
            var clip = CutVfx.projectClip(camera, point);
            if (clip == null) return 0.0;
            clipPoints.add(new SpacialExcisionRenderMath.ClipPoint(
                    clip.x, clip.y, clip.w));
        }
        return SpacialExcisionRenderMath.clippedProjectedArea(
                clipPoints, NEAR_CLIP_MIN_W);
    }

    private static @Nullable Vector4f sourceValidationPlane(
            VfxCamera camera,
            VisibleCut visible,
            long stableId,
            Matrix4f inverseTranspose
    ) {
        var segment = visible.segment();
        var basis = visible.basis();
        var cameraWorld = new Vec3(camera.pos());
        // The near proxy is only a rasterization aid. Source half-space
        // validation must remain anchored to the physical world cut.
        var planePoint = segment.start();
        var signedDistance = cameraWorld.subtract(planePoint).dot(basis.planeNormal());
        if (!Double.isFinite(signedDistance)) return null;
        var previousSide = sourceValidationSides.getOrDefault(
                stableId, signedDistance >= 0.0 ? 1 : -1);
        var stableSide = SpacialExcisionRenderMath.sourceValidationSide(
                signedDistance, previousSide);
        sourceValidationSides.put(stableId, stableSide);
        var side = (float) stableSide;
        var normal = new Vector3f(
                (float) basis.planeNormal().x,
                (float) basis.planeNormal().y,
                (float) basis.planeNormal().z).mul(-side);
        var relativePoint = new Vector3f(
                (float) (planePoint.x - camera.pos().x),
                (float) (planePoint.y - camera.pos().y),
                (float) (planePoint.z - camera.pos().z));
        var worldRelativePlane = new Vector4f(normal, -normal.dot(relativePoint));
        var viewPlane = inverseTranspose.transform(worldRelativePlane);
        var length = (float) Math.sqrt(
                viewPlane.x * viewPlane.x
                        + viewPlane.y * viewPlane.y
                        + viewPlane.z * viewPlane.z);
        if (!Float.isFinite(length) || length < 1.0e-5f) return null;
        viewPlane.div(length);
        return viewPlane.isFinite() && viewPlane.w <= 1.0e-4f ? viewPlane : null;
    }

    private static @Nullable RenderTarget ensureSourceValidationDepthTarget(
            int slot, int width, int height
    ) {
        var target = sourceValidationDepthTargets[slot];
        if (target == null) {
            target = new TextureTarget(null, width, height, true, GpuFormat.RGBA8_UNORM);
            sourceValidationDepthTargets[slot] = target;
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        return target;
    }

    private static @Nullable RenderTarget ensureSourceValidationMaskTarget(
            int slot, int width, int height
    ) {
        var target = sourceValidationMaskTargets[slot];
        if (target == null) {
            target = new TextureTarget(null, width, height, false, GpuFormat.RGBA16_FLOAT);
            sourceValidationMaskTargets[slot] = target;
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        return target;
    }

    /** Runs after PostEffect.pre() has copied the complete current frame. */
    public static void renderPost() {
        var minecraft = Minecraft.getInstance();
        var camera = VFX.latestCamera;
        var mainTarget = minecraft.gameRenderer.mainRenderTarget();
        var main = mainTarget.getColorTextureView();
        var mainDepth = mainTarget.getDepthTextureView();
        var scene = PostEffect.MAIN_SCENE.getColorTextureView();
        if (sourceValidationReady[0] && camera != null && postUniforms != null
                && main != null && mainDepth != null && scene != null) {
            var validationDepth0 = sourceValidationDepthTargets[0] == null
                    ? null : sourceValidationDepthTargets[0].getDepthTextureView();
            var validationMask0 = sourceValidationMaskTargets[0] == null
                    ? null : sourceValidationMaskTargets[0].getColorTextureView();
            if (validationDepth0 != null && validationMask0 != null) {
                var sceneSampler = RenderSystem.getSamplerCache()
                        .getClampToEdge(FilterMode.LINEAR);
                // Linear mask sampling removes sub-pixel stair stepping. Each
                // validation slot contains only one cut, so unrelated segments
                // cannot average or cancel its signed half direction.
                var maskSampler = RenderSystem.getSamplerCache()
                        .getClampToEdge(FilterMode.LINEAR);
                var depthSampler = RenderSystem.getSamplerCache()
                        .getClampToEdge(FilterMode.NEAREST);
                writePostUniforms();
                var textures = new ArrayList<TextureBinding>(3 + MAX_SOURCE_VALIDATION_CUTS * 2);
                textures.add(new TextureBinding("Sampler0", scene, sceneSampler));
                textures.add(new TextureBinding("Sampler3", mainDepth, depthSampler));
                var validBindings = true;
                for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
                    var validationDepth = sourceValidationReady[slot]
                            && sourceValidationDepthTargets[slot] != null
                            ? sourceValidationDepthTargets[slot].getDepthTextureView()
                            : validationDepth0;
                    var validationMask = sourceValidationReady[slot]
                            && sourceValidationMaskTargets[slot] != null
                            ? sourceValidationMaskTargets[slot].getColorTextureView()
                            : validationMask0;
                    if (validationDepth == null || validationMask == null) {
                        validBindings = false;
                        break;
                    }
                    textures.add(new TextureBinding("Sampler" + (4 + slot),
                            validationDepth, depthSampler));
                    textures.add(new TextureBinding("Sampler" + (9 + slot),
                            validationMask, maskSampler));
                }
                if (!validBindings) {
                    VFX.renderVisibleLines();
                    return;
                }
                Render.runBlitPass(
                        main,
                        VfxPipelines.SPATIAL_CUT_POST,
                        Render.Buffers.getInstance().getFSQuadVBNDC(),
                        textures,
                        List.of(
                                new UniformBinding("DynamicTransforms",
                                        RenderSystem.getDynamicUniforms().writeTransform(
                                                camera.viewRotationMatrix())),
                                new UniformBinding("Projection", projectionFor(camera)),
                                new UniformBinding("SpatialCutPost", postUniforms.slice())
                        ),
                        false
                );
            }
        }
        // The visible portal material is drawn after the scene has moved. There
        // is deliberately no independent flat-color core pass over the crack.
        VFX.renderVisibleLines();
    }

    private static void writePostUniforms() {
        if (postUniforms == null) return;
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, POST_UNIFORM_SIZE);
            var selectedCount = 0.0f;
            for (var ready : sourceValidationReady) {
                if (ready) selectedCount += 1.0f;
            }
            builder.putVec4(new Vector4f(
                    selectedCount,
                    RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 1.0f : 0.0f,
                    DEBUG_POST_CLASSIFICATION ? 1.0f : 0.0f,
                    0.0f));
            for (var plane : sourceValidationPlanes) builder.putVec4(plane);
            for (var offset : sourceValidationOffsets) builder.putVec4(offset);
            builder.putMat4f(sourceValidationInverseProjection);
            RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(postUniforms.slice(), builder.get());
        }
    }

    private static GpuBufferSlice projectionFor(VfxCamera camera) {
        var captured = camera.projectionUniform();
        return captured != null
                ? captured
                : Render.Buffers.getInstance().getProjectionUB(camera.projectionMatrix()).slice();
    }

    private static final class Session {
        private final long startTick;
        private final long endTick;
        private final double localStartTick;
        private final double localEndTick;
        private final List<Segment> segments = new ArrayList<>();

        private Session(long startTick, long endTick, double localStartTick) {
            this.startTick = startTick;
            this.endTick = endTick;
            this.localStartTick = localStartTick;
            this.localEndTick = localSessionTick(localStartTick, startTick, endTick);
        }
    }

    private record Segment(
            double localCreatedTick,
            Identifier dimension,
            Vec3 start,
            Vec3 end,
            Vec3 delta,
            double length,
            Vec3 center,
            float requestedFalloffWidth,
            long seed,
            SpacialExcisionMath.PlaneBasis basis,
            SpacialExcisionRenderMath.FlowParams flowParams,
            SpacialExcisionRenderMath.BreathParams breathParams
    ) {
    }

    private record VisibleCut(
            Segment segment,
            float progress,
            float breathScale,
            SpacialExcisionMath.PlaneBasis basis,
            Vec3 worldDisplacement,
            float falloffWidth,
            Vec3 proxyOffset,
            boolean clipOriginalToCameraFront
    ) {
    }

    private record NearProxy(Vec3 offset, boolean clipOriginalToCameraFront) {
        private static final NearProxy NONE = new NearProxy(Vec3.ZERO, false);
    }

    private interface LineData extends VfxRenderData {
        ByteBuffer vertices();

        int vertexCount();
    }

    private record CutMaterialData(ByteBuffer vertices, int vertexCount) implements LineData {
    }

    private record CutGlowData(ByteBuffer vertices, int vertexCount) implements LineData {
    }

    private static final class CutVfx implements Vfx {
        private ByteBuffer lineVertexData = BufferUtils.createByteBuffer(
                INITIAL_VERTICES * LINE_VERTEX_STRIDE);
        // Render-thread-only, fixed scratch. putLineTriangle is deliberately non-reentrant.
        private final double[] lineClipScratchA = new double[LINE_CLIP_SCRATCH_COMPONENTS];
        private final double[] lineClipScratchB = new double[LINE_CLIP_SCRATCH_COMPONENTS];
        private @Nullable ByteBuffer latestLineVertices;
        private int latestLineVertexCount;
        private @Nullable VfxCamera latestCamera;
        private float latestGameTime;
        private List<VisibleCut> latestVisibleCuts = List.of();

        private void clearLatestFrame() {
            latestLineVertices = null;
            latestLineVertexCount = 0;
            latestCamera = null;
            latestGameTime = 0.0f;
            latestVisibleCuts = List.of();
            sourceValidationSides.clear();
        }

        @Override
        public void sample(VfxFrameContext context, VfxSink sink) {
            var level = Minecraft.getInstance().level;
            if (level == null) {
                clearLatestFrame();
                return;
            }
            var now = context.gameTime();
            var dimension = level.dimension().identifier();
            List<Session> snapshot;
            synchronized (SESSIONS) {
                pruneEndedSessions(now);
                var iterator = SESSIONS.entrySet().iterator();
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    if (now < entry.getValue().localEndTick) continue;
                    rememberEndedSession(entry.getKey(), now + TOMBSTONE_TICKS);
                    iterator.remove();
                }
                snapshot = List.copyOf(SESSIONS.values());
            }
            if (snapshot.isEmpty()) {
                clearLatestFrame();
                return;
            }

            lineVertexData.clear();
            var lineData = lineVertexData;
            var lineVertices = 0;
            var camera = context.camera();
            var mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            var visibleCuts = new ArrayList<VisibleCut>();
            for (var session : snapshot) {
                for (var segment : session.segments) {
                    if (!dimension.equals(segment.dimension)) continue;
                    var progress = SpacialExcisionMath.transitionProgress(
                            now, segment.localCreatedTick, session.localEndTick);
                    if (progress <= 0.0f) continue;
                    var basis = segment.basis;
                    var falloffWidth = effectiveFalloffWidth(
                            camera, segment, basis, mainTarget.width, mainTarget.height);
                    if (!isPotentiallyVisible(camera, segment, basis, falloffWidth)) continue;
                    var proxy = nearMaskProxy(camera, segment, basis, falloffWidth);
                    var visible = new VisibleCut(
                            segment, progress,
                            SpacialExcisionRenderMath.breathScale(segment.breathParams, now),
                            basis, requestedWorldDisplacement(basis, progress),
                            falloffWidth,
                            proxy.offset(), proxy.clipOriginalToCameraFront());
                    visibleCuts.add(visible);
                    var lineRequired = LINE_MAX_VERTICES_PER_SEGMENT;
                    var lineGrowthRequirement = lineGrowthVertexRequirement(lineVertices);
                    if (lineGrowthRequirement < 0) {
                        clearLatestFrame();
                        return;
                    }
                    if (lineData.remaining() < (long) lineRequired * LINE_VERTEX_STRIDE) {
                        var grown = grow(lineData, lineGrowthRequirement, LINE_VERTEX_STRIDE);
                        if (grown == null) {
                            clearLatestFrame();
                            return;
                        }
                        lineData = grown;
                        lineVertexData = grown;
                    }
                    lineVertices += appendLine(lineData, visible, camera);
                }
            }
            if (!visibleCuts.isEmpty()) {
                latestCamera = camera;
                latestGameTime = now;
                latestVisibleCuts = List.copyOf(visibleCuts);
            } else {
                clearLatestFrame();
            }
            if (lineVertices > 0) {
                lineData.flip();
                latestLineVertices = lineData.asReadOnlyBuffer();
                latestLineVertexCount = lineVertices;
                sink.push(new CutGlowData(lineData.duplicate(), lineVertices));
            } else {
                latestLineVertices = null;
                latestLineVertexCount = 0;
            }
        }

        private void renderVisibleLines() {
            var vertices = latestLineVertices;
            var camera = latestCamera;
            if (vertices == null || camera == null || latestLineVertexCount <= 0) return;
            MATERIAL_RENDERER.renderAfterPost(
                    camera, latestGameTime, vertices.duplicate(), latestLineVertexCount);
        }

        private static int appendSegment(
                ByteBuffer target,
                VisibleCut visible,
                VfxCamera camera
        ) {
            var segment = visible.segment();
            var basis = visible.basis();
            var length = segment.length;
            if (!Double.isFinite(length) || length <= 0.001) return 0;

            var falloffWidth = visible.falloffWidth();
            var worldDisplacement = visible.worldDisplacement();
            var count = 0;
            for (var i = 0; i + 1 < MASK_LONGITUDINAL_SECTIONS; i++) {
                var along = longitudinalDistanceAt(i, length, falloffWidth);
                var nextAlong = longitudinalDistanceAt(i + 1, length, falloffWidth);
                var coverage0 = longitudinalCoverage(along, length, falloffWidth);
                var coverage1 = longitudinalCoverage(nextAlong, length, falloffWidth);
                var start = segment.start.add(basis.tangent().scale(along));
                var end = segment.start.add(basis.tangent().scale(nextAlong));
                // Both halves are rigid slabs. The mask alpha applies this
                // unsigned world displacement in equal and opposite directions.
                count += appendMaskHalf(
                        target, start, end, basis, false, worldDisplacement,
                        falloffWidth,
                        coverage0, coverage1, visible, camera);
                count += appendMaskHalf(
                        target, start, end, basis, true, worldDisplacement,
                        falloffWidth,
                        coverage0, coverage1, visible, camera);
            }
            return count;
        }

        private static NearProxy nearMaskProxy(
                VfxCamera camera,
                Segment segment,
                SpacialExcisionMath.PlaneBasis basis,
                float falloffWidth
        ) {
            var minimumW = Float.POSITIVE_INFINITY;
            var maximumW = Float.NEGATIVE_INFINITY;
            for (var point : footprintPoints(segment, basis, Vec3.ZERO, falloffWidth)) {
                var clip = projectClip(camera, point);
                if (clip == null) return NearProxy.NONE;
                minimumW = Math.min(minimumW, clip.w);
                maximumW = Math.max(maximumW, clip.w);
            }
            // Any footprint that touches the near gap is moved as one parallel
            // plane. If it crosses the camera, crop at w=0 first so rear
            // geometry is never resurrected.
            var plan = SpacialExcisionRenderMath.nearMaskProxyPlan(
                    minimumW, maximumW, NEAR_CLIP_MIN_W);
            if (plan.offset() <= 0.0f) return NearProxy.NONE;
            var worldForward = new Vector3f(0.0f, 0.0f, -1.0f);
            new Matrix4f(camera.viewRotationMatrix()).invert()
                    .transformDirection(worldForward);
            var lengthSquared = worldForward.lengthSquared();
            if (!Float.isFinite(lengthSquared) || lengthSquared < 1.0e-8f) {
                return NearProxy.NONE;
            }
            worldForward.normalize().mul(plan.offset());
            return new NearProxy(
                    new Vec3(worldForward), plan.clipOriginalToCameraFront());
        }

        private static List<Vec3> footprintPoints(VisibleCut visible, VfxCamera camera) {
            var original = footprintPoints(
                    visible.segment(), visible.basis(), Vec3.ZERO, visible.falloffWidth());
            if (visible.clipOriginalToCameraFront()) {
                original = clipWorldPolygonToFront(original, camera, 0.0f);
            }
            if (original.isEmpty()) return List.of();
            return original.stream()
                    .map(point -> point.add(visible.proxyOffset()))
                    .toList();
        }

        private static List<Vec3> footprintPoints(
                Segment segment,
                SpacialExcisionMath.PlaneBasis basis,
                Vec3 proxyOffset,
                float falloffWidth
        ) {
            var visibleStart = segment.start.subtract(
                    basis.tangent().scale(falloffWidth))
                    .add(proxyOffset);
            var visibleEnd = segment.end.add(
                    basis.tangent().scale(falloffWidth))
                    .add(proxyOffset);
            var outerHalfWidth = DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth;
            var left = basis.planeUp().scale(-outerHalfWidth);
            var right = basis.planeUp().scale(outerHalfWidth);
            // Perimeter order is required by homogeneous polygon clipping.
            return List.of(
                    visibleStart.add(left),
                    visibleEnd.add(left),
                    visibleEnd.add(right),
                    visibleStart.add(right));
        }

        private static float displacementFalloffWidth(
                SpacialExcisionMath.PlaneBasis basis
        ) {
            var weights = SpacialExcisionRenderMath.worldOrientationWeights(
                    vector(basis.tangent()));
            return SpacialExcisionRenderMath.adaptiveFalloffWidth(
                    MIN_DISPLACEMENT_FALLOFF_WIDTH,
                    HORIZONTAL_SLIDE_DISTANCE_BLOCKS * weights.horizontal(),
                    VERTICAL_CONVERGENCE_DISTANCE_BLOCKS * weights.vertical());
        }

        private static float effectiveFalloffWidth(
                VfxCamera camera,
                Segment segment,
                SpacialExcisionMath.PlaneBasis basis,
                int viewportWidth,
                int viewportHeight
        ) {
            var requested = segment.requestedFalloffWidth;
            if (camera == null || viewportWidth < 2 || viewportHeight < 2) {
                return requested;
            }

            var cameraPosition = new Vec3(camera.pos().x, camera.pos().y, camera.pos().z);
            var delta = segment.delta;
            var lengthSquared = segment.length * segment.length;
            if (!Double.isFinite(lengthSquared) || lengthSquared <= 0.0) return requested;
            var along = cameraPosition.subtract(segment.start).dot(delta) / lengthSquared;
            along = Math.max(0.0, Math.min(1.0, along));
            var support = segment.start.add(delta.scale(along));

            var tangent = vector(basis.tangent());
            var planeUp = vector(basis.planeUp());
            var tangentPixels = projectedPixelsPerWorldUnit(
                    camera, support, tangent, viewportWidth, viewportHeight);
            var planeUpPixels = projectedPixelsPerWorldUnit(
                    camera, support, planeUp, viewportWidth, viewportHeight);
            var projectedPixels = Float.isFinite(tangentPixels)
                    && Float.isFinite(planeUpPixels)
                    ? Math.max(tangentPixels, planeUpPixels)
                    : Float.NaN;
            if (!Float.isFinite(projectedPixels)
                    || projectedPixels <= 1.0e-6f) {
                var clip = projectClip(camera, support);
                var depth = clip != null && Float.isFinite(clip.w)
                        ? Math.max(clip.w, NEAR_CLIP_MIN_W + 0.01f)
                        : NEAR_CLIP_MIN_W + 0.01f;
                var projection = camera.projectionMatrix();
                var focalPixels = 0.5f * Math.max(
                        Math.abs(projection.m00()) * viewportWidth,
                        Math.abs(projection.m11()) * viewportHeight);
                projectedPixels = Float.isFinite(focalPixels)
                        && focalPixels > 1.0e-6f
                        ? focalPixels / depth
                        : Float.NaN;
            }
            return SpacialExcisionRenderMath.screenBoundedFalloffWidth(
                    requested, projectedPixels, viewportWidth, viewportHeight);
        }

        private static float projectedPixelsPerWorldUnit(
                VfxCamera camera,
                Vec3 origin,
                Vector3f direction,
                int viewportWidth,
                int viewportHeight
        ) {
            var originClip = projectClip(camera, origin);
            var displacedClip = projectClip(
                    camera,
                    origin.add(new Vec3(direction.x, direction.y, direction.z)));
            if (originClip == null || displacedClip == null
                    || originClip.w < NEAR_CLIP_MIN_W
                    || displacedClip.w < NEAR_CLIP_MIN_W) {
                return Float.NaN;
            }
            var originNdcX = originClip.x / originClip.w;
            var originNdcY = originClip.y / originClip.w;
            var displacedNdcX = displacedClip.x / displacedClip.w;
            var displacedNdcY = displacedClip.y / displacedClip.w;
            var pixels = (float) Math.hypot(
                    0.5f * viewportWidth * (displacedNdcX - originNdcX),
                    0.5f * viewportHeight * (displacedNdcY - originNdcY));
            return Float.isFinite(pixels) ? pixels : Float.NaN;
        }

        private static List<Vec3> clipWorldPolygonToFront(
                List<Vec3> input,
                VfxCamera camera,
                float minimumW
        ) {
            if (input.size() < 3 || !Float.isFinite(minimumW) || minimumW < 0.0f) {
                return List.of();
            }
            var vertices = new ArrayList<WorldClipVertex>(input.size());
            for (var point : input) {
                var clip = projectClip(camera, point);
                if (clip == null) return List.of();
                vertices.add(new WorldClipVertex(point, clip.w));
            }
            var output = new ArrayList<WorldClipVertex>(vertices.size() + 1);
            var previous = vertices.get(vertices.size() - 1);
            var previousInside = previous.clipW() >= minimumW;
            for (var current : vertices) {
                var currentInside = current.clipW() >= minimumW;
                if (currentInside != previousInside) {
                    var t = SpacialExcisionMath.frontClipInterpolation(
                            previous.clipW(), current.clipW(), minimumW);
                    if (!Float.isFinite(t)) return List.of();
                    output.add(new WorldClipVertex(
                            previous.point().lerp(current.point(), t), minimumW));
                }
                if (currentInside) output.add(current);
                previous = current;
                previousInside = currentInside;
            }
            return output.size() < 3 ? List.of()
                    : output.stream().map(WorldClipVertex::point).toList();
        }

        private static int appendMaskHalf(
                ByteBuffer target,
                Vec3 start,
                Vec3 end,
                SpacialExcisionMath.PlaneBasis basis,
                boolean right,
                Vec3 worldDisplacement,
                float falloffWidth,
                float longitudinalCoverage0,
                float longitudinalCoverage1,
                VisibleCut visible,
                VfxCamera camera
        ) {
            var sign = right ? 1.0f : -1.0f;
            var planeUp = basis.planeUp();
            var crossDistance = crossDistanceAt(0, falloffWidth);
            var offset = sign * crossDistance;
            var previousStart = start.add(planeUp.scale(offset));
            var previousEnd = end.add(planeUp.scale(offset));
            var previousCoverage = sign * crossCoverage(crossDistance, falloffWidth);
            var count = 0;
            for (var i = 0; i + 1 < MASK_CROSS_SECTIONS; i++) {
                var nextDistance = crossDistanceAt(i + 1, falloffWidth);
                var nextOffset = sign * nextDistance;
                var nextStart = start.add(planeUp.scale(nextOffset));
                var nextEnd = end.add(planeUp.scale(nextOffset));
                var nextCoverage = sign * crossCoverage(nextDistance, falloffWidth);
                var coverage0 = previousCoverage * longitudinalCoverage0;
                var coverage1 = nextCoverage * longitudinalCoverage0;
                var nextCoverage0 = nextCoverage * longitudinalCoverage1;
                var previousCoverage1 = previousCoverage * longitudinalCoverage1;
                if (right) {
                    // Keep the center edge oppositely directed on the two halves.
                    // With additive signed rendering, identical edge direction can
                    // make the top-left rasterization rule include both center
                    // triangles (cancelling -1/+1) or exclude both (leaving zero).
                    count += putMaskTriangle(
                            target, previousStart, nextEnd, nextStart,
                            worldDisplacement,
                            coverage0, nextCoverage0, coverage1,
                            visible, camera);
                    count += putMaskTriangle(
                            target, previousStart, previousEnd, nextEnd,
                            worldDisplacement,
                            coverage0, previousCoverage1, nextCoverage0,
                            visible, camera);
                } else {
                    count += putMaskTriangle(
                            target, previousStart, nextStart, nextEnd,
                            worldDisplacement,
                            coverage0, coverage1, nextCoverage0,
                            visible, camera);
                    count += putMaskTriangle(
                            target, previousStart, nextEnd, previousEnd,
                            worldDisplacement,
                            coverage0, nextCoverage0, previousCoverage1,
                            visible, camera);
                }
                previousStart = nextStart;
                previousEnd = nextEnd;
                previousCoverage = nextCoverage;
            }
            return count;
        }

        private int appendLine(
                ByteBuffer target,
                VisibleCut visible,
                VfxCamera camera
        ) {
            var segment = visible.segment();
            var progress = visible.progress();
            var basis = visible.basis();
            var length = segment.length;
            if (!Double.isFinite(length) || length <= 0.001) return 0;

            // The visible portal material is independent from the post-process
            // tangent. Looking along the cut may fade the displacement direction,
            // but it must not hide the world-space crack itself.
            var flowParams = segment.flowParams;
            var center = segment.center;
            var breathScale = visible.breathScale();
            var count = 0;
            for (var i = 0; i < LINE_LONGITUDINAL_SECTIONS; i++) {
                var t0 = (double) i / LINE_LONGITUDINAL_SECTIONS;
                var t1 = (double) (i + 1) / LINE_LONGITUDINAL_SECTIONS;
                var start = segment.start.lerp(segment.end, t0);
                var end = segment.start.lerp(segment.end, t1);
                var width0 = PORTAL_MAX_HALF_WIDTH * centeredSpindleProfile(t0);
                var width1 = PORTAL_MAX_HALF_WIDTH * centeredSpindleProfile(t1);
                var left0 = scaleAround(
                        start.subtract(basis.planeUp().scale(width0)), center, breathScale);
                var right0 = scaleAround(
                        start.add(basis.planeUp().scale(width0)), center, breathScale);
                var left1 = scaleAround(
                        end.subtract(basis.planeUp().scale(width1)), center, breathScale);
                var right1 = scaleAround(
                        end.add(basis.planeUp().scale(width1)), center, breathScale);
                var flow0 = (float) t0 + flowParams.phase();
                var flow1 = (float) t1 + flowParams.phase();
                var materialU0 = flowParams.speed();
                var materialU1 = flowParams.speed();

                count += putLineTriangle(
                        target, left0, right0, right1,
                        flow0, flow0, flow1,
                        materialU0, materialU0, materialU1,
                        0.0f, 1.0f, 1.0f,
                        progress, progress, progress, visible, camera);
                count += putLineTriangle(
                        target, left0, right1, left1,
                        flow0, flow1, flow1,
                        materialU0, materialU1, materialU1,
                        0.0f, 1.0f, 0.0f,
                        progress, progress, progress, visible, camera);
            }
            return count;
        }

        private static Vec3 scaleAround(Vec3 point, Vec3 center, float scale) {
            if (!SpacialExcisionMath.isFinite(point)
                    || !SpacialExcisionMath.isFinite(center)
                    || !Float.isFinite(scale)) {
                return point;
            }
            return center.add(point.subtract(center).scale(scale));
        }

        private static boolean isPotentiallyVisible(
                VfxCamera camera,
                Segment segment,
                SpacialExcisionMath.PlaneBasis basis,
                float falloffWidth
        ) {
            var delta = segment.delta;
            var lengthSquared = segment.length * segment.length;
            if (!Double.isFinite(lengthSquared) || lengthSquared <= 0.0) return false;

            var cameraWorld = new Vec3(camera.pos().x, camera.pos().y, camera.pos().z);
            var along = cameraWorld.subtract(segment.start).dot(delta) / lengthSquared;
            along = Math.max(0.0, Math.min(1.0, along));
            var closest = segment.start.add(delta.scale(along));
            if (cameraWorld.distanceToSqr(closest) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
                return false;
            }

            // Do not binary-cull edge-on cuts. The projected mask naturally
            // narrows, and a hard facing threshold made the crack disappear or
            // switch behavior while the player crossed the plane.
            var visibleStart = segment.start.subtract(
                    basis.tangent().scale(falloffWidth));
            var visibleEnd = segment.end.add(
                    basis.tangent().scale(falloffWidth));
            var startLeft = visibleStart.subtract(
                    basis.planeUp().scale(DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth));
            var startRight = visibleStart.add(
                    basis.planeUp().scale(DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth));
            var endLeft = visibleEnd.subtract(
                    basis.planeUp().scale(DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth));
            var endRight = visibleEnd.add(
                    basis.planeUp().scale(DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth));
            var points = new Vec3[]{startLeft, startRight, endLeft, endRight};
            var minX = Double.POSITIVE_INFINITY;
            var maxX = Double.NEGATIVE_INFINITY;
            var minY = Double.POSITIVE_INFINITY;
            var maxY = Double.NEGATIVE_INFINITY;
            var hasFrontPoint = false;
            for (var point : points) {
                var clip = project(camera, point);
                if (clip == null) return true;
                if (clip.w <= 0.0f) continue;
                var x = clip.x / clip.w;
                var y = clip.y / clip.w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                hasFrontPoint = true;
            }
            return hasFrontPoint && maxX >= -1.0 && minX <= 1.0
                    && maxY >= -1.0 && minY <= 1.0;
        }

        private static float crossDistanceAt(int index, float falloffWidth) {
            if (index <= 0) return 0.0f;
            if (index == 1) return DISPLACEMENT_RIGID_HALF_WIDTH;
            return DISPLACEMENT_RIGID_HALF_WIDTH
                    + falloffWidth * (index - 1) / MASK_FALLOFF_STEPS;
        }

        private static double longitudinalDistanceAt(
                int index, double length, float falloffWidth
        ) {
            if (index < MASK_FALLOFF_STEPS) {
                return -falloffWidth * (MASK_FALLOFF_STEPS - index)
                        / MASK_FALLOFF_STEPS;
            }
            var coreIndex = index - MASK_FALLOFF_STEPS;
            if (coreIndex <= LINE_LONGITUDINAL_SECTIONS) {
                return length * coreIndex / LINE_LONGITUDINAL_SECTIONS;
            }
            return length + falloffWidth * (coreIndex - LINE_LONGITUDINAL_SECTIONS)
                    / MASK_FALLOFF_STEPS;
        }

        private static float crossCoverage(float crossDistance, float falloffWidth) {
            if (!Float.isFinite(crossDistance)) return 0.0f;
            var distance = Math.abs(crossDistance);
            if (distance <= DISPLACEMENT_RIGID_HALF_WIDTH) return 1.0f;
            if (distance >= DISPLACEMENT_RIGID_HALF_WIDTH + falloffWidth) return 0.0f;
            return oneMinusSmootherstep(
                    (distance - DISPLACEMENT_RIGID_HALF_WIDTH)
                            / falloffWidth);
        }

        private static float longitudinalCoverage(
                double along,
                double length,
                float falloffWidth
        ) {
            if (!Double.isFinite(along) || !Double.isFinite(length) || length <= 0.0) {
                return 0.0f;
            }
            if (along >= 0.0 && along <= length) return 1.0f;
            if (along < 0.0) {
                return oneMinusSmootherstep((float) (-along / falloffWidth));
            }
            return oneMinusSmootherstep(
                    (float) ((along - length) / falloffWidth));
        }

        private static float oneMinusSmootherstep(float value) {
            var t = clamp01(value);
            return 1.0f - t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
        }

        private static float centeredSpindleProfile(double coordinate) {
            if (!Double.isFinite(coordinate)) return 0.0f;
            var t = Math.max(0.0, Math.min(1.0, coordinate));
            var centeredDistance = Math.abs(t * 2.0 - 1.0);
            var profile = 1.0 - centeredDistance * centeredDistance;
            return Double.isFinite(profile) ? (float) Math.max(0.0, profile) : 0.0f;
        }

        private static float clamp01(float value) {
            return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.0f;
        }

        private static float clampSigned(float value) {
            return Float.isFinite(value) ? Math.max(-1.0f, Math.min(1.0f, value)) : 0.0f;
        }

        private static float finiteOrZero(float value) {
            return Float.isFinite(value) ? value : 0.0f;
        }

        private static Vec3 finiteVec(Vec3 value) {
            return SpacialExcisionMath.isFinite(value) ? value : Vec3.ZERO;
        }

        private static Vector3f vector(Vec3 value) {
            return new Vector3f((float) value.x, (float) value.y, (float) value.z);
        }

        private static int putMaskTriangle(
                ByteBuffer target,
                Vec3 a,
                Vec3 b,
                Vec3 c,
                Vec3 worldDisplacement,
                float coverageA,
                float coverageB,
                float coverageC,
                VisibleCut visible,
                VfxCamera camera
        ) {
            if (!visible.clipOriginalToCameraFront()
                    && visible.proxyOffset().equals(Vec3.ZERO)) {
                var clipWA = projectLineClipW(camera, a.x, a.y, a.z);
                var clipWB = projectLineClipW(camera, b.x, b.y, b.z);
                var clipWC = projectLineClipW(camera, c.x, c.y, c.z);
                if (clipWA >= NEAR_CLIP_MIN_W
                        && clipWB >= NEAR_CLIP_MIN_W
                        && clipWC >= NEAR_CLIP_MIN_W) {
                    var cameraPos = camera.pos();
                    putMaskVertex(target, a, worldDisplacement, coverageA, cameraPos);
                    putMaskVertex(target, b, worldDisplacement, coverageB, cameraPos);
                    putMaskVertex(target, c, worldDisplacement, coverageC, cameraPos);
                    return 3;
                }
            }
            var first = maskClipVertex(a, worldDisplacement, coverageA, camera);
            var second = maskClipVertex(b, worldDisplacement, coverageB, camera);
            var third = maskClipVertex(c, worldDisplacement, coverageC, camera);
            if (first == null || second == null || third == null) return 0;
            var clipped = prepareMaskVertices(
                    List.of(first, second, third), visible, camera);
            if (clipped.size() < 3) return 0;

            var cameraPos = camera.pos();
            var count = 0;
            for (var i = 1; i + 1 < clipped.size(); i++) {
                putMaskVertex(target, clipped.get(0), cameraPos);
                putMaskVertex(target, clipped.get(i), cameraPos);
                putMaskVertex(target, clipped.get(i + 1), cameraPos);
                count += 3;
            }
            return count;
        }

        private static void putMaskVertex(
                ByteBuffer target,
                Vec3 point,
                Vec3 worldDisplacement,
                float coverage,
                Vector3f camera
        ) {
            target.putFloat((float) (point.x - camera.x));
            target.putFloat((float) (point.y - camera.y));
            target.putFloat((float) (point.z - camera.z));
            target.putFloat((float) worldDisplacement.x);
            target.putFloat((float) worldDisplacement.y);
            target.putFloat((float) worldDisplacement.z);
            target.putFloat(clampSigned(coverage));
        }

        private static @Nullable MaskClipVertex maskClipVertex(
                Vec3 point,
                Vec3 worldDisplacement,
                float coverage,
                VfxCamera camera
        ) {
            var clip = projectClip(camera, point);
            return clip == null ? null : new MaskClipVertex(
                    point,
                    finiteVec(worldDisplacement),
                    clampSigned(coverage),
                    clip.w);
        }

        private static List<MaskClipVertex> prepareMaskVertices(
                List<MaskClipVertex> input,
                VisibleCut visible,
                VfxCamera camera
        ) {
            var originalFront = visible.clipOriginalToCameraFront()
                    ? clipMaskToFront(input, 0.0f) : input;
            if (originalFront.size() < 3) return List.of();
            var moved = new ArrayList<MaskClipVertex>(originalFront.size());
            for (var vertex : originalFront) {
                var point = vertex.point().add(visible.proxyOffset());
                var clip = projectClip(camera, point);
                if (clip == null) return List.of();
                moved.add(new MaskClipVertex(
                        point,
                        vertex.worldDisplacement(),
                        vertex.coverage(),
                        clip.w));
            }
            return clipMaskToFront(moved, NEAR_CLIP_MIN_W);
        }

        private static List<MaskClipVertex> clipMaskToFront(
                List<MaskClipVertex> input,
                float minimumW
        ) {
            var output = new ArrayList<MaskClipVertex>(4);
            var previous = input.get(input.size() - 1);
            var previousInside = previous.clipW() >= minimumW;
            for (var current : input) {
                var currentInside = current.clipW() >= minimumW;
                if (currentInside != previousInside) {
                    var intersection = interpolateMaskClip(previous, current, minimumW);
                    if (intersection == null) return List.of();
                    output.add(intersection);
                }
                if (currentInside) output.add(current);
                previous = current;
                previousInside = currentInside;
            }
            return output;
        }

        private static @Nullable MaskClipVertex interpolateMaskClip(
                MaskClipVertex from,
                MaskClipVertex to,
                float minimumW
        ) {
            var t = SpacialExcisionMath.frontClipInterpolation(
                    from.clipW(), to.clipW(), minimumW);
            if (!Float.isFinite(t)) return null;
            return new MaskClipVertex(
                    from.point().lerp(to.point(), t),
                    from.worldDisplacement().lerp(to.worldDisplacement(), t),
                    lerp(from.coverage(), to.coverage(), t),
                    minimumW);
        }

        private static void putMaskVertex(
                ByteBuffer target,
                MaskClipVertex vertex,
                Vector3f camera
        ) {
            target.putFloat((float) (vertex.point().x - camera.x));
            target.putFloat((float) (vertex.point().y - camera.y));
            target.putFloat((float) (vertex.point().z - camera.z));
            target.putFloat((float) vertex.worldDisplacement().x);
            target.putFloat((float) vertex.worldDisplacement().y);
            target.putFloat((float) vertex.worldDisplacement().z);
            target.putFloat(clampSigned(vertex.coverage()));
        }

        private int putLineTriangle(
                ByteBuffer target,
                Vec3 a,
                Vec3 b,
                Vec3 c,
                float flowA,
                float flowB,
                float flowC,
                float materialUA,
                float materialUB,
                float materialUC,
                float materialVA,
                float materialVB,
                float materialVC,
                float alphaA,
                float alphaB,
                float alphaC,
                VisibleCut visible,
                VfxCamera camera
        ) {
            if (!putLineClipVertex(
                    lineClipScratchA, 0, a, flowA, materialUA, materialVA,
                    alphaA, camera)
                    || !putLineClipVertex(
                    lineClipScratchA, 1, b, flowB, materialUB, materialVB,
                    alphaB, camera)
                    || !putLineClipVertex(
                    lineClipScratchA, 2, c, flowC, materialUC, materialVC,
                    alphaC, camera)) {
                return 0;
            }
            var clippedCount = prepareLineVertices(3, visible, camera);
            if (clippedCount < 3) return 0;
            var clipped = visible.clipOriginalToCameraFront()
                    ? lineClipScratchA : lineClipScratchB;

            var cameraPos = camera.pos();
            var count = 0;
            for (var i = 1; i + 1 < clippedCount; i++) {
                putLineVertex(target, clipped, 0, cameraPos);
                putLineVertex(target, clipped, i, cameraPos);
                putLineVertex(target, clipped, i + 1, cameraPos);
                count += 3;
            }
            return count;
        }

        private static boolean putLineClipVertex(
                double[] target,
                int vertex,
                Vec3 point,
                float flow,
                float materialU,
                float materialV,
                float alpha,
                VfxCamera camera
        ) {
            var clipW = projectLineClipW(camera, point.x, point.y, point.z);
            if (!Float.isFinite(clipW)) return false;
            var offset = vertex * LINE_CLIP_VERTEX_COMPONENTS;
            target[offset] = point.x;
            target[offset + 1] = point.y;
            target[offset + 2] = point.z;
            target[offset + 3] = finiteOrZero(flow);
            target[offset + 4] = finiteOrZero(materialU);
            target[offset + 5] = clamp01(materialV);
            target[offset + 6] = clamp01(alpha);
            target[offset + 7] = clipW;
            return true;
        }

        private int prepareLineVertices(
                int inputCount,
                VisibleCut visible,
                VfxCamera camera
        ) {
            var moved = lineClipScratchA;
            var movedCount = inputCount;
            if (visible.clipOriginalToCameraFront()) {
                movedCount = clipLineVertices(
                        lineClipScratchA, inputCount, 0.0f, lineClipScratchB);
                if (movedCount < 3) return 0;
                moved = lineClipScratchB;
            }
            if (!moveAndProjectLineVertices(
                    moved, movedCount, visible.proxyOffset(), camera)) {
                return 0;
            }
            var nearOutput = visible.clipOriginalToCameraFront()
                    ? lineClipScratchA : lineClipScratchB;
            return clipLineVertices(moved, movedCount, NEAR_CLIP_MIN_W, nearOutput);
        }

        private static boolean moveAndProjectLineVertices(
                double[] vertices,
                int vertexCount,
                Vec3 proxyOffset,
                VfxCamera camera
        ) {
            for (var vertex = 0; vertex < vertexCount; vertex++) {
                var offset = vertex * LINE_CLIP_VERTEX_COMPONENTS;
                var x = vertices[offset] + proxyOffset.x;
                var y = vertices[offset + 1] + proxyOffset.y;
                var z = vertices[offset + 2] + proxyOffset.z;
                var clipW = projectLineClipW(camera, x, y, z);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                        || !Float.isFinite(clipW)) {
                    return false;
                }
                vertices[offset] = x;
                vertices[offset + 1] = y;
                vertices[offset + 2] = z;
                vertices[offset + 7] = clipW;
            }
            return true;
        }

        private static float projectLineClipW(
                VfxCamera camera,
                double worldX,
                double worldY,
                double worldZ
        ) {
            var cameraPos = camera.pos();
            var relativeX = (float) (worldX - cameraPos.x);
            var relativeY = (float) (worldY - cameraPos.y);
            var relativeZ = (float) (worldZ - cameraPos.z);
            var view = camera.viewRotationMatrix();
            var viewX = Math.fma(view.m00(), relativeX,
                    Math.fma(view.m10(), relativeY,
                            Math.fma(view.m20(), relativeZ, view.m30())));
            var viewY = Math.fma(view.m01(), relativeX,
                    Math.fma(view.m11(), relativeY,
                            Math.fma(view.m21(), relativeZ, view.m31())));
            var viewZ = Math.fma(view.m02(), relativeX,
                    Math.fma(view.m12(), relativeY,
                            Math.fma(view.m22(), relativeZ, view.m32())));
            var viewW = Math.fma(view.m03(), relativeX,
                    Math.fma(view.m13(), relativeY,
                            Math.fma(view.m23(), relativeZ, view.m33())));
            var projection = camera.projectionMatrix();
            var clipX = Math.fma(projection.m00(), viewX,
                    Math.fma(projection.m10(), viewY,
                            Math.fma(projection.m20(), viewZ, projection.m30() * viewW)));
            var clipY = Math.fma(projection.m01(), viewX,
                    Math.fma(projection.m11(), viewY,
                            Math.fma(projection.m21(), viewZ, projection.m31() * viewW)));
            var clipZ = Math.fma(projection.m02(), viewX,
                    Math.fma(projection.m12(), viewY,
                            Math.fma(projection.m22(), viewZ, projection.m32() * viewW)));
            var clipW = Math.fma(projection.m03(), viewX,
                    Math.fma(projection.m13(), viewY,
                            Math.fma(projection.m23(), viewZ, projection.m33() * viewW)));
            return Float.isFinite(clipX) && Float.isFinite(clipY)
                    && Float.isFinite(clipZ) && Float.isFinite(clipW)
                    ? clipW : Float.NaN;
        }

        private static void putLineVertex(
                ByteBuffer target,
                double[] vertices,
                int vertex,
                Vector3f camera
        ) {
            var offset = vertex * LINE_CLIP_VERTEX_COMPONENTS;
            target.putFloat((float) (vertices[offset] - camera.x));
            target.putFloat((float) (vertices[offset + 1] - camera.y));
            target.putFloat((float) (vertices[offset + 2] - camera.z));
            target.putFloat(1.0f);
            target.putFloat(1.0f);
            target.putFloat(1.0f);
            target.putFloat(clamp01((float) vertices[offset + 6]));
            target.putFloat(finiteOrZero((float) vertices[offset + 3]));
            target.putFloat(finiteOrZero((float) vertices[offset + 4]));
            target.putFloat(clamp01((float) vertices[offset + 5]));
        }

        private static float lerp(float from, float to, float t) {
            return from + (to - from) * t;
        }

        private record MaskClipVertex(
                Vec3 point,
                Vec3 worldDisplacement,
                float coverage,
                float clipW
        ) {
        }

        private record WorldClipVertex(Vec3 point, float clipW) {
        }

        private static @Nullable Vector4f project(VfxCamera camera, Vec3 point) {
            var relative = projectClip(camera, point);
            return relative != null && Math.abs(relative.w) > 1.0e-5f ? relative : null;
        }

        private static @Nullable Vector4f projectClip(VfxCamera camera, Vec3 point) {
            var relative = new Vector4f(
                    (float) (point.x - camera.pos().x),
                    (float) (point.y - camera.pos().y),
                    (float) (point.z - camera.pos().z),
                    1.0f
            );
            relative.mul(camera.viewRotationMatrix()).mul(camera.projectionMatrix());
            return Float.isFinite(relative.x) && Float.isFinite(relative.y)
                    && Float.isFinite(relative.z) && Float.isFinite(relative.w)
                    ? relative : null;
        }

        @Override
        public boolean isAlive() {
            return true;
        }
    }

    private static final class CutRenderer {
        private @Nullable GpuBuffer vertexBuffer;
        private final ByteBuffer[] selectedVertexData =
                new ByteBuffer[MAX_SOURCE_VALIDATION_CUTS];
        private int capacityVertices;

        public void init(GpuDevice device) {
            vertexBuffer = device.createBuffer(
                    () -> "Spatial Cut Mask Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    (long) INITIAL_VERTICES * VERTEX_STRIDE
            );
            postUniforms = device.createBuffer(
                    () -> "Spatial Cut Post Uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    POST_UNIFORM_SIZE
            );
            for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
                selectedVertexData[slot] = BufferUtils.createByteBuffer(
                        MASK_MAX_VERTICES_PER_SEGMENT * VERTEX_STRIDE);
            }
            capacityVertices = INITIAL_VERTICES;
        }

        private float renderSelectedGeometry(
                int slot,
                VfxCamera camera,
                VisibleCut visible,
                RenderTarget maskTarget,
                RenderTarget depthTarget
        ) {
            if (slot < 0 || slot >= MAX_SOURCE_VALIDATION_CUTS || vertexBuffer == null) {
                return -1.0f;
            }
            var vertices = selectedVertexData[slot];
            if (vertices == null) return -1.0f;
            vertices.clear();
            var vertexCount = CutVfx.appendSegment(vertices, visible, camera);
            if (vertexCount <= 0) return -1.0f;
            vertices.flip();
            if (vertexCount > capacityVertices) grow(vertexCount);
            if (vertexBuffer == null) return -1.0f;
            var totalBytes = (long) vertexCount * VERTEX_STRIDE;
            RenderSystem.getDevice().createCommandEncoder()
                    .writeToBuffer(vertexBuffer.slice(0, totalBytes), vertices);

            var maskColor = maskTarget.getColorTextureView();
            var depthColor = depthTarget.getColorTextureView();
            var targetDepth = depthTarget.getDepthTextureView();
            var mainDepth = Minecraft.getInstance().gameRenderer.mainRenderTarget()
                    .getDepthTextureView();
            if (maskColor == null || depthColor == null
                    || targetDepth == null || mainDepth == null) return -1.0f;
            var device = RenderSystem.getDevice();
            device.createCommandEncoder().clearColorTexture(
                    maskColor.texture(), new Vector4f(0));
            device.createCommandEncoder().clearDepthTexture(targetDepth.texture(), 1.0);

            var maskEncoder = device.createCommandEncoder();
            try (var pass = maskEncoder.createRenderPass(
                    () -> "Spatial Cut Selected Mask", maskColor, Optional.empty(),
                    mainDepth, OptionalDouble.empty())) {
                pass.setPipeline(VfxPipelines.SPATIAL_CUT_MASK);
                pass.setUniform("Projection", projectionFor(camera));
                pass.setUniform("DynamicTransforms",
                        RenderSystem.getDynamicUniforms().writeTransform(
                                camera.viewRotationMatrix()));
                pass.setVertexBuffer(0, vertexBuffer.slice(0, totalBytes));
                var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
                pass.setIndexBuffer(sequential.getBuffer(vertexCount), sequential.type());
                pass.drawIndexed(vertexCount, 1, 0, 0, 0);
            }

            var encoder = device.createCommandEncoder();
            try (var pass = encoder.createRenderPass(
                    () -> "Spatial Cut Selected Depth", depthColor, Optional.empty(),
                    targetDepth, OptionalDouble.empty())) {
                pass.setPipeline(VfxPipelines.SPATIAL_CUT_DEPTH);
                pass.setUniform("Projection", projectionFor(camera));
                pass.setUniform("DynamicTransforms",
                        RenderSystem.getDynamicUniforms().writeTransform(
                                camera.viewRotationMatrix()));
                pass.bindTexture("Sampler0", mainDepth,
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.setVertexBuffer(0, vertexBuffer.slice(0, totalBytes));
                var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
                pass.setIndexBuffer(sequential.getBuffer(vertexCount), sequential.type());
                pass.drawIndexed(vertexCount, 1, 0, 0, 0);
            }
            return 0.0f;
        }

        private void grow(int requiredVertices) {
            if (vertexBuffer == null) return;
            var old = vertexBuffer;
            capacityVertices = Math.max(requiredVertices, capacityVertices * 2);
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Spatial Cut Mask Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    (long) capacityVertices * VERTEX_STRIDE
            );
            old.close();
        }

        public void close() {
            if (vertexBuffer != null) vertexBuffer.close();
            if (postUniforms != null) postUniforms.close();
            vertexBuffer = null;
            postUniforms = null;
            for (var slot = 0; slot < MAX_SOURCE_VALIDATION_CUTS; slot++) {
                selectedVertexData[slot] = null;
            }
            capacityVertices = 0;
        }
    }

    private enum LineKind {
        MATERIAL,
        GLOW
    }

    private static final class CutLineRenderer<T extends LineData> implements VfxRenderer<T> {
        private final LineKind kind;
        private @Nullable GpuBuffer vertexBuffer;
        private @Nullable GpuBuffer flowUniforms;
        private @Nullable ByteBuffer vertexData;
        private int capacityVertices;

        private CutLineRenderer(LineKind kind) {
            this.kind = kind;
        }

        private boolean isGlow() {
            return kind == LineKind.GLOW;
        }

        @Override
        public void init(GpuDevice device) {
            if (isGlow()) RENDERER.init(device);
            vertexBuffer = device.createBuffer(
                    () -> "Spatial Cut " + kind + " Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    (long) INITIAL_VERTICES * LINE_VERTEX_STRIDE
            );
            flowUniforms = device.createBuffer(
                    () -> "Spatial Cut " + kind + " Flow Uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    FLOW_UNIFORM_SIZE
            );
            capacityVertices = INITIAL_VERTICES;
        }

        @Override
        public void render(VfxRenderContext context, List<? extends T> data) {
            if (data.isEmpty() || vertexBuffer == null) return;
            var color = isGlow() ? context.bloomInputColor() : context.mainColor();
            var depth = isGlow() ? context.bloomInputDepth() : context.mainDepth();
            if (color == null || depth == null) return;

            var totalVertices = data.stream().mapToInt(LineData::vertexCount).sum();
            if (totalVertices <= 0) return;
            if (totalVertices > capacityVertices) grow(totalVertices);
            var totalBytes = (long) totalVertices * LINE_VERTEX_STRIDE;
            if (vertexData == null || vertexData.capacity() < totalBytes) {
                vertexData = BufferUtils.createByteBuffer(Math.toIntExact(totalBytes));
            }
            vertexData.clear();
            for (var item : data) {
                var vertices = item.vertices().duplicate();
                var oldLimit = vertices.limit();
                var requiredLimit = vertices.position() + item.vertexCount() * LINE_VERTEX_STRIDE;
                if (requiredLimit > oldLimit) continue;
                vertices.limit(requiredLimit);
                vertexData.put(vertices);
                vertices.limit(oldLimit);
            }
            vertexData.flip();
            draw(
                    context.device(), color, depth, context.projectionUniform(),
                    context.viewRotationMatrix(), context.gameTime(), vertexData, totalVertices);
        }

        private void renderAfterPost(
                VfxCamera camera,
                float gameTime,
                ByteBuffer vertices,
                int vertexCount
        ) {
            if (vertexBuffer == null || vertexCount <= 0) return;
            var minecraft = Minecraft.getInstance();
            var color = minecraft.gameRenderer.mainRenderTarget().getColorTextureView();
            var depth = minecraft.gameRenderer.mainRenderTarget().getDepthTextureView();
            if (color == null || depth == null) return;
            if (vertexCount > capacityVertices) grow(vertexCount);
            var totalBytes = (long) vertexCount * LINE_VERTEX_STRIDE;
            if (vertexData == null || vertexData.capacity() < totalBytes) {
                vertexData = BufferUtils.createByteBuffer(Math.toIntExact(totalBytes));
            }
            vertexData.clear();
            var copy = vertices.duplicate();
            var requiredLimit = copy.position() + Math.toIntExact(totalBytes);
            if (requiredLimit > copy.limit()) return;
            copy.limit(requiredLimit);
            vertexData.put(copy);
            vertexData.flip();
            draw(
                    RenderSystem.getDevice(), color, depth, projectionFor(camera),
                    camera.viewRotationMatrix(), gameTime, vertexData, vertexCount);
        }

        private void draw(
                GpuDevice device,
                GpuTextureView color,
                GpuTextureView depth,
                GpuBufferSlice projection,
                Matrix4f viewRotation,
                float gameTime,
                ByteBuffer vertices,
                int totalVertices
        ) {
            if (vertexBuffer == null || totalVertices <= 0) return;
            var totalBytes = (long) totalVertices * LINE_VERTEX_STRIDE;
            device.createCommandEncoder().writeToBuffer(
                    vertexBuffer.slice(0, totalBytes), vertices);
            if (flowUniforms == null) return;
            try (var stack = MemoryStack.stackPush()) {
                var builder = Std140Builder.onStack(stack, FLOW_UNIFORM_SIZE);
                var time = Float.isFinite(gameTime) ? gameTime : 0.0f;
                builder.putVec4(new Vector4f(time, 0.0f, 0.16f, 0.0f));
                device.createCommandEncoder().writeToBuffer(flowUniforms.slice(), builder.get());
            }
            var encoder = device.createCommandEncoder();
            try (var pass = encoder.createRenderPass(
                    () -> "Spatial Cut " + kind,
                    color, Optional.empty(), depth, OptionalDouble.empty()
            )) {
                pass.setPipeline(switch (kind) {
                    case MATERIAL -> VfxPipelines.SPATIAL_CUT_MATERIAL;
                    case GLOW -> VfxPipelines.SPATIAL_CUT_GLOW;
                });
                pass.setUniform("Projection", projection);
                pass.setUniform("DynamicTransforms",
                        RenderSystem.getDynamicUniforms().writeTransform(viewRotation));
                pass.setUniform("SpatialCutFlow", flowUniforms.slice());
                var textureManager = Minecraft.getInstance().getTextureManager();
                var endSky = textureManager.getTexture(END_SKY_TEXTURE);
                var endPortal = textureManager.getTexture(END_PORTAL_TEXTURE);
                pass.bindTexture("Sampler0", endSky.getTextureView(), endSky.getSampler());
                pass.bindTexture("Sampler1", endPortal.getTextureView(), endPortal.getSampler());
                pass.setVertexBuffer(0, vertexBuffer.slice(0, totalBytes));
                var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.TRIANGLES);
                pass.setIndexBuffer(sequential.getBuffer(totalVertices), sequential.type());
                pass.drawIndexed(totalVertices, 1, 0, 0, 0);
            }
        }

        private void grow(int requiredVertices) {
            if (vertexBuffer == null) return;
            var old = vertexBuffer;
            capacityVertices = Math.max(requiredVertices, capacityVertices * 2);
            vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Spatial Cut " + kind + " Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    (long) capacityVertices * LINE_VERTEX_STRIDE
            );
            old.close();
        }

        @Override
        public void close() {
            if (vertexBuffer != null) vertexBuffer.close();
            if (flowUniforms != null) flowUniforms.close();
            if (isGlow()) RENDERER.close();
            vertexBuffer = null;
            flowUniforms = null;
            vertexData = null;
            capacityVertices = 0;
        }
    }
}
