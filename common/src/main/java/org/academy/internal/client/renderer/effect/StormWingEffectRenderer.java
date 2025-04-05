package org.academy.internal.client.renderer.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.academy.AcademyCraft;
import org.academy.api.client.render.renderer.EffectRenderer;
import org.academy.api.client.util.RenderUtil;
import org.academy.api.common.util.ImprovedNoise;
import org.academy.internal.common.ability.builtin.accelerator.skills.StormWing;
import org.academy.internal.common.world.entity.player.PlayerSyncSkillData;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@SuppressWarnings({"SuspiciousNameCombination", "DuplicatedCode"})
public class StormWingEffectRenderer implements EffectRenderer {
    public static final EffectRenderer INSTANCE = new StormWingEffectRenderer();
    public static final ResourceLocation TEXTURE = new ResourceLocation(AcademyCraft.MOD_ID, "textures/skill/effect/accelerator/tornado_ring.png");
    private static final RandomSource RAND = RandomSource.create();
    private static final Matrix4f BASE_MATRIX = new Matrix4f().rotateX((float) Math.toRadians(90)).translate(0,0.25f,0);
    private static final RenderType RENDER_TYPE = RenderUtil.RingRenderer.RING_RENDER_TYPE.apply(TEXTURE);

    // --- 参数: 平滑稳定且具有复杂性 ---
    private static final int NUM_RINGS = 32;                  // 环的数量
    private static final float HEIGHT = 3.5f;                 // 目标总高度 (用于计算平均间隙)
    private static final float SIZE = 1;                      // 整体尺寸缩放

    // --- 混乱与细节控制 ---
    private static final float HORIZONTAL_DISPLACEMENT_SCALE = 1.6f; // 水平位移范围缩放
    private static final double POS_DOMAIN_WARP_SCALE = 0.15f;        // 坐标领域扭曲程度 (影响位置噪声采样)
    //--- 垂直间距 ---
    private static final float GAP_VARIANCE_SCALE = 0.6f;           // 垂直间隙的变化范围缩放
    private static final float VERTICAL_POSITION_JITTER = 0.03f;   // 额外的Y轴随机抖动
    //--- 半径 ---
    private static final float RADIUS_BASE_NOISE_SCALE = 0.15f;     // 基础半径噪声强度
    private static final float RADIUS_EXTRA_NOISE_SCALE = 0.20f;    // 额外半径噪声强度 (用于打破相邻环的相关性)
    private static final float RADIUS_JITTER_SCALE = 0.03f;         // 最终半径抖动强度
    //--- 旋转 ---
    private static final float ROTATION_BASE_NOISE_SCALE = 0.45f * (float) Math.PI; // 基础旋转噪声强度
    private static final float ROTATION_MODULATION_SCALE = 0.30f;  // 旋转噪声强度的调制幅度 (使旋转强度也随时间/高度变化)
    //--- 倾斜 ---
    private static final float RING_TILT_SCALE = 0.10f;            // 环的倾斜幅度
    //--- 宽度 ---
    private static final float WIDTH_BASE_NOISE_SCALE = 0.4f;      // 基础宽度噪声强度 (较慢变化)
    private static final float WIDTH_DETAIL_NOISE_SCALE = 0.25f;   // 细节宽度噪声强度 (较快变化)
    //--- 透明度 ---
    private static final float ALPHA_BASE_NOISE_SCALE = 0.25f;     // 基础慢速透明度噪声强度
    private static final float ALPHA_FLICKER_NOISE_SCALE = 0.20f;  // 快速透明度闪烁噪声强度

    // --- 龙卷风形状控制 ---
    private static final float FUNNEL_BASE_RADIUS = 0.2F;         // 漏斗形状的底部基础半径比例 (相对于最大半径)
    private static final float FUNNEL_EXPONENT = 1.75F;             // 漏斗半径随高度变化的指数 (控制形状陡峭程度)

    // --- 外观 ---
    private static final float BASE_ALPHA = 0.75f;                  // 基础透明度
    private static final float BASE_RING_WIDTH = 0.075f;          // 基础环带宽度
    public static final int RING_SEGMENTS = 16;                  // 环渲染的分段数 (用于顶点缓存)

