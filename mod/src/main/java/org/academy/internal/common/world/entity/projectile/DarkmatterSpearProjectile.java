package org.academy.internal.common.world.entity.projectile;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.academy.AcademyCraft;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.DarkmatterTargeting;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterShaping;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.world.item.Items;

/**
 * Server-authoritative shaped spear projection; gamma launches return after impact.
 */
public final class DarkmatterSpearProjectile extends AbstractArrow implements ItemSupplier {
    private static final Identifier PENETRATION_ID =
            AcademyCraft.academy("darkmatter_spear_projectile_penetration");
    private float phaseDamage = 5.0f;
    private float penetration;
    private float gammaPower;
    private float betaPower;
    private float launchSpeed = 1.5f;
    private int shapingMilestone;
    private int sixWingsMilestone;
    private int maximumLifetime = 20;
    private boolean returning;

    public DarkmatterSpearProjectile(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        pickup = Pickup.DISALLOWED;
        setNoGravity(true);
    }

    public void configure(ServerPlayer owner, float alphaPower, float betaPower, float gammaPower,
                          int shapingMilestone, int sixWingsMilestone) {
        setOwner(owner);
        this.phaseDamage = DarkmatterShaping.Server.spearDamage(alphaPower);
        this.betaPower = Float.isFinite(betaPower) ? Math.max(0.0f, betaPower) : 0.0f;
        this.penetration = DarkmatterShaping.Server.spearPenetration(this.betaPower);
        this.gammaPower = Math.max(0.0f, gammaPower);
        this.shapingMilestone = Math.clamp(shapingMilestone, 0, 3);
        this.sixWingsMilestone = Math.clamp(sixWingsMilestone, 0, 3);
        this.launchSpeed = DarkmatterShaping.Server.spearSpeed(this.betaPower);
        var range = DarkmatterShaping.Server.spearRange(alphaPower);
        this.maximumLifetime = Math.max(2, Mth.ceil(range / launchSpeed));
        snapTo(owner.getX(), owner.getEyeY() - 0.12, owner.getZ(), owner.getYRot(), owner.getXRot());
        shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0f, launchSpeed, 0.0f);
    }

    @Override
    public void tick() {
        if (returning) {
            var owner = getOwner();
            if (owner == null || !owner.isAlive()) {
                discard();
                return;
            }
            var delta = owner.getEyePosition().subtract(position());
            if (delta.lengthSqr() <= 1.0) {
                discard();
                return;
            }
            setDeltaMovement(delta.normalize().scale(Math.max(1.8f, launchSpeed * 1.15f)));
        }
        super.tick();
        if (!returning && tickCount >= maximumLifetime) {
            if (gammaPower > 0.0f) beginReturn();
            else discard();
        } else if (returning && tickCount >= maximumLifetime + 40) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (returning || !(level() instanceof ServerLevel level)
                || !(getOwner() instanceof ServerPlayer owner)
                || !(result.getEntity() instanceof LivingEntity target)
                || !DarkmatterTargeting.isAttackableBy(owner, target)) return;
        var source = SkillDamageSource.of(owner, Skills.DARKMATTER_SHAPING.get());
        hurtWithPenetration(level, target, source, phaseDamage, penetration);
        if (gammaPower > 0.0f) {
            pursueNearby(level, owner, target, source);
            beginReturn();
        } else discard();
        Skills.DARKMATTER_SHAPING.get().reportActivity(owner, true);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (level().isClientSide()) return;
        if (gammaPower > 0.0f) beginReturn();
        else discard();
    }

    private void beginReturn() {
        returning = true;
        setNoPhysics(true);
        setInGround(false);
    }

    private void pursueNearby(ServerLevel level, ServerPlayer owner, LivingEntity primary,
                              DamageSource source) {
        var shapingGamma = DarkmatterShaping.Server.gammaShapingMultiplier(shapingMilestone);
        var count = 1 + (int) Math.floor(gammaPower * shapingGamma);
        var damageMultiplier = DarkmatterSixWings.Server.gammaMagnitudeMultiplier(sixWingsMilestone);
        var damage = (1.0f + 0.5f * gammaPower * shapingGamma) * damageMultiplier;
        var pursuitRange = 8.0
                * DarkmatterSixWings.Server.areaMultiplier(sixWingsMilestone);
        var processed = 0;
        for (var nearby : level.getEntitiesOfClass(LivingEntity.class,
                primary.getBoundingBox().inflate(pursuitRange), candidate -> candidate != primary
                        && DarkmatterTargeting.isAttackableBy(owner, candidate))) {
            if (processed++ >= count) break;
            hurtWithPenetration(level, nearby, source, damage, penetration);
        }
    }

    private static boolean hurtWithPenetration(ServerLevel level, LivingEntity target,
                                               DamageSource source,
                                               float damage, float penetration) {
        var armor = target.getAttribute(Attributes.ARMOR);
        if (armor == null || penetration <= 0.0f) {
            return DarkmatterTargeting.hurt(level, target, source, damage);
        }
        var existing = armor.getModifier(PENETRATION_ID);
        if (existing != null) armor.removeModifier(PENETRATION_ID);
        armor.addTransientModifier(new AttributeModifier(PENETRATION_ID,
                -Math.clamp(penetration, 0.0f, 0.50f),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        try {
            return DarkmatterTargeting.hurt(level, target, source, damage);
        } finally {
            armor.removeModifier(PENETRATION_ID);
            if (existing != null) armor.addTransientModifier(existing);
        }
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.DARKMATTER_SPEAR.get());
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return getItem();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        phaseDamage = input.getIntOr("academy_damage_milli", 5000) / 1000.0f;
        penetration = input.getIntOr("academy_penetration_milli", 0) / 1000.0f;
        gammaPower = input.getIntOr("academy_gamma_milli", 0) / 1000.0f;
        betaPower = input.getIntOr("academy_beta_milli", 0) / 1000.0f;
        launchSpeed = input.getIntOr("academy_speed_milli", 1500) / 1000.0f;
        shapingMilestone = Math.clamp(input.getIntOr("academy_shaping_milestone", 0), 0, 3);
        sixWingsMilestone = Math.clamp(input.getIntOr("academy_six_wings_milestone", 0), 0, 3);
        maximumLifetime = Math.max(5, input.getIntOr("academy_maximum_lifetime", 20));
        returning = input.getIntOr("academy_returning", 0) != 0;
        if (returning) setNoPhysics(true);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("academy_damage_milli", Math.round(phaseDamage * 1000.0f));
        output.putInt("academy_penetration_milli", Math.round(penetration * 1000.0f));
        output.putInt("academy_gamma_milli", Math.round(gammaPower * 1000.0f));
        output.putInt("academy_beta_milli", Math.round(betaPower * 1000.0f));
        output.putInt("academy_speed_milli", Math.round(launchSpeed * 1000.0f));
        output.putInt("academy_shaping_milestone", shapingMilestone);
        output.putInt("academy_six_wings_milestone", sixWingsMilestone);
        output.putInt("academy_maximum_lifetime", maximumLifetime);
        output.putInt("academy_returning", returning ? 1 : 0);
    }
}
