package org.academy.internal.client.render.vfx;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.ImprovedNoise;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.academy.api.client.render.Render.RenderTypes.*;

public final class WingVfx implements Vfx {
    private static final int SWEEP_DURATION_TICKS = 10;
    private static final float SWEEP_PIVOT_SIDE = 0.18f;
    private static final float SWEEP_PIVOT_FORWARD = 0.18f;
    private static final float SWEEP_PIVOT_UPPER_Z = -0.22f;
    private static final float SWEEP_BASE_YAW = 12.0f;
    private static final float SWEEP_ARC_DEGREES = 168.0f;
    private static final float SWEEP_SCALE = 1.12f;
    private static final float TORNADO_OFFSET_LEFT = 0.0f;
    private static final float TORNADO_OFFSET_RIGHT = 20.0f;

    private static final int NUM_RINGS = 24;
    private static final float HEIGHT = 3.5f;
    private static final float SIZE = 1.0f;
    private static final float AVERAGE_GAP = HEIGHT / (float) (NUM_RINGS - 1);
    private static final float FUNNEL_BASE_RADIUS_FACTOR = 0.2F;
    private static final float FUNNEL_EXPONENT = 1.75F;
    private static final float HORIZONTAL_DISPLACEMENT_SCALE = 1.6f;
    private static final double POS_DOMAIN_WARP_SCALE = 0.15;
    private static final float GAP_VARIANCE_SCALE = 0.6f;
    private static final float BASE_RING_WIDTH = 0.075f * SIZE;
    private static final float RADIUS_BASE_NOISE_SCALE = 0.15f;
    private static final float RADIUS_EXTRA_NOISE_SCALE = 0.20f;
    private static final float RADIUS_JITTER_SCALE = 0.03f;
    private static final float ROTATION_BASE_NOISE_SCALE = 0.45f * Mth.PI;
    private static final float ROTATION_MODULATION_SCALE = 0.30f;
    private static final float RING_TILT_SCALE = 0.10f;
    private static final float WIDTH_BASE_NOISE_SCALE = 0.4f;
    private static final float WIDTH_DETAIL_NOISE_SCALE = 0.25f;
    private static final float TIME_SCALE_GLOBAL = 1.1f;
    private static final double TIME_POS_BASE = 0.07 * TIME_SCALE_GLOBAL;
    private static final double TIME_POS_WARP = 0.30 * TIME_SCALE_GLOBAL;
    private static final double TIME_GAP = 0.09 * TIME_SCALE_GLOBAL;
    private static final double TIME_RAD_BASE = 0.11 * TIME_SCALE_GLOBAL;
    private static final double TIME_RAD_EXTRA = 0.22 * TIME_SCALE_GLOBAL;
    private static final double TIME_JITTER = 0.85 * TIME_SCALE_GLOBAL;
    private static final double TIME_ROT_BASE = 0.55 * TIME_SCALE_GLOBAL;
    private static final double TIME_ROT_MOD = 0.16 * TIME_SCALE_GLOBAL;
    private static final double TIME_WIDTH_BASE = 0.15 * TIME_SCALE_GLOBAL;
    private static final double TIME_WIDTH_DETAIL = 0.55 * TIME_SCALE_GLOBAL;
    private static final double TIME_TILT = 0.18 * TIME_SCALE_GLOBAL;
    private static final float NESTED_RADIUS_FACTOR = 0.50f;
    private static final float NESTED_WIDTH_FACTOR = 0.75f;
    private static final float TORNADO_OFFSET_1 = 0.0f;
    private static final float TORNADO_OFFSET_2 = 20.0f;
    private static final float TORNADO_OFFSET_3 = 45.0f;
    private static final float TORNADO_OFFSET_4 = 70.0f;
    private static final int RINGS_PER_TORNADO = NUM_RINGS * 2;

    private static final Matrix4f WING_BASE_MATRIX = new Matrix4f()
            .rotateX(90.0f * Mth.DEG_TO_RAD)
            .translate(0, 0.25f, 0);
    private static final Matrix4f STORM_BASE_MATRIX = new Matrix4f()
            .rotateX(90.0f * Mth.DEG_TO_RAD)
            .translate(0, 0.25f, -0.25f);

    private static final int RING_SEGMENTS = 4;
    private static final float[][][] CACHED_VERTICAL_VERTEX_BUFFER =
            VertexUtil.Ring.getVerticalVertexBuffer(1.0f, 1.0f, RING_SEGMENTS);

    private static final double[] displacementBuffer = new double[2];
    private static final double[] warpedYBuffer = new double[1];
    private static final Quaternionf tempTiltQuat = new Quaternionf();
    private static final Quaternionf tempRotQuat = new Quaternionf();

    private static final Map<WingKind, SweepAnimationTimeline<SweepAnimation>> SWEEP_ANIMATIONS =
            new EnumMap<>(WingKind.class);
    private static final Map<Integer, Long> BLACK_TO_WHITE_TRANSITIONS = new HashMap<>();
    private static ClientLevel animationLevel;
    private static final int INSTANCE_STRIDE = 64;

    static {
        for (var kind : WingKind.values()) SWEEP_ANIMATIONS.put(kind, new SweepAnimationTimeline<>());
    }

    private final Map<WingKind, ByteBuffer> instanceBuffers = new EnumMap<>(WingKind.class);
    private ByteBuffer ascensionBuffer;

    public static void enqueueSweep(WingKind kind, int entityId, boolean leftWing,
                                    float yawOffsetDeg, float pitchOffsetDeg) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (animationLevel != minecraft.level) {
            clearSweeps();
            animationLevel = minecraft.level;
        }
        var entity = minecraft.level.getEntity(entityId);
        if (entity == null) return;
        SWEEP_ANIMATIONS.get(kind).enqueue(
                entityId,
                minecraft.level.getGameTime(),
                new SweepAnimation(leftWing, yawOffsetDeg, pitchOffsetDeg)
        );
    }

    public static void enqueueBlackToWhiteTransition(int entityId) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getEntity(entityId) == null) return;
        if (animationLevel != minecraft.level) {
            clearSweeps();
            animationLevel = minecraft.level;
        }
        BLACK_TO_WHITE_TRANSITIONS.put(entityId, minecraft.level.getGameTime());
    }

    public static void clientTick() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clearSweeps();
            return;
        }
        if (animationLevel != minecraft.level) {
            clearSweeps();
            animationLevel = minecraft.level;
            return;
        }
        var currentTick = (double) minecraft.level.getGameTime();
        for (var timeline : SWEEP_ANIMATIONS.values()) {
            timeline.prune(currentTick, SWEEP_DURATION_TICKS,
                    entityId -> minecraft.level.getEntity(entityId) != null);
        }
        BLACK_TO_WHITE_TRANSITIONS.entrySet().removeIf(entry ->
                !WingTransitionAnimation.isActive(currentTick - entry.getValue())
                        || minecraft.level.getEntity(entry.getKey()) == null
        );
    }

    public static void clearSweeps() {
        for (var timeline : SWEEP_ANIMATIONS.values()) timeline.clear();
        BLACK_TO_WHITE_TRANSITIONS.clear();
        animationLevel = null;
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null) return;
        var roots = WingAvatarRegistry.entries();
        if (roots.isEmpty()) return;

        var partialTick = ctx.partialTick();
        var currentTick = (double) ctx.gameTime();
        var counts = new EnumMap<WingKind, Integer>(WingKind.class);
        var ascensionInstances = 0;

        for (var root : roots.entrySet()) {
            var entity = level.getEntity(root.getKey());
            if (!(entity instanceof Player player)) continue;
            var transition = transitionProjection(player.getId(), currentTick);
            for (var kind : WingKind.values()) {
                var active = isActive(player, kind);
                if (!active && !isTransitionWing(kind, transition)) continue;
                counts.merge(kind, countInstances(kind, player.getId(), currentTick, active), Integer::sum);
            }
            if (transition != null && transition.ascension().visible()) {
                ascensionInstances += 2 * RINGS_PER_TORNADO;
            }
        }
        if (counts.isEmpty() && ascensionInstances == 0) return;

        for (var countEntry : counts.entrySet()) {
            var kind = countEntry.getKey();
            var buffer = ensureBuffer(kind, countEntry.getValue());
            var builder = Std140Builder.intoBuffer(buffer);
            for (var root : roots.entrySet()) {
                var entity = level.getEntity(root.getKey());
                if (!(entity instanceof Player player)) continue;
                var active = isActive(player, kind);
                var transition = transitionProjection(player.getId(), currentTick);
                if (!active && !isTransitionWing(kind, transition)) continue;
                var effectTime = player.tickCount + partialTick;
                if (isTransitionWing(kind, transition)) {
                    var pose = kind == WingKind.BLACK ? transition.blackWing() : transition.whiteWing();
                    buildTransitionWings(builder, root.getValue(), kind, effectTime, pose);
                } else {
                    buildPersistentWings(builder, root.getValue(), kind, effectTime);
                }
                if (active) {
                    buildSweepAnimations(builder, root.getValue(), kind, player.getId(), currentTick, effectTime);
                }
            }
            buffer.flip();
            if (buffer.hasRemaining()) sink.push(new WingData(kind, WingData.Layer.STABLE, buffer));
        }

        if (ascensionInstances > 0) {
            var buffer = ensureAscensionBuffer(ascensionInstances);
            var builder = Std140Builder.intoBuffer(buffer);
            for (var root : roots.entrySet()) {
                var entity = level.getEntity(root.getKey());
                if (!(entity instanceof Player player)) continue;
                var transition = transitionProjection(player.getId(), currentTick);
                if (transition == null || !transition.ascension().visible()) continue;
                buildTransitionWings(
                        builder,
                        root.getValue(),
                        WingKind.WHITE,
                        player.tickCount + partialTick,
                        transition.ascension()
                );
            }
            buffer.flip();
            if (buffer.hasRemaining()) {
                sink.push(new WingData(WingKind.WHITE, WingData.Layer.ASCENSION, buffer));
            }
        }
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    private static boolean isActive(Player player, WingKind kind) {
        return switch (kind) {
            case STORM -> player.getData(AttachmentTypes.ACTIVATED_STORM_WING);
            case BLACK -> player.getData(AttachmentTypes.ACTIVATED_BLACK_WING);
            case WHITE -> player.getData(AttachmentTypes.ACTIVATED_WHITE_WING);
            case PLATINUM -> player.getData(AttachmentTypes.ACTIVATED_PLATINUM_WING);
        };
    }

    private static Matrix4f baseMatrix(WingKind kind) {
        return kind == WingKind.STORM ? STORM_BASE_MATRIX : WING_BASE_MATRIX;
    }

    private static float ringWidthScale(WingKind kind) {
        return switch (kind) {
            case STORM, BLACK -> 1.0f;
            case WHITE, PLATINUM -> 0.11f / 0.075f;
        };
    }

    private static int persistentTornadoCount(WingKind kind) {
        return kind == WingKind.STORM ? 4 : 2;
    }

    private static int countInstances(WingKind kind, int entityId, double currentTick, boolean includeSweeps) {
        var count = persistentTornadoCount(kind) * RINGS_PER_TORNADO;
        if (entityId < 0 || !includeSweeps) return count;
        for (var entry : SWEEP_ANIMATIONS.get(kind).entries(entityId)) {
            var progress = SweepAnimationTimeline.progress(entry, currentTick, SWEEP_DURATION_TICKS);
            if (progress >= 0.0f && progress < 1.0f) count += RINGS_PER_TORNADO;
        }
        return count;
    }

    private static WingTransitionAnimation.Projection transitionProjection(int entityId, double currentTick) {
        var startTick = BLACK_TO_WHITE_TRANSITIONS.get(entityId);
        if (startTick == null) return null;
        var elapsedTicks = currentTick - startTick;
        return WingTransitionAnimation.isActive(elapsedTicks)
                ? WingTransitionAnimation.sample(elapsedTicks)
                : null;
    }

    private static boolean isTransitionWing(WingKind kind, WingTransitionAnimation.Projection transition) {
        return transition != null && (kind == WingKind.BLACK || kind == WingKind.WHITE);
    }

    private ByteBuffer ensureBuffer(WingKind kind, int requiredInstances) {
        var buffer = instanceBuffers.get(kind);
        var requiredBytes = (long) requiredInstances * INSTANCE_STRIDE;
        if (buffer == null || buffer.capacity() < requiredBytes) {
            buffer = BufferUtils.createByteBuffer(Math.max((int) requiredBytes, 512 * INSTANCE_STRIDE));
            instanceBuffers.put(kind, buffer);
        }
        buffer.clear();
        return buffer;
    }

    private ByteBuffer ensureAscensionBuffer(int requiredInstances) {
        var requiredBytes = (long) requiredInstances * INSTANCE_STRIDE;
        if (ascensionBuffer == null || ascensionBuffer.capacity() < requiredBytes) {
            ascensionBuffer = BufferUtils.createByteBuffer(Math.max((int) requiredBytes, 256 * INSTANCE_STRIDE));
        }
        ascensionBuffer.clear();
        return ascensionBuffer;
    }

    private static void buildPersistentWings(Std140Builder builder, Matrix4f modelRoot, WingKind kind, float time) {
        var tornado = new Matrix4f(modelRoot).mul(baseMatrix(kind));
        var widthScale = ringWidthScale(kind);
        if (kind == WingKind.STORM) {
            var m1 = new Matrix4f(tornado).mul(new Matrix4f().rotate(new Quaternionf().rotateZ(30.0f * Mth.DEG_TO_RAD).rotateX(30.0f * Mth.DEG_TO_RAD)));
            renderTornado(builder, m1, time + TORNADO_OFFSET_1, widthScale);
            var m2 = new Matrix4f(tornado).mul(new Matrix4f().rotate(new Quaternionf().rotateZ(-30.0f * Mth.DEG_TO_RAD).rotateX(30.0f * Mth.DEG_TO_RAD)));
            renderTornado(builder, m2, time + TORNADO_OFFSET_2, widthScale);
            var m3 = new Matrix4f(tornado).mul(new Matrix4f().rotate(new Quaternionf().rotateZ(30.0f * Mth.DEG_TO_RAD).rotateX(-30.0f * Mth.DEG_TO_RAD)));
            renderTornado(builder, m3, time + TORNADO_OFFSET_3, widthScale);
            var m4 = new Matrix4f(tornado).mul(new Matrix4f().rotate(new Quaternionf().rotateZ(-30.0f * Mth.DEG_TO_RAD).rotateX(-30.0f * Mth.DEG_TO_RAD)));
            renderTornado(builder, m4, time + TORNADO_OFFSET_4, widthScale);
        } else {
            buildAdvancedWings(builder, tornado, time, widthScale, 1.0f, 1.0f, 30.0f, 30.0f);
        }
    }

    private static void buildTransitionWings(
            Std140Builder builder,
            Matrix4f modelRoot,
            WingKind kind,
            float time,
            WingTransitionAnimation.Pose pose
    ) {
        if (!pose.visible()) return;
        var tornado = new Matrix4f(modelRoot).mul(WING_BASE_MATRIX);
        buildAdvancedWings(
                builder,
                tornado,
                time,
                ringWidthScale(kind),
                pose.radialScale(),
                pose.lengthScale(),
                pose.spreadDegrees(),
                pose.pitchDegrees()
        );
    }

    private static void buildAdvancedWings(
            Std140Builder builder,
            Matrix4f tornado,
            float time,
            float widthScale,
            float radialScale,
            float lengthScale,
            float spreadDegrees,
            float pitchDegrees
    ) {
        var left = new Matrix4f(tornado)
                .rotate(new Quaternionf()
                        .rotateZ(spreadDegrees * Mth.DEG_TO_RAD)
                        .rotateX(pitchDegrees * Mth.DEG_TO_RAD))
                .scale(radialScale, lengthScale, radialScale);
        renderTornado(builder, left, time + TORNADO_OFFSET_LEFT, widthScale);
        var right = new Matrix4f(tornado)
                .rotate(new Quaternionf()
                        .rotateZ(-spreadDegrees * Mth.DEG_TO_RAD)
                        .rotateX(pitchDegrees * Mth.DEG_TO_RAD))
                .scale(radialScale, lengthScale, radialScale);
        renderTornado(builder, right, time + TORNADO_OFFSET_RIGHT, widthScale);
    }

    private static void buildSweepAnimations(Std140Builder builder, Matrix4f modelRoot, WingKind kind,
                                             int entityId, double currentTick, float effectTime) {
        if (entityId < 0) return;
        var animations = SWEEP_ANIMATIONS.get(kind).entries(entityId);
        for (var entry : animations) {
            var progress = SweepAnimationTimeline.progress(entry, currentTick, SWEEP_DURATION_TICKS);
            if (progress < 0.0f || progress >= 1.0f) continue;
            var animation = entry.payload();
            var eased = 1.0f - (1.0f - progress) * (1.0f - progress);
            var side = animation.leftWing ? -1.0f : 1.0f;
            var sweepYaw = side * (SWEEP_BASE_YAW + SWEEP_ARC_DEGREES * eased) + animation.yawOffsetDeg;

            var matrix = new Matrix4f(modelRoot).mul(baseMatrix(kind));
            matrix.translate(side * SWEEP_PIVOT_SIDE, SWEEP_PIVOT_FORWARD, SWEEP_PIVOT_UPPER_Z);
            matrix.rotate(new Quaternionf().rotateZ(-sweepYaw * Mth.DEG_TO_RAD));
            matrix.rotate(new Quaternionf().rotateX(animation.pitchOffsetDeg * Mth.DEG_TO_RAD));
            matrix.rotate(new Quaternionf()
                    .rotateZ((animation.leftWing ? 30.0f : -30.0f) * Mth.DEG_TO_RAD)
                    .rotateX(30.0f * Mth.DEG_TO_RAD));
            matrix.scale(SWEEP_SCALE, SWEEP_SCALE, SWEEP_SCALE);
            renderTornado(
                    builder,
                    matrix,
                    effectTime + (animation.leftWing ? TORNADO_OFFSET_LEFT : TORNADO_OFFSET_RIGHT),
                    ringWidthScale(kind)
            );
        }
    }

    private static void renderTornado(Std140Builder builder, Matrix4f tornadoBase,
                                      float effectiveTime, float ringWidthScale) {
        var tPosBase = effectiveTime * TIME_POS_BASE;
        var tWarp = effectiveTime * TIME_POS_WARP;
        var tGap = effectiveTime * TIME_GAP;
        var tRadBase = effectiveTime * TIME_RAD_BASE;
        var tRadExtra = effectiveTime * TIME_RAD_EXTRA;
        var tJitter = effectiveTime * TIME_JITTER;
        var tRotBase = effectiveTime * TIME_ROT_BASE;
        var tRotMod = effectiveTime * TIME_ROT_MOD;
        var tWidthBase = effectiveTime * TIME_WIDTH_BASE;
        var tWidthDetail = effectiveTime * TIME_WIDTH_DETAIL;
        var tTilt = effectiveTime * TIME_TILT;

        var currentY = 0.0f;
        var matrix = new Matrix4f();
        var rotQuat = new Quaternionf();
        var tiltQuat = new Quaternionf();

        for (var i = 0; i < NUM_RINGS; i++) {
            var normalizedY = (NUM_RINGS <= 1) ? 0.5 : i / (double) (NUM_RINGS - 1);
            if (i > 0) currentY += calculateGap(i, tGap);

            var warpedY = normalizedY + ImprovedNoise.noise(normalizedY * 2.0, tWarp, 5.0) * POS_DOMAIN_WARP_SCALE;
            var actualY = currentY;

            var noiseX = ImprovedNoise.noise(warpedY * 1.3, tPosBase * 0.75, 10.0);
            var noiseZ = ImprovedNoise.noise(warpedY * 1.3, tPosBase * 0.75, 20.0);
            var heightScaleFactor = 0.4 + normalizedY * 1.6;
            var actualDx = (float) (noiseX * heightScaleFactor * SIZE * HORIZONTAL_DISPLACEMENT_SCALE * normalizedY);
            var actualDz = (float) (noiseZ * heightScaleFactor * SIZE * HORIZONTAL_DISPLACEMENT_SCALE * normalizedY);

            var rBase = calculateBaseRadius(normalizedY, tRadBase);
            var rWithExtra = addExtraRadiusNoise(rBase, i, tRadExtra);
            var rJitter = calculateRadiusJitter(i, tJitter);
            var finalRadiusMain = (float) Math.max(0.015 * SIZE, (rWithExtra + rJitter) * SIZE);

            var rotationAngle = calculateRotation(normalizedY, i, tRotBase, tRotMod);
            var ringWidth = calculateRingWidth(normalizedY, i, tWidthBase, tWidthDetail) * ringWidthScale;
            tiltQuat.identity()
                    .rotateZ((float) (ImprovedNoise.noise(i * 1.6, tTilt * 1.1, 90.0) * RING_TILT_SCALE))
                    .rotateX((float) (ImprovedNoise.noise(i * 1.6, tTilt * 1.1, 100.0) * RING_TILT_SCALE));
            rotQuat.identity().rotateY((float) rotationAngle).mul(tiltQuat);

            matrix.set(tornadoBase)
                    .translate(actualDx, actualY, actualDz)
                    .rotate(rotQuat)
                    .scale(finalRadiusMain, ringWidth, finalRadiusMain);
            builder.putMat4f(matrix);

            var nestedBaseRadiusRaw = rWithExtra * NESTED_RADIUS_FACTOR;
            var nestedJitter = calculateRadiusJitter(i + NUM_RINGS, tJitter + 0.5);
            var finalRadiusNested = (float) Math.max(0.01 * SIZE, (nestedBaseRadiusRaw + nestedJitter) * SIZE);
            var nestedWidth = Math.max(0.01f * SIZE, ringWidth * NESTED_WIDTH_FACTOR);

            matrix.set(tornadoBase)
                    .translate(actualDx, actualY, actualDz)
                    .rotate(rotQuat)
                    .scale(finalRadiusNested, nestedWidth, finalRadiusNested);
            builder.putMat4f(matrix);
        }
    }

    private static float calculateGap(int ringIndex, double timeGap) {
        var noise = (float) ImprovedNoise.noise(ringIndex * 0.7, timeGap, 11.0);
        var variablePart = noise * AVERAGE_GAP * GAP_VARIANCE_SCALE;
        return Math.max(AVERAGE_GAP * 0.2f, AVERAGE_GAP + variablePart);
    }

    private static double calculateBaseRadius(double normalizedY, double timeRadBase) {
        var funnelRadius = FUNNEL_BASE_RADIUS_FACTOR + Math.pow(normalizedY, FUNNEL_EXPONENT) * (1.0 - FUNNEL_BASE_RADIUS_FACTOR);
        var baseNoise = ImprovedNoise.noise(normalizedY * 1.1, timeRadBase * 0.8, 40.0);
        return funnelRadius * (1.0 + baseNoise * RADIUS_BASE_NOISE_SCALE);
    }

    private static double addExtraRadiusNoise(double baseRadius, int ringIndex, double timeRadExtra) {
        var extraNoise = ImprovedNoise.noise(ringIndex * 1.5, timeRadExtra * 1.5, 50.0);
        return baseRadius + extraNoise * RADIUS_EXTRA_NOISE_SCALE;
    }

    private static float calculateRadiusJitter(int ringIndex, double timeJitter) {
        return (float) (ImprovedNoise.noise(ringIndex * 3.0, timeJitter * 1.8, 55.0) * RADIUS_JITTER_SCALE);
    }

    private static double calculateRotation(double normalizedY, int ringIndex,
                                            double timeRotBase, double timeRotMod) {
        var modulation = (ImprovedNoise.noise(normalizedY * 0.7, timeRotMod, 65.0) + 1.0) * 0.5;
        var currentNoiseScale = ROTATION_BASE_NOISE_SCALE
                * (1.0 - ROTATION_MODULATION_SCALE + modulation * ROTATION_MODULATION_SCALE * 2.0);
        var baseRotation = timeRotBase * (1.0 + normalizedY * 0.35);
        var noiseOffset = ImprovedNoise.noise(ringIndex * 1.0, timeRotBase * 1.2, 60.0);
        return baseRotation + noiseOffset * currentNoiseScale;
    }

    private static float calculateRingWidth(double normalizedY, int ringIndex,
                                            double timeWidthBase, double timeWidthDetail) {
        var baseNoise = (float) ImprovedNoise.noise(normalizedY * 1.2, timeWidthBase, 80.0);
        var detailNoise = (float) ImprovedNoise.noise(ringIndex * 2.5, timeWidthDetail, 85.0);
        var width = BASE_RING_WIDTH
                * (1.0f + baseNoise * WIDTH_BASE_NOISE_SCALE + detailNoise * WIDTH_DETAIL_NOISE_SCALE);
        return Math.max(0.015f * SIZE, width);
    }

    static void renderSingleTornado(PoseStack poseStack, VertexConsumer vertexConsumer, float effectiveTime) {
        renderSingleTornado(poseStack, vertexConsumer, effectiveTime, 1.0f, 1.0f);
    }

    static void renderSingleTornado(PoseStack poseStack, VertexConsumer vertexConsumer,
                                    float effectiveTime, float ringWidthScale) {
        renderSingleTornado(poseStack, vertexConsumer, effectiveTime, ringWidthScale, 1.0f);
    }

    static void renderSingleTornado(PoseStack poseStack, VertexConsumer vertexConsumer,
                                    float effectiveTime, float ringWidthScale, float alpha) {
        var tPosBase = effectiveTime * TIME_POS_BASE;
        var tWarp = effectiveTime * TIME_POS_WARP;
        var tGap = effectiveTime * TIME_GAP;
        var tRadBase = effectiveTime * TIME_RAD_BASE;
        var tRadExtra = effectiveTime * TIME_RAD_EXTRA;
        var tJitter = effectiveTime * TIME_JITTER;
        var tRotBase = effectiveTime * TIME_ROT_BASE;
        var tRotMod = effectiveTime * TIME_ROT_MOD;
        var tWidthBase = effectiveTime * TIME_WIDTH_BASE;
        var tWidthDetail = effectiveTime * TIME_WIDTH_DETAIL;
        var tTilt = effectiveTime * TIME_TILT;

        var currentY = 0.0f;

        for (var i = 0; i < NUM_RINGS; i++) {
            var normalizedY = (NUM_RINGS <= 1) ? 0.5 : i / (double) (NUM_RINGS - 1);
            if (i > 0) currentY += calculateGap(i, tGap);

            warpedYBuffer[0] = normalizedY + ImprovedNoise.noise(normalizedY * 2.0, tWarp, 5.0) * POS_DOMAIN_WARP_SCALE;
            var actualY = currentY;

            var noiseX = ImprovedNoise.noise(warpedYBuffer[0] * 1.3, tPosBase * 0.75, 10.0);
            var noiseZ = ImprovedNoise.noise(warpedYBuffer[0] * 1.3, tPosBase * 0.75, 20.0);
            var heightScaleFactor = 0.4 + normalizedY * 1.6;
            var actualDx = (float) (noiseX * heightScaleFactor * SIZE * HORIZONTAL_DISPLACEMENT_SCALE * normalizedY);
            var actualDz = (float) (noiseZ * heightScaleFactor * SIZE * HORIZONTAL_DISPLACEMENT_SCALE * normalizedY);

            var rBase = calculateBaseRadius(normalizedY, tRadBase);
            var rWithExtra = addExtraRadiusNoise(rBase, i, tRadExtra);
            var rJitter = calculateRadiusJitter(i, tJitter);
            var finalRadiusMain = (float) Math.max(0.015 * SIZE, (rWithExtra + rJitter) * SIZE);

            var rotationAngle = calculateRotation(normalizedY, i, tRotBase, tRotMod);
            var ringWidth = calculateRingWidth(normalizedY, i, tWidthBase, tWidthDetail) * ringWidthScale;

            tempTiltQuat.identity()
                    .rotateZ((float) (ImprovedNoise.noise(i * 1.6, tTilt * 1.1, 90.0) * RING_TILT_SCALE))
                    .rotateX((float) (ImprovedNoise.noise(i * 1.6, tTilt * 1.1, 100.0) * RING_TILT_SCALE));

            poseStack.pushPose();
            {
                poseStack.translate(actualDx, actualY, actualDz);
                tempRotQuat.identity().rotateY((float) rotationAngle).mul(tempTiltQuat);
                poseStack.mulPose(tempRotQuat);

                poseStack.pushPose();
                {
                    poseStack.scale(finalRadiusMain, ringWidth, finalRadiusMain);
                    renderRing(
                            poseStack.last().pose(),
                            vertexConsumer,
                            RING_SEGMENTS,
                            CACHED_VERTICAL_VERTEX_BUFFER,
                            1, 1, 1, alpha
                    );
                }
                poseStack.popPose();

                var nestedBaseRadiusRaw = rWithExtra * NESTED_RADIUS_FACTOR;
                var nestedJitter = calculateRadiusJitter(i + NUM_RINGS, tJitter + 0.5);
                var finalRadiusNested = (float) Math.max(0.01 * SIZE, (nestedBaseRadiusRaw + nestedJitter) * SIZE);
                var nestedWidth = Math.max(0.01f * SIZE, ringWidth * NESTED_WIDTH_FACTOR);

                poseStack.pushPose();
                {
                    poseStack.scale(finalRadiusNested, nestedWidth, finalRadiusNested);
                    renderRing(
                            poseStack.last().pose(), vertexConsumer,
                            RING_SEGMENTS, CACHED_VERTICAL_VERTEX_BUFFER,
                            1, 1, 1, alpha
                    );
                }
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }

    public static void renderRing(Matrix4f matrix, VertexConsumer vertexConsumer,
                                  int segments, float[][][] vertexBuffer,
                                  float red, float green, float blue, float alpha) {
        for (var i = 0; i < segments; i++) {
            var v0 = vertexBuffer[i][0];
            var v1 = vertexBuffer[i][1];
            var v2 = vertexBuffer[i][2];
            var v3 = vertexBuffer[i][3];

            vertexConsumer.addVertex(matrix, v0[0], v0[1], v0[2]).setUv(v0[3], 0).setColor(red, green, blue, alpha);
            vertexConsumer.addVertex(matrix, v1[0], v1[1], v1[2]).setUv(v1[3], 0).setColor(red, green, blue, alpha);
            vertexConsumer.addVertex(matrix, v2[0], v2[1], v2[2]).setUv(v2[3], 1).setColor(red, green, blue, alpha);
            vertexConsumer.addVertex(matrix, v3[0], v3[1], v3[2]).setUv(v3[3], 1).setColor(red, green, blue, alpha);
        }
    }

    public static void submitThirdPersonCosmos(
            PoseStack poseStack, SubmitNodeCollector collector,
            int entityId, double currentTick, float effectTime
    ) {
        submitThirdPersonGeometry(
                poseStack, collector, PLATINUM_WING_BYPASS, WingKind.PLATINUM,
                entityId, currentTick, effectTime
        );
    }

    private static void submitThirdPersonGeometry(
            PoseStack poseStack, SubmitNodeCollector collector, RenderType activeRenderType,
            WingKind kind, int entityId, double currentTick, float effectTime
    ) {
        collector.submitCustomGeometry(poseStack, activeRenderType, (pose, buffer) -> {
            var wingStack = new PoseStack();
            wingStack.last().set(pose);
            wingStack.mulPose(baseMatrix(kind));
            renderPersistentWings(wingStack, buffer, kind, effectTime);
            renderSweepAnimations(wingStack, buffer, kind, entityId, currentTick, effectTime);
        });
    }

    public static boolean submitFirstPersonCosmos(
            PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player,
            int packedLight, float partialTick
    ) {
        return submitFirstPersonGeometry(
                poseStack, collector, player, partialTick, WingKind.PLATINUM, PLATINUM_WING_BYPASS
        );
    }

    public static boolean submitFirstPersonFallback(
            PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player,
            int packedLight, float partialTick
    ) {
        return submitFirstPersonGeometry(
                poseStack, collector, player, partialTick, WingKind.PLATINUM, WHITE_WING_FIRST_PERSON
        );
    }

    public static boolean submitFirstPerson(
            PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player,
            int packedLight, float partialTick
    ) {
        var submitted = false;
        for (var kind : WingKind.values()) {
            if (kind == WingKind.STORM) continue;
            if (!isActive(player, kind)) continue;
            submitted |= submitFirstPersonGeometry(
                    poseStack, collector, player, partialTick, kind, firstPersonRenderType(kind)
            );
        }
        return submitted;
    }

    public static boolean renderFirstPersonWhenHudHidden() {
        var player = Minecraft.getInstance().player;
        return player == null
                || !player.getData(AttachmentTypes.ACTIVATED_PLATINUM_WING);
    }

    private static RenderType firstPersonRenderType(WingKind kind) {
        return switch (kind) {
            case STORM -> STORM_WING;
            case BLACK -> BLACK_WING_FIRST_PERSON;
            case WHITE -> WHITE_WING_FIRST_PERSON;
            case PLATINUM -> PLATINUM_WING_FIRST_PERSON;
        };
    }

    private static boolean submitFirstPersonGeometry(
            PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player,
            float partialTick, WingKind kind, RenderType activeRenderType
    ) {
        if (!isActive(player, kind)) return false;
        var animations = SWEEP_ANIMATIONS.get(kind).entries(player.getId());
        if (animations.isEmpty()) return false;
        var currentTick = (double) player.level().getGameTime() + partialTick;
        collector.submitCustomGeometry(poseStack, activeRenderType,
                (pose, buffer) -> {
                    var projectionStack = new PoseStack();
                    projectionStack.last().set(pose);
                    renderFirstPersonSweeps(
                            projectionStack, buffer, animations, kind, currentTick,
                            player.tickCount + partialTick
                    );
                });
        return true;
    }

    private static void renderPersistentWings(PoseStack poseStack, VertexConsumer buffer, WingKind kind, float time) {
        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf()
                .rotateZ(30.0f * Mth.DEG_TO_RAD)
                .rotateX(30.0f * Mth.DEG_TO_RAD));
        renderSingleTornado(poseStack, buffer, time + TORNADO_OFFSET_LEFT, ringWidthScale(kind));
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf()
                .rotateZ(-30.0f * Mth.DEG_TO_RAD)
                .rotateX(30.0f * Mth.DEG_TO_RAD));
        renderSingleTornado(poseStack, buffer, time + TORNADO_OFFSET_RIGHT, ringWidthScale(kind));
        poseStack.popPose();
    }

    private static void renderSweepAnimations(PoseStack poseStack, VertexConsumer buffer,
                                              WingKind kind, int entityId, double currentTick, float effectTime) {
        if (entityId < 0) return;
        var animations = SWEEP_ANIMATIONS.get(kind).entries(entityId);
        for (var entry : animations) {
            var progress = SweepAnimationTimeline.progress(entry, currentTick, SWEEP_DURATION_TICKS);
            if (progress < 0.0f || progress >= 1.0f) continue;
            var animation = entry.payload();
            var eased = 1.0f - (1.0f - progress) * (1.0f - progress);
            var side = animation.leftWing ? -1.0f : 1.0f;
            var sweepYaw = side * (SWEEP_BASE_YAW + SWEEP_ARC_DEGREES * eased) + animation.yawOffsetDeg;

            poseStack.pushPose();
            poseStack.translate(side * SWEEP_PIVOT_SIDE, SWEEP_PIVOT_FORWARD, SWEEP_PIVOT_UPPER_Z);
            poseStack.mulPose(new Quaternionf().rotateZ(-sweepYaw * Mth.DEG_TO_RAD));
            poseStack.mulPose(new Quaternionf().rotateX(animation.pitchOffsetDeg * Mth.DEG_TO_RAD));
            poseStack.mulPose(new Quaternionf()
                    .rotateZ((animation.leftWing ? 30.0f : -30.0f) * Mth.DEG_TO_RAD)
                    .rotateX(30.0f * Mth.DEG_TO_RAD));
            poseStack.scale(SWEEP_SCALE, SWEEP_SCALE, SWEEP_SCALE);
            renderSingleTornado(
                    poseStack,
                    buffer,
                    effectTime + (animation.leftWing ? TORNADO_OFFSET_LEFT : TORNADO_OFFSET_RIGHT),
                    ringWidthScale(kind)
            );
            poseStack.popPose();
        }
    }

    private static void renderFirstPersonSweeps(PoseStack poseStack, VertexConsumer buffer,
                                                List<SweepAnimationTimeline.Entry<SweepAnimation>> animations,
                                                WingKind kind, double currentTick, float effectTime) {
        for (var entry : animations) {
            var progress = SweepAnimationTimeline.progress(entry, currentTick, SWEEP_DURATION_TICKS);
            if (progress < 0.0f || progress >= 1.0f) continue;
            var animation = entry.payload();
            var projection = FirstPersonSweepGeometry.wingProjection(animation.leftWing, progress);
            if (projection.alpha() <= 0.001f) continue;

            poseStack.pushPose();
            poseStack.translate(projection.rootX(), projection.rootY(), projection.rootZ());
            poseStack.mulPose(new Quaternionf()
                    .rotateZ(projection.sweepDegrees() * Mth.DEG_TO_RAD)
                    .rotateX(projection.tiltDegrees() * Mth.DEG_TO_RAD));
            poseStack.scale(projection.scale(), projection.scale(), projection.scale());
            renderSingleTornado(
                    poseStack,
                    buffer,
                    effectTime + (animation.leftWing ? TORNADO_OFFSET_LEFT : TORNADO_OFFSET_RIGHT),
                    ringWidthScale(kind),
                    projection.alpha() * 0.92f
            );
            poseStack.popPose();
        }
    }

    private record SweepAnimation(boolean leftWing, float yawOffsetDeg, float pitchOffsetDeg) {
    }
}