    // --- 时间尺度 (控制各种变化的速率) ---
    private static final float TIME_SCALE_GLOBAL = 1.1f;          // 全局时间缩放
    private static final float TIME_SCALE_POS_BASE = 0.07f * TIME_SCALE_GLOBAL; // 基础位置变化速率
    private static final float TIME_SCALE_POS_WARP = 0.3f * TIME_SCALE_GLOBAL;  // 位置坐标扭曲变化速率
    private static final float TIME_SCALE_GAP = 0.09f * TIME_SCALE_GLOBAL;      // 间隙变化速率
    private static final float TIME_SCALE_RAD_BASE = 0.11f * TIME_SCALE_GLOBAL;// 基础半径噪声变化速率
    private static final float TIME_SCALE_RAD_EXTRA = 0.22f * TIME_SCALE_GLOBAL;// 额外半径噪声变化速率
    private static final float TIME_SCALE_ROT_BASE = 0.55f * TIME_SCALE_GLOBAL;// 基础旋转速率
    private static final float TIME_SCALE_ROT_MOD = 0.16f * TIME_SCALE_GLOBAL; // 旋转噪声强度调制速率
    private static final float TIME_SCALE_ALPHA_BASE = 0.18f * TIME_SCALE_GLOBAL;// 基础透明度变化速率
    private static final float TIME_SCALE_ALPHA_FLICKER = 0.75f * TIME_SCALE_GLOBAL;// 透明度闪烁速率
    private static final float TIME_SCALE_WIDTH_BASE = 0.15f * TIME_SCALE_GLOBAL;// 基础宽度变化速率
    private static final float TIME_SCALE_WIDTH_DETAIL = 0.55f * TIME_SCALE_GLOBAL;// 宽度细节变化速率
    private static final float TIME_SCALE_TILT = 0.18f * TIME_SCALE_GLOBAL;     // 倾斜变化速率
    private static final float TIME_SCALE_JITTER = 0.85f * TIME_SCALE_GLOBAL;    // 最终抖动变化速率 (半径、位置等)

    // --- 嵌套环参数 ---
    private static final float NESTED_RING_PROBABILITY = 0.40f;    // 出现嵌套环的概率
    private static final float NESTED_RADIUS_FACTOR = 0.50f;       // 嵌套环相对父环的半径比例
    private static final float NESTED_WIDTH_FACTOR = 0.75f;        // 嵌套环相对父环的宽度比例
    private static final float NESTED_ALPHA_FACTOR = 0.88f;        // 嵌套环相对父环的透明度比例

    // --- 避免渲染时重复分配内存的缓冲区 ---
    private static final double[] displacementBuffer = new double[2];     // 用于存储计算出的水平位移 (dx, dz)
    private static final Quaternionf tempTiltQuat = new Quaternionf();    // 用于存储计算出的倾斜旋转四元数
    private static final double[] warpedYBuffer = new double[1];          // 用于存储领域扭曲后的Y坐标

    // --- 顶点缓冲区缓存 ---
    private static final int CACHED_RING_SEGMENTS = RING_SEGMENTS;
    private static final float[][][] CACHED_VERTICAL_VERTEX_BUFFER = RenderUtil.RingRenderer.getVerticalVertexBuffer(1.0f, 1.0f, CACHED_RING_SEGMENTS);

    // 为每个龙卷风定义时间偏移量
    private static final float TORNADO_OFFSET_1 = 0.0f;
    private static final float TORNADO_OFFSET_2 = 20.0f; // 任意设定的偏移量，可根据需要调整
    private static final float TORNADO_OFFSET_3 = 45.0f;
    private static final float TORNADO_OFFSET_4 = 70.0f;

    private StormWingEffectRenderer() {
        // 私有构造函数，防止外部实例化
    }

    private static double getNoise(double x, double y, double seed) {
        return ImprovedNoise.noise(x, y, seed);
    }

    /**
     * 应用领域扭曲，修改传入的归一化 Y 坐标（通过 outWarpedY 返回）
     * 这会使噪声采样点发生偏移，增加视觉复杂性
     *
     * @param normalizedY 归一化的 Y 坐标 (0.0 到 1.0)
     * @param warpTime    用于领域扭曲的时间变量
     */
    private static void applyDomainWarp(double normalizedY, double warpTime) {
        // 使用基础时间和扭曲时间计算扭曲查找坐标
        StormWingEffectRenderer.warpedYBuffer[0] = normalizedY + getNoise(normalizedY * 2.0, warpTime, 5.0) * StormWingEffectRenderer.POS_DOMAIN_WARP_SCALE;
    }

    /**
     * 计算水平位移 (dx, dz) - 注意：这里计算的是 *潜在* 的位移，之后会根据高度进行缩放
     *
     * @param normalizedY 归一化的 Y 坐标
     * @param tPosBase    基础位置时间变量
     * @param tWarp       领域扭曲时间变量
     */
    private static void calculateHorizontalDisplacement(double normalizedY, double tPosBase, double tWarp) {
        applyDomainWarp(normalizedY, tWarp); // 获取扭曲后的Y坐标
        double noiseX = getNoise(warpedYBuffer[0] * 1.3, tPosBase * 0.75, 10.0);
        double noiseZ = getNoise(warpedYBuffer[0] * 1.3, tPosBase * 0.75, 20.0);
        double heightScaleFactor = 0.4 + normalizedY * 1.6;
        StormWingEffectRenderer.displacementBuffer[0] = noiseX * heightScaleFactor;
        StormWingEffectRenderer.displacementBuffer[1] = noiseZ * heightScaleFactor;
    }

    /**
     * 计算垂直方向的小幅随机抖动
     *
     * @param normalizedY 归一化的 Y 坐标
     * @param tWarp       领域扭曲时间变量
     * @param tPosBase    基础位置时间变量
     * @return Y 轴抖动值
     */
    private static double calculateVerticalJitter(double normalizedY, double tWarp, double tPosBase) {
        applyDomainWarp(normalizedY, tWarp); // 获取扭曲后的Y坐标
        // 使用扭曲坐标查找垂直抖动噪声
        double noise = getNoise(warpedYBuffer[0] * 2.2, tPosBase * 1.1, 30.0);
        return noise * VERTICAL_POSITION_JITTER; // 应用抖动缩放
    }

    /**
     * 计算下一个环的垂直间隙大小
     *
     * @param ringIndex  当前环的索引
     * @param tGap       间隙时间变量
     * @param averageGap 平均间隙大小
     * @return 计算出的下一个间隙大小
     */
    private static float calculateGap(int ringIndex, double tGap, float averageGap) {
        // 噪声基于环索引和对应时间尺度，产生随时间和索引变化的间隙
        float noise = (float) getNoise(ringIndex * 0.7, tGap, 11.0);
        // 允许的间隙变化量
        float variablePart = noise * averageGap * GAP_VARIANCE_SCALE;
        // 保证一个最小间隙 (例如平均值的20%)，防止环重叠过密
        return Math.max(averageGap * 0.2f, averageGap + variablePart);
    }

    /**
     * 计算基础半径（考虑漏斗形状和基础噪声）
     *
     * @param normalizedY 归一化的 Y 坐标
     * @param tRadBase    基础半径时间变量
     * @return 基础半径值
     */
    private static double calculateBaseRadius(double normalizedY, double tRadBase) {
        // 漏斗形状的基础半径：底部半径较小，顶部半径较大，指数控制过渡形状
        double funnelRadius = FUNNEL_BASE_RADIUS + Math.pow(normalizedY, FUNNEL_EXPONENT) * (1.0 - FUNNEL_BASE_RADIUS);
        // 叠加基础噪声，使半径不完全规则
        double baseNoise = getNoise(normalizedY * 1.1, tRadBase * 0.8, 40.0);
        return funnelRadius * (1.0 + baseNoise * RADIUS_BASE_NOISE_SCALE); // 应用噪声缩放
    }

    /**
     * 叠加额外的半径噪声 (使用环索引`i`来打破相邻环的相关性，增加细节)
     *
     * @param baseRadius 基础半径
     * @param ringIndex  当前环的索引
     * @param tRadExtra  额外半径时间变量
     * @return 添加了额外噪声的半径
     */
    private static double addExtraRadiusNoise(double baseRadius, int ringIndex, double tRadExtra) {
        // 噪声基于环索引和额外半径时间
        double extraNoise = getNoise(ringIndex * 1.5, tRadExtra * 1.5, 50.0);
        return baseRadius + extraNoise * RADIUS_EXTRA_NOISE_SCALE; // 应用额外噪声缩放
    }

    /**
     * 计算最终的高频半径抖动 (使用环索引`i`增加随机性)
     *
     * @param ringIndex 当前环的索引
     * @param tJitter   抖动时间变量
     * @return 半径抖动值
     */
    private static float calculateRadiusJitter(int ringIndex, double tJitter) {
        // 噪声基于环索引和抖动时间
        double jitterNoise = getNoise(ringIndex * 3.0, tJitter * 1.8, 55.0);
        return (float) jitterNoise * RADIUS_JITTER_SCALE; // 应用抖动缩放
    }

    /**
     * 计算旋转角度 (包括基础旋转、噪声影响和强度调制)
     *
     * @param normalizedY 归一化的 Y 坐标
     * @param ringIndex   当前环的索引
     * @param tRotBase    基础旋转时间变量
     * @param tRotMod     旋转调制时间变量
     * @return 最终的旋转角度 (弧度)
     */
    private static double calculateRotation(double normalizedY, int ringIndex, double tRotBase, double tRotMod) {
        // 计算旋转噪声强度的调制因子 [0, 1] (让噪声强度本身也变化)
        double modulation = (getNoise(normalizedY * 0.7, tRotMod, 65.0) + 1.0) * 0.5;
        // 当前实际应用的噪声强度 (在基础强度上根据调制因子调整)
        double currentNoiseScale = ROTATION_BASE_NOISE_SCALE * (1.0 - ROTATION_MODULATION_SCALE + modulation * ROTATION_MODULATION_SCALE * 2.0);
        // 基础旋转角度 (随时间增加，并轻微受高度影响，高处转得快一点)
        double baseRotation = tRotBase * (1.0 + normalizedY * 0.35);
        // 叠加基于环索引的旋转噪声
        double noise = getNoise(ringIndex * 1.0, tRotBase * 1.2, 60.0);
        return baseRotation + noise * currentNoiseScale;
    }

    /**
     * 计算透明度 (结合基础慢速变化、快速闪烁和高度淡出)
     *
     * @param normalizedY   归一化的 Y 坐标
     * @param ringIndex     当前环的索引
     * @param tAlphaBase    基础透明度时间变量
     * @param tAlphaFlicker 透明度闪烁时间变量
     * @return 最终透明度值 (0.0 到 1.0 之间)
     */
    private static float calculateAlpha(double normalizedY, int ringIndex, double tAlphaBase, double tAlphaFlicker) {
        // 基础慢速噪声 (基于 normalizedY 和基础时间)
        float baseNoise = (float) getNoise(normalizedY * 1.4, tAlphaBase, 70.0);
        // 快速闪烁噪声 (基于 ringIndex 和闪烁时间，产生快速明暗变化)
        float flickerNoise = (float) getNoise(ringIndex * 2.0, tAlphaFlicker, 75.0);
        // 合并噪声影响 (1.0 +/- 噪声值)
        float noiseFactor = 1.0f + baseNoise * ALPHA_BASE_NOISE_SCALE + flickerNoise * ALPHA_FLICKER_NOISE_SCALE;
        // 基于高度的淡出因子 (顶部更透明，指数使淡出更明显)
        float fadeFactor = 1.0f - (float) Math.pow(normalizedY, 2.2) * 0.5f;
        // 最终透明度 = 基础值 * 噪声影响 * 淡出因子
        float alpha = BASE_ALPHA * noiseFactor * fadeFactor;
        // 限制透明度范围，避免完全透明或完全不透明
        return Math.max(0.05f, Math.min(0.95f, alpha));
    }

    /**
     * 计算环带宽度 (结合基础慢速变化和细节快速变化)
     *
     * @param normalizedY  归一化的 Y 坐标
     * @param ringIndex    当前环的索引
     * @param tWidthBase   基础宽度时间变量
     * @param tWidthDetail 宽度细节时间变量
     * @return 最终环带宽度
     */
    private static float calculateRingWidth(double normalizedY, int ringIndex, double tWidthBase, double tWidthDetail) {
        // 基础宽度噪声 (基于 normalizedY 和基础时间，较慢变化)
        float baseNoise = (float) getNoise(normalizedY * 1.2, tWidthBase, 80.0);
        // 细节宽度噪声 (基于 ringIndex 和细节时间，较快变化)
        float detailNoise = (float) getNoise(ringIndex * 2.5, tWidthDetail, 85.0);
        // 最终宽度 = 基础宽度 * (1 + 基础噪声影响 + 细节噪声影响)
        float width = BASE_RING_WIDTH * (1.0f + baseNoise * WIDTH_BASE_NOISE_SCALE + detailNoise * WIDTH_DETAIL_NOISE_SCALE);
        return Math.max(0.015f, width); // 保证最小宽度，防止宽度为0或负数
    }

    /**
     * 计算倾斜旋转四元数，结果存储在全局变量 tempTiltQuat 中
     *
     * @param ringIndex 当前环的索引
     * @param tTilt     倾斜时间变量
     */
    private static void calculateTilt(int ringIndex, double tTilt) {
        // 倾斜噪声基于环索引和倾斜时间，生成 X 和 Z 方向的倾斜噪声
        double tiltNoiseX = getNoise(ringIndex * 1.6, tTilt * 1.1, 90.0);
        double tiltNoiseZ = getNoise(ringIndex * 1.6, tTilt * 1.1, 100.0);
        // 设置倾斜四元数 (先绕Z轴旋转，再绕X轴旋转，应用倾斜缩放)
        StormWingEffectRenderer.tempTiltQuat.identity().rotateZ((float) (tiltNoiseZ * RING_TILT_SCALE)).rotateX((float) (tiltNoiseX * RING_TILT_SCALE));
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, @NotNull AbstractClientPlayer livingEntity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!livingEntity.getEntityData().get(PlayerSyncSkillData.SKILL_DATA).getBoolean(StormWing.TAG_KEY)) {
            return;
        }
        poseStack.pushPose(); // 保存当前姿态栈状态
        poseStack.mulPoseMatrix(BASE_MATRIX); // 应用基础变换，使 Z 轴朝上
        VertexConsumer vertexConsumer = buffer.getBuffer(RENDER_TYPE); // 获取顶点消费者

        // --- 渲染四个龙卷风实例 ---
        // 每个实例应用不同的旋转，并传入不同的时间偏移量

        // 龙卷风 1 (+30 Z, +30 X) - 偏移量 1
        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(30)).rotateX((float) Math.toRadians(30))); // 应用实例特定的旋转
        renderSingleTornado(poseStack, vertexConsumer, livingEntity, partialTick, TORNADO_OFFSET_1); // 传递偏移量
        poseStack.popPose();

        // 龙卷风 2 (-30 Z, +30 X) - 偏移量 2
        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-30)).rotateX((float) Math.toRadians(30)));
        renderSingleTornado(poseStack, vertexConsumer, livingEntity, partialTick, TORNADO_OFFSET_2); // 传递偏移量
        poseStack.popPose();

        // 龙卷风 3 (+30 Z, -30 X) - 偏移量 3
        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(30)).rotateX((float) Math.toRadians(-30)));
        renderSingleTornado(poseStack, vertexConsumer, livingEntity, partialTick, TORNADO_OFFSET_3); // 传递偏移量
        poseStack.popPose();

        // 龙卷风 4 (-30 Z, -30 X) - 偏移量 4
        poseStack.pushPose();
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-30)).rotateX((float) Math.toRadians(-30)));
        renderSingleTornado(poseStack, vertexConsumer, livingEntity, partialTick, TORNADO_OFFSET_4); // 传递偏移量
        poseStack.popPose();

        poseStack.popPose(); // 恢复初始姿态栈状态 (弹出 BASE_MATRIX 变换)
    }

    /**
     * 渲染单个龙卷风实例
     *
     * @param poseStack      姿态栈
     * @param vertexConsumer 顶点消费者
     * @param livingEntity   渲染目标实体
     * @param partialTick    部分 tick (用于平滑动画)
     * @param timeOffset     一个时间偏移量，用于区分不同实例的动画时间基准，使它们看起来不同
     */
    private static void renderSingleTornado(@NotNull PoseStack poseStack, @NotNull VertexConsumer vertexConsumer, @NotNull AbstractClientPlayer livingEntity, float partialTick, float timeOffset) {
        // --- 预先计算与时间相关的变量 ---
        // 使用有效时间 = 实体时间 + 部分tick + 此实例的特定偏移量
        float effectiveTime = livingEntity.tickCount + partialTick + timeOffset;

        // 使用 effectiveTime 计算所有基于时间的噪声参数
        double tPosBase = effectiveTime * TIME_SCALE_POS_BASE;
        double tWarp = effectiveTime * TIME_SCALE_POS_WARP;
        double tGap = effectiveTime * TIME_SCALE_GAP;
        double tRadBase = effectiveTime * TIME_SCALE_RAD_BASE;
        double tRadExtra = effectiveTime * TIME_SCALE_RAD_EXTRA;
        double tJitter = effectiveTime * TIME_SCALE_JITTER;
        double tRotBase = effectiveTime * TIME_SCALE_ROT_BASE;
        double tRotMod = effectiveTime * TIME_SCALE_ROT_MOD;
        double tAlphaBase = effectiveTime * TIME_SCALE_ALPHA_BASE;
        double tAlphaFlicker = effectiveTime * TIME_SCALE_ALPHA_FLICKER;
        double tWidthBase = effectiveTime * TIME_SCALE_WIDTH_BASE;
        double tWidthDetail = effectiveTime * TIME_SCALE_WIDTH_DETAIL;
        double tTilt = effectiveTime * TIME_SCALE_TILT;

        // 计算平均垂直间隙
        float averageGap = (NUM_RINGS > 0) ? HEIGHT / (float) NUM_RINGS : 0.1f;
        float currentY = 0.0f; // 当前环的起始 Y 坐标

        // 循环渲染每个环
        for (int i = 0; i < NUM_RINGS; i++) {
            // 1. 计算归一化 Y 坐标 (0.0 到 1.0)
            double normalizedY = (NUM_RINGS <= 1) ? 0.5 : i / (double) (NUM_RINGS - 1);

            // 2. 计算此环的实际 Y 坐标
            if (i > 0) { // 第一个环在 Y=0 处
                float gap = calculateGap(i, tGap, averageGap); // 计算与上一个环的间隙
                currentY += gap;
            }
            double yJitter = calculateVerticalJitter(normalizedY, tWarp, tPosBase); // 计算垂直抖动
            double actualY = currentY + yJitter; // 应用抖动

            // 3. 计算水平位移 (dx, dz)
            calculateHorizontalDisplacement(normalizedY, tPosBase, tWarp); // 计算潜在位移并存入 buffer
            double potentialDx = displacementBuffer[0] * SIZE * HORIZONTAL_DISPLACEMENT_SCALE;
            double potentialDz = displacementBuffer[1] * SIZE * HORIZONTAL_DISPLACEMENT_SCALE;
            // 根据归一化高度缩放位移 (低处位移小，高处位移大)
            double actualDx = potentialDx * normalizedY;
            double actualDz = potentialDz * normalizedY;

            // 4. 计算主环的半径
            double rBase = calculateBaseRadius(normalizedY, tRadBase);       // 漏斗基础半径 + 基础噪声
            double rWithExtra = addExtraRadiusNoise(rBase, i, tRadExtra);   // + 额外噪声 (打破相关性)
            float rJitter = calculateRadiusJitter(i, tJitter);              // + 最终抖动
            // 合并基础/额外噪声和抖动，然后应用全局缩放并限制最小值
            float finalRadiusMain = (float) Math.max(0.015, (rWithExtra + rJitter) * SIZE);

            // 5. 计算其他属性：旋转角度、透明度、环带宽度、倾斜四元数
            double rotationAngle = calculateRotation(normalizedY, i, tRotBase, tRotMod);
            float alpha = calculateAlpha(normalizedY, i, tAlphaBase, tAlphaFlicker);
            float ringWidth = calculateRingWidth(normalizedY, i, tWidthBase, tWidthDetail);
            calculateTilt(i, tTilt); // 结果存储在 tempTiltQuat 中

            // --- 开始应用此环（及其可能的嵌套环）的变换 ---
            poseStack.pushPose(); // 隔离此高度级别的变换

            // 6. 应用共享的变换 (位移 -> Y轴旋转 -> 倾斜)
            poseStack.translate(actualDx, actualY, actualDz);               // 应用计算出的位移
            poseStack.mulPose(new Quaternionf().rotationY((float) rotationAngle)); // 应用Y轴旋转
            poseStack.mulPose(tempTiltQuat);                                 // 应用计算出的倾斜

            // --- 使用缓存的顶点和缩放来渲染主环 ---
            poseStack.pushPose(); // 隔离主环的缩放
            // 缩放单位环 (半径=1, 高度=1) 到所需的尺寸
            // X 和 Z 方向按最终半径缩放，Y 方向按环带宽度缩放 (即环带的“高度”)
            poseStack.scale(finalRadiusMain, ringWidth, finalRadiusMain);
            // 渲染缓存的环 - 它将使用当前的姿态栈，包含所有变换
            RenderUtil.RingRenderer.renderVerticalRing(poseStack.last().pose(),       // 传递完全变换后的矩阵
                    vertexConsumer, CACHED_RING_SEGMENTS,           // 使用与缓存匹配的分段数
                    CACHED_VERTICAL_VERTEX_BUFFER, // 使用缓存的顶点数据
                    1.0f, 1.0f, 1.0f, alpha       // 传递颜色/透明度 (这里使用白色)
            );
            poseStack.popPose(); // 渲染完主环后恢复缩放

            // --- 随机渲染一个嵌套环 ---
            if (RAND.nextFloat() < NESTED_RING_PROBABILITY) { // 根据概率决定是否渲染
                // 计算嵌套环的属性
                float nestedBaseRadiusRaw = (float) (rWithExtra * NESTED_RADIUS_FACTOR); // 嵌套环的基础半径 (基于父环半径)
                // 复用抖动计算，但使用偏移的索引/时间以产生变化
                float nestedJitter = calculateRadiusJitter(i + NUM_RINGS, tJitter + 0.5); // 添加偏移量
                // 合并基础和抖动，然后缩放并限制最小值
                float finalRadiusNested = Math.max(0.01f, (nestedBaseRadiusRaw + nestedJitter) * SIZE);

                float nestedWidth = Math.max(0.01f, ringWidth * NESTED_WIDTH_FACTOR); // 嵌套环宽度
                float nestedAlpha = Math.max(0.0f, Math.min(0.9f, alpha * NESTED_ALPHA_FACTOR)); // 嵌套环透明度

                poseStack.pushPose(); // 隔离嵌套环的缩放
                poseStack.scale(finalRadiusNested, nestedWidth, finalRadiusNested); // 应用嵌套环的缩放
                RenderUtil.RingRenderer.renderVerticalRing(poseStack.last().pose(), vertexConsumer, CACHED_RING_SEGMENTS, CACHED_VERTICAL_VERTEX_BUFFER, 1.0f, 1.0f, 1.0f, nestedAlpha // 使用嵌套环的透明度
                );
                poseStack.popPose();
            }

            poseStack.popPose();
        }
    }
}