package org.academy.internal.common.world.entity.skill;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.ability.Skill;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.util.LevelUtil;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.accelerator.reflection.*;
import org.academy.internal.common.ability.meltdowner.MeltdownerBeamDamage;
import org.academy.internal.common.ability.meltdowner.skills.lv1.RadiationIntensify;
import org.academy.internal.common.world.entity.RenderOnlyEntity;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class HighSpeedElectronBeam extends RenderOnlyEntity {
    public static final int MAX_CHARGE_TICKS = 40;
    public static final int MAX_RAY_LIFE_TICKS = 15;
    private static final int BLOCK_MINING_TIER = 3;
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
    private static final EntityDataAccessor<Boolean> RAY_FIRED = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> REFLECTION_ACTIVE = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Float> REFLECTION_DISTANCE = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> REFLECTION_RETURN_LENGTH = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> REFLECTION_DIRECTION_X = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> REFLECTION_DIRECTION_Y = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.FLOAT
    );
    private static final EntityDataAccessor<Float> REFLECTION_DIRECTION_Z = SynchedEntityData.defineId(
            HighSpeedElectronBeam.class,
            EntityDataSerializers.FLOAT
    );
    public int currentChargerTicks = 0;
    public int currentRayLifeTicks = MAX_RAY_LIFE_TICKS;
    public boolean shouldStopRay = true;
    public float length = 50f;
    public boolean fired = false;
    private UUID ownerId;
    private UUID ignoredTargetId;
    private Skill sourceSkill;
    private float baseDamage;
    private float targetMaxHealthDamageRatio;
    private float abilityPower = 1.0f;
    private float playerDamageMultiplier;
    private boolean powerScaledBase;
    private boolean radiationEnabled;
    private boolean betaTrailOnFire;
    private int proficiencyMilestone;

    public HighSpeedElectronBeam(EntityType<?> entityType, Level level) {
        super(entityType, level);
        setNoGravity(false);
    }

    private static void destroyBlocks(
            ServerLevel level,
            ServerPlayer breaker,
            LinearSegment segment
    ) {
        LevelUtil.destroyBlocksAlongPath(
                level,
                segment.start(),
                segment.end(),
                0.25f,
                BLOCK_MINING_TIER,
                false,
                true,
                true,
                false,
                breaker
        );
    }

    private static void spawnBetaTrail(ServerLevel level, ResolvedLinearAttack attack) {
        for (var segment : attack.segments()) {
            var delta = segment.delta();
            for (var step = 0; step <= 12; step++) {
                var point = segment.start().add(delta.scale(step / 12.0));
                level.sendParticles(
                        ParticleTypes.ELECTRIC_SPARK,
                        point.x,
                        point.y,
                        point.z,
                        1,
                        0.02,
                        0.02,
                        0.02,
                        0.01
                );
            }
        }
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

    public void configure(
            ServerPlayer owner,
            Skill sourceSkill,
            float baseDamage,
            float targetMaxHealthDamageRatio,
            float playerDamageMultiplier,
            boolean radiationEnabled,
            boolean destroysBlocks,
            int proficiencyMilestone
    ) {
        configure(owner, sourceSkill, baseDamage, targetMaxHealthDamageRatio,
                playerDamageMultiplier, radiationEnabled, destroysBlocks);
        this.proficiencyMilestone = Math.max(0, Math.min(3, proficiencyMilestone));
    }

    public void configure(
            ServerPlayer owner,
            Skill sourceSkill,
            float baseDamage,
            float targetMaxHealthDamageRatio,
            float abilityPower,
            float playerDamageMultiplier,
            boolean radiationEnabled,
            boolean destroysBlocks,
            int proficiencyMilestone
    ) {
        configure(owner, sourceSkill, baseDamage, targetMaxHealthDamageRatio,
                playerDamageMultiplier, radiationEnabled, destroysBlocks, proficiencyMilestone);
        this.abilityPower = Math.max(0.0f, abilityPower);
        powerScaledBase = true;
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
        builder.define(RAY_FIRED, false);
        builder.define(REFLECTION_ACTIVE, false);
        builder.define(REFLECTION_DISTANCE, 0.0f);
        builder.define(REFLECTION_RETURN_LENGTH, 0.0f);
        builder.define(REFLECTION_DIRECTION_X, 0.0f);
        builder.define(REFLECTION_DIRECTION_Y, 0.0f);
        builder.define(REFLECTION_DIRECTION_Z, 0.0f);
    }

    @Override
    public void tick() {
        super.tick();
        length = entityData.get(BEAM_LENGTH);

        if (level().isClientSide() && hasFired() && !fired) {
            currentChargerTicks = Math.max(currentChargerTicks, getAttackDelayTicks());
            fired = true;
        }

        if (isContinuous()) {
            currentChargerTicks = getAttackDelayTicks();
            currentRayLifeTicks = MAX_RAY_LIFE_TICKS;
            shouldStopRay = false;
            fired = true;
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        if (isHeldCharge() && !hasFired()) {
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
            var phaseX = (uuidHash % 1000) / 1000.0f * Mth.TWO_PI;
            var phaseY = (((float) uuidHash / 1000) % 1000) / 1000.0f * Mth.TWO_PI;
            var phaseZ = (((float) uuidHash / 1000000) % 1000) / 1000.0f * Mth.TWO_PI;

            var offsetX = Mth.sin(tickCount * frequency + phaseX) * amplitude;
            var offsetY = Mth.sin(tickCount * frequency * 2 + phaseY) * amplitude * 2;
            var offsetZ = Mth.cos(tickCount * frequency + phaseZ) * amplitude;

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
                fire(serverLevel, owner);
                markFired();
            }
        }
    }

    private void fire(ServerLevel level, ServerPlayer owner) {
        var start = position();
        var end = start.add(getLookAngle().scale(length));

        var hitIndex = new AtomicInteger();
        var source = SkillDamageSource.of(owner, sourceSkill);
        var payload = LinearAttackPayload
                .builder(owner, sourceSkill, source, 0.125f)
                .targetFilter(target -> target.getType() != getType())
                .outboundTargetFilter(target -> !target.getUUID().equals(ignoredTargetId)
                        && !owner.isAlliedTo(target))
                .damage(target -> {
                    var living = target instanceof LivingEntity entity ? entity : null;
                    var index = hitIndex.getAndIncrement();
                    if (index > (proficiencyMilestone >= 3 ? 1 : 0)) return 0.0f;
                    var marked = radiationEnabled && living != null
                            && RadiationIntensify
                            .isMarked(living, level.getGameTime());
                    var markMultiplier = Skills.RADIATION_INTENSIFY.get()
                            .hasProficiencyMilestone(owner, 2) ? 1.6f : 1.5f;
                    var hitBaseDamage = index == 0 ? baseDamage : baseDamage * 0.6f;
                    var hitMaxHealthRatio = index == 0 ? targetMaxHealthDamageRatio : 0.0f;
                    var targetMaxHealth = living == null ? 0.0f : living.getMaxHealth();
                    return powerScaledBase
                            ? MeltdownerBeamDamage
                            .calculatePowerScaledBase(hitBaseDamage, hitMaxHealthRatio, targetMaxHealth,
                                    abilityPower, playerDamageMultiplier, marked, markMultiplier)
                            : MeltdownerBeamDamage.calculate(
                            hitBaseDamage, hitMaxHealthRatio, targetMaxHealth,
                            playerDamageMultiplier, marked, markMultiplier);
                })
                .onHit((target, _, hurt) -> {
                    if (hurt && radiationEnabled && target instanceof LivingEntity living) {
                        RadiationIntensify
                                .mark(owner, living, level.getGameTime());
                    }
                })
                .build();
        var attack = LinearReflectionResolver.resolve(level, new LinearSegment(start, end), payload);

        if (destroysBlocks()) destroyBlocks(level, owner, attack.outbound());
        var outboundResult = LinearAttackExecutor.executeOutbound(level, attack, payload);
        if (destroysBlocks() && attack.isReflected()) {
            destroyBlocks(
                    level,
                    attack.reflectionCandidate().orElseThrow().reflector(),
                    attack.returnSegment().orElseThrow()
            );
        }
        if (attack.isReflected()) {
            setReflection(
                    (float) attack.outbound().length(),
                    (float) attack.returnVisualLength(),
                    attack.returnSegment().orElseThrow().direction()
            );
        } else clearReflection();
        LinearAttackExecutor.executeReturn(level, attack, payload, outboundResult);
        if (betaTrailOnFire) {
            spawnBetaTrail(level, attack);
            betaTrailOnFire = false;
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

    /**
     * Excludes one launch subject from this beam without making its allies immune.
     */
    public void setIgnoredTarget(Entity target) {
        ignoredTargetId = target == null ? null : target.getUUID();
    }

    public int getAttackDelayTicks() {
        return entityData.get(ATTACK_DELAY_TICKS);
    }

    public void setAttackDelayTicks(int ticks) {
        entityData.set(ATTACK_DELAY_TICKS, Mth.clamp(ticks, 0, 20 * 60));
    }

    public float getBeamLength() {
        return entityData.get(BEAM_LENGTH);
    }

    public void setBeamLength(float length) {
        var safeLength = Float.isFinite(length) ? Math.max(0.0f, length) : 0.0f;
        this.length = safeLength;
        entityData.set(BEAM_LENGTH, safeLength);
    }

    public float getBeamScale() {
        return entityData.get(BEAM_SCALE);
    }

    public void setBeamScale(float scale) {
        entityData.set(BEAM_SCALE, Float.isFinite(scale) ? Math.max(0.0f, scale) : 0.0f);
    }

    public float getVisualSideOffset() {
        return entityData.get(VISUAL_SIDE_OFFSET);
    }

    public void setVisualSideOffset(float offset) {
        entityData.set(VISUAL_SIDE_OFFSET, Float.isFinite(offset) ? offset : 0.0f);
    }

    public boolean isContinuous() {
        return entityData.get(CONTINUOUS);
    }

    public void setContinuous(boolean continuous) {
        entityData.set(CONTINUOUS, continuous);
    }

    public boolean isHeldCharge() {
        return entityData.get(HELD_CHARGE);
    }

    public void setHeldCharge(boolean heldCharge) {
        entityData.set(HELD_CHARGE, heldCharge);
    }

    public boolean destroysBlocks() {
        return entityData.get(DESTROYS_BLOCKS);
    }

    public void setBetaTrailOnFire(boolean betaTrailOnFire) {
        this.betaTrailOnFire = betaTrailOnFire;
    }

    public void setReflection(float distance) {
        setReflection(distance, getBeamLength(), getLookAngle().scale(-1.0));
    }

    public void setReflection(float distance, float returnLength) {
        setReflection(distance, returnLength, getLookAngle().scale(-1.0));
    }

    public void setReflection(float distance, float returnLength, Vec3 returnDirection) {
        if (!Float.isFinite(distance)
                || !Float.isFinite(returnLength)
                || returnDirection == null
                || !Double.isFinite(returnDirection.lengthSqr())
                || returnDirection.lengthSqr() < 1.0E-12) {
            clearReflection();
            return;
        }
        var safeDistance = Math.max(0.0f, distance);
        var safeReturnLength = Math.max(0.0f, returnLength);
        var normalizedDirection = returnDirection.normalize();
        entityData.set(REFLECTION_DISTANCE, safeDistance);
        entityData.set(REFLECTION_RETURN_LENGTH, safeReturnLength);
        entityData.set(REFLECTION_DIRECTION_X, (float) normalizedDirection.x);
        entityData.set(REFLECTION_DIRECTION_Y, (float) normalizedDirection.y);
        entityData.set(REFLECTION_DIRECTION_Z, (float) normalizedDirection.z);
        entityData.set(REFLECTION_ACTIVE, true);
    }

    public void clearReflection() {
        entityData.set(REFLECTION_ACTIVE, false);
        entityData.set(REFLECTION_DISTANCE, 0.0f);
        entityData.set(REFLECTION_RETURN_LENGTH, 0.0f);
        entityData.set(REFLECTION_DIRECTION_X, 0.0f);
        entityData.set(REFLECTION_DIRECTION_Y, 0.0f);
        entityData.set(REFLECTION_DIRECTION_Z, 0.0f);
    }

    public boolean isReflectionActive() {
        return entityData.get(REFLECTION_ACTIVE);
    }

    public float getReflectionDistance() {
        return entityData.get(REFLECTION_DISTANCE);
    }

    public float getReflectionReturnLength() {
        return entityData.get(REFLECTION_RETURN_LENGTH);
    }

    public Vec3 getReflectionReturnDirection() {
        return new Vec3(
                entityData.get(REFLECTION_DIRECTION_X),
                entityData.get(REFLECTION_DIRECTION_Y),
                entityData.get(REFLECTION_DIRECTION_Z)
        );
    }

    public boolean hasFired() {
        return entityData.get(RAY_FIRED);
    }

    private void markFired() {
        fired = true;
        entityData.set(RAY_FIRED, true);
    }
}
