package org.academy.internal.common.world.entity.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.academy.api.common.util.ImprovedNoise;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.client.renderer.entity.PlasmaRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

@SuppressWarnings("resource")
public class Plasma extends Entity {
    private static final EntityDataAccessor<Float> DATA_GATHER_PROGRESS = SynchedEntityData.defineId(Plasma.class, EntityDataSerializers.FLOAT); // 同步实体数据：聚集进度
    public static final int IDX_POS_X = 0; // 粒子数据索引：当前X坐标
    public static final int IDX_POS_Y = 1; // 粒子数据索引：当前Y坐标
    public static final int IDX_POS_Z = 2; // 粒子数据索引：当前Z坐标
    public static final int IDX_PREV_POS_X = 3; // 粒子数据索引：上一帧X坐标
    public static final int IDX_PREV_POS_Y = 4; // 粒子数据索引：上一帧Y坐标
    public static final int IDX_PREV_POS_Z = 5; // 粒子数据索引：上一帧Z坐标
    public static final int IDX_AGE = 6; // 粒子数据索引：粒子年龄 (tick)
    public static final int IDX_MAX_AGE = 7; // 粒子数据索引：粒子最大寿命 (tick)
    public static final int ATM_PARTICLE_DATA_SIZE = 15; // 大气粒子数据数组大小
    public static final int GROUND_PARTICLE_DATA_SIZE = 15; // 地面粒子数据数组大小
    private static final int IDX_ATM_INITIAL_RADIUS = 8; // 大气粒子索引：初始半径
    private static final int IDX_ATM_INITIAL_Y = 9; // 大气粒子索引：初始Y坐标
    private static final int IDX_ATM_INITIAL_ANGLE = 10; // 大气粒子索引：初始角度
    private static final int IDX_ATM_ROTATION_SPEED = 11; // 大气粒子索引：旋转速度
    private static final int IDX_ATM_NOISE_SEED_X = 12; // 大气粒子索引：噪声种子X
    private static final int IDX_ATM_NOISE_SEED_Y = 13; // 大气粒子索引：噪声种子Y
    private static final int IDX_ATM_NOISE_SEED_Z = 14; // 大气粒子索引：噪声种子Z
    private static final int IDX_GROUND_START_X = 8; // 地面粒子索引：起始X坐标
    private static final int IDX_GROUND_START_Y = 9; // 地面粒子索引：起始Y坐标
    private static final int IDX_GROUND_START_Z = 10; // 地面粒子索引：起始Z坐标
    private static final int IDX_GROUND_VEL_X = 11; // 地面粒子索引：X轴速度
    private static final int IDX_GROUND_VEL_Y = 12; // 地面粒子索引：Y轴速度
    private static final int IDX_GROUND_VEL_Z = 13; // 地面粒子索引：Z轴速度
    private static final int IDX_GROUND_NOISE_SEED = 14; // 地面粒子索引：噪声种子
    private static final float EFFECT_GATHER_DURATION_TICKS = 80.0f; // 效果聚集持续时间 (tick) - Currently unused due to fix below

    private static final int NUM_ATMOSPHERE_PARTICLES_TO_MANAGE = 350; // 管理的大气粒子数量
    private static final float ATMOSPHERE_PARTICLE_LIFE_TICKS = 90f; // 大气粒子寿命 (tick)
    private static final float ATMOSPHERE_PARTICLE_ROTATION_SPEED_SCALE = 0.07f; // 大气粒子旋转速度缩放
    private static final float ATMOSPHERE_PARTICLE_DRIFT_SCALE = 0.9f; // 大气粒子漂移缩放
    private static final float ATMOSPHERE_TARGET_RADIUS_NEAR_CORE = 5.0f; // 大气粒子靠近核心的目标半径

    private static final int NUM_GROUND_PARTICLES_TO_MANAGE = 200; // 管理的地面粒子数量
    private static final int GROUND_PARTICLES_SPAWN_PER_TICK = 5; // 每tick生成的地面粒子数量
    private static final float GROUND_PARTICLE_LIFE_TICKS = 50f; // 地面粒子寿命 (tick)
    private static final float GROUND_PARTICLE_SPEED_SCALE = 1.1f; // 地面粒子速度缩放
    private static final float GROUND_PARTICLE_DRIFT_SCALE = 3.0f; // 地面粒子漂移缩放

    private static final float GROUND_PARTICLE_MAX_SPAWN_RADIUS_SIM = 120f; // 地面粒子最大生成半径（模拟用）

    private final Random simulationRandom = new Random();
    private final float[][] atmosphereParticles;
    private final float[][] groundParticles;
    private float prevGatherProgress;

    public Plasma(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
        this.blocksBuilding = true;

        atmosphereParticles = new float[NUM_ATMOSPHERE_PARTICLES_TO_MANAGE][ATM_PARTICLE_DATA_SIZE];
        groundParticles = new float[NUM_GROUND_PARTICLES_TO_MANAGE][GROUND_PARTICLE_DATA_SIZE];

        for (float[] pData : atmosphereParticles) {
            pData[IDX_AGE] = -1f;
        }
        for (float[] pData : groundParticles) {
            pData[IDX_AGE] = -1f;
        }

        simulationRandom.setSeed(this.getId() + 12345L);
        // Initialize prevGatherProgress based on the default defined value
        prevGatherProgress = this.entityData.get(DATA_GATHER_PROGRESS);
    }

    @Override
    protected void defineSynchedData() {
        // Default gather progress is 1.0f (full size)
        this.entityData.define(DATA_GATHER_PROGRESS, 1.0f);
    }

    @Override
    public void tick() {
        super.tick();
        // Always update prevGatherProgress *before* any potential changes
        this.prevGatherProgress = getGatherProgress();

        // --- FIX: Remove the server-side logic that decreases gatherProgress ---
        // if (!this.level().isClientSide) {
        //     float currentProgress = 1.0f - MathUtil.clamp((float) this.tickCount / EFFECT_GATHER_DURATION_TICKS, 0.0f, 1.0f);
        //     this.entityData.set(DATA_GATHER_PROGRESS, currentProgress);
        // }
        // --- End of Fix ---
        // By removing the update, DATA_GATHER_PROGRESS will remain at its default value (1.0f)

        // Client-side particle simulation still runs
        if (this.level().isClientSide) {
            simulateParticlesClient();
        }
    }

    // No changes needed below this line for the fix //
    // ... (rest of the Plasma class methods remain the same) ...
    private void simulateParticlesClient() {
        // gatherFactor will now consistently be calculated based on gatherProgress = 1.0
        float currentGatherProgress = getGatherProgress();
        // smoothedGatherProgress will be smoothStep(1.0) = 1.0
        // gatherFactor will be lerp(1.0, 0.25, 1.0) = 1.0
        float gatherFactor = MathUtil.lerpFactorStartEnd(MathUtil.smoothStep(currentGatherProgress), PlasmaRenderer.MIN_GATHER_FACTOR, 1.0f);

        simulateAtmosphereParticles(gatherFactor); // Will receive gatherFactor = 1.0
        simulateGroundParticles(gatherFactor);   // Will receive gatherFactor = 1.0
    }

    private void simulateAtmosphereParticles(float gatherFactor) {
        for (int i = 0; i < atmosphereParticles.length; ++i) {
            float[] pData = atmosphereParticles[i];
            if (pData[IDX_AGE] < 0) {
                spawnAtmosphereParticle(i, gatherFactor);
            } else {
                pData[IDX_PREV_POS_X] = pData[IDX_POS_X];
                pData[IDX_PREV_POS_Y] = pData[IDX_POS_Y];
                pData[IDX_PREV_POS_Z] = pData[IDX_POS_Z];
                pData[IDX_AGE] += 1.0f;
                if (pData[IDX_AGE] >= pData[IDX_MAX_AGE]) {
                    pData[IDX_AGE] = -1f;
                } else {
                    calculateAtmosphereParticlePos(pData, gatherFactor);
                }
            }
        }
    }

    private void simulateGroundParticles(float gatherFactor) {
        int spawnedThisTick = 0;
        float currentGroundSpawnRadius = GROUND_PARTICLE_MAX_SPAWN_RADIUS_SIM * gatherFactor;

        for (int i = 0; i < groundParticles.length; ++i) {
            float[] pData = groundParticles[i];
            if (pData[IDX_AGE] < 0) {
                if (spawnedThisTick < GROUND_PARTICLES_SPAWN_PER_TICK * (0.75f + simulationRandom.nextFloat() * 0.5f)) {
                    spawnGroundParticle(i, currentGroundSpawnRadius);
                    spawnedThisTick++;
                }
            } else {
                pData[IDX_PREV_POS_X] = pData[IDX_POS_X];
                pData[IDX_PREV_POS_Y] = pData[IDX_POS_Y];
                pData[IDX_PREV_POS_Z] = pData[IDX_POS_Z];
                pData[IDX_AGE] += 1.0f;
                if (pData[IDX_AGE] >= pData[IDX_MAX_AGE]) {
                    pData[IDX_AGE] = -1f;
                } else {
                    updateGroundParticlePos(pData);
                }
            }
        }
    }

    @SuppressWarnings("DuplicatedCode")
    private void spawnAtmosphereParticle(int index, float gatherFactor) {
        float[] pData = atmosphereParticles[index];

        // Uses gatherFactor (which will be 1.0 with the fix)
        float currentMaxVisualRadius = PlasmaRenderer.MAX_EFFECT_RADIUS * gatherFactor;
        float initialRadius = currentMaxVisualRadius * (0.8f + simulationRandom.nextFloat() * 0.2f);
        initialRadius = Math.max(1.0f, initialRadius);

        float initialAngle = simulationRandom.nextFloat() * MathUtil.TWO_PI;

        float effectMinYAbs = PlasmaRenderer.CORE_Y_OFFSET + PlasmaRenderer.MIN_EFFECT_HEIGHT_REL_CORE;
        float effectMaxYAbs = PlasmaRenderer.CORE_Y_OFFSET + PlasmaRenderer.MAX_EFFECT_HEIGHT_REL_CORE;
        float initialY = MathUtil.lerpFactorStartEnd(simulationRandom.nextFloat(), effectMinYAbs, effectMaxYAbs);

        float rotationSpeed = (simulationRandom.nextFloat() - 0.5f) * 2.0f * ATMOSPHERE_PARTICLE_ROTATION_SPEED_SCALE;

        float initialX = initialRadius * (float) Math.cos(initialAngle);
        float initialZ = initialRadius * (float) Math.sin(initialAngle);

        pData[IDX_POS_X] = initialX;
        pData[IDX_POS_Y] = initialY;
        pData[IDX_POS_Z] = initialZ;
        pData[IDX_PREV_POS_X] = initialX;
        pData[IDX_PREV_POS_Y] = initialY;
        pData[IDX_PREV_POS_Z] = initialZ;
        pData[IDX_AGE] = 0f;
        pData[IDX_MAX_AGE] = ATMOSPHERE_PARTICLE_LIFE_TICKS * (0.8f + simulationRandom.nextFloat() * 0.4f);
        pData[IDX_ATM_INITIAL_RADIUS] = initialRadius;
        pData[IDX_ATM_INITIAL_Y] = initialY;
        pData[IDX_ATM_INITIAL_ANGLE] = initialAngle;
        pData[IDX_ATM_ROTATION_SPEED] = rotationSpeed;
        pData[IDX_ATM_NOISE_SEED_X] = simulationRandom.nextFloat() * 1000f;
        pData[IDX_ATM_NOISE_SEED_Y] = simulationRandom.nextFloat() * 1000f;
        pData[IDX_ATM_NOISE_SEED_Z] = simulationRandom.nextFloat() * 1000f;
    }


