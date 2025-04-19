package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.academy.AcademyCraft;
import org.academy.api.client.renderer.EffectRenderer;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.ImprovedNoise;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.ability.builtin.accelerator.skills.StormWing;
import org.academy.internal.common.world.entity.player.PlayerSyncSkillData;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@SuppressWarnings("SuspiciousNameCombination")
public class StormWingEffectRenderer implements EffectRenderer {
    public static final EffectRenderer INSTANCE = new StormWingEffectRenderer();
    public static final ResourceLocation TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/skill/effect/accelerator/tornado_ring.png");
    public static final int RING_SEGMENTS = 12; // 每个环的顶点数
    private static final RandomSource RAND = RandomSource.create(); // 随机数生成器
    private static final Matrix4f BASE_MATRIX = new Matrix4f().rotateX((float) Math.toRadians(90.0f)).translate(0, 0.25f, 0); // 基础变换矩阵
    private static final RenderType RENDER_TYPE = RenderUtil.RingRenderer.RING_RENDER_TYPE.apply(TEXTURE); // 渲染类型
    private static final int NUM_RINGS = 24; // 每个龙卷风的环数量
    private static final float HEIGHT = 3.5f; // 目标视觉高度
    private static final float SIZE = 1.0f; // 整体尺寸缩放
    private static final float averageGap = (NUM_RINGS > 1) ? HEIGHT / (float) (NUM_RINGS - 1) : HEIGHT; // 平均垂直间隙
    private static final float FUNNEL_BASE_RADIUS_FACTOR = 0.2F; // 漏斗底部半径因子
    private static final float FUNNEL_EXPONENT = 1.75F; // 漏斗曲率指数
    private static final float HORIZONTAL_DISPLACEMENT_SCALE = 1.6f; // 水平位移缩放
    private static final double POS_DOMAIN_WARP_SCALE = 0.15; // 位置域扭曲强度
    private static final float GAP_VARIANCE_SCALE = 0.6f; // 间隙变化缩放
    private static final float VERTICAL_POSITION_JITTER = 0.03f; // 垂直位置抖动
    private static final float BASE_RING_WIDTH = 0.075f * SIZE; // 基础环宽度
    private static final float RADIUS_BASE_NOISE_SCALE = 0.15f; // 半径基础噪声缩放
    private static final float RADIUS_EXTRA_NOISE_SCALE = 0.20f; // 半径额外噪声缩放
    private static final float RADIUS_JITTER_SCALE = 0.03f; // 半径抖动缩放
    private static final float ROTATION_BASE_NOISE_SCALE = 0.45f * MathUtil.PI; // 旋转基础噪声缩放
    private static final float ROTATION_MODULATION_SCALE = 0.30f; // 旋转调制缩放
    private static final float RING_TILT_SCALE = 0.10f; // 环倾斜缩放
    private static final float WIDTH_BASE_NOISE_SCALE = 0.4f; // 宽度基础噪声缩放
    private static final float WIDTH_DETAIL_NOISE_SCALE = 0.25f; // 宽度细节噪声缩放
    private static final float TIME_SCALE_GLOBAL = 1.1f; // 全局时间缩放
    private static final double TIME_POS_BASE = 0.07 * TIME_SCALE_GLOBAL; // 时间 - 位置基础
    private static final double TIME_POS_WARP = 0.30 * TIME_SCALE_GLOBAL; // 时间 - 位置扭曲
    private static final double TIME_GAP = 0.09 * TIME_SCALE_GLOBAL; // 时间 - 间隙
    private static final double TIME_RAD_BASE = 0.11 * TIME_SCALE_GLOBAL; // 时间 - 半径基础
    private static final double TIME_RAD_EXTRA = 0.22 * TIME_SCALE_GLOBAL; // 时间 - 半径额外
    private static final double TIME_ROT_BASE = 0.55 * TIME_SCALE_GLOBAL; // 时间 - 旋转基础
    private static final double TIME_ROT_MOD = 0.16 * TIME_SCALE_GLOBAL; // 时间 - 旋转调制
    private static final double TIME_WIDTH_BASE = 0.15 * TIME_SCALE_GLOBAL; // 时间 - 宽度基础
    private static final double TIME_WIDTH_DETAIL = 0.55 * TIME_SCALE_GLOBAL; // 时间 - 宽度细节
    private static final double TIME_TILT = 0.18 * TIME_SCALE_GLOBAL; // 时间 - 倾斜
    private static final double TIME_JITTER = 0.85 * TIME_SCALE_GLOBAL; // 时间 - 抖动
    private static final float NESTED_RING_PROBABILITY = 0.40f; // 嵌套环概率
    private static final float NESTED_RADIUS_FACTOR = 0.50f; // 嵌套环半径因子
    private static final float NESTED_WIDTH_FACTOR = 0.75f; // 嵌套环宽度因子
    private static final float TORNADO_OFFSET_1 = 0.0f; // 龙卷风偏移量1
    private static final float TORNADO_OFFSET_2 = 20.0f; // 龙卷风偏移量2
    private static final float TORNADO_OFFSET_3 = 45.0f; // 龙卷风偏移量3
    private static final float TORNADO_OFFSET_4 = 70.0f; // 龙卷风偏移量4
    private static final double[] displacementBuffer = new double[2]; // 位移缓冲区
    private static final Quaternionf tempTiltQuat = new Quaternionf(); // 临时倾斜四元数
    private static final Quaternionf tempRotQuat = new Quaternionf(); // 临时旋转四元数
    private static final double[] warpedYBuffer = new double[1]; // 扭曲Y坐标缓冲区
    private static final float[][][] CACHED_VERTICAL_VERTEX_BUFFER = VertexUtil.Ring.getVerticalVertexBuffer(1.0f, 1.0f, RING_SEGMENTS); // 缓存的垂直顶点缓冲区

    private StormWingEffectRenderer() {
    }

    private static void applyDomainWarp(double normalizedY, double timeWarp) {
        warpedYBuffer[0] = normalizedY + ImprovedNoise.noise(normalizedY * 2.0, timeWarp, 5.0) * POS_DOMAIN_WARP_SCALE;
    }

    private static void calculateHorizontalDisplacement(double normalizedY, double timePosBase) {
        double noiseX = ImprovedNoise.noise(warpedYBuffer[0] * 1.3, timePosBase * 0.75, 10.0);
        double noiseZ = ImprovedNoise.noise(warpedYBuffer[0] * 1.3, timePosBase * 0.75, 20.0);
        double heightScaleFactor = 0.4 + normalizedY * 1.6;
        displacementBuffer[0] = noiseX * heightScaleFactor;
        displacementBuffer[1] = noiseZ * heightScaleFactor;
    }

    private static double calculateVerticalJitter(double timePosBase, double timeJitter) {
        double noise = ImprovedNoise.noise(warpedYBuffer[0] * 2.2, timePosBase * 1.1 + timeJitter * 0.5, 30.0);
        return noise * VERTICAL_POSITION_JITTER;
    }

    private static float calculateGap(int ringIndex, double timeGap) {
        float noise = (float) ImprovedNoise.noise(ringIndex * 0.7, timeGap, 11.0);
        float variablePart = noise * averageGap * GAP_VARIANCE_SCALE;
        return Math.max(averageGap * 0.2f, averageGap + variablePart);
    }

    private static double calculateBaseRadius(double normalizedY, double timeRadBase) {
        double funnelRadius = FUNNEL_BASE_RADIUS_FACTOR + Math.pow(normalizedY, FUNNEL_EXPONENT) * (1.0 - FUNNEL_BASE_RADIUS_FACTOR);
        double baseNoise = ImprovedNoise.noise(normalizedY * 1.1, timeRadBase * 0.8, 40.0);
        return funnelRadius * (1.0 + baseNoise * RADIUS_BASE_NOISE_SCALE);
    }

    private static double addExtraRadiusNoise(double baseRadius, int ringIndex, double timeRadExtra) {
        double extraNoise = ImprovedNoise.noise(ringIndex * 1.5, timeRadExtra * 1.5, 50.0);
        return baseRadius + extraNoise * RADIUS_EXTRA_NOISE_SCALE;
    }

    private static float calculateRadiusJitter(int ringIndex, double timeJitter) {
        double jitterNoise = ImprovedNoise.noise(ringIndex * 3.0, timeJitter * 1.8, 55.0);
        return (float) jitterNoise * RADIUS_JITTER_SCALE;
    }

    private static double calculateRotation(double normalizedY, int ringIndex, double timeRotBase, double timeRotMod) {
        double modulation = (ImprovedNoise.noise(normalizedY * 0.7, timeRotMod, 65.0) + 1.0) * 0.5;
        double currentNoiseScale = ROTATION_BASE_NOISE_SCALE * (1.0 - ROTATION_MODULATION_SCALE + modulation * ROTATION_MODULATION_SCALE * 2.0);
        double baseRotation = timeRotBase * (1.0 + normalizedY * 0.35);
        double noiseOffset = ImprovedNoise.noise(ringIndex * 1.0, timeRotBase * 1.2, 60.0);
        return baseRotation + noiseOffset * currentNoiseScale;
    }

    private static float calculateRingWidth(double normalizedY, int ringIndex, double timeWidthBase, double timeWidthDetail) {
        float baseNoise = (float) ImprovedNoise.noise(normalizedY * 1.2, timeWidthBase, 80.0);
        float detailNoise = (float) ImprovedNoise.noise(ringIndex * 2.5, timeWidthDetail, 85.0);
        float width = BASE_RING_WIDTH * (1.0f + baseNoise * WIDTH_BASE_NOISE_SCALE + detailNoise * WIDTH_DETAIL_NOISE_SCALE);
        return Math.max(0.015f * SIZE, width);
    }

    private static void calculateTilt(int ringIndex, double timeTilt) {
        double tiltNoiseX = ImprovedNoise.noise(ringIndex * 1.6, timeTilt * 1.1, 90.0);
        double tiltNoiseZ = ImprovedNoise.noise(ringIndex * 1.6, timeTilt * 1.1, 100.0);
        tempTiltQuat.identity().rotateZ((float) (tiltNoiseZ * RING_TILT_SCALE)).rotateX((float) (tiltNoiseX * RING_TILT_SCALE));
    }

    private static void renderSingleTornado(@NotNull PoseStack poseStack, @NotNull VertexConsumer vertexConsumer, float effectiveTime) {
        double tPosBase = effectiveTime * TIME_POS_BASE;
        double tWarp = effectiveTime * TIME_POS_WARP;
        double tGap = effectiveTime * TIME_GAP;
        double tRadBase = effectiveTime * TIME_RAD_BASE;
        double tRadExtra = effectiveTime * TIME_RAD_EXTRA;
        double tJitter = effectiveTime * TIME_JITTER;
        double tRotBase = effectiveTime * TIME_ROT_BASE;
        double tRotMod = effectiveTime * TIME_ROT_MOD;
        double tWidthBase = effectiveTime * TIME_WIDTH_BASE;
        double tWidthDetail = effectiveTime * TIME_WIDTH_DETAIL;
        double tTilt = effectiveTime * TIME_TILT;

        float currentY = 0.0f;

        for (int i = 0; i < NUM_RINGS; i++) {
            double normalizedY = (NUM_RINGS <= 1) ? 0.5 : i / (double) (NUM_RINGS - 1);

            if (i > 0) {
                currentY += calculateGap(i, tGap);
            }

            applyDomainWarp(normalizedY, tWarp);

            double yJitter = calculateVerticalJitter(tPosBase, tJitter);
            double actualY = currentY + yJitter;

            calculateHorizontalDisplacement(normalizedY, tPosBase);
            double actualDx = displacementBuffer[0] * SIZE * HORIZONTAL_DISPLACEMENT_SCALE;
            double actualDz = displacementBuffer[1] * SIZE * HORIZONTAL_DISPLACEMENT_SCALE;
            actualDx *= normalizedY;
            actualDz *= normalizedY;

            double rBase = calculateBaseRadius(normalizedY, tRadBase);
            double rWithExtra = addExtraRadiusNoise(rBase, i, tRadExtra);
            float rJitter = calculateRadiusJitter(i, tJitter);
            float finalRadiusMain = (float) Math.max(0.015 * SIZE, (rWithExtra + rJitter) * SIZE);

            double rotationAngle = calculateRotation(normalizedY, i, tRotBase, tRotMod);
            float ringWidth = calculateRingWidth(normalizedY, i, tWidthBase, tWidthDetail);
            calculateTilt(i, tTilt);

            poseStack.pushPose();
            poseStack.translate(actualDx, actualY, actualDz);
            tempRotQuat.identity().rotateY((float) rotationAngle);
            tempRotQuat.mul(tempTiltQuat);
            poseStack.mulPose(tempRotQuat);

            poseStack.pushPose();
            poseStack.scale(finalRadiusMain, ringWidth, finalRadiusMain);
            RenderUtil.RingRenderer.renderRing(
                    poseStack.last().pose(),
                    vertexConsumer,
                    RING_SEGMENTS,
                    CACHED_VERTICAL_VERTEX_BUFFER
            );
            poseStack.popPose();

            if (RAND.nextFloat() < NESTED_RING_PROBABILITY) {
                double nestedBaseRadiusRaw = rWithExtra * NESTED_RADIUS_FACTOR;
                float nestedJitter = calculateRadiusJitter(i + NUM_RINGS, tJitter + 0.5);
                float finalRadiusNested = (float) Math.max(0.01 * SIZE, (nestedBaseRadiusRaw + nestedJitter) * SIZE);
                float nestedWidth = Math.max(0.01f * SIZE, ringWidth * NESTED_WIDTH_FACTOR);

                poseStack.pushPose();
                poseStack.scale(finalRadiusNested, nestedWidth, finalRadiusNested);
                RenderUtil.RingRenderer.renderRing(
                        poseStack.last().pose(), vertexConsumer,
                        RING_SEGMENTS, CACHED_VERTICAL_VERTEX_BUFFER
                );
                poseStack.popPose();
            }

            poseStack.popPose();
        }
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, @NotNull AbstractClientPlayer livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!livingEntity.getEntityData().get(PlayerSyncSkillData.SKILL_DATA).getBoolean(StormWing.TAG_KEY)) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPoseMatrix(BASE_MATRIX);

        VertexConsumer vertexConsumer = buffer.getBuffer(RENDER_TYPE);

        float time = livingEntity.tickCount + partialTick;

        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(30.0f)).rotateX((float) Math.toRadians(30.0f)));
        renderSingleTornado(poseStack, vertexConsumer, time + TORNADO_OFFSET_1);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-30.0f)).rotateX((float) Math.toRadians(30.0f)));
        renderSingleTornado(poseStack, vertexConsumer, time + TORNADO_OFFSET_2);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(30.0f)).rotateX((float) Math.toRadians(-30.0f)));
        renderSingleTornado(poseStack, vertexConsumer, time + TORNADO_OFFSET_3);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-30.0f)).rotateX((float) Math.toRadians(-30.0f)));
        renderSingleTornado(poseStack, vertexConsumer, time + TORNADO_OFFSET_4);
        poseStack.popPose();

        poseStack.popPose();
    }
}