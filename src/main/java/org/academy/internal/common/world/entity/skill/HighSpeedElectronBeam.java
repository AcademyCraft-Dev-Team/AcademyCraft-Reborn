package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import org.academy.api.common.util.LevelUtil;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

public class HighSpeedElectronBeam extends RenderOnlyEntity {
    public static final int MAX_CHARGE_TICKS = 40;
    public static final int MAX_RAY_LIFE_TICKS = 15;

    public int currentChargerTicks = 0;
    public int currentRayLifeTicks = MAX_RAY_LIFE_TICKS;
    public boolean shouldStopRay = true;
    public float length = 50f;
    public boolean fired = false;

    public HighSpeedElectronBeam(EntityType<?> entityType, Level level) {
        super(entityType, level);
        setNoGravity(false);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide()) {
            var frequency = 0.25f;
            var amplitude = 0.00075f;

            var uuidHash = getUUID().hashCode();
            var phaseX = (uuidHash % 1000) / 1000.0f * (float) Math.PI * 2;
            var phaseY = (((float) uuidHash / 1000) % 1000) / 1000.0f * (float) Math.PI * 2;
            var phaseZ = (((float) uuidHash / 1000000) % 1000) / 1000.0f * (float) Math.PI * 2;

            var offsetX = (float) Math.sin(tickCount * frequency + phaseX) * amplitude;
            var offsetY = (float) Math.sin(tickCount * frequency * 2 + phaseY) * amplitude * 2;
            var offsetZ = (float) Math.cos(tickCount * frequency + phaseZ) * amplitude;

            push(offsetX, offsetY, offsetZ);
        }

        if (isCharging()) {
            currentChargerTicks++;
        } else {
            if (shouldStopRay) {
                if (currentRayLifeTicks <= 0) {
                    if (level() instanceof ServerLevel serverLevel) {
                        kill(serverLevel);
                    }
                } else {
                    currentRayLifeTicks--;
                }
            }
        }

        move(MoverType.SELF, getDeltaMovement());

        if (!isCharging() && !fired) {
            var result = LevelUtil.destroyBlocksAlongPath(level(), position(), position().add(getLookAngle().scale(length)), 0.25f, 3, false, true, true, level().isClientSide());
            if (result.getKey()) {
                double d = result.getValue();
                length = (float) d;
            }

            if (!level().isClientSide()) {
                LevelUtil.attackEntitiesAlongPath(level(), position(), position().add(getLookAngle().scale(length)), 0.125f, new DamageSource(level().damageSources().damageTypes.getOrThrow(DamageTypes.MOB_ATTACK), this), 100);
            }
            fired = true;
        }
    }

    public boolean isCharging() {
        return currentChargerTicks < MAX_CHARGE_TICKS;
    }
}