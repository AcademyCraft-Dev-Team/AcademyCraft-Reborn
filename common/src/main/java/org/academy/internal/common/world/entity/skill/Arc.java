package org.academy.internal.common.world.entity.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.EntityTypes;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("resource")
public class Arc extends Entity {
    public static final int defaultLifetime = 8;
    public int currentLifetime = defaultLifetime;
    public long random;

    public Arc(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public Arc(Level level, Player player) {
        super(EntityTypes.ARC_ENTITY_TYPE, level);
        this.setPos(player.position().add(0, 1, 0));
        this.setYRot(player.getYRot());
        this.setXRot(player.getXRot());
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
    public void tick() {
        super.tick();
        this.random = MathUtil.RANDOM.nextLong();
        currentLifetime--;
        if (currentLifetime <= 0) {
            if (!level().isClientSide()) {
                kill();
            }
        }
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        Vec3 pos = this.position();
        double radius = 10;
        return new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
    }

    @Override
    public boolean shouldRender(double d, double e, double f) {
        return true;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
    }
}