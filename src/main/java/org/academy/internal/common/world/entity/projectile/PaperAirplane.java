package org.academy.internal.common.world.entity.projectile;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.SkillNames;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.aeromanip.AeromanipConfig;
import org.academy.internal.common.ability.aeromanip.AeromanipTargeting;
import org.academy.internal.common.ability.aeromanip.AeromanipVfx;
import org.academy.internal.common.world.entity.EntityTypes;
import org.academy.internal.common.world.item.Items;

import java.util.UUID;

/** Slow reusable paper projectile that can be accelerated by Airflow Jet. */
public final class PaperAirplane extends AbstractArrow implements ItemSupplier {
    public static final float BOOSTED_DAMAGE = 8.0f;
    public static final double BOOSTED_SPEED = 2.4;
    private static final int ATTACHED_LIFETIME_TICKS = 1_200;
    private static final EntityDataAccessor<Integer> ATTACHED_ENTITY_ID =
            SynchedEntityData.defineId(PaperAirplane.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ATTACHMENT_X =
            SynchedEntityData.defineId(PaperAirplane.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ATTACHMENT_Y =
            SynchedEntityData.defineId(PaperAirplane.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ATTACHMENT_Z =
            SynchedEntityData.defineId(PaperAirplane.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BOOSTED =
            SynchedEntityData.defineId(PaperAirplane.class, EntityDataSerializers.BOOLEAN);

    private UUID attachedEntityUuid;
    private UUID boosterUuid;
    private int attachedTicks;

    public PaperAirplane(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        pickup = Pickup.ALLOWED;
        setNoGravity(true);
    }

    public PaperAirplane(Level level, LivingEntity owner) {
        super(EntityTypes.PAPER_AIRPLANE.get(), owner, level,
                new ItemStack(Items.PAPER_AIRPLANE.get()), null);
        pickup = Pickup.ALLOWED;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACHED_ENTITY_ID, -1);
        builder.define(ATTACHMENT_X, 0.0f);
        builder.define(ATTACHMENT_Y, 0.0f);
        builder.define(ATTACHMENT_Z, 0.0f);
        builder.define(BOOSTED, false);
    }

    public boolean boost(ServerPlayer booster, Vec3 direction) {
        if (booster == null || booster.isRemoved() || attachedEntityId() >= 0) return false;
        var resolvedDirection = direction != null && direction.lengthSqr() > 1.0e-8
                ? direction.normalize() : booster.getLookAngle();
        boosterUuid = booster.getUUID();
        entityData.set(BOOSTED, true);
        setNoPhysics(false);
        setInGround(false);
        setNoGravity(true);
        setDeltaMovement(resolvedDirection.scale(BOOSTED_SPEED));
        return true;
    }

    public boolean isBoosted() {
        return entityData.get(BOOSTED);
    }

    @Override
    public void tick() {
        var attached = resolveAttachedEntity();
        if (attached != null) {
            setNoPhysics(true);
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
            super.tick();
            setPos(attached.position().add(attachmentOffset()));
            if (!level().isClientSide() && ++attachedTicks >= ATTACHED_LIFETIME_TICKS) {
                dropAndDiscard();
            }
            return;
        }
        if (!level().isClientSide() && attachedEntityUuid != null) {
            dropAndDiscard();
            return;
        }
        super.tick();
        if (isBoosted() && level() instanceof ServerLevel serverLevel && tickCount % 5 == 0) {
            var trailDirection = getDeltaMovement().lengthSqr() > 1.0e-8
                    ? getDeltaMovement().normalize().scale(-1.0)
                    : new Vec3(0, 1, 0);
            AeromanipVfx.stream(serverLevel, position(), trailDirection, 0.8);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(result.getEntity() instanceof LivingEntity target)) return;
        if (level() instanceof ServerLevel serverLevel && isBoosted()) {
            var booster = boosterUuid == null ? null : serverLevel.getServer()
                    .getPlayerList().getPlayer(boosterUuid);
            if (booster != null && AeromanipTargeting.canAffectNegatively(booster, target)) {
                var damage = BOOSTED_DAMAGE
                        * AeromanipConfig.damageMultiplier(booster, SkillNames.AIRFLOW_JET)
                        * AbilitySystemServer.getSystem(booster)
                        .getPlayerDamageMultiplier(booster.getUUID());
                target.hurtServer(serverLevel,
                        SkillDamageSource.of(booster, Skills.AIRFLOW_JET.get()), damage);
            }
        }
        attachTo(target, result.getLocation());
    }

    private void attachTo(LivingEntity target, Vec3 hitLocation) {
        var offset = hitLocation.subtract(target.position());
        attachedEntityUuid = target.getUUID();
        entityData.set(ATTACHED_ENTITY_ID, target.getId());
        entityData.set(ATTACHMENT_X, (float) offset.x);
        entityData.set(ATTACHMENT_Y, (float) offset.y);
        entityData.set(ATTACHMENT_Z, (float) offset.z);
        pickup = Pickup.DISALLOWED;
        setNoPhysics(true);
        setInGround(false);
        setDeltaMovement(Vec3.ZERO);
    }

    private Entity resolveAttachedEntity() {
        var id = attachedEntityId();
        var attached = id < 0 ? null : level().getEntity(id);
        if (attached != null && !attached.isRemoved()) return attached;
        if (!(level() instanceof ServerLevel serverLevel) || attachedEntityUuid == null) return null;
        attached = serverLevel.getEntity(attachedEntityUuid);
        if (attached != null && !attached.isRemoved()) {
            entityData.set(ATTACHED_ENTITY_ID, attached.getId());
            return attached;
        }
        return null;
    }

    private int attachedEntityId() {
        return entityData.get(ATTACHED_ENTITY_ID);
    }

    private Vec3 attachmentOffset() {
        return new Vec3(entityData.get(ATTACHMENT_X),
                entityData.get(ATTACHMENT_Y), entityData.get(ATTACHMENT_Z));
    }

    private void dropAndDiscard() {
        if (level() instanceof ServerLevel serverLevel) {
            spawnAtLocation(serverLevel, getDefaultPickupItem(), 0.1f);
        }
        discard();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.PAPER_AIRPLANE.get());
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return getItem();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getString("academy_attached_entity").ifPresent(value ->
                attachedEntityUuid = parseUuid(value));
        input.getString("academy_booster").ifPresent(value -> boosterUuid = parseUuid(value));
        entityData.set(ATTACHMENT_X, input.getIntOr("academy_attachment_x_milli", 0) / 1_000.0f);
        entityData.set(ATTACHMENT_Y, input.getIntOr("academy_attachment_y_milli", 0) / 1_000.0f);
        entityData.set(ATTACHMENT_Z, input.getIntOr("academy_attachment_z_milli", 0) / 1_000.0f);
        entityData.set(BOOSTED, input.getBooleanOr("academy_boosted", false));
        attachedTicks = Math.max(0, input.getIntOr("academy_attached_ticks", 0));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (attachedEntityUuid != null) output.putString("academy_attached_entity", attachedEntityUuid.toString());
        if (boosterUuid != null) output.putString("academy_booster", boosterUuid.toString());
        var offset = attachmentOffset();
        output.putInt("academy_attachment_x_milli", (int) Math.round(offset.x * 1_000.0));
        output.putInt("academy_attachment_y_milli", (int) Math.round(offset.y * 1_000.0));
        output.putInt("academy_attachment_z_milli", (int) Math.round(offset.z * 1_000.0));
        output.putBoolean("academy_boosted", isBoosted());
        output.putInt("academy_attached_ticks", attachedTicks);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
