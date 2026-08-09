package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import org.academy.api.client.render.LevelRenderEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

/**
 * A render-only Dir Strike timeline. Block models are baked once when the packet arrives, then
 * every active block is appended to one of at most three layer batches per frame. No entity or
 * per-frame block model tessellation is involved.
 */
public final class DirStrikeGroundEffect {
    private static final int MIN_Y_OFFSET = -3;
    private static final int MAX_Y_OFFSET = 5;
    // Radius 12's 90-degree ground sector contains at most 123 columns, while the
    // radius 18 airborne circle contains 1009. These budgets therefore preserve
    // the complete single-player wave instead of randomly dropping edge columns.
    private static final int MAX_GROUND_BLOCKS = 128;
    private static final int MAX_AIRBORNE_BLOCKS = 1024;
    private static final int MAX_ACTIVE_TIMELINES = 4;
    private static final int MAX_ACTIVE_BLOCKS = 2048;
    private static final int BASE_DURATION = 18;
    private static final int GROUND_HOLD_TICKS = 20;
    private static final int AIRBORNE_HOLD_TICKS = 60;
    private static final float BASE_PEAK = 0.38f;
    private static final float RISE_TICKS = 6.0f;
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final double GROUND_SECTOR_COS = Math.cos(Math.toRadians(45.0));
    private static final double MAX_RENDER_DISTANCE_SQR = 128.0 * 128.0;
    private static final List<Timeline> ACTIVE = new ArrayList<>();

    private DirStrikeGroundEffect() {
    }

