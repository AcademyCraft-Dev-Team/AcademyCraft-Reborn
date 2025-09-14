package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.academy.api.client.renderer.ArcFactory;
import org.academy.api.client.renderer.ArcStyles;
import org.academy.api.common.util.MathUtil;
import org.academy.internal.common.world.entity.EntityTypes;

public class Arc extends Entity {
    public static final EntityDataAccessor<Float> ID_LENGTH = SynchedEntityData.defineId(Arc.class, EntityDataSerializers.FLOAT);
    public static final int defaultLifetime = 12;
    public int currentLifetime = defaultLifetime;
    public ArcFactory.ArcRenderData renderData = new ArcFactory.ArcRenderData();

    public Arc(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    public Arc(Level level, Vec3 handPos, Vec3 targetPos) {
        super(EntityTypes.ARC.get(), level);
        setPos(handPos);
        var dir = targetPos.subtract(handPos).normalize();
        var yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        var pitch = (float) Math.toDegrees(-Math.asin(dir.y));
        setYRot(yaw);
        setXRot(pitch);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ID_LENGTH, 0f);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            var style = ArcStyles.classic();
            style.seed = MathUtil.RANDOM.nextLong();
            style.start.set(0, 0, 0);
            style.end.set(0, getLength(), 0);
            renderData = ArcFactory.Generator.generate(style);
        }

        currentLifetime--;
        if (currentLifetime <= 0) {
            if (level() instanceof  ServerLevel serverLevel) {
                kill(serverLevel);
            }
        }
    }

    @Override
    public boolean shouldRender(double d, double e, double f) {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    public float getLength() {
        return entityData.get(ID_LENGTH);
    }

    public void setLength(float length) {
        entityData.set(ID_LENGTH, length);
    }
}