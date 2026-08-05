package org.academy.internal.common.world.entity.skill;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.entity.RenderOnlyEntity;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class Plasma extends RenderOnlyEntity {
    private static final EntityDataAccessor<Float> GATHER_PROGRESS = SynchedEntityData.defineId(
            Plasma.class, EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Boolean> LAUNCHED = SynchedEntityData.defineId(
            Plasma.class, EntityDataSerializers.BOOLEAN
    );
    private static final int MAX_LIFETIME = 20 * 30;

    private @Nullable UUID ownerUUID;
    private @Nullable Vec3 targetPosition;
    private double travelSpeed;
    private float damage;
    private float damageRadius;
    private float explosionPower;
    private boolean destroyBlocks;

    public Plasma(EntityType<?> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GATHER_PROGRESS, 0.0f);
        builder.define(LAUNCHED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (tickCount > MAX_LIFETIME) {
            discard();
            return;
        }
        if (!isLaunched()) return;
        if (targetPosition == null) {
            discard();
            return;
        }

        var delta = targetPosition.subtract(position());
        var distance = delta.length();
        if (distance <= Math.max(0.05, travelSpeed)) {
            setPos(targetPosition);
            impact();
            discard();
            return;
        }

        var movement = delta.scale(travelSpeed / distance);
        setDeltaMovement(movement);
        move(MoverType.SELF, movement);
    }

    public void launch(UUID ownerUUID, Vec3 targetPosition, double travelSpeed,
                       float damage, float damageRadius, float explosionPower,
                       boolean destroyBlocks) {
        this.ownerUUID = ownerUUID;
        this.targetPosition = targetPosition;
        this.travelSpeed = Math.max(0.05, travelSpeed);
        this.damage = Math.max(0.0f, damage);
        this.damageRadius = Math.max(0.0f, damageRadius);
        this.explosionPower = Math.max(0.0f, explosionPower);
        this.destroyBlocks = destroyBlocks;
        setGatherProgress(1.0f);
        entityData.set(LAUNCHED, true);
    }

    private void impact() {
        if (!(level() instanceof ServerLevel level)) return;
        var impact = position();
        var owner = ownerUUID == null
                ? null
                : level.getServer().getPlayerList().getPlayer(ownerUUID);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
        level.playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.PLASMA_GENERATION_BOOM.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        var source = owner == null
                ? level.damageSources().magic()
                : SkillDamageSource.of(owner, Skills.PLASMA_GENERATION.get());
        if (damage > 0.0f && damageRadius > 0.0f) {
            var radiusSquared = damageRadius * damageRadius;
            var area = new AABB(impact, impact).inflate(damageRadius);
            for (var target : level.getEntitiesOfClass(
                    LivingEntity.class,
                    area,
                    target -> target != owner
                            && target.isAlive()
                            && target.distanceToSqr(impact) <= radiusSquared
            )) {
                target.hurtServer(level, source, damage);
            }
        }

        if (explosionPower > 0.0f) {
            level.explode(
                    owner,
                    null,
                    null,
                    impact.x,
                    impact.y,
                    impact.z,
                    explosionPower,
                    false,
                    destroyBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE
            );
        }
    }

    public float getGatherProgress() {
        return entityData.get(GATHER_PROGRESS);
    }

    public void setGatherProgress(float progress) {
        entityData.set(GATHER_PROGRESS, Mth.clamp(progress, 0.0f, 1.0f));
    }

    public boolean isLaunched() {
        return entityData.get(LAUNCHED);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
