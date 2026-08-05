package org.academy.internal.client.renderer.vfx;

import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.academy.api.client.render.vfx.Vfx;
import org.academy.api.client.render.vfx.VfxFrameContext;
import org.academy.api.client.render.vfx.VfxSink;
import org.academy.api.client.vanilla.AvatarRendererContext;
import org.academy.api.common.util.ImprovedNoise;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

public final class StormWingVfx implements Vfx {
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
    private static final Matrix4f BASE_MATRIX = new Matrix4f()
            .rotateX((float) Math.toRadians(90.0f))
            .translate(0, 0.25f, -0.25f);
    private static final int MAX_INSTANCES = 512;

    private @Nullable ByteBuffer instanceData;

    private static void buildGeometry(Std140Builder builder, Matrix4f modelRoot, float time) {
        var base = new Matrix4f();
        base.set(modelRoot);
        var tornado = new Matrix4f(base).mul(BASE_MATRIX);
        var m1 = new Matrix4f(tornado).mul(new Matrix4f().rotate(new Quaternionf().rotateZ((float) Math.toRadians(30.0f)).rotateX((float) Math.toRadians(30.0f))));
        renderTornado(builder, m1, time + TORNADO_OFFSET_1);
        var m2 = new Matrix4f(tornado).mul(new Matrix4f().rotate(new Quaternionf().rotateZ((float) Math.toRadians(-30.0f)).rotateX((float) Math.toRadians(30.0f))));
        renderTornado(builder, m2, time + TORNADO_OFFSET_2);
        var m3 = new Matrix4f(tornado).mul(new Matrix4f().rotate(new Quaternionf().rotateZ((float) Math.toRadians(30.0f)).rotateX((float) Math.toRadians(-30.0f))));
        renderTornado(builder, m3, time + TORNADO_OFFSET_3);
        var m4 = new Matrix4f(tornado).mul(new Matrix4f().rotate(new Quaternionf().rotateZ((float) Math.toRadians(-30.0f)).rotateX((float) Math.toRadians(-30.0f))));
        renderTornado(builder, m4, time + TORNADO_OFFSET_4);
    }

    private static void renderTornado(Std140Builder builder, Matrix4f tornadoBase, float effectiveTime) {
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
            var ringWidth = calculateRingWidth(normalizedY, i, tWidthBase, tWidthDetail);
            tiltQuat.identity()
                    .rotateZ((float) (ImprovedNoise.noise(i * 1.6, tTilt * 1.1, 90.0) * RING_TILT_SCALE))
                    .rotateX((float) (ImprovedNoise.noise(i * 1.6, tTilt * 1.1, 100.0) * RING_TILT_SCALE));
            rotQuat.identity().rotateY((float) rotationAngle).mul(tiltQuat);

            matrix.set(tornadoBase)
                    .translate(actualDx, actualY, actualDz)
                    .rotate(rotQuat)
                    .scale(finalRadiusMain, ringWidth, finalRadiusMain);
            matrix.m30(matrix.m30() - (float) 0.0);
            matrix.m31(matrix.m31() - (float) 0.0);
            matrix.m32(matrix.m32() - (float) 0.0);
            builder.putMat4f(matrix);

            var nestedBaseRadiusRaw = rWithExtra * NESTED_RADIUS_FACTOR;
            var nestedJitter = calculateRadiusJitter(i + NUM_RINGS, tJitter + 0.5);
            var finalRadiusNested = (float) Math.max(0.01 * SIZE, (nestedBaseRadiusRaw + nestedJitter) * SIZE);
            var nestedWidth = Math.max(0.01f * SIZE, ringWidth * NESTED_WIDTH_FACTOR);

            matrix.set(tornadoBase)
                    .translate(actualDx, actualY, actualDz)
                    .rotate(rotQuat)
                    .scale(finalRadiusNested, nestedWidth, finalRadiusNested);
            matrix.m30(matrix.m30() - (float) 0.0);
            matrix.m31(matrix.m31() - (float) 0.0);
            matrix.m32(matrix.m32() - (float) 0.0);
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

    private static double calculateRotation(double normalizedY, int ringIndex, double timeRotBase, double timeRotMod) {
        var modulation = (ImprovedNoise.noise(normalizedY * 0.7, timeRotMod, 65.0) + 1.0) * 0.5;
        var currentNoiseScale = ROTATION_BASE_NOISE_SCALE * (1.0 - ROTATION_MODULATION_SCALE + modulation * ROTATION_MODULATION_SCALE * 2.0);
        var baseRotation = timeRotBase * (1.0 + normalizedY * 0.35);
        var noiseOffset = ImprovedNoise.noise(ringIndex * 1.0, timeRotBase * 1.2, 60.0);
        return baseRotation + noiseOffset * currentNoiseScale;
    }

    private static float calculateRingWidth(double normalizedY, int ringIndex, double timeWidthBase, double timeWidthDetail) {
        var baseNoise = (float) ImprovedNoise.noise(normalizedY * 1.2, timeWidthBase, 80.0);
        var detailNoise = (float) ImprovedNoise.noise(ringIndex * 2.5, timeWidthDetail, 85.0);
        var width = BASE_RING_WIDTH * (1.0f + baseNoise * WIDTH_BASE_NOISE_SCALE + detailNoise * WIDTH_DETAIL_NOISE_SCALE);
        return Math.max(0.015f * SIZE, width);
    }

    @Override
    public void sample(VfxFrameContext ctx, VfxSink sink) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;
        if (!player.getData(AttachmentTypes.ACTIVATED_STORM_WING)) return;

        var partialTick = ctx.partialTick();

        if (instanceData == null) {
            instanceData = BufferUtils.createByteBuffer(MAX_INSTANCES * 64);
        }
        instanceData.clear();
        var builder = Std140Builder.intoBuffer(instanceData);
        var modelRoot = getModelRootMatrix(mc.getEntityRenderDispatcher().getPlayerRenderer(player));
        if (modelRoot == null) return;
        buildGeometry(
                builder,
                modelRoot,
                player.tickCount + partialTick
        );
        instanceData.flip();
        if (instanceData.hasRemaining()) {
            sink.push(new StormWingData(instanceData));
        }
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    private static @Nullable Matrix4f getModelRootMatrix(AvatarRendererContext context) {
        return context.takeModelRootMatrix();
    }
}