    public static void spawn(
            Vec3 center,
            BlockPos origin,
            int radius,
            boolean airborne,
            float lookX,
            float lookZ,
            long seed
    ) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || center == null || origin == null || radius <= 0) return;

        var modelRenderer = new ModelBlockRenderer(
                minecraft.options.ambientOcclusion().get(), false, minecraft.getBlockColors());
        var modelSet = minecraft.getModelManager().getBlockStateModelSet();
        var cutoutLeaves = minecraft.options.cutoutLeaves().get();
        var samples = sampleSurface(
                level,
                center,
                origin,
                Math.min(radius, 32),
                airborne,
                normalizedLook(lookX, lookZ),
                seed,
                modelRenderer,
                modelSet,
                cutoutLeaves
        );
        if (samples.isEmpty()) return;

        var endTick = samples.stream().mapToDouble(BlockSample::timelineEndTick).max().orElse(1.0);
        var timeline = new Timeline(
                level,
                center,
                level.getGameTime(),
                samples,
                endTick,
                samplesHaveLayer(samples, ChunkSectionLayer.SOLID),
                samplesHaveLayer(samples, ChunkSectionLayer.CUTOUT),
                samplesHaveLayer(samples, ChunkSectionLayer.TRANSLUCENT)
        );
        synchronized (ACTIVE) {
            while (!ACTIVE.isEmpty() && (ACTIVE.size() >= MAX_ACTIVE_TIMELINES
                    || activeBlockCount() + samples.size() > MAX_ACTIVE_BLOCKS)) {
                ACTIVE.removeFirst();
            }
            ACTIVE.add(timeline);
        }
        spawnDebris(level, samples, seed);
    }

    @SubscribeEvent
    public static void onLevelRender(LevelRenderEvent event) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }

        var now = level.getGameTime() + event.getPartialTick();
        var camera = minecraft.gameRenderer.mainCamera().position();
        List<Timeline> timelines;
        synchronized (ACTIVE) {
            ACTIVE.removeIf(timeline -> timeline.level != level
                    || now - timeline.startTick > timeline.endTick + 2.0);
            if (ACTIVE.isEmpty()) return;
            timelines = List.copyOf(ACTIVE);
        }

        submitLayer(event, timelines, camera, now, ChunkSectionLayer.SOLID);
        submitLayer(event, timelines, camera, now, ChunkSectionLayer.CUTOUT);
        submitLayer(event, timelines, camera, now, ChunkSectionLayer.TRANSLUCENT);
    }

    static float motion(float activeTick, int duration, int holdTicks) {
        var riseTicks = Math.min(RISE_TICKS, Math.max(2.0f, duration * 0.45f));
        var fallTicks = Math.max(6.0f, duration - riseTicks);
        if (activeTick <= riseTicks) {
            var rise = Mth.clamp(activeTick / riseTicks, 0.0f, 1.0f);
            var inverse = 1.0f - rise;
            return 1.0f - inverse * inverse * inverse;
        }
        if (activeTick <= riseTicks + holdTicks) return 1.0f;
        var fall = Mth.clamp((activeTick - riseTicks - holdTicks) / fallTicks, 0.0f, 1.0f);
        return 1.0f - fall * fall;
    }

    private static void submitLayer(LevelRenderEvent event, List<Timeline> timelines, Vec3 camera,
                                    double now, ChunkSectionLayer layer) {
        if (!timelinesHaveLayer(timelines, layer)) return;
        event.submitPoseGeometry(renderType(layer), (basePose, consumer) ->
                renderLayer(basePose, consumer, timelines, camera, now, layer));
    }

    private static void renderLayer(PoseStack.Pose basePose, VertexConsumer consumer,
                                    List<Timeline> timelines, Vec3 camera, double now,
                                    ChunkSectionLayer layer) {
        var poseStack = new PoseStack();
        poseStack.last().set(basePose);
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        var rotation = new Quaternionf();
        var transformedPosition = new Vector3f();
        var transformedNormal = new Vector3f();

        for (var timeline : timelines) {
            if (!timeline.hasLayer(layer)
                    || timeline.center.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }
            var age = now - timeline.startTick;
            for (var sample : timeline.samples) {
                var vertices = sample.mesh.vertices(layer);
                if (vertices.isEmpty()) continue;
                var activeTick = (float) (age - sample.delay);
                if (activeTick < 0.0f || activeTick > sample.localEndTick()) continue;
                var amount = motion(activeTick, sample.duration, sample.holdTicks);
                if (amount <= 0.001f) continue;

                poseStack.pushPose();
                poseStack.translate(
                        sample.position.getX() + 0.5,
                        sample.position.getY(),
                        sample.position.getZ() + 0.5
                );
                poseStack.translate(
                        0.0,
                        amount * sample.peakHeight * 1.12f + 0.03f + amount * sample.cornerLift,
                        0.0
                );
                poseStack.mulPose(rotation.rotationY(sample.yRotRadians));
                poseStack.translate(0.0, 0.0, amount * 0.1f);
                poseStack.mulPose(rotation.rotationX(-amount * 12.0f * DEG_TO_RAD));
                poseStack.mulPose(rotation.rotationZ(amount * 4.2f * DEG_TO_RAD));
                poseStack.mulPose(rotation.rotationX(amount * sample.cornerPitchRadians));
                poseStack.mulPose(rotation.rotationZ(amount * sample.cornerRollRadians));
                poseStack.scale(0.98f, 0.98f, 0.98f);
                poseStack.translate(-0.5, 0.0, -0.5);
                putVertices(
                        poseStack.last(), consumer, vertices, transformedPosition, transformedNormal);
                poseStack.popPose();
            }
        }
    }

    private static void putVertices(PoseStack.Pose pose, VertexConsumer consumer,
                                    List<CachedVertex> vertices, Vector3f position,
                                    Vector3f normal) {
        var positionMatrix = pose.pose();
        for (var vertex : vertices) {
            positionMatrix.transformPosition(vertex.x, vertex.y, vertex.z, position);
            pose.transformNormal(vertex.normalX, vertex.normalY, vertex.normalZ, normal);
            consumer.addVertex(
                    position.x,
                    position.y,
                    position.z,
                    vertex.color,
                    vertex.u,
                    vertex.v,
                    vertex.overlay,
                    vertex.light,
                    normal.x,
                    normal.y,
                    normal.z
            );
        }
    }

    private static List<BlockSample> sampleSurface(
            ClientLevel level,
            Vec3 center,
            BlockPos origin,
            int radius,
            boolean airborne,
            Vec3 look,
            long seed,
            ModelBlockRenderer modelRenderer,
            BlockStateModelSet modelSet,
            boolean cutoutLeaves
    ) {
        var candidates = new ArrayList<BlockPos>();
        for (var xOffset = -radius; xOffset <= radius; xOffset++) {
            for (var zOffset = -radius; zOffset <= radius; zOffset++) {
                var distanceSqr = xOffset * xOffset + zOffset * zOffset;
                if (distanceSqr > radius * radius
                        || !airborne && !insideGroundSector(xOffset + 0.5, zOffset + 0.5, look)) {
                    continue;
                }
                var surface = findSurface(level, origin, xOffset, zOffset);
                if (surface != null) candidates.add(surface);
            }
        }

        var limit = airborne ? MAX_AIRBORNE_BLOCKS : MAX_GROUND_BLOCKS;
        if (candidates.size() > limit) {
            candidates.sort(Comparator.comparingLong(pos -> priority(seed, pos)));
            candidates.subList(limit, candidates.size()).clear();
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(center)));

        var samples = new ArrayList<BlockSample>(candidates.size());
        for (var pos : candidates) {
            var state = level.getBlockState(pos);
            if (state.getRenderShape() != RenderShape.MODEL) continue;
            var mesh = bakeMesh(
                    level, pos, state, modelRenderer, modelSet, cutoutLeaves);
            if (mesh.isEmpty()) continue;

            var random = RandomSource.create(seed ^ pos.asLong());
            var distance = (float) Math.sqrt(pos.distToCenterSqr(center));
            var delay = Math.max(0, Mth.floor(distance * 1.1f) - 1) + random.nextInt(2);
            var duration = BASE_DURATION + random.nextInt(3);
            var peak = BASE_PEAK + random.nextFloat() * 0.2f
                    + Math.max(0.0f, 1.0f - distance / Math.max(1, radius)) * 0.08f
                    - (airborne ? 0.2f : 0.0f);
            var outward = Vec3.atCenterOf(pos).subtract(center);
            if (outward.lengthSqr() <= 1.0E-4) outward = new Vec3(0.0, 0.0, 1.0);
            var modelSeed = state.getSeed(pos);
            samples.add(new BlockSample(
                    pos.immutable(),
                    state,
                    mesh,
                    delay,
                    duration,
                    airborne ? AIRBORNE_HOLD_TICKS : GROUND_HOLD_TICKS,
                    Math.max(0.05f, peak),
                    (float) Math.atan2(outward.x, outward.z),
                    (((modelSeed >> 1) & 1L) == 0L ? 4.0f : -4.0f) * DEG_TO_RAD,
                    (((modelSeed >> 2) & 1L) == 0L ? 3.4f : -3.4f) * DEG_TO_RAD,
                    0.015f + ((modelSeed >> 3) & 3L) * 0.005f
            ));
        }
        return List.copyOf(samples);
    }

    private static CachedMesh bakeMesh(ClientLevel level, BlockPos pos, BlockState state,
                                       ModelBlockRenderer modelRenderer, BlockStateModelSet modelSet,
                                       boolean cutoutLeaves) {
        var renderState = new MovingBlockRenderState();
        renderState.randomSeedPos = pos;
        renderState.blockPos = pos;
        renderState.blockState = state;
        renderState.biome = level.getBiome(pos);
        renderState.cardinalLighting = level.cardinalLighting();
        renderState.lightEngine = level.getLightEngine();

        var vertices = new EnumMap<ChunkSectionLayer, List<CachedVertex>>(ChunkSectionLayer.class);
        for (var layer : ChunkSectionLayer.values()) vertices.put(layer, new ArrayList<>());
        var forcedLayer = ModelBlockRenderer.forceOpaque(cutoutLeaves, state)
                ? ChunkSectionLayer.SOLID
                : null;
        BlockQuadOutput output = (x, y, z, quad, instance) -> appendQuad(
                vertices.get(forcedLayer == null ? quad.materialInfo().layer() : forcedLayer),
                x, y, z, quad, instance);
        modelRenderer.tesselateBlock(
                output,
                0.0f,
                0.0f,
                0.0f,
                renderState,
                pos,
                state,
                modelSet.get(state),
                state.getSeed(pos)
        );
        return new CachedMesh(
                List.copyOf(vertices.get(ChunkSectionLayer.SOLID)),
                List.copyOf(vertices.get(ChunkSectionLayer.CUTOUT)),
                List.copyOf(vertices.get(ChunkSectionLayer.TRANSLUCENT))
        );
    }

    private static void appendQuad(List<CachedVertex> output, float x, float y, float z,
                                   BakedQuad quad, QuadInstance instance) {
        var faceNormal = quad.direction().getUnitVec3f();
        var lightEmission = quad.materialInfo().lightEmission();
        for (var vertexIndex = 0; vertexIndex < BakedQuad.VERTEX_COUNT; vertexIndex++) {
            var position = quad.position(vertexIndex);
            var packedUv = quad.packedUV(vertexIndex);
            var packedNormal = quad.bakedNormals().normal(vertexIndex);
            var normalX = faceNormal.x();
            var normalY = faceNormal.y();
            var normalZ = faceNormal.z();
            if (!BakedNormals.isUnspecified(packedNormal)) {
                normalX = BakedNormals.unpackX(packedNormal);
                normalY = BakedNormals.unpackY(packedNormal);
                normalZ = BakedNormals.unpackZ(packedNormal);
            }
            output.add(new CachedVertex(
                    position.x() + x,
                    position.y() + y,
                    position.z() + z,
                    ARGB.multiply(instance.getColor(vertexIndex), quad.bakedColors().color(vertexIndex)),
                    UVPair.unpackU(packedUv),
                    UVPair.unpackV(packedUv),
                    instance.overlayCoords(),
                    LightCoordsUtil.lightCoordsWithEmission(
                            instance.getLightCoords(vertexIndex), lightEmission),
                    normalX,
                    normalY,
                    normalZ
            ));
        }
    }

    private static BlockPos findSurface(ClientLevel level, BlockPos origin,
                                        int xOffset, int zOffset) {
        for (var yOffset = MAX_Y_OFFSET; yOffset >= MIN_Y_OFFSET; yOffset--) {
            var pos = origin.offset(xOffset, yOffset, zOffset);
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if (!renderableGround(level, pos, state)) continue;
            var above = pos.above();
            var aboveState = level.getBlockState(above);
            if (!aboveState.isAir() && !aboveState.getCollisionShape(level, above).isEmpty()) continue;
            return pos.immutable();
        }
        return null;
    }

    private static boolean renderableGround(ClientLevel level, BlockPos pos, BlockState state) {
        return !state.isAir()
                && !state.hasBlockEntity()
                && state.getRenderShape() == RenderShape.MODEL
                && state.getDestroySpeed(level, pos) >= 0.0f
                && state.getFluidState().isEmpty();
    }

    private static boolean insideGroundSector(double xOffset, double zOffset, Vec3 look) {
        var distanceSqr = xOffset * xOffset + zOffset * zOffset;
        if (distanceSqr <= 1.0E-8) return true;
        var inverseDistance = 1.0 / Math.sqrt(distanceSqr);
        return (xOffset * look.x + zOffset * look.z) * inverseDistance >= GROUND_SECTOR_COS;
    }

    private static Vec3 normalizedLook(float lookX, float lookZ) {
        if (!Float.isFinite(lookX) || !Float.isFinite(lookZ)) return new Vec3(0.0, 0.0, 1.0);
        var look = new Vec3(lookX, 0.0, lookZ);
        return look.lengthSqr() <= 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
    }

    private static RenderType renderType(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderTypes.solidMovingBlock();
            case CUTOUT -> RenderTypes.cutoutMovingBlock();
            case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
        };
    }

    private static boolean samplesHaveLayer(List<BlockSample> samples, ChunkSectionLayer layer) {
        for (var sample : samples) {
            if (!sample.mesh.vertices(layer).isEmpty()) return true;
        }
        return false;
    }

    private static boolean timelinesHaveLayer(List<Timeline> timelines, ChunkSectionLayer layer) {
        for (var timeline : timelines) {
            if (timeline.hasLayer(layer)) return true;
        }
        return false;
    }

    private static long priority(long seed, BlockPos pos) {
        var value = seed ^ pos.asLong() * 0x9E3779B97F4A7C15L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static int activeBlockCount() {
        var count = 0;
        for (var timeline : ACTIVE) count += timeline.samples.size();
        return count;
    }

    private static void spawnDebris(ClientLevel level, List<BlockSample> samples, long seed) {
        var random = RandomSource.create(seed ^ 0xD1A57A1B10C5L);
        var stride = Math.max(2, samples.size() / 48);
        for (var index = 0; index < samples.size(); index += stride) {
            var sample = samples.get(index);
            var pos = sample.position;
            level.addParticle(
                    new BlockParticleOption(ParticleTypes.BLOCK, sample.state),
                    pos.getX() + 0.5,
                    pos.getY() + 0.9,
                    pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 0.08,
                    0.03 + random.nextDouble() * 0.05,
                    (random.nextDouble() - 0.5) * 0.08
            );
        }
    }

    private static void clear() {
        synchronized (ACTIVE) {
            ACTIVE.clear();
        }
    }

    private record Timeline(
            ClientLevel level,
            Vec3 center,
            long startTick,
            List<BlockSample> samples,
            double endTick,
            boolean solid,
            boolean cutout,
            boolean translucent
    ) {
        private boolean hasLayer(ChunkSectionLayer layer) {
            return switch (layer) {
                case SOLID -> solid;
                case CUTOUT -> cutout;
                case TRANSLUCENT -> translucent;
            };
        }
    }

    private record BlockSample(
            BlockPos position,
            BlockState state,
            CachedMesh mesh,
            int delay,
            int duration,
            int holdTicks,
            float peakHeight,
            float yRotRadians,
            float cornerPitchRadians,
            float cornerRollRadians,
            float cornerLift
    ) {
        private float localEndTick() {
            return duration + holdTicks + 2.0f;
        }

        private float timelineEndTick() {
            return delay + localEndTick();
        }
    }

    private record CachedMesh(
            List<CachedVertex> solid,
            List<CachedVertex> cutout,
            List<CachedVertex> translucent
    ) {
        private List<CachedVertex> vertices(ChunkSectionLayer layer) {
            return switch (layer) {
                case SOLID -> solid;
                case CUTOUT -> cutout;
                case TRANSLUCENT -> translucent;
            };
        }

        private boolean isEmpty() {
            return solid.isEmpty() && cutout.isEmpty() && translucent.isEmpty();
        }
    }

    private record CachedVertex(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int overlay,
            int light,
            float normalX,
            float normalY,
            float normalZ
    ) {
    }
}
