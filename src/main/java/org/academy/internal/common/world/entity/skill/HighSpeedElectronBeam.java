package org.academy.internal.common.world.entity.skill;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.util.LevelUtil;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.ability.Skill;
import org.academy.internal.common.ability.meltdowner.MeltdownerBeamDamage;
import org.academy.internal.common.ability.meltdowner.skills.RadiationIntensify;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

import java.util.UUID;

public class HighSpeedElectronBeam extends RenderOnlyEntity {
    private static final EntityDataAccessor<Float> BEAM_LENGTH = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Boolean> DESTROYS_BLOCKS = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Float> BEAM_SCALE = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> VISUAL_SIDE_OFFSET = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Boolean> CONTINUOUS = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> HELD_CHARGE = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Integer> ATTACK_DELAY_TICKS = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.INT
    );
    public static final int MAX_CHARGE_TICKS = 40;
    public static final int MAX_RAY_LIFE_TICKS = 15;

    public int currentChargerTicks = 0;
    public int currentRayLifeTicks = MAX_RAY_LIFE_TICKS;
    public boolean shouldStopRay = true;
    public float length = 50f;
    public boolean fired = false;
    private UUID ownerId;
    private Skill sourceSkill;
    private float baseDamage;
    private float targetMaxHealthDamageRatio;
    private float playerDamageMultiplier;
    private boolean radiationEnabled;

    public HighSpeedElectronBeam(EntityType<?> entityType, Level level) {
        super(entityType, level);
        setNoGravity(false);
    }

    public void configure(
            ServerPlayer owner,
            Skill sourceSkill,
            float baseDamage,
            float targetMaxHealthDamageRatio,
            float playerDamageMultiplier,
            boolean radiationEnabled
    ) {
        configure(
                owner,
                sourceSkill,
                baseDamage,
                targetMaxHealthDamageRatio,
                playerDamageMultiplier,
                radiationEnabled,
                true
        );
    }

    public void configure(
            ServerPlayer owner,
            Skill sourceSkill,
            float baseDamage,
            float targetMaxHealthDamageRatio,
            float playerDamageMultiplier,
            boolean radiationEnabled,
            boolean destroysBlocks
    ) {
        ownerId = owner.getUUID();
        this.sourceSkill = sourceSkill;
        this.baseDamage = Math.max(0.0f, baseDamage);
        this.targetMaxHealthDamageRatio = Math.max(0.0f, targetMaxHealthDamageRatio);
        this.playerDamageMultiplier = Math.max(0.0f, playerDamageMultiplier);
        this.radiationEnabled = radiationEnabled;
        entityData.set(DESTROYS_BLOCKS, destroysBlocks);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BEAM_LENGTH, 50.0f);
        builder.define(DESTROYS_BLOCKS, true);
        builder.define(BEAM_SCALE, 1.0f);
        builder.define(VISUAL_SIDE_OFFSET, 0.0f);
        builder.define(CONTINUOUS, false);
        builder.define(HELD_CHARGE, false);
        builder.define(ATTACK_DELAY_TICKS, MAX_CHARGE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        length = entityData.get(BEAM_LENGTH);

        if (isContinuous()) {
            currentChargerTicks = getAttackDelayTicks();
            currentRayLifeTicks = MAX_RAY_LIFE_TICKS;
            shouldStopRay = false;
            fired = true;
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        if (isHeldCharge()) {
            currentChargerTicks = Math.min(currentChargerTicks + 1, Math.max(0, getAttackDelayTicks() - 1));
            currentRayLifeTicks = MAX_RAY_LIFE_TICKS;
            shouldStopRay = true;
            fired = false;
            setDeltaMovement(Vec3.ZERO);
            return;
        }

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
            if (level() instanceof ServerLevel serverLevel) {
                var owner = resolveOwner(serverLevel);
                if (owner == null) {
                    fired = true;
                    kill(serverLevel);
                    return;
                }
                if (destroysBlocks()) updateBlockPath(serverLevel, owner, false);
                else setBeamLength((float) LevelUtil.getValidViewDistance(this, length));
            } else if (destroysBlocks()) {
                updateBlockPath(level(), null, true);
            } else {
                setBeamLength((float) LevelUtil.getValidViewDistance(this, length));
            }

            if (!level().isClientSide() && (baseDamage > 0.0f || targetMaxHealthDamageRatio > 0.0f)) {
                attackEntities((ServerLevel) level());
            }
            fired = true;
        }
    }

    private void updateBlockPath(Level level, ServerPlayer owner, boolean simulate) {
        var start = position();
        var end = start.add(getLookAngle().scale(length));
        var result = LevelUtil.destroyBlocksAlongPath(
                level,
                start,
                end,
                0.25f,
                10,
                false,
                true,
                true,
                simulate,
                owner
        );
        if (result.getKey()) setBeamLength(result.getValue().floatValue());
    }

    private void attackEntities(ServerLevel level) {
        var owner = resolveOwner(level);
        if (owner == null) return;
        var start = position();
        var end = start.add(getLookAngle().scale(length));
        var radius = 0.125f;
        var pathBounds = new AABB(start, end).inflate(radius);
        var damageSource = SkillDamageSource.of(owner, sourceSkill);
        var now = level.getGameTime();
        var candidates = level.getEntities(
                this,
                pathBounds,
                entity -> entity != owner
                        && entity.isAlive()
                        && entity.getType() != getType()
                        && !owner.isAlliedTo(entity)
        );
        for (var target : candidates) {
            var hitBounds = target.getBoundingBox().inflate(radius);
            if (!hitBounds.contains(start) && hitBounds.clip(start, end).isEmpty()) continue;
            var maxHealth = target instanceof LivingEntity living ? living.getMaxHealth() : 0.0f;
            var marked = radiationEnabled
                    && target instanceof LivingEntity living
                    && RadiationIntensify.isMarked(living, now);
            var damage = MeltdownerBeamDamage.calculate(
                    baseDamage,
                    targetMaxHealthDamageRatio,
                    maxHealth,
                    playerDamageMultiplier,
                    marked
            );
            var hurt = target.hurtServer(level, damageSource, damage);
            if (hurt && radiationEnabled && target instanceof LivingEntity living) {
                RadiationIntensify.mark(living, now);
            }
        }
    }

    private ServerPlayer resolveOwner(ServerLevel level) {
        if (ownerId == null || sourceSkill == null) return null;
        var owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null
                || owner.level() != level
                || !owner.isAlive()
                || !sourceSkill.isEnabled(owner)) {
            return null;
        }
        return owner;
    }

    public boolean isCharging() {
        return currentChargerTicks < getAttackDelayTicks();
    }

    public void setAttackDelayTicks(int ticks) {
        entityData.set(ATTACK_DELAY_TICKS, Math.clamp(ticks, 0, 20 * 60));
    }

    public int getAttackDelayTicks() {
        return entityData.get(ATTACK_DELAY_TICKS);
    }

    public void setBeamLength(float length) {
        var safeLength = Float.isFinite(length) ? Math.max(0.0f, length) : 0.0f;
        this.length = safeLength;
        entityData.set(BEAM_LENGTH, safeLength);
    }

    public float getBeamLength() {
        return entityData.get(BEAM_LENGTH);
    }

    public void setBeamScale(float scale) {
        entityData.set(BEAM_SCALE, Float.isFinite(scale) ? Math.max(0.0f, scale) : 0.0f);
    }

    public float getBeamScale() {
        return entityData.get(BEAM_SCALE);
    }

    public void setVisualSideOffset(float offset) {
        entityData.set(VISUAL_SIDE_OFFSET, Float.isFinite(offset) ? offset : 0.0f);
    }

    public float getVisualSideOffset() {
        return entityData.get(VISUAL_SIDE_OFFSET);
    }

    public void setContinuous(boolean continuous) {
        entityData.set(CONTINUOUS, continuous);
    }

    public boolean isContinuous() {
        return entityData.get(CONTINUOUS);
    }

    public void setHeldCharge(boolean heldCharge) {
        entityData.set(HELD_CHARGE, heldCharge);
    }

    public boolean isHeldCharge() {
        return entityData.get(HELD_CHARGE);
    }

    public boolean destroysBlocks() {
        return entityData.get(DESTROYS_BLOCKS);
    }
}
