package org.academy.internal.common.world.entity.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("resource")
public class HighSpeedElectronBeam extends Entity {
    public static final int maxChargerTicks = 40;
    public int currentChargerTicks = 0;
    public float progress = 0f;

    public HighSpeedElectronBeam(EntityType<?> entityType, Level level) {
        super(entityType, level);
        setNoGravity(false);
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

        // 充能逻辑
        if (currentChargerTicks >= maxChargerTicks) {
            shoot();
        } else {
            currentChargerTicks++;
        }

        // 更新进度
        this.progress = (float) currentChargerTicks / maxChargerTicks;
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    public void shoot() {
        if (!level().isClientSide) {
            kill();
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