package org.academy.internal.client.renderer.entity;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.academy.AcademyCraft;
import org.academy.api.client.util.RenderStateUtil;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.client.util.VertexUtil;
import org.academy.api.common.util.ImprovedNoise;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.skill.Plasma;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Random;

@SuppressWarnings("DuplicatedCode")
public class PlasmaRenderer extends EntityRenderer<Plasma> {
    public static final float CORE_Y_OFFSET = 200F; // 核心Y轴偏移
    public static final float MIN_GATHER_FACTOR = 0.25f; // 最小聚集因子
    public static final float MAX_EFFECT_RADIUS = 512F; // 最大效果半径
    public static final float MAX_EFFECT_HEIGHT_REL_CORE = 175F; // 相对于核心的最大效果高度
    public static final float MIN_EFFECT_HEIGHT_REL_CORE = -CORE_Y_OFFSET; // 相对于核心的最小效果高度 (与核心Y偏移一致)
    private static final float TOTAL_VERTICAL_RANGE_REL_CORE = MAX_EFFECT_HEIGHT_REL_CORE - MIN_EFFECT_HEIGHT_REL_CORE; // 相对于核心的总垂直范围
    private static final float RADIUS_MIDPOINT_NORMALIZED_Y = Math.abs(MIN_EFFECT_HEIGHT_REL_CORE) / TOTAL_VERTICAL_RANGE_REL_CORE; // 半径中点的归一化Y坐标
    private static final ResourceLocation PLASMA_PARTICLE_TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/ability/generic/effect/generic/sparkle_blurred.png"); // 等离子体粒子纹理
    private static final ResourceLocation ATMOSPHERE_TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/ability/generic/effect/generic/white_smoke_hq.png"); // 大气效果纹理
    private static final float CORE_BASE_RADIUS = 16.0f; // 核心基础半径
    private static final float CORE_PULSATION_AMP = 1.0f; // 核心脉动幅度
    private static final float CORE_PULSATION_FREQ = 0.5f; // 核心脉动频率
    private static final int CORE_SPHERE_FACES = 16; // 核心球体面数
    private static final float[] CORE_COLOR_INNER = {0.8f, 0.1f, 1.0f, 0.85f}; // 核心内部颜色 (RGBA)
    private static final float[] CORE_COLOR_GLOW = {0.5f, 0.0f, 0.8f, 0.4f}; // 核心辉光颜色 (RGBA)
    private static final int NUM_CORE_TENDRILS = 20; // 核心触须数量
    private static final float TENDRIL_LENGTH_MIN = 2.0f; // 触须最小长度
    private static final float TENDRIL_LENGTH_MAX = 4.0f; // 触须最大长度
    private static final float TENDRIL_WIDTH_MIN = 0.1f; // 触须最小宽度
    private static final float TENDRIL_WIDTH_MAX = 0.2f; // 触须最大宽度
    private static final float TENDRIL_RENDER_CHANCE = 0.8f; // 触须渲染概率
    private static final int TENDRIL_SEGMENTS = 6; // 触须分段数
    private static final int NUM_PLASMA_ARCS_CORE = 8; // 核心等离子弧数量
    private static final float ARC_MAX_LENGTH_CORE = CORE_BASE_RADIUS * 2.0f; // 核心等离子弧最大长度
    private static final float ARC_THICKNESS_CORE = 0.1f; // 核心等离子弧粗细
    private static final float ARC_CORE_RENDER_CHANCE = 0.8f; // 核心等离子弧渲染概率
    private static final int ARC_SEGMENTS_CORE = 6; // 核心等离子弧分段数
    private static final int NUM_LAYERS = 35; // 大气层数量
    private static final int LAYER_SEGMENTS = 12; // 大气层分段数
    private static final float LAYER_SKIP_CHANCE = 0.05f; // 大气层跳过渲染概率
    private static final float HORIZONTAL_DISPLACEMENT_SCALE = 4.0f; // 水平位移缩放
    private static final float DISPLACEMENT_FALLOFF_EXPONENT = 2.0f; // 位移衰减指数
    private static final float GAP_VARIANCE_SCALE = 0.9f; // 间隙变化缩放 (增加随机性)
    private static final float VERTICAL_JITTER_SCALE = 1.5f; // 垂直抖动缩放
    private static final float RADIUS_NOISE_SCALE = 0.35f; // 半径噪声缩放 (增加随机性)
    private static final float RADIUS_JITTER_SCALE = 0.2f; // 半径抖动缩放 (基于噪声) (增加随机性)
    private static final float ROTATION_OFFSET_SCALE = MathUtil.PI; // 旋转偏移缩放
    private static final float ROTATION_SPEED_VARIATION_SCALE = 1.5f; // 旋转速度变化缩放
    private static final float ROTATION_SPEED_GATHER_MULT = 1.2f; // 聚集时旋转速度乘数
    private static final float ROTATION_SPEED_EXPAND_MULT = 0.4f; // 扩散时旋转速度乘数
    private static final float GLOBAL_ROTATION_MODULATION_SCALE = 0.2f; // 全局旋转调制缩放
    private static final float LAYER_TILT_SCALE = (float) (1.2f * Math.toRadians(1.0f)); // 大气层倾斜缩放 (增加随机性)
    private static final float LAYER_WIDTH_NOISE_SCALE = 1.0f; // 大气层宽度噪声缩放 (增加随机性)
    private static final float BASE_LAYER_WIDTH_GROUND = 18F; // 地面附近基础层宽度
    private static final float BASE_LAYER_WIDTH_MID = 6.0f; // 中间区域基础层宽度
    private static final float BASE_LAYER_WIDTH_TOP = 15.0f; // 顶部区域基础层宽度
    private static final int NUM_ATMOSPHERIC_ARCS = 24; // 大气电弧数量
    private static final float ATMOSPHERE_ARC_SPAWN_RADIUS = 60.0f; // 大气电弧生成半径
    private static final float ATMOSPHERE_ARC_SPAWN_HEIGHT_MIN_REL_CORE = -50.0f; // 相对于核心的大气电弧最小生成高度
    private static final float ATMOSPHERE_ARC_SPAWN_HEIGHT_MAX_REL_CORE = 75.0f; // 相对于核心的大气电弧最大生成高度
    private static final float ARC_MAX_LENGTH_ATMOSPHERE = 100.0f; // 大气电弧最大长度
    private static final float ARC_MIN_LENGTH_ATMOSPHERE = 60.0f; // 大气电弧最小长度
    private static final float ARC_THICKNESS_MIN_ATMOSPHERE = 0.8f; // 大气电弧最小粗细
    private static final float ARC_THICKNESS_MAX_ATMOSPHERE = 1.5f; // 大气电弧最大粗细
    private static final float ARC_ATMOSPHERE_RENDER_CHANCE = 0.75f; // 大气电弧渲染概率
    private static final int ARC_SEGMENTS_ATMOSPHERE_MIN = 10; // 大气电弧最小分段数
    private static final int ARC_SEGMENTS_ATMOSPHERE_MAX = 16; // 大气电弧最大分段数
    private static final float ATMOSPHERE_PARTICLE_SIZE_MIN = 0.4f; // 大气粒子最小尺寸
    private static final float ATMOSPHERE_PARTICLE_SIZE_MAX = 1.0f; // 大气粒子最大尺寸
    private static final float INNER_PARTICLE_SIZE_MIN = 0.5f; // 内部/地面粒子最小尺寸
    private static final float INNER_PARTICLE_SIZE_MAX = 0.9f; // 内部/地面粒子最大尺寸
    private static final float TIME_SCALE_DYNAMICS = 0.025f; // 动态效果时间缩放 (减小以减缓抖动)
    private static final float TIME_SCALE_ROTATION = 0.045f; // 旋转时间缩放 (减小以减缓抖动)
    private static final float TIME_SCALE_TILT_JITTER = 0.03f; // 倾斜/抖动时间缩放 (减小以减缓抖动)
    private static final float CORE_ARC_FLICKER_TIME_MULT = 15.0f; // 核心电弧闪烁时间乘数
    private static final float ATM_ARC_FLICKER_TIME_MULT = 1.2f; // 大气电弧闪烁时间乘数
    private static final float TENDRIL_FLICKER_TIME_MULT = 2.0f; // 触须闪烁时间乘数
    private static final RenderType ATMOSPHERE_RING_RENDER_TYPE =
            RenderUtil.RingRenderer.RING_RENDER_TYPE.apply(ATMOSPHERE_TEXTURE);
    private static final RenderType PARTICLE_RENDER_TYPE = new RenderType.CompositeRenderType(
            AcademyCraft.MOD_ID + ":plasma_particle_additive_" + PLASMA_PARTICLE_TEXTURE.toString().replace(':', '_').replace('/', '_'),
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateUtil.POSITION_TEX_SHADER)
                    .setTextureState(new RenderType.TextureStateShard(PLASMA_PARTICLE_TEXTURE, true, false))
                    .setTransparencyState(RenderStateUtil.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateUtil.NO_CULL)
                    .createCompositeState(false)
    );

    private final Random RAND = new Random();
    private static final float[][][] cachedLayerVertexBuffer = VertexUtil.Ring.getVerticalVertexBuffer(1.0f, 1.0f, LAYER_SEGMENTS);
    private final float[] tempVec1 = new float[3];
    private final float[] tempVec2 = new float[3];
    private final float[] tempVec3 = new float[3];
    private final float[] tempVec4 = new float[3];
    private final float[] interpolatedParticlePos = new float[3];
    private final Quaternionf tempQuat1 = new Quaternionf();
    private final Quaternionf tempQuat2 = new Quaternionf();

    public PlasmaRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
        this.shadowStrength = 0.0f;
    }

    private static void normalize(float[] v) {
        float lenSq = v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
        if (lenSq > 1e-8f) {
            float invLen = 1.0f / (float) Math.sqrt(lenSq);
            v[0] *= invLen;
            v[1] *= invLen;
            v[2] *= invLen;
        }
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private static void sub(float[] a, float[] b, float[] out) {
        out[0] = a[0] - b[0];
        out[1] = a[1] - b[1];
        out[2] = a[2] - b[2];
    }

    private static void add(float[] a, float[] b, float[] out) {
        out[0] = a[0] + b[0];
        out[1] = a[1] + b[1];
        out[2] = a[2] + b[2];
    }

    private static void mul(float[] v, float scalar, float[] out) {
        out[0] = v[0] * scalar;
        out[1] = v[1] * scalar;
        out[2] = v[2] * scalar;
    }

    private static void set(float[] target, float x, float y, float z) {
        target[0] = x;
        target[1] = y;
        target[2] = z;
    }

    private static void setRandomGaussianDir(float[] outDir, Random rand) {
        double x, y, z, dSq;
        do {
            x = rand.nextGaussian();
            y = rand.nextGaussian();
            z = rand.nextGaussian();
            dSq = x * x + y * y + z * z;
        } while (dSq < 1e-6 || dSq > 16);
        float invLen = (float) (1.0 / Math.sqrt(dSq));
        outDir[0] = (float) (x * invLen);
        outDir[1] = (float) (y * invLen);
        outDir[2] = (float) (z * invLen);
    }

    public static float getAtmosphereParticleSize(float age, float maxAge) {
        if (maxAge <= 0) return ATMOSPHERE_PARTICLE_SIZE_MIN;
        float lifeRatio = MathUtil.clamp(age / maxAge, 0.0f, 1.0f);
        float fadeIn = MathUtil.smoothStep(MathUtil.clamp(lifeRatio * 4.0f, 0.0f, 1.0f));
        float fadeOut = 1.0f - MathUtil.smoothStep(MathUtil.clamp((lifeRatio - 0.5f) * 2.0f, 0.0f, 1.0f));
        float sizeFactor = Math.min(fadeIn, fadeOut);
        return MathUtil.lerpFactorStartEnd(sizeFactor, ATMOSPHERE_PARTICLE_SIZE_MIN, ATMOSPHERE_PARTICLE_SIZE_MAX);
    }

    public static float getGroundParticleSize(float age, float maxAge) {
        if (maxAge <= 0) return INNER_PARTICLE_SIZE_MIN;
        float lifeRatio = MathUtil.clamp(age / maxAge, 0.0f, 1.0f);
        float sizeFactor = 1.0f - MathUtil.smoothStep(lifeRatio);
        return MathUtil.lerpFactorStartEnd(sizeFactor, INNER_PARTICLE_SIZE_MIN, INNER_PARTICLE_SIZE_MAX);
    }

    private void getRandomPointInVolumeRel(float radius, float minYRel, float maxYRel, float[] outPos, Random rand) {
        double angle = rand.nextDouble() * MathUtil.TWO_PI;
        double rSq = rand.nextDouble();
        double r = Math.sqrt(rSq) * radius;
        outPos[0] = (float) (r * Math.cos(angle));
        outPos[1] = MathUtil.lerpFactorStartEnd(rand.nextFloat(), minYRel, maxYRel);
        outPos[2] = (float) (r * Math.sin(angle));
    }

    @Override
    public void render(@NotNull Plasma entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        float effectiveTime = entity.tickCount + partialTick;
        float gatherProgress = entity.getInterpolatedGatherProgress(partialTick);
        long entityId = entity.getId();
        float smoothedGatherProgress = MathUtil.smoothStep(gatherProgress);
        float gatherFactor = MathUtil.lerpFactorStartEnd(smoothedGatherProgress, MIN_GATHER_FACTOR, 1.0f);

        poseStack.pushPose();
        poseStack.translate(0.0, CORE_Y_OFFSET, 0.0);

        renderPurpleCore(poseStack, buffer, effectiveTime);
        renderCoreTendrils(poseStack, buffer, effectiveTime, entityId);
        renderCoreArcs(poseStack, buffer, effectiveTime, entityId);
        renderAtmosphericLayers(poseStack, buffer, effectiveTime, entityId, gatherFactor);
        renderAtmosphericArcsNearCore(poseStack, buffer, effectiveTime, entityId, gatherFactor);

        poseStack.popPose();

        renderAtmosphericDust(poseStack, buffer, entity, partialTick);
        renderGroundDisturbance(poseStack, buffer, entity, partialTick);
        poseStack.popPose();
    }

    private void renderPurpleCore(PoseStack poseStack, MultiBufferSource buffer, float effectiveTime) {
        float pulseFactor = 0.5f + 0.5f * (float) Math.sin(effectiveTime * CORE_PULSATION_FREQ * MathUtil.TWO_PI);
        float currentRadius = MathUtil.lerpFactorStartEnd(pulseFactor, CORE_BASE_RADIUS - CORE_PULSATION_AMP, CORE_BASE_RADIUS + CORE_PULSATION_AMP);
        float glowRadius = currentRadius * (1.0f + pulseFactor * 0.15f);

        RenderUtil.BallRenderer.renderBall(poseStack, buffer, currentRadius, CORE_SPHERE_FACES,
                CORE_COLOR_INNER[0], CORE_COLOR_INNER[1], CORE_COLOR_INNER[2], CORE_COLOR_INNER[3]);

        RenderUtil.BallRenderer.renderBall(poseStack, buffer, glowRadius, CORE_SPHERE_FACES,
                CORE_COLOR_GLOW[0], CORE_COLOR_GLOW[1], CORE_COLOR_GLOW[2], CORE_COLOR_GLOW[3]);
    }

    private void renderCoreTendrils(PoseStack poseStack, MultiBufferSource buffer, float effectiveTime, long entityId) {
        float pulseFactor = 0.5f + 0.5f * (float) Math.sin(effectiveTime * CORE_PULSATION_FREQ * MathUtil.TWO_PI);
        float currentCoreRadius = MathUtil.lerpFactorStartEnd(pulseFactor, CORE_BASE_RADIUS - CORE_PULSATION_AMP, CORE_BASE_RADIUS + CORE_PULSATION_AMP);
        long baseSeed = entityId + 150L;

        for (int i = 0; i < NUM_CORE_TENDRILS; ++i) {
            long tendrilSeed = baseSeed + i + (long) (effectiveTime * TENDRIL_FLICKER_TIME_MULT);
            RAND.setSeed(tendrilSeed);

            float chanceMod = 0.6f + 0.4f * RAND.nextFloat();
            if (RAND.nextFloat() > TENDRIL_RENDER_CHANCE * chanceMod) continue;

            setRandomGaussianDir(tempVec1, RAND);
            float startRadiusFactor = currentCoreRadius * (0.8f + RAND.nextFloat() * 0.3f);
            mul(tempVec1, startRadiusFactor, tempVec2);

            float len = MathUtil.lerpFactorStartEnd(RAND.nextFloat(), TENDRIL_LENGTH_MIN, TENDRIL_LENGTH_MAX);

            float noiseFactor = 0.9f;
            double tendrilNoiseTime = effectiveTime * TIME_SCALE_DYNAMICS;
            float noiseX = (float) ImprovedNoise.noise(tempVec1[0] * 4.0 + tendrilNoiseTime, i * 0.7, 700.0);
            float noiseY = (float) ImprovedNoise.noise(tempVec1[1] * 4.0 + tendrilNoiseTime, i * 0.7, 710.0);
            float noiseZ = (float) ImprovedNoise.noise(tempVec1[2] * 4.0 + tendrilNoiseTime, i * 0.7, 720.0);

            set(tempVec3, noiseX * noiseFactor, noiseY * noiseFactor, noiseZ * noiseFactor);
            add(tempVec1, tempVec3, tempVec1);
            normalize(tempVec1);
            mul(tempVec1, len, tempVec1);
            add(tempVec2, tempVec1, tempVec3);

            float width = MathUtil.lerpFactorStartEnd(RAND.nextFloat(), TENDRIL_WIDTH_MIN, TENDRIL_WIDTH_MAX);

            RenderUtil.ArcRenderer.renderArc(poseStack, buffer, tendrilSeed,
                    tempVec2[0], tempVec2[1], tempVec2[2],
                    tempVec3[0], tempVec3[1], tempVec3[2],
                    width, TENDRIL_SEGMENTS);
        }
    }

    private void renderCoreArcs(PoseStack poseStack, MultiBufferSource buffer, float effectiveTime, long entityId) {
        float pulseFactor = 0.5f + 0.5f * (float) Math.sin(effectiveTime * CORE_PULSATION_FREQ * MathUtil.TWO_PI);
        float currentCoreRadius = MathUtil.lerpFactorStartEnd(pulseFactor, CORE_BASE_RADIUS - CORE_PULSATION_AMP, CORE_BASE_RADIUS + CORE_PULSATION_AMP);
        long baseSeed = entityId + 110L + (long) (effectiveTime * CORE_ARC_FLICKER_TIME_MULT);

        for (int i = 0; i < NUM_PLASMA_ARCS_CORE; ++i) {
            long arcSeed = baseSeed + i;
            RAND.setSeed(arcSeed);

            if (RAND.nextFloat() > ARC_CORE_RENDER_CHANCE) continue;

            float r1 = RAND.nextFloat() * currentCoreRadius * 1.20f;
            float r2 = RAND.nextFloat() * currentCoreRadius * 1.20f;
            setRandomGaussianDir(tempVec1, RAND);
            mul(tempVec1, r1, tempVec1);
            setRandomGaussianDir(tempVec2, RAND);
            mul(tempVec2, r2, tempVec2);

            float targetMaxLength = RAND.nextFloat() * ARC_MAX_LENGTH_CORE;
            float minAllowedLength = targetMaxLength * 0.2f;

            sub(tempVec2, tempVec1, tempVec3);
            float currentArcLen = length(tempVec3);

            if (currentArcLen < minAllowedLength && minAllowedLength > 1e-4f) {
                setRandomGaussianDir(tempVec4, RAND);
                float newLen = MathUtil.lerpFactorStartEnd(RAND.nextFloat(), minAllowedLength, targetMaxLength);
                mul(tempVec4, newLen, tempVec4);
                add(tempVec1, tempVec4, tempVec2);
            } else if (currentArcLen > targetMaxLength) {
                normalize(tempVec3);
                mul(tempVec3, targetMaxLength, tempVec3);
                add(tempVec1, tempVec3, tempVec2);
            }

            RenderUtil.ArcRenderer.renderArc(poseStack, buffer, arcSeed,
                    tempVec1[0], tempVec1[1], tempVec1[2],
                    tempVec2[0], tempVec2[1], tempVec2[2],
                    ARC_THICKNESS_CORE, ARC_SEGMENTS_CORE);
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private void renderAtmosphericLayers(PoseStack poseStack, MultiBufferSource buffer, float effectiveTime, long entityId, float gatherFactor) {
        VertexConsumer atmosphereConsumer = buffer.getBuffer(ATMOSPHERE_RING_RENDER_TYPE);

        double timeParamDyn = effectiveTime * TIME_SCALE_DYNAMICS;
        double timeParamRot = effectiveTime * TIME_SCALE_ROTATION;
        double timeParamTiltJitter = effectiveTime * TIME_SCALE_TILT_JITTER;

        float averageGap = TOTAL_VERTICAL_RANGE_REL_CORE / (float) Math.max(1, NUM_LAYERS);
        float currentBaseRelativeY = MIN_EFFECT_HEIGHT_REL_CORE - averageGap * 0.5f;
        long baseSeed = entityId + 500L;

        float previousLayerTopY = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < NUM_LAYERS; ++i) {
            RAND.setSeed(baseSeed + i);

            float gap = calculateGap(i, timeParamDyn, averageGap);
            float nextBaseRelativeY = currentBaseRelativeY + gap;

            if (RAND.nextFloat() < MathUtil.clamp(LAYER_SKIP_CHANCE, 0.0f, 0.9f)) {
                currentBaseRelativeY = nextBaseRelativeY;
                continue;
            }

            double normalizedY = MathUtil.clamp((currentBaseRelativeY - MIN_EFFECT_HEIGHT_REL_CORE) / TOTAL_VERTICAL_RANGE_REL_CORE, 0.0, 1.0);

            if (currentBaseRelativeY > MAX_EFFECT_HEIGHT_REL_CORE + averageGap || currentBaseRelativeY < MIN_EFFECT_HEIGHT_REL_CORE - averageGap) {
                currentBaseRelativeY = nextBaseRelativeY;
                continue;
            }

            double actualRelativeY = calculateLayerY(normalizedY, i, currentBaseRelativeY, timeParamTiltJitter, gatherFactor);
            calculateLayerDisplacement(normalizedY, i, timeParamDyn, gatherFactor, tempVec1);
            float finalRadius = calculateRadius(normalizedY, i, timeParamDyn, timeParamTiltJitter) * gatherFactor;
            double rotationAngle = calculateVariedRotation(normalizedY, i, timeParamRot, gatherFactor);
            float layerWidth = calculateLayerWidth(normalizedY, i, timeParamDyn);
            calculateTiltQuaternion(normalizedY, i, timeParamTiltJitter, tempQuat1);

            float currentLayerBottom = (float) actualRelativeY - layerWidth * 0.5f;
            if (currentLayerBottom < previousLayerTopY) {
                actualRelativeY = previousLayerTopY + layerWidth * 0.5f;
            }
            previousLayerTopY = (float) actualRelativeY + layerWidth * 0.5f;

            poseStack.pushPose();
            poseStack.translate(tempVec1[0], actualRelativeY, tempVec1[2]);
            tempQuat2.identity().rotateY((float) rotationAngle);
            tempQuat2.mul(tempQuat1);
            poseStack.mulPose(tempQuat2);
            poseStack.scale(finalRadius, layerWidth, finalRadius);

            RenderUtil.RingRenderer.renderRing(poseStack.last().pose(), atmosphereConsumer, LAYER_SEGMENTS, cachedLayerVertexBuffer);

            poseStack.popPose();

            currentBaseRelativeY = nextBaseRelativeY;
        }
    }


    private void renderAtmosphericArcsNearCore(PoseStack poseStack, MultiBufferSource buffer, float effectiveTime, long entityId, float gatherFactor) {
        long baseSeed = entityId + 310L;

        float currentSpawnRadius = MathUtil.lerpFactorStartEnd(gatherFactor, CORE_BASE_RADIUS * 1.5f, ATMOSPHERE_ARC_SPAWN_RADIUS);
        float currentMinYRelCore = MathUtil.lerpFactorStartEnd(gatherFactor, MIN_EFFECT_HEIGHT_REL_CORE * 0.5f, ATMOSPHERE_ARC_SPAWN_HEIGHT_MIN_REL_CORE);
        float currentMaxYRelCore = MathUtil.lerpFactorStartEnd(gatherFactor, MAX_EFFECT_HEIGHT_REL_CORE * 0.8f, ATMOSPHERE_ARC_SPAWN_HEIGHT_MAX_REL_CORE);
        float currentMinLength = ARC_MIN_LENGTH_ATMOSPHERE * gatherFactor;
        float currentMaxLength = ARC_MAX_LENGTH_ATMOSPHERE * gatherFactor;
        float minAllowedLength = currentMinLength * 0.3f;

        for (int i = 0; i < NUM_ATMOSPHERIC_ARCS; ++i) {
            long arcSeed = baseSeed + i + (long) (effectiveTime * ATM_ARC_FLICKER_TIME_MULT);
            RAND.setSeed(arcSeed);

            float flickerCycle = (effectiveTime * TIME_SCALE_DYNAMICS * 0.7f + RAND.nextFloat() * 0.6f) % 1.0f;
            float visibility = MathUtil.smoothStep((float) Math.sin(flickerCycle * MathUtil.PI));
            if (RAND.nextFloat() > ARC_ATMOSPHERE_RENDER_CHANCE * visibility) continue;

            getRandomPointInVolumeRel(currentSpawnRadius, currentMinYRelCore, currentMaxYRelCore, tempVec1, RAND);
            getRandomPointInVolumeRel(currentSpawnRadius, currentMinYRelCore, currentMaxYRelCore, tempVec2, RAND);

            float targetLen = MathUtil.lerpFactorStartEnd(RAND.nextFloat(), currentMinLength, currentMaxLength);

            sub(tempVec2, tempVec1, tempVec3);
            float dist = length(tempVec3);

            if (dist < minAllowedLength && minAllowedLength > 1e-5f) {
                setRandomGaussianDir(tempVec4, RAND);
                float newLen = MathUtil.lerpFactorStartEnd(RAND.nextFloat(), minAllowedLength, targetLen);
                mul(tempVec4, newLen, tempVec4);
                add(tempVec1, tempVec4, tempVec2);
            } else if (dist > targetLen) {
                normalize(tempVec3);
                mul(tempVec3, targetLen, tempVec3);
                add(tempVec1, tempVec3, tempVec2);
            }

            float thickness = MathUtil.lerpFactorStartEnd(RAND.nextFloat(), ARC_THICKNESS_MIN_ATMOSPHERE, ARC_THICKNESS_MAX_ATMOSPHERE);
            int segments = RAND.nextInt(ARC_SEGMENTS_ATMOSPHERE_MIN, ARC_SEGMENTS_ATMOSPHERE_MAX + 1);

            RenderUtil.ArcRenderer.renderArc(poseStack, buffer, arcSeed,
                    tempVec1[0], tempVec1[1], tempVec1[2],
                    tempVec2[0], tempVec2[1], tempVec2[2],
                    thickness, segments);
        }
    }

    private void renderAtmosphericDust(PoseStack poseStack, MultiBufferSource buffer, Plasma entity, float partialTick) {
        VertexConsumer dustConsumer = buffer.getBuffer(PARTICLE_RENDER_TYPE);
        float[][] particles = entity.getAtmosphereParticles();

        for (float[] pData : particles) {
            if (pData[Plasma.IDX_AGE] < 0) continue;

            interpolatedParticlePos[0] = MathUtil.lerpFactorStartEnd(partialTick, pData[Plasma.IDX_PREV_POS_X], pData[Plasma.IDX_POS_X]);
            interpolatedParticlePos[1] = MathUtil.lerpFactorStartEnd(partialTick, pData[Plasma.IDX_PREV_POS_Y], pData[Plasma.IDX_POS_Y]);
            interpolatedParticlePos[2] = MathUtil.lerpFactorStartEnd(partialTick, pData[Plasma.IDX_PREV_POS_Z], pData[Plasma.IDX_POS_Z]);

            float size = getAtmosphereParticleSize(pData[Plasma.IDX_AGE] + partialTick, pData[Plasma.IDX_MAX_AGE]);
            renderBillboardParticle(poseStack, dustConsumer, interpolatedParticlePos, size);
        }
    }

    private void renderGroundDisturbance(PoseStack poseStack, MultiBufferSource buffer, Plasma entity, float partialTick) {
        VertexConsumer groundParticleConsumer = buffer.getBuffer(PARTICLE_RENDER_TYPE);
        float[][] particles = entity.getGroundParticles();

        for (float[] pData : particles) {
            if (pData[Plasma.IDX_AGE] < 0) continue;

            interpolatedParticlePos[0] = MathUtil.lerpFactorStartEnd(partialTick, pData[Plasma.IDX_PREV_POS_X], pData[Plasma.IDX_POS_X]);
            interpolatedParticlePos[1] = MathUtil.lerpFactorStartEnd(partialTick, pData[Plasma.IDX_PREV_POS_Y], pData[Plasma.IDX_POS_Y]);
            interpolatedParticlePos[2] = MathUtil.lerpFactorStartEnd(partialTick, pData[Plasma.IDX_PREV_POS_Z], pData[Plasma.IDX_POS_Z]);

            float size = getGroundParticleSize(pData[Plasma.IDX_AGE] + partialTick, pData[Plasma.IDX_MAX_AGE]);
            renderBillboardParticle(poseStack, groundParticleConsumer, interpolatedParticlePos, size);
        }
    }

    private float calculateGap(int layerIndex, double timeParamDyn, float averageGap) {
        float noiseVal = (float) ImprovedNoise.noise(layerIndex * 0.3, timeParamDyn * 1.2, 11.0);
        float variedGap = averageGap * (1.0f + noiseVal * GAP_VARIANCE_SCALE);
        return Math.max(averageGap * 0.1f, variedGap);
    }

    private double calculateLayerY(double normalizedY, int layerIndex, float baseRelativeY, double timeParamTiltJitter, float gatherFactor) {
        double jitterNoise = ImprovedNoise.noise(layerIndex * 0.9, timeParamTiltJitter * 1.4, 32.0 + normalizedY * 0.2);
        double yJitter = jitterNoise * VERTICAL_JITTER_SCALE * gatherFactor;
        return baseRelativeY + yJitter;
    }

    private void calculateLayerDisplacement(double normalizedY, int layerIndex, double timeParamDyn, float gatherFactor, float[] outDisp) {
        double yDistFactor = Math.abs(normalizedY - RADIUS_MIDPOINT_NORMALIZED_Y);
        double displacementFactor = MathUtil.smoothStep(1.0 - Math.pow(yDistFactor * 2.0, DISPLACEMENT_FALLOFF_EXPONENT));
        displacementFactor = MathUtil.clamp(displacementFactor, 0.0, 1.0);

        double noiseDx = ImprovedNoise.noise(normalizedY * 0.8, timeParamDyn * 0.8, 10.0 + layerIndex * 0.01);
        double noiseDz = ImprovedNoise.noise(normalizedY * 0.8, timeParamDyn * 0.8, 20.0 + layerIndex * 0.01);

        float finalDx = (float) (noiseDx * HORIZONTAL_DISPLACEMENT_SCALE * displacementFactor * gatherFactor);
        float finalDz = (float) (noiseDz * HORIZONTAL_DISPLACEMENT_SCALE * displacementFactor * gatherFactor);

        set(outDisp, finalDx, 0.0f, finalDz);
    }

    private float calculateRadius(double normalizedYRelCore, int layerIndex, double timeParamDyn, double timeParamTiltJitter) {
        float baseRadiusScale = MAX_EFFECT_RADIUS;
        double rNoise = ImprovedNoise.noise(normalizedYRelCore * 0.4, timeParamDyn * 1.1, 40.0 + layerIndex * 0.1) * RADIUS_NOISE_SCALE;
        double rJitter = ImprovedNoise.noise(layerIndex * 0.9, timeParamTiltJitter * 1.3, 55.0 + normalizedYRelCore * 0.3) * RADIUS_JITTER_SCALE * baseRadiusScale;
        float calculatedRadius = (float) (baseRadiusScale * (1.0 + rNoise) + rJitter);
        return Math.max(30.0f, calculatedRadius);
    }

    private double calculateVariedRotation(double normalizedYRelCore, int layerIndex, double timeParamRot, float gatherFactor) {
        double speedNoise = ImprovedNoise.noise(normalizedYRelCore * 0.6, timeParamRot * 0.9, 65.0 + layerIndex * 0.05);
        double baseSpeedMultiplier = 1.0 + speedNoise * ROTATION_SPEED_VARIATION_SCALE;
        baseSpeedMultiplier *= MathUtil.lerpFactorStartEnd(gatherFactor, ROTATION_SPEED_GATHER_MULT, ROTATION_SPEED_EXPAND_MULT);

        double baseRotationSpeed = Math.toRadians(1.0) * baseSpeedMultiplier;
        double rotationOffset = ImprovedNoise.noise(layerIndex * 0.7 + normalizedYRelCore * 0.25, timeParamRot * 0.1, 60.0) * ROTATION_OFFSET_SCALE;
        double globalModulation = ImprovedNoise.noise(timeParamRot * 0.05, layerIndex * 0.02, 66.0) * MathUtil.TWO_PI * GLOBAL_ROTATION_MODULATION_SCALE;
        return timeParamRot * baseRotationSpeed + rotationOffset + globalModulation;
    }

    private float calculateLayerWidth(double normalizedYRelCore, int layerIndex, double timeParamDyn) {
        float baseLayerWidth;
        float midPoint = RADIUS_MIDPOINT_NORMALIZED_Y;

        if (normalizedYRelCore < midPoint) {
            float factor = MathUtil.clamp((float) normalizedYRelCore / midPoint, 0f, 1f);
            baseLayerWidth = MathUtil.lerpFactorStartEnd(MathUtil.smoothStep(factor), BASE_LAYER_WIDTH_GROUND, BASE_LAYER_WIDTH_MID);
        } else {
            float rangeAbove = 1.0f - midPoint;
            float factor = rangeAbove > 1e-5f ? MathUtil.clamp(((float) normalizedYRelCore - midPoint) / rangeAbove, 0f, 1f) : 0f;
            baseLayerWidth = MathUtil.lerpFactorStartEnd(MathUtil.smoothStep(factor), BASE_LAYER_WIDTH_MID, BASE_LAYER_WIDTH_TOP);
        }

        float widthNoise = (float) ImprovedNoise.noise(normalizedYRelCore * 0.7 + layerIndex * 0.1, timeParamDyn * 1.1, 80.0 + layerIndex * 0.05);
        float finalWidth = baseLayerWidth * (1.0f + widthNoise * LAYER_WIDTH_NOISE_SCALE);

        return Math.max(3.0f, finalWidth);
    }

    private void calculateTiltQuaternion(double normalizedYRelCore, int layerIndex, double timeParamTiltJitter, Quaternionf outQuat) {
        double tiltNoiseX = ImprovedNoise.noise(layerIndex * 0.5, timeParamTiltJitter * 1.1, 90.0 + normalizedYRelCore * 0.2);
        double tiltNoiseZ = ImprovedNoise.noise(layerIndex * 0.5, timeParamTiltJitter * 1.1, 100.0 + normalizedYRelCore * 0.2);

        double globalTiltX = ImprovedNoise.noise(timeParamTiltJitter * 0.3, 91.0, 0.0) * 0.50;
        double globalTiltZ = ImprovedNoise.noise(timeParamTiltJitter * 0.3, 101.0, 0.0) * 0.50;

        float finalTiltX = (float) ((tiltNoiseX * 0.7 + globalTiltX * 0.3) * LAYER_TILT_SCALE);
        float finalTiltZ = (float) ((tiltNoiseZ * 0.7 + globalTiltZ * 0.3) * LAYER_TILT_SCALE);

        outQuat.identity().rotateZ(finalTiltZ).rotateX(finalTiltX);
    }

    private void renderBillboardParticle(PoseStack poseStack, VertexConsumer vc, float[] pos, float size) {
        if (size <= 1e-3f) return;

        poseStack.pushPose();
        poseStack.translate(pos[0], pos[1], pos[2]);
        poseStack.mulPose(this.entityRenderDispatcher.camera.rotation());
        poseStack.scale(size, size, size);

        Matrix4f matrix = poseStack.last().pose();
        float hs = 0.5f;

        vc.vertex(matrix, -hs, -hs, 0).uv(0, 1).color(1.0f, 1.0f, 1.0f, 1.0f).uv2(OverlayTexture.NO_OVERLAY).endVertex();
        vc.vertex(matrix, hs, -hs, 0).uv(1, 1).color(1.0f, 1.0f, 1.0f, 1.0f).uv2(OverlayTexture.NO_OVERLAY).endVertex();
        vc.vertex(matrix, hs, hs, 0).uv(1, 0).color(1.0f, 1.0f, 1.0f, 1.0f).uv2(OverlayTexture.NO_OVERLAY).endVertex();
        vc.vertex(matrix, -hs, hs, 0).uv(0, 0).color(1.0f, 1.0f, 1.0f, 1.0f).uv2(OverlayTexture.NO_OVERLAY).endVertex();

        poseStack.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull Plasma plasma) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}