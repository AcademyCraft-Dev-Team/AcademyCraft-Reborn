package org.academy.internal.common.world.entity.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.skills.DarkmatterCreation;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class DarkmatterBeetle extends Monster {
    private UUID ownerUUID;

    public DarkmatterBeetle(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    public Optional<UUID> getOwnerUUID() {
        return Optional.ofNullable(ownerUUID);
    }

    public void setOwnerUUID(UUID uuid) {
        ownerUUID = uuid;
    }

    public boolean isOwnedBy(ServerPlayer player) {
        return getOwnerUUID().filter(player.getUUID()::equals).isPresent();
    }

    @Nullable
    public ServerPlayer getOwnerPlayer() {
        if (!(level() instanceof ServerLevel serverLevel)) return null;
        return getOwnerUUID()
                .map(uuid -> serverLevel.getServer().getPlayerList().getPlayer(uuid))
                .orElse(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) return;
        var owner = getOwnerPlayer();
        if (owner == null || !owner.isAlive() || owner.hasDisconnected()
                || !Skills.DARKMATTER_CREATION.get().isEnabled(owner)) {
            discard();
            return;
        }

        if (distanceToSqr(owner) > 48 * 48) {
            teleportTo(owner.getX(), owner.getY(), owner.getZ());
        } else if (getTarget() == null && distanceToSqr(owner) > 5 * 5) {
            getNavigation().moveTo(owner, 1.1);
        }

        if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) heal(1.0f);

        if (getTarget() != null && !isCommandedTarget(owner, getTarget())) {
            setTarget(null);
        }
        if (tickCount % 10 == 0 && (getTarget() == null || !getTarget().isAlive())) {
            var target = serverLevel.getEntitiesOfClass(LivingEntity.class,
                            getBoundingBox().inflate(16), candidate -> isCommandedTarget(owner, candidate))
                    .stream()
                    .min(Comparator.comparingDouble(this::distanceToSqr))
                    .orElse(null);
            setTarget(target);
        }
    }

    private boolean isCommandedTarget(ServerPlayer owner, LivingEntity target) {
        if (target == this || target == owner || !target.isAlive()
                || target.isRemoved() || owner.isAlliedTo(target)) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return target == getLastHurtByMob()
                || target == owner.getLastHurtByMob()
                || target == owner.getLastHurtMob()
                || target instanceof Mob mob && mob.getTarget() == owner;
    }

    @Override
    public boolean doHurtTarget(ServerLevel serverLevel, Entity entity) {
        if (!(entity instanceof LivingEntity target)) return false;
        var owner = getOwnerPlayer();
        if (owner == null || !isCommandedTarget(owner, target)) return false;
        var multiplier = AbilitySystemServer.getSystem(owner)
                .getPlayerDamageMultiplier(owner.getUUID());
        var hurt = target.hurtServer(serverLevel,
                SkillDamageSource.of(owner, Skills.DARKMATTER_DISASSEMBLE.get()),
                12.0f * multiplier);
        if (hurt) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    getX(), getY() + 0.25, getZ(), 4, 0.1, 0.1, 0.1, 0.01);
        }
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMITE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDERMITE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMITE_DEATH;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            var owner = getOwnerPlayer();
            if (owner != null) DarkmatterCreation.Server.removeOwned(owner, getUUID());
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getString("academy_owner").ifPresent(value -> {
            try {
                setOwnerUUID(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        getOwnerUUID().ifPresent(uuid -> output.putString("academy_owner", uuid.toString()));
    }
}
