package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import org.academy.api.client.compatibility.IrisCompat;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.ImprovedNoise;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import static org.academy.AcademyCraft.academy;
import static org.academy.api.client.Render.RenderTypes.STORM_WING;

@SuppressWarnings("SuspiciousNameCombination")
public final class StormWingEffectRenderer implements EffectRenderer {
    public static final ContextKey<Boolean> CONTEXT_KEY = new ContextKey<>(academy("storm_wing"));
    public static final StormWingEffectRenderer INSTANCE = new StormWingEffectRenderer();
    public static final int RING_SEGMENTS = 4;
    private static final Matrix4f BASE_MATRIX = new Matrix4f().rotateX((float) Math.toRadians(90.0f)).translate(0, 0.25f, -0.25f);
    private static final int NUM_RINGS = 24;
    private static final float HEIGHT = 3.5f;
    private static final float SIZE = 1.0f;
    private static final float averageGap = (NUM_RINGS > 1) ? HEIGHT / (float) (NUM_RINGS - 1) : HEIGHT;
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
    private static final double[] displacementBuffer = new double[2];
    private static final Quaternionf tempTiltQuat = new Quaternionf();
    private static final Quaternionf tempRotQuat = new Quaternionf();
    private static final double[] warpedYBuffer = new double[1];
    private static final float[][][] CACHED_VERTICAL_VERTEX_BUFFER = VertexUtil.Ring.getVerticalVertexBuffer(1.0f, 1.0f, RING_SEGMENTS);

    @Override
    public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState renderState, float yRot, float xRot) {
        if (IrisCompat.isShadowRendererActive()) return;
        if (!renderState.getRenderDataOrDefault(CONTEXT_KEY, false)) return;

        submitNodeCollector.submitCustomGeometry(poseStack, STORM_WING, (pose, buffer) -> {
            var poseStack1 = new PoseStack();
            poseStack1.last().set(pose);

            poseStack1.pushPose();
            poseStack1.mulPose(BASE_MATRIX);
            var time = renderState.ageInTicks;

            poseStack1.pushPose();
            poseStack1.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(30.0f)).rotateX((float) Math.toRadians(30.0f)));
            renderSingleTornado(poseStack1, buffer, time + TORNADO_OFFSET_1);
            poseStack1.popPose();

            poseStack1.pushPose();
            poseStack1.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-30.0f)).rotateX((float) Math.toRadians(30.0f)));
            renderSingleTornado(poseStack1, buffer, time + TORNADO_OFFSET_2);
            poseStack1.popPose();

            poseStack1.pushPose();
            poseStack1.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(30.0f)).rotateX((float) Math.toRadians(-30.0f)));
            renderSingleTornado(poseStack1, buffer, time + TORNADO_OFFSET_3);
            poseStack1.popPose();

            poseStack1.pushPose();
            poseStack1.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-30.0f)).rotateX((float) Math.toRadians(-30.0f)));
            renderSingleTornado(poseStack1, buffer, time + TORNADO_OFFSET_4);
            poseStack1.popPose();

            poseStack1.popPose();
        });
    }

    private static void applyDomainWarp(double normalizedY, double timeWarp) {
        warpedYBuffer[0] = normalizedY + ImprovedNoise.noise(normalizedY * 2.0, timeWarp, 5.0) * POS_DOMAIN_WARP_SCALE;
    }

    private static void calculateHorizontalDisplacement(double normalizedY, double timePosBase) {
        var noiseX = ImprovedNoise.noise(warpedYBuffer[0] * 1.3, timePosBase * 0.75, 10.0);
        var noiseZ = ImprovedNoise.noise(warpedYBuffer[0] * 1.3, timePosBase * 0.75, 20.0);
        var heightScaleFactor = 0.4 + normalizedY * 1.6;
        displacementBuffer[0] = noiseX * heightScaleFactor;
        displacementBuffer[1] = noiseZ * heightScaleFactor;
    }

    private static float calculateGap(int ringIndex, double timeGap) {
        var noise = (float) ImprovedNoise.noise(ringIndex * 0.7, timeGap, 11.0);
        var variablePart = noise * averageGap * GAP_VARIANCE_SCALE;
        return Math.max(averageGap * 0.2f, averageGap + variablePart);
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
        var jitterNoise = ImprovedNoise.noise(ringIndex * 3.0, timeJitter * 1.8, 55.0);
        return (float) jitterNoise * RADIUS_JITTER_SCALE;
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

    private static void calculateTilt(int ringIndex, double timeTilt) {
        var tiltNoiseX = ImprovedNoise.noise(ringIndex * 1.6, timeTilt * 1.1, 90.0);
        var tiltNoiseZ = ImprovedNoise.noise(ringIndex * 1.6, timeTilt * 1.1, 100.0);
        tempTiltQuat.identity().rotateZ((float) (tiltNoiseZ * RING_TILT_SCALE)).rotateX((float) (tiltNoiseX * RING_TILT_SCALE));
    }

    private static void renderSingleTornado(PoseStack poseStack, VertexConsumer vertexConsumer, float effectiveTime) {
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

            applyDomainWarp(normalizedY, tWarp);

            var actualY = currentY;

            calculateHorizontalDisplacement(normalizedY, tPosBase);
            var actualDx = displacementBuffer[0] * SIZE * HORIZONTAL_DISPLACEMENT_SCALE;
            var actualDz = displacementBuffer[1] * SIZE * HORIZONTAL_DISPLACEMENT_SCALE;
            actualDx *= normalizedY;
            actualDz *= normalizedY;

            var rBase = calculateBaseRadius(normalizedY, tRadBase);
            var rWithExtra = addExtraRadiusNoise(rBase, i, tRadExtra);
            var rJitter = calculateRadiusJitter(i, tJitter);
            var finalRadiusMain = (float) Math.max(0.015 * SIZE, (rWithExtra + rJitter) * SIZE);

            var rotationAngle = calculateRotation(normalizedY, i, tRotBase, tRotMod);
            var ringWidth = calculateRingWidth(normalizedY, i, tWidthBase, tWidthDetail);
            calculateTilt(i, tTilt);

            poseStack.pushPose();
            {
                poseStack.translate(actualDx, actualY, actualDz);
                tempRotQuat.identity().rotateY((float) rotationAngle);
                tempRotQuat.mul(tempTiltQuat);
                poseStack.mulPose(tempRotQuat);

                poseStack.pushPose();
                {
                    poseStack.scale(finalRadiusMain, ringWidth, finalRadiusMain);
                    renderRing(
                            poseStack.last().pose(),
                            vertexConsumer,
                            RING_SEGMENTS,
                            CACHED_VERTICAL_VERTEX_BUFFER,
                            1, 1, 1, 1
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
                            1, 1, 1, 1
                    );
                }
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }

    public static void renderRing(Matrix4f matrix, VertexConsumer vertexConsumer,
                                  int segments, float[][][] vertexBuffer, float red, float green, float blue, float alpha) {
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

    private StormWingEffectRenderer() {
    }
}