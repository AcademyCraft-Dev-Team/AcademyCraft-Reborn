package org.academy.internal.common.world.entity.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.util.LevelUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@SuppressWarnings("resource")
public class HighSpeedElectronBeam extends Entity {
    public static final int maxChargerTicks = 40;
    public static final int maxRayLifeTicks = 15;
    public int currentChargerTicks = 0;
    public int currentRayLifeTicks = maxRayLifeTicks;
    public float rayProgress = 0;
    public boolean shouldStopRay = true;
    public int endShootTicks = 0;
    public float progress = 0;
    public float smoothProgress;
    public float smoothRayProgress;
    public float length = 50f;

    public HighSpeedElectronBeam(EntityType<?> entityType, Level level) {
        super(entityType, level);
        setNoGravity(false);
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

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            float frequency = 0.25f;
            float amplitude = 0.00075f;

            int uuidHash = this.getUUID().hashCode();
            float phaseX = (uuidHash % 1000) / 1000.0f * (float) Math.PI * 2;
            float phaseY = (((float) uuidHash / 1000) % 1000) / 1000.0f * (float) Math.PI * 2;
            float phaseZ = (((float) uuidHash / 1000000) % 1000) / 1000.0f * (float) Math.PI * 2;

            float offsetX = (float) Math.sin(tickCount * frequency + phaseX) * amplitude;
            float offsetY = (float) Math.sin(tickCount * frequency * 2 + phaseY) * amplitude * 2;
            float offsetZ = (float) Math.cos(tickCount * frequency + phaseZ) * amplitude;

            push(offsetX, offsetY, offsetZ);
        }

        if (currentChargerTicks >= maxChargerTicks) {
            if (shouldStopRay) {
                if (currentRayLifeTicks <= 0) {
                    kill();
                } else {
                    currentRayLifeTicks--;
                    if (!(endShootTicks > 15)) {
                        endShootTicks++;
                    }
                }
            }
            rayProgress = (float) currentRayLifeTicks / maxRayLifeTicks;
        } else {
            currentChargerTicks++;
        }
        progress = (float) currentChargerTicks / (float) maxChargerTicks - (float) endShootTicks / 15;
        move(MoverType.SELF, this.getDeltaMovement());
        if (rayProgress > 0.125f) {
            Optional<Pair<Boolean, Double>> result = LevelUtil.destroyBlocksAlongPath(level(), position(), position().add(getLookAngle().scale(length)), 0.25f, 10, false, true, true);
            if (result.isPresent()) {
                double d = result.get().getValue();
                length = (float) d;
            }
            if (!level().isClientSide) {
                LevelUtil.attackEntitiesAlongPath(level(), position(), position().add(getLookAngle().scale(length)), 1, new DamageSource(level().damageSources().damageTypes.getHolderOrThrow(DamageTypes.MOB_ATTACK), this), 100);

            }
        }
    }

    @Override
    public boolean shouldRender(double d, double e, double f) {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compound) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compound) {
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        Vec3 pos = this.position();
        double radius = 50.0;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }
}