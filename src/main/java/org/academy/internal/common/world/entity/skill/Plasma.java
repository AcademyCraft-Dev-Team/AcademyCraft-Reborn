package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.util.LevelUtil;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.TimedSkillEffectRuntime;
import org.academy.internal.common.sounds.SoundEvents;
import org.academy.internal.common.world.damagesource.DamageTypes;
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
    private static final EntityDataAccessor<Integer> OWNER_ENTITY_ID = SynchedEntityData.defineId(
            Plasma.class, EntityDataSerializers.INT
    );
    private static final int MAX_LIFETIME = 20 * 30;
    private static final int LAUNCH_DELAY_TICKS = 4;

    private @Nullable UUID ownerUUID;
    private @Nullable Vec3 targetPosition;
    private double travelSpeed;
    private float damage;
    private float damageRadius;
    private float explosionPower;
    private boolean destroyBlocks;
    private int proficiencyMilestone;
    private int launchDelayTicks;

    public Plasma(EntityType<?> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
        setNoGravity(true);
    }

    private static void destroyExplosionBlocks(
            ServerLevel level,
            ServerPlayer owner,
            Vec3 center,
            float radius
    ) {
        final var rayCount = 24;
        final var goldenAngle = Mth.PI * (3.0 - Mth.sqrt((float) (5.0)));
        for (var index = 0; index < rayCount; index++) {
            var y = 1.0 - 2.0 * (index + 0.5) / rayCount;
            var horizontal = Mth.sqrt((float) (Math.max(0.0, 1.0 - y * y)));
            var angle = index * goldenAngle;
            var direction = new Vec3(
                    Mth.cos(angle) * horizontal,
                    y,
                    Mth.sin(angle) * horizontal
            );
            LevelUtil.destroyBlocksAlongPath(
                    level,
                    center,
                    center.add(direction.scale(radius)),
                    0.7f,
                    3,
                    false,
                    true,
                    true,
                    false,
                    owner
            );
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GATHER_PROGRESS, 0.0f);
        builder.define(LAUNCHED, false);
        builder.define(OWNER_ENTITY_ID, 0);
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
        if (launchDelayTicks > 0) {
            launchDelayTicks--;
            setDeltaMovement(Vec3.ZERO);
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
                       boolean destroyBlocks, int proficiencyMilestone) {
        this.ownerUUID = ownerUUID;
        this.targetPosition = targetPosition;
        this.travelSpeed = Math.max(0.05, travelSpeed);
        this.damage = Math.max(0.0f, damage);
        this.damageRadius = Math.max(0.0f, damageRadius);
        this.explosionPower = Math.max(0.0f, explosionPower);
        this.destroyBlocks = destroyBlocks;
        this.proficiencyMilestone = Math.clamp(proficiencyMilestone, 0, 3);
        this.launchDelayTicks = LAUNCH_DELAY_TICKS;
        setGatherProgress(1.0f);
        entityData.set(LAUNCHED, true);
    }

    private void impact() {
        if (!(level() instanceof ServerLevel level)) return;
        var impact = position();
        var owner = ownerUUID == null
                ? null
                : level.getServer().getPlayerList().getPlayer(ownerUUID);

        level.playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.PLASMA_GENERATION_BOOM.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        if (owner != null && damage > 0.0f && damageRadius > 0.0f) {
            var source = SkillDamageSource.of(
                    owner,
                    Skills.PLASMA_GENERATION.get(),
                    DamageTypes.VEC
            );
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
            if (proficiencyMilestone >= 3) {
                for (var pulse = 1; pulse <= 5; pulse++) {
                    TimedSkillEffectRuntime.schedule(owner, pulse * 20, () -> {
                        if (owner.isRemoved() || !owner.isAlive()) return;
                        for (var target : level.getEntitiesOfClass(
                                LivingEntity.class,
                                area,
                                entity -> entity != owner && entity.isAlive()
                                        && entity.distanceToSqr(impact) <= radiusSquared)) {
                            target.hurtServer(level, source, damage * 0.08f);
                        }
                    });
                }
            }
        }

        if (destroyBlocks && owner != null && explosionPower > 0.0f) {
            destroyExplosionBlocks(level, owner, impact, explosionPower);
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

    public int getOwnerEntityId() {
        return entityData.get(OWNER_ENTITY_ID);
    }

    public void setOwnerEntityId(int entityId) {
        entityData.set(OWNER_ENTITY_ID, entityId);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