    private void calculateAtmosphereParticlePos(float[] pData, float gatherFactor) {
        float age = pData[IDX_AGE];
        float maxAge = pData[IDX_MAX_AGE];
        float lifeRatio = MathUtil.clamp(age / maxAge, 0.0f, 1.0f);
        float smoothedLifeRatio = MathUtil.smoothStep(lifeRatio);

        float initialRadius = pData[IDX_ATM_INITIAL_RADIUS];
        // Particle still shrinks towards core visually, independently of overall effect size
        float currentRadius = MathUtil.lerpFactorStartEnd(smoothedLifeRatio, initialRadius, ATMOSPHERE_TARGET_RADIUS_NEAR_CORE);

        float initialY = pData[IDX_ATM_INITIAL_Y];
        float targetY = PlasmaRenderer.CORE_Y_OFFSET;
        float currentY = MathUtil.lerpFactorStartEnd(smoothedLifeRatio, initialY, targetY);

        float initialAngle = pData[IDX_ATM_INITIAL_ANGLE];
        float rotationSpeed = pData[IDX_ATM_ROTATION_SPEED];
        float currentAngle = initialAngle + age * rotationSpeed;

        float baseX = currentRadius * (float) Math.cos(currentAngle);
        float baseZ = currentRadius * (float) Math.sin(currentAngle);

        double driftTime = age * 0.05;
        double noiseFreq = 0.08;
        // Uses gatherFactor (which will be 1.0 with the fix)
        float driftFactor = ATMOSPHERE_PARTICLE_DRIFT_SCALE * (1.0f - lifeRatio * 0.8f) * gatherFactor;

        float noiseSeedX = pData[IDX_ATM_NOISE_SEED_X];
        float noiseSeedY = pData[IDX_ATM_NOISE_SEED_Y];
        float noiseSeedZ = pData[IDX_ATM_NOISE_SEED_Z];

        float driftX = (float) ImprovedNoise.noise(initialRadius * noiseFreq + noiseSeedX, currentAngle, driftTime + 0.0) * driftFactor;
        float driftY = (float) ImprovedNoise.noise(initialY * noiseFreq + noiseSeedY, currentAngle, driftTime + 4.0) * driftFactor * 0.5f;
        float driftZ = (float) ImprovedNoise.noise(initialRadius * noiseFreq + noiseSeedZ, currentAngle + MathUtil.PI, driftTime + 8.0) * driftFactor;

        pData[IDX_POS_X] = baseX + driftX;
        pData[IDX_POS_Y] = currentY + driftY;
        pData[IDX_POS_Z] = baseZ + driftZ;
    }


    @SuppressWarnings("DuplicatedCode")
    private void spawnGroundParticle(int index, float currentMaxSpawnRadius) {
        float[] pData = groundParticles[index];

        // Uses currentMaxSpawnRadius (which depends on gatherFactor = 1.0 with the fix)
        double angle = simulationRandom.nextDouble() * MathUtil.TWO_PI;
        double radius = Math.sqrt(simulationRandom.nextDouble()) * currentMaxSpawnRadius;

        float startX = (float) (radius * Math.cos(angle));
        float startZ = (float) (radius * Math.sin(angle));

        float effectMinYAbs = PlasmaRenderer.CORE_Y_OFFSET + PlasmaRenderer.MIN_EFFECT_HEIGHT_REL_CORE;
        float effectMaxYAbs = PlasmaRenderer.CORE_Y_OFFSET + PlasmaRenderer.MAX_EFFECT_HEIGHT_REL_CORE;
        float startY = MathUtil.lerpFactorStartEnd(simulationRandom.nextFloat(), effectMinYAbs, effectMaxYAbs);

        float speed = GROUND_PARTICLE_SPEED_SCALE * (0.8f + simulationRandom.nextFloat() * 0.4f);
        double velAngleXY = simulationRandom.nextDouble() * MathUtil.TWO_PI;
        double velAngleZ = (simulationRandom.nextDouble() - 0.4) * MathUtil.PI * 0.8;
        float cosZ = (float) Math.cos(velAngleZ);
        float velX = speed * (float) Math.cos(velAngleXY) * cosZ;
        float velY = speed * (float) Math.sin(velAngleZ);
        float velZ = speed * (float) Math.sin(velAngleXY) * cosZ;

        pData[IDX_POS_X] = startX;
        pData[IDX_POS_Y] = startY;
        pData[IDX_POS_Z] = startZ;
        pData[IDX_PREV_POS_X] = startX;
        pData[IDX_PREV_POS_Y] = startY;
        pData[IDX_PREV_POS_Z] = startZ;
        pData[IDX_AGE] = 0f;
        pData[IDX_MAX_AGE] = GROUND_PARTICLE_LIFE_TICKS * (0.8f + simulationRandom.nextFloat() * 0.4f);
        pData[IDX_GROUND_START_X] = startX;
        pData[IDX_GROUND_START_Y] = startY;
        pData[IDX_GROUND_START_Z] = startZ;
        pData[IDX_GROUND_VEL_X] = velX;
        pData[IDX_GROUND_VEL_Y] = velY;
        pData[IDX_GROUND_VEL_Z] = velZ;
        pData[IDX_GROUND_NOISE_SEED] = simulationRandom.nextFloat() * 1000f;
    }

    private void updateGroundParticlePos(float[] pData) {
        float age = pData[IDX_AGE];
        float maxAge = pData[IDX_MAX_AGE];
        float lifeRatio = MathUtil.clamp(age / maxAge, 0.0f, 1.0f);

        double driftTime = pData[IDX_GROUND_NOISE_SEED] + age * 0.11;
        float driftModulation = lifeRatio * (1.5f - lifeRatio);
        float currentDriftScale = GROUND_PARTICLE_DRIFT_SCALE * driftModulation;

        float startX = pData[IDX_GROUND_START_X];
        float startY = pData[IDX_GROUND_START_Y];
        float startZ = pData[IDX_GROUND_START_Z];
        double noiseSeed = pData[IDX_GROUND_NOISE_SEED];

        float driftX = (float) ImprovedNoise.noise(startX * 0.15 + noiseSeed, startY * 0.15, driftTime + 0) * currentDriftScale;
        float driftY = (float) ImprovedNoise.noise(startY * 0.15 + noiseSeed, startZ * 0.15, driftTime + 4) * currentDriftScale * 0.6f;
        float driftZ = (float) ImprovedNoise.noise(startZ * 0.15 + noiseSeed, startX * 0.15, driftTime + 8) * currentDriftScale;

        float finalX = startX + pData[IDX_GROUND_VEL_X] * age + driftX;
        float finalY = startY + pData[IDX_GROUND_VEL_Y] * age + driftY;
        float finalZ = startZ + pData[IDX_GROUND_VEL_Z] * age + driftZ;

        pData[IDX_POS_X] = finalX;
        pData[IDX_POS_Y] = finalY;
        pData[IDX_POS_Z] = finalZ;
    }


    public float getGatherProgress() {
        return this.entityData.get(DATA_GATHER_PROGRESS);
    }

    public float getInterpolatedGatherProgress(float partialTick) {
        return MathUtil.lerpFactorStartEnd(partialTick, this.prevGatherProgress, getGatherProgress());
    }

    public float[][] getAtmosphereParticles() {
        return atmosphereParticles;
    }

    public float[][] getGroundParticles() {
        return groundParticles;
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        double radius = 512;
        return new AABB( radius,  radius,  radius, radius, radius,  radius);
    }

    @Override
    public boolean shouldRender(double camX, double camY, double camZ) {
        return true;
    }


    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compound) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compound) {
    }


    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }
}