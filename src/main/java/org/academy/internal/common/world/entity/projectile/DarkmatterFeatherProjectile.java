package org.academy.internal.common.world.entity.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.DarkmatterTargeting;
import org.academy.internal.common.world.item.Items;

import java.util.UUID;

/**
 * Short-lived, server-owned feather blade used by Dark Matter Interference.
 */
public final class DarkmatterFeatherProjectile extends AbstractArrow implements ItemSupplier {
    private UUID targetId;
    private float damage = 1.0f;
    private float exposureBurstDamage;
    private int maximumLifetime = 40;

    public DarkmatterFeatherProjectile(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        pickup = Pickup.DISALLOWED;
        setNoGravity(true);
    }

    public void configure(
            ServerPlayer owner,
            LivingEntity target,
            Vec3 direction,
            float damage,
            float exposureBurstDamage
    ) {
        setOwner(owner);
        targetId = target == null ? null : target.getUUID();
        this.damage = Math.max(0.0f, Float.isFinite(damage) ? damage : 0.0f);
        this.exposureBurstDamage = Math.max(
                0.0f, Float.isFinite(exposureBurstDamage) ? exposureBurstDamage : 0.0f);
        snapTo(owner.getX(), owner.getEyeY() - 0.15, owner.getZ(),
                owner.getYRot(), owner.getXRot());
        var initial = target == null
                ? direction
                : target.getBoundingBox().getCenter().subtract(position());
        if (initial == null || initial.lengthSqr() < 1.0e-8) {
            initial = owner.getLookAngle();
        }
        var velocity = initial.normalize().scale(1.65);
        setDeltaMovement(velocity);
    }

    @Override
    public void tick() {
        if (!level().isClientSide() && targetId != null) {
            if (!(level().getEntity(targetId) instanceof LivingEntity target)
                    || !target.isAlive() || target.isRemoved()) {
                // A targeted feather belongs to that attack. Letting it continue after its
                // target disappears makes it damage an unrelated entity that happens to enter
                // the old trajectory, which also breaks per-target exposure accounting.
                discard();
                return;
            }
            var desired = target.getBoundingBox().getCenter().subtract(position());
            if (desired.lengthSqr() > 1.0e-8) {
                var blended = getDeltaMovement().normalize().scale(0.65)
                        .add(desired.normalize().scale(0.35));
                if (blended.lengthSqr() > 1.0e-8) {
                    setDeltaMovement(blended.normalize().scale(1.65));
                }
            }
        }
        super.tick();
        if (!level().isClientSide() && tickCount >= maximumLifetime) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel level)
                || !(getOwner() instanceof ServerPlayer owner)
                || !(result.getEntity() instanceof LivingEntity target)
                || !DarkmatterTargeting.isAttackableBy(owner, target)) {
            discard();
            return;
        }
        var source = SkillDamageSource.of(owner, Skills.DARKMATTER_RADIATION.get());
        target.invulnerableTime = 0;
        var hit = damage > 0.0f && DarkmatterTargeting.hurt(level, target, source, damage);
        if (exposureBurstDamage > 0.0f && target.isAlive()) {
            target.invulnerableTime = 0;
            hit |= DarkmatterTargeting.hurt(level, target, source, exposureBurstDamage);
        }
        if (hit) Skills.DARKMATTER_RADIATION.get().reportActivity(owner, true);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!level().isClientSide()) discard();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.DARKMATTER_FEATHER.get());
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return getItem();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getString("academy_target").ifPresent(value -> {
            try {
                targetId = UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                targetId = null;
            }
        });
        damage = input.getIntOr("academy_damage_milli", 1_000) / 1_000.0f;
        exposureBurstDamage = input.getIntOr(
                "academy_exposure_burst_milli", 0) / 1_000.0f;
        maximumLifetime = Math.max(5, input.getIntOr("academy_maximum_lifetime", 40));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (targetId != null) output.putString("academy_target", targetId.toString());
        output.putInt("academy_damage_milli", Math.round(damage * 1_000.0f));
        output.putInt("academy_exposure_burst_milli",
                Math.round(exposureBurstDamage * 1_000.0f));
        output.putInt("academy_maximum_lifetime", maximumLifetime);
    }
}
