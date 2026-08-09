package org.academy.internal.client.renderer.effect;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import org.academy.api.client.render.LevelRenderEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One client-side timeline per Dir Strike. All lifted blocks are sampled once and submitted from
 * one world-render callback; no entity, synchronized data, entity tick, or renderer state exists
 * per block.
 */
public final class DirStrikeGroundEffect {
    private static final int MIN_Y_OFFSET = -3;
    private static final int MAX_Y_OFFSET = 5;
    private static final int MAX_GROUND_BLOCKS = 96;
    private static final int MAX_AIRBORNE_BLOCKS = 192;
    private static final int MAX_ACTIVE_TIMELINES = 4;
    private static final int MAX_ACTIVE_BLOCKS = 384;
    private static final int BASE_DURATION = 18;
    private static final int GROUND_HOLD_TICKS = 20;
    private static final int AIRBORNE_HOLD_TICKS = 60;
    private static final float BASE_PEAK = 0.38f;
    private static final float RISE_TICKS = 6.0f;
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
        var horizontalLook = normalizedLook(lookX, lookZ);
        var samples = sampleSurface(
                level, center, origin, Math.min(radius, 32), airborne, horizontalLook, seed);
        if (samples.isEmpty()) return;

        var timeline = new Timeline(
                level,
                center,
                level.getGameTime(),
                samples,
                samples.stream().mapToDouble(BlockSample::endTick).max().orElse(1.0)
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
            synchronized (ACTIVE) {
                ACTIVE.clear();
            }
            return;
        }
        var now = level.getGameTime() + event.getPartialTick();
        var camera = minecraft.gameRenderer.mainCamera().position();
        var poseStack = event.getPoseStack();
        var collector = event.getSubmitNodeCollector();

        synchronized (ACTIVE) {
            for (var iterator = ACTIVE.iterator(); iterator.hasNext(); ) {
                var timeline = iterator.next();
                var age = now - timeline.startTick;
                if (timeline.level != level || age > timeline.endTick + 2.0) {
                    iterator.remove();
                    continue;
                }
                if (timeline.center.distanceToSqr(camera) > MAX_RENDER_DISTANCE_SQR) continue;
                for (var sample : timeline.samples) {
                    var activeTick = (float) (age - sample.delay);
                    if (activeTick < 0.0f || activeTick > sample.endTick()) continue;
                    var motion = motion(activeTick, sample.duration, sample.holdTicks);
                    if (motion <= 0.001f) continue;

                    var lift = motion * sample.peakHeight * 1.12f;
                    var tilt = motion * 12.0f;
                    var cornerPitch = (((sample.modelSeed >> 1) & 1L) == 0L ? 1.0f : -1.0f)
                            * motion * 4.0f;
                    var cornerRoll = (((sample.modelSeed >> 2) & 1L) == 0L ? 1.0f : -1.0f)
                            * motion * 3.4f;
                    var cornerLift = (0.015f + ((sample.modelSeed >> 3) & 3L) * 0.005f) * motion;
                    var pos = sample.position;

                    poseStack.pushPose();
                    poseStack.translate(
                            pos.getX() + 0.5 - camera.x,
                            pos.getY() - camera.y,
                            pos.getZ() + 0.5 - camera.z
                    );
                    poseStack.translate(0.0, lift + 0.03 + cornerLift, 0.0);
                    poseStack.mulPose(Axis.YP.rotationDegrees(sample.yRot));
                    poseStack.translate(0.0, 0.0, motion * 0.1f);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-tilt));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(tilt * 0.35f));
                    poseStack.mulPose(Axis.XP.rotationDegrees(cornerPitch));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(cornerRoll));
                    poseStack.scale(0.98f, 0.98f, 0.98f);
                    poseStack.translate(-0.5, 0.0, -0.5);
                    collector.submitMovingBlock(poseStack, sample.renderState, 0);
                    poseStack.popPose();
                }
            }
        }
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

    private static List<BlockSample> sampleSurface(
            ClientLevel level,
            Vec3 center,
            BlockPos origin,
            int radius,
            boolean airborne,
            Vec3 look,
            long seed
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
            // Pick a deterministic spatial distribution first, then order the chosen blocks by
            // radius. This keeps the visible wave reaching the edge instead of rendering only
            // the closest blocks when the airborne circle contains hundreds of candidates.
            candidates.sort(Comparator.comparingLong(pos -> priority(seed, pos)));
            candidates.subList(limit, candidates.size()).clear();
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(center)));

        var samples = new ArrayList<BlockSample>(candidates.size());
        for (var pos : candidates) {
            var state = level.getBlockState(pos);
            if (state.getRenderShape() != RenderShape.MODEL) continue;
            var random = RandomSource.create(seed ^ pos.asLong());
            var distance = (float) Math.sqrt(pos.distToCenterSqr(center));
            var delay = Math.max(0, Mth.floor(distance * 1.1f) - 1) + random.nextInt(2);
            var duration = BASE_DURATION + random.nextInt(3);
            var peak = BASE_PEAK + random.nextFloat() * 0.2f
                    + Math.max(0.0f, 1.0f - distance / Math.max(1, radius)) * 0.08f
                    - (airborne ? 0.2f : 0.0f);
            var outward = Vec3.atCenterOf(pos).subtract(center);
            if (outward.lengthSqr() <= 1.0E-4) outward = new Vec3(0.0, 0.0, 1.0);
            var yRot = (float) Math.toDegrees(Math.atan2(outward.x, outward.z));
            var holdTicks = airborne ? AIRBORNE_HOLD_TICKS : GROUND_HOLD_TICKS;
            samples.add(new BlockSample(
                    pos.immutable(),
                    movingBlockState(level, pos, state),
                    delay,
                    duration,
                    holdTicks,
                    Math.max(0.05f, peak),
                    yRot,
                    state.getSeed(pos)
            ));
        }
        return List.copyOf(samples);
    }

    private static MovingBlockRenderState movingBlockState(
            ClientLevel level,
            BlockPos pos,
            BlockState state
    ) {
        var renderState = new MovingBlockRenderState();
        renderState.randomSeedPos = pos;
        renderState.blockPos = pos;
        renderState.blockState = state;
        renderState.biome = level.getBiome(pos);
        renderState.cardinalLighting = level.cardinalLighting();
        renderState.lightEngine = level.getLightEngine();
        return renderState;
    }

    private static BlockPos findSurface(
            ClientLevel level,
            BlockPos origin,
            int xOffset,
            int zOffset
    ) {
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
                    new BlockParticleOption(ParticleTypes.BLOCK, sample.renderState.blockState),
                    pos.getX() + 0.5,
                    pos.getY() + 0.9,
                    pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5) * 0.08,
                    0.03 + random.nextDouble() * 0.05,
                    (random.nextDouble() - 0.5) * 0.08
            );
        }
    }

    private record Timeline(
            ClientLevel level,
            Vec3 center,
            long startTick,
            List<BlockSample> samples,
            double endTick
    ) {
    }

    private record BlockSample(
            BlockPos position,
            MovingBlockRenderState renderState,
            int delay,
            int duration,
            int holdTicks,
            float peakHeight,
            float yRot,
            long modelSeed
    ) {
        private float endTick() {
            return delay + duration + holdTicks + 2.0f;
        }
    }
}
