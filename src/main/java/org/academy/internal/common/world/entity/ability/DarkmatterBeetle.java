package org.academy.internal.common.world.entity.ability;

import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.darkmatter.DarkmatterCreaturePartType;
import org.academy.api.common.ability.darkmatter.DarkmatterCreatureRegistries;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.DarkmatterTargeting;
import org.academy.internal.common.ability.darkmatter.creature.DarkmatterCreatureBlueprint;
import org.academy.internal.common.ability.darkmatter.skills.lv4.DarkmatterCreation;
import org.academy.internal.common.ability.level0.skills.OutputControl;
import org.academy.internal.common.world.entity.EntityTypes;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * One entity type whose synchronized model branches and behavior are defined by a saved blueprint snapshot.
 */
public final class DarkmatterBeetle extends Monster {
    private static final Identifier PENETRATION_ID =
            AcademyCraft.academy("darkmatter_creature_penetration");
    private static final int MAX_CARGO_SLOTS = 27;
    private static final EntityDataAccessor<Integer> HEAD_MODEL = SynchedEntityData.defineId(
            DarkmatterBeetle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TORSO_MODEL = SynchedEntityData.defineId(
            DarkmatterBeetle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIMBS_MODEL = SynchedEntityData.defineId(
            DarkmatterBeetle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ADDITIONAL_MODEL = SynchedEntityData.defineId(
            DarkmatterBeetle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GAMMA_CATALYZED = SynchedEntityData.defineId(
            DarkmatterBeetle.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEGRADED = SynchedEntityData.defineId(
            DarkmatterBeetle.class, EntityDataSerializers.BOOLEAN);

    private UUID ownerUUID;
    private int blueprintVersion = DarkmatterCreatureBlueprint.VERSION;
    private int blueprintSlot;
    private int investment = 5;
    private int abilityLevel = 1;
    private int proficiencyMilestone;
    private String head = DarkmatterCreatureRegistries.HEAD_JAW.toString();
    private String torso = DarkmatterCreatureRegistries.TORSO_WALK.toString();
    private String limbs = DarkmatterCreatureRegistries.LIMBS_GUARD.toString();
    private String additional = DarkmatterCreatureRegistries.ADDITIONAL_NONE.toString();
    private int headAlpha = 25;
    private int torsoAlpha = 25;
    private int limbsAlpha = 25;
    private int additionalAlpha = 25;
    private List<String> modules = List.of();
    private float averageGammaPower;
    private int outOfRangeTicks;
    private int outOfCombatTicks;
    private UUID recentHeadTarget;
    private int recentHeadAttackTick = Integer.MIN_VALUE;
    private int nextHeadAttackTick;
    private int nextExcavationTick;
    private boolean outputAdjustmentBypassed;
    private NonNullList<ItemStack> cargo = NonNullList.withSize(MAX_CARGO_SLOTS, ItemStack.EMPTY);

    public DarkmatterBeetle(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.204)
                .add(Attributes.ATTACK_DAMAGE, 2.4)
                .add(Attributes.ARMOR, 0.5)
                .add(Attributes.FOLLOW_RANGE, 17.0)
                .add(Attributes.FLYING_SPEED, 0.32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HEAD_MODEL, 0);
        builder.define(TORSO_MODEL, 0);
        builder.define(LIMBS_MODEL, 0);
        builder.define(ADDITIONAL_MODEL, 0);
        builder.define(GAMMA_CATALYZED, false);
        builder.define(DEGRADED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    public void applyBlueprint(DarkmatterCreatureBlueprint blueprint, int slot, int level,
                               int milestone, boolean gammaCatalyzed) {
        blueprintVersion = blueprint.version();
        blueprintSlot = Math.clamp(slot, 0, 3);
        investment = blueprint.investment();
        abilityLevel = Math.clamp(level, 1, 5);
        proficiencyMilestone = Math.clamp(milestone, 0, 3);
        head = blueprint.head();
        torso = blueprint.torso();
        limbs = blueprint.limbs();
        additional = blueprint.additional();
        headAlpha = blueprint.headAlpha();
        torsoAlpha = blueprint.torsoAlpha();
        limbsAlpha = blueprint.limbsAlpha();
        additionalAlpha = blueprint.additionalAlpha();
        modules = blueprint.modules();
        averageGammaPower = gammaCatalyzed ? blueprint.averageGammaPower(abilityLevel) : 0.0f;
        outputAdjustmentBypassed = OutputControl.isOutputAdjustmentBypassed();
        entityData.set(GAMMA_CATALYZED, gammaCatalyzed);
        syncModels();
        configureNavigation();
        applyAttributes(blueprint);
        setCustomName(Component.literal(blueprint.name()));
        applyDegradedName();
        setCustomNameVisible(false);
    }

    private void applyAttributes(DarkmatterCreatureBlueprint blueprint) {
        var stats = blueprint.createBaseStats(proficiencyMilestone, abilityLevel);
        setAttribute(Attributes.MAX_HEALTH, stats.maxHealth);
        setAttribute(Attributes.ATTACK_DAMAGE, stats.attackDamage);
        setAttribute(Attributes.ARMOR, stats.armor);
        setAttribute(Attributes.MOVEMENT_SPEED, stats.movementSpeed);
        setAttribute(Attributes.FLYING_SPEED, Math.max(0.32,
                stats.movementSpeed * (1.55 + 0.08 * betaPower(torsoAlpha))));
        setAttribute(Attributes.FOLLOW_RANGE, stats.followRange
                * DarkmatterCreation.targetingRange(proficiencyMilestone));
        setAttribute(Attributes.KNOCKBACK_RESISTANCE,
                torso.equals(DarkmatterCreatureRegistries.TORSO_WALK.toString())
                        ? Math.min(0.85, 0.12 * alphaPower(torsoAlpha)) : 0.0);
        setHealth(getMaxHealth());
    }

    private void configureNavigation() {
        if (torso.equals(DarkmatterCreatureRegistries.TORSO_FLY.toString())) {
            navigation = new FlyingPathNavigation(this, level());
            moveControl = new FlyingMoveControl<>(this, 35, true);
        } else if (torso.equals(DarkmatterCreatureRegistries.TORSO_SWIM.toString())) {
            navigation = new AmphibiousPathNavigation(this, level());
            moveControl = new MoveControl<>(this);
        } else {
            navigation = new GroundPathNavigation(this, level());
            moveControl = new MoveControl<>(this);
        }
    }

    private void setAttribute(Holder<Attribute> key,
                              double value) {
        var attribute = getAttribute(key);
        if (attribute != null) attribute.setBaseValue(Math.max(0.0, value));
    }

    private void syncModels() {
        entityData.set(HEAD_MODEL, modelId(head, DarkmatterCreaturePartType.BodySlot.HEAD));
        entityData.set(TORSO_MODEL, modelId(torso, DarkmatterCreaturePartType.BodySlot.TORSO));
        entityData.set(LIMBS_MODEL, modelId(limbs, DarkmatterCreaturePartType.BodySlot.LIMBS));
        entityData.set(ADDITIONAL_MODEL, modelId(additional, DarkmatterCreaturePartType.BodySlot.ADDITIONAL));
        entityData.set(DEGRADED,
                modelId(head, DarkmatterCreaturePartType.BodySlot.HEAD) < 0
                        || modelId(torso, DarkmatterCreaturePartType.BodySlot.TORSO) < 0
                        || modelId(limbs, DarkmatterCreaturePartType.BodySlot.LIMBS) < 0
                        || modelId(additional, DarkmatterCreaturePartType.BodySlot.ADDITIONAL) < 0
                        || modules.stream().anyMatch(module -> {
                    var id = Identifier.tryParse(module);
                    return id == null || DarkmatterCreatureRegistries.module(id).isEmpty();
                }));
    }

    private static int modelId(String raw, DarkmatterCreaturePartType.BodySlot slot) {
        var id = Identifier.tryParse(raw);
        return id == null ? -1 : DarkmatterCreatureRegistries.part(id)
                .filter(type -> type.slot() == slot).map(DarkmatterCreaturePartType::clientModelId).orElse(-1);
    }

    private void applyDegradedName() {
        if (getCustomName() == null) return;
        var value = getCustomName().getString();
        if (degraded() && !value.endsWith(" [DEGRADED]")) {
            setCustomName(getCustomName().copy().append(" [DEGRADED]"));
        } else if (!degraded() && value.endsWith(" [DEGRADED]")) {
            setCustomName(Component.literal(value.substring(0,
                    value.length() - " [DEGRADED]".length())));
        }
    }

    public int headModel() {
        return entityData.get(HEAD_MODEL);
    }

    public int torsoModel() {
        return entityData.get(TORSO_MODEL);
    }

    public int limbsModel() {
        return entityData.get(LIMBS_MODEL);
    }

    public int additionalModel() {
        return entityData.get(ADDITIONAL_MODEL);
    }

    public boolean gammaCatalyzed() {
        return entityData.get(GAMMA_CATALYZED);
    }

    public boolean degraded() {
        return entityData.get(DEGRADED);
    }

    public int investment() {
        return investment;
    }

    public int blueprintSlot() {
        return blueprintSlot;
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

    public boolean isOwnerAlly(Entity entity) {
        var owner = getOwnerPlayer();
        if (owner == null || entity == null) return false;
        if (entity == owner || TeamRelations.areAllied(owner, entity)) return true;
        return entity instanceof DarkmatterBeetle beetle
                && beetle.getOwnerUUID().filter(owner.getUUID()::equals).isPresent();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isOwnerAlly(target) && super.canAttack(target);
    }

    @Nullable
    public ServerPlayer getOwnerPlayer() {
        if (!(level() instanceof ServerLevel serverLevel)) return null;
        return getOwnerUUID().map(uuid -> serverLevel.getServer().getPlayerList().getPlayer(uuid))
                .orElse(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) return;
        var owner = getOwnerPlayer();
        if (owner == null || !owner.isAlive() || owner.hasDisconnected()
                || !Skills.DARKMATTER_CREATION.get().isEnabled(owner)
                || !DarkmatterCreation.Server.isRecorded(owner, getUUID())) {
            discard();
            return;
        }

        if (owner.level() != level()) {
            if (owner.level() instanceof ServerLevel destination) {
                teleportTo(destination, owner.getX(), owner.getY(), owner.getZ(),
                        Set.of(), getYRot(), getXRot(), false);
            }
            return;
        }

        var flying = torso.equals(DarkmatterCreatureRegistries.TORSO_FLY.toString());
        setNoGravity(flying);
        if (torso.equals(DarkmatterCreatureRegistries.TORSO_SWIM.toString()) && isInWater()) {
            var boost = 1.0 + 0.025 * betaPower(torsoAlpha);
            setDeltaMovement(getDeltaMovement().multiply(boost, 1.0 + (boost - 1.0) * 0.5, boost));
        }

        if (distanceToSqr(owner) > 48 * 48) outOfRangeTicks++;
        else outOfRangeTicks = 0;
        if (outOfRangeTicks >= DarkmatterCreation.stuckTeleportTicks(proficiencyMilestone)) {
            teleportTo(owner.getX(), owner.getY(), owner.getZ());
            outOfRangeTicks = 0;
        }

        if (getTarget() != null && !isCommandedTarget(owner, getTarget())) setTarget(null);
        if (tickCount % 10 == 0 && (getTarget() == null || !getTarget().isAlive())) findTarget(serverLevel, owner);
        var combatTarget = getTarget();
        if (combatTarget == null) {
            outOfCombatTicks++;
            followOwner(owner, flying);
        } else {
            outOfCombatTicks = 0;
            updateCombatMovement(combatTarget, flying);
            if (head.equals(DarkmatterCreatureRegistries.HEAD_JAW.toString())
                    && tickCount >= nextHeadAttackTick
                    && distanceToSqr(combatTarget) <= meleeReachSqr(combatTarget)) {
                doHurtTarget(serverLevel, combatTarget);
                nextHeadAttackTick = tickCount + meleeCooldownTicks();
            }
        }

        if (tickCount % 20 == 0) {
            runModules(serverLevel, owner);
            if (isCarrier()) {
                collectNearbyItems(serverLevel);
                transferCargo(owner);
            }
            if (isExcavator()) assistOwnerExcavation(serverLevel, owner);
            if (modules.contains(DarkmatterCreatureRegistries.MODULE_SELF_REPAIR.toString())
                    && outOfCombatTicks >= 100 && getHealth() < getMaxHealth()) {
                heal(getMaxHealth() * 0.01f * DarkmatterCreation.moduleValueMultiplier(proficiencyMilestone));
            }
            if (additional.equals(DarkmatterCreatureRegistries.ADDITIONAL_CARAPACE.toString())
                    && outOfCombatTicks >= 100 && getHealth() < getMaxHealth()) {
                heal(getMaxHealth() * 0.006f * betaPower(additionalAlpha));
            }
        }

        if (combatTarget != null
                && modelId(head, DarkmatterCreaturePartType.BodySlot.HEAD) >= 0
                && !head.equals(DarkmatterCreatureRegistries.HEAD_JAW.toString())) {
            var beta = betaPower(headAlpha);
            var cooldown = Math.max(8, Math.round((head.contains("homing") ? 30 : 20)
                    / (1.0f + 0.10f * beta)
                    * (gammaCatalyzed() ? Math.max(0.5f, 1.0f - 0.05f * averageGammaPower) : 1.0f)));
            var homing = head.equals(DarkmatterCreatureRegistries.HEAD_HOMING.toString());
            if (tickCount >= nextHeadAttackTick && (homing || hasLineOfSight(combatTarget))) {
                rangedAttack(serverLevel, owner, combatTarget, false);
                nextHeadAttackTick = tickCount + cooldown;
            }
        }
        if (gammaCatalyzed() && recentHeadTarget != null
                && tickCount - recentHeadAttackTick >= DarkmatterCreation.gammaRepeatTicks(proficiencyMilestone)) {
            var target = serverLevel.getEntity(recentHeadTarget);
            if (target instanceof LivingEntity living && living.isAlive())
                rangedAttack(serverLevel, owner, living, true);
            recentHeadAttackTick = tickCount;
        }
    }

    private void followOwner(ServerPlayer owner, boolean flying) {
        var followDistance = modules.contains(DarkmatterCreatureRegistries.MODULE_FORMATION.toString())
                ? 3.0 : 5.0;
        if (distanceToSqr(owner) <= followDistance * followDistance) {
            getNavigation().stop();
            return;
        }
        var destination = owner.position();
        if (modules.contains(DarkmatterCreatureRegistries.MODULE_FORMATION.toString())) {
            var angle = (getUUID().hashCode() & 0xffff) / 65535.0 * Math.PI * 2.0;
            destination = destination.add(Math.cos(angle) * 2.5,
                    flying ? 2.0 + (getUUID().hashCode() & 3) * 0.45 : 0.0,
                    Math.sin(angle) * 2.5);
        } else if (flying) {
            destination = destination.add(0.0, 2.0, 0.0);
        }
        getNavigation().moveTo(destination.x, destination.y, destination.z,
                flying ? flightSpeed() : DarkmatterCreation.followSpeed(proficiencyMilestone));
    }

    private void updateCombatMovement(LivingEntity target, boolean flying) {
        if (head.equals(DarkmatterCreatureRegistries.HEAD_JAW.toString())) {
            var y = flying ? target.getEyeY() : target.getY();
            getNavigation().moveTo(target.getX(), y, target.getZ(),
                    flying ? flightSpeed() : 1.15 * DarkmatterCreation.followSpeed(proficiencyMilestone));
            return;
        }
        var beta = betaPower(headAlpha);
        var homing = head.equals(DarkmatterCreatureRegistries.HEAD_HOMING.toString());
        var preferred = (homing ? 14.0 : 11.0) + 2.0 * beta;
        var offset = position().subtract(target.position());
        var horizontal = new Vec3(offset.x, 0.0, offset.z);
        if (horizontal.lengthSqr() < 1.0e-6) horizontal = new Vec3(1.0, 0.0, 0.0);
        var radial = horizontal.normalize();
        Vec3 destination;
        var distance = Math.sqrt(distanceToSqr(target));
        if (distance < preferred * 0.72) {
            destination = target.position().add(radial.scale(preferred));
        } else if (distance > preferred * 1.18) {
            destination = target.position().add(radial.scale(preferred * 0.92));
        } else {
            var orbitDirection = ((getUUID().hashCode() & 1) == 0 ? 1.0 : -1.0);
            var tangent = new Vec3(-radial.z, 0.0, radial.x).scale(orbitDirection * 3.0);
            destination = target.position().add(radial.scale(preferred)).add(tangent);
        }
        if (flying) destination = new Vec3(destination.x, target.getEyeY() + 2.0, destination.z);
        getNavigation().moveTo(destination.x, destination.y, destination.z,
                flying ? flightSpeed() : 1.05 * DarkmatterCreation.followSpeed(proficiencyMilestone));
        getLookControl().setLookAt(target, 30.0f, 30.0f);
    }

    private double flightSpeed() {
        return (1.45 + 0.16 * betaPower(torsoAlpha))
                * DarkmatterCreation.followSpeed(proficiencyMilestone);
    }

    private double meleeReachSqr(LivingEntity target) {
        var reach = getBbWidth() * 1.6 + target.getBbWidth() + 0.8;
        return reach * reach;
    }

    private int meleeCooldownTicks() {
        var beta = betaPower(headAlpha);
        if (limbs.equals(DarkmatterCreatureRegistries.LIMBS_GUARD.toString())) {
            beta += 0.5f * betaPower(limbsAlpha);
        }
        return Math.max(7, Math.round(20.0f / (1.0f + 0.12f * beta)));
    }

    private void findTarget(ServerLevel level, ServerPlayer owner) {
        var range = getAttributeValue(Attributes.FOLLOW_RANGE);
        if (additional.equals(DarkmatterCreatureRegistries.ADDITIONAL_SENSOR.toString())) {
            range += 2.0 * alphaPower(additionalAlpha);
        }
        var target = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range),
                        candidate -> isCommandedTarget(owner, candidate)).stream()
                .min(Comparator.comparingDouble(candidate -> targetScore(level, candidate)))
                .orElse(null);
        setTarget(target);
    }

    private double targetScore(ServerLevel level, LivingEntity candidate) {
        var score = distanceToSqr(candidate);
        if (!modules.contains(DarkmatterCreatureRegistries.MODULE_FORMATION.toString())) return score;
        var duplicates = level.getEntitiesOfClass(
                DarkmatterBeetle.class, getBoundingBox().inflate(48.0),
                other -> other != this && ownerUUID != null
                        && other.getOwnerUUID().filter(ownerUUID::equals).isPresent()
                        && other.getTarget() == candidate).size();
        return score + duplicates * 144.0;
    }

    private void runModules(ServerLevel level, ServerPlayer owner) {
        var multiplier = DarkmatterCreation.moduleValueMultiplier(proficiencyMilestone);
        for (var raw : modules) {
            var id = Identifier.tryParse(raw);
            if (id != null) DarkmatterCreatureRegistries.module(id)
                    .ifPresent(type -> type.handler().tick(this, owner, multiplier));
        }
        if (modules.contains(DarkmatterCreatureRegistries.MODULE_SCOUT.toString())) {
            for (var target : level.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(
                    getAttributeValue(Attributes.FOLLOW_RANGE)), mob -> mob.isAlive()
                    && DarkmatterTargeting.isAttackableBy(owner, mob))) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
            }
        }
        if (additional.equals(DarkmatterCreatureRegistries.ADDITIONAL_SENSOR.toString())) {
            var beta = betaPower(additionalAlpha);
            for (var target : level.getEntitiesOfClass(Monster.class,
                    getBoundingBox().inflate(getAttributeValue(Attributes.FOLLOW_RANGE)),
                    mob -> mob.isAlive() && DarkmatterTargeting.isAttackableBy(owner, mob))) {
                target.addEffect(new MobEffectInstance(
                        MobEffects.GLOWING, 30 + Math.round(10.0f * beta), 0, false, false));
            }
        }
    }

    private boolean isCarrier() {
        return limbs.equals(DarkmatterCreatureRegistries.LIMBS_CARRIER.toString())
                || modules.contains(DarkmatterCreatureRegistries.MODULE_PICKUP.toString());
    }

    private boolean isExcavator() {
        return limbs.equals(DarkmatterCreatureRegistries.LIMBS_MINER.toString())
                || modules.contains(DarkmatterCreatureRegistries.MODULE_EXCAVATION.toString());
    }

    private void assistOwnerExcavation(ServerLevel level, ServerPlayer owner) {
        if (tickCount < nextExcavationTick || !owner.swinging || owner.level() != level) return;
        var start = owner.getEyePosition();
        var end = start.add(owner.getLookAngle().scale(6.0));
        var hit = level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        var pos = hit.getBlockPos();
        if (distanceToSqr(Vec3.atCenterOf(pos))
                > Math.pow(8.0 + betaPower(limbsAlpha), 2.0)) return;
        var hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        if (hardness < 0.0f || hardness > 10.0f + 10.0f * alphaPower(limbsAlpha)) return;
        if (owner.gameMode.destroyBlock(pos)) {
            var alpha = alphaPower(limbsAlpha);
            nextExcavationTick = tickCount
                    + Math.max(4, Math.round(20.0f / (1.0f + 0.25f * alpha)));
            // The owner's destroy call keeps every permission and block-event hook intact.
            // Beta improves retrieval range through the carrier/pickup path.
            if (isCarrier()) collectNearbyItems(level);
        }
    }

    private void collectNearbyItems(ServerLevel level) {
        var range = 3.0 + 2.0 * betaPower(limbsAlpha)
                * DarkmatterCreation.moduleValueMultiplier(proficiencyMilestone);
        for (var item : level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(range),
                candidate -> candidate.isAlive() && !candidate.hasPickUpDelay())) {
            var remaining = insertCargo(item.getItem().copy());
            if (remaining.isEmpty()) item.discard();
            else item.setItem(remaining);
            break;
        }
    }

    private int cargoSlotCount() {
        if (!isCarrier()) return 0;
        return Math.clamp(3 + 3L * (int) Math.floor(alphaPower(limbsAlpha)),
                3, MAX_CARGO_SLOTS);
    }

    private ItemStack insertCargo(ItemStack incoming) {
        var remaining = incoming.copy();
        var slots = cargoSlotCount();
        for (var slot = 0; slot < slots && !remaining.isEmpty(); slot++) {
            var current = cargo.get(slot);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, remaining)) continue;
            var moved = Math.min(remaining.getCount(), current.getMaxStackSize() - current.getCount());
            if (moved > 0) {
                current.grow(moved);
                remaining.shrink(moved);
            }
        }
        for (var slot = 0; slot < slots && !remaining.isEmpty(); slot++) {
            if (!cargo.get(slot).isEmpty()) continue;
            var moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            var stored = remaining.copy();
            stored.setCount(moved);
            cargo.set(slot, stored);
            remaining.shrink(moved);
        }
        return remaining;
    }

    private void transferCargo(ServerPlayer owner) {
        if (distanceToSqr(owner) > 4.0 * 4.0) return;
        for (var slot = 0; slot < cargoSlotCount(); slot++) {
            var stack = cargo.get(slot);
            if (stack.isEmpty()) continue;
            var remaining = stack.copy();
            owner.getInventory().add(remaining);
            cargo.set(slot, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        }
    }

    private void rangedAttack(ServerLevel level, ServerPlayer owner, LivingEntity target, boolean gammaRepeat) {
        var alpha = alphaPower(headAlpha);
        var beta = betaPower(headAlpha);
        var base = getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.75 + 0.20 * alpha);
        if (additional.equals(DarkmatterCreatureRegistries.ADDITIONAL_WEAPON.toString())) {
            base += 0.5 + 0.6 * alphaPower(additionalAlpha);
        }
        if (gammaRepeat) base *= 0.5;
        var multiplier = AbilitySystemServer.getSystem(owner)
                .getPlayerAbilityPowerMultiplier(owner.getUUID())
                * AbilitySystemServer.getSystem(owner).getPlayerDamageMultiplier(owner.getUUID());
        target.invulnerableTime = 0;
        var penetration = Math.min(0.5f, 0.08f * beta
                + (additional.equals(DarkmatterCreatureRegistries.ADDITIONAL_WEAPON.toString())
                ? 0.06f * betaPower(additionalAlpha) : 0.0f));
        var projectile = EntityTypes
                .DARKMATTER_CREATURE_PROJECTILE.get().create(
                        level, EntitySpawnReason.MOB_SUMMONED);
        if (projectile == null) return;
        var homing = head.equals(DarkmatterCreatureRegistries.HEAD_HOMING.toString());
        var projectileSpeed = (homing ? 0.95f : 1.25f) + 0.12f * alpha;
        projectile.configure(owner, this, target, homing,
                (float) (base * multiplier), penetration, beta, projectileSpeed,
                outputAdjustmentBypassed);
        if (level.addFreshEntity(projectile) && !gammaRepeat) {
            recentHeadTarget = target.getUUID();
            recentHeadAttackTick = tickCount;
        }
    }

    private boolean isCommandedTarget(ServerPlayer owner, LivingEntity target) {
        if (!DarkmatterTargeting.isAttackableBy(owner, target)) return false;
        var guard = limbs.equals(DarkmatterCreatureRegistries.LIMBS_GUARD.toString())
                || modules.contains(DarkmatterCreatureRegistries.MODULE_GUARD.toString());
        var focus = modules.contains(DarkmatterCreatureRegistries.MODULE_FOCUS.toString());
        var scout = modules.contains(DarkmatterCreatureRegistries.MODULE_SCOUT.toString());
        var sensorShared = additional.equals(DarkmatterCreatureRegistries.ADDITIONAL_SENSOR.toString())
                && level().getEntitiesOfClass(DarkmatterBeetle.class,
                getBoundingBox().inflate(getAttributeValue(Attributes.FOLLOW_RANGE)),
                other -> other != this && ownerUUID != null
                        && other.getOwnerUUID().filter(ownerUUID::equals).isPresent()
                        && other.getTarget() == target).stream().findAny().isPresent();
        return target == getLastHurtByMob()
                || (guard && target == owner.getLastHurtByMob())
                || (focus && target == owner.getLastHurtMob())
                || sensorShared
                || (scout && target instanceof Monster)
                || (guard && target instanceof Mob mob && mob.getTarget() == owner);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity entity) {
        if (!(entity instanceof LivingEntity target)) return false;
        var owner = getOwnerPlayer();
        if (owner == null || !isCommandedTarget(owner, target)) return false;
        var alpha = alphaPower(headAlpha);
        var beta = betaPower(headAlpha);
        var base = getAttributeValue(Attributes.ATTACK_DAMAGE) * (1.0 + 0.16 * alpha);
        if (limbs.equals(DarkmatterCreatureRegistries.LIMBS_GUARD.toString())) {
            base += 0.4 * alphaPower(limbsAlpha);
        }
        if (torso.equals(DarkmatterCreatureRegistries.TORSO_SWIM.toString()) && isInWater()) {
            base *= 1.0 + 0.08 * alphaPower(torsoAlpha);
        }
        if (additional.equals(DarkmatterCreatureRegistries.ADDITIONAL_WEAPON.toString())) {
            base += 0.5 + 0.6 * alphaPower(additionalAlpha);
        }
        var multiplier = AbilitySystemServer.getSystem(owner)
                .getPlayerAbilityPowerMultiplier(owner.getUUID())
                * AbilitySystemServer.getSystem(owner).getPlayerDamageMultiplier(owner.getUUID());
        var penetration = Math.min(0.5f, 0.06f * beta
                + (additional.equals(DarkmatterCreatureRegistries.ADDITIONAL_WEAPON.toString())
                ? 0.06f * betaPower(additionalAlpha) : 0.0f));
        var damage = (float) (base * multiplier);
        var hurt = outputAdjustmentBypassed
                ? OutputControl.callWithoutOutputAdjustment(() -> hurtWithPenetration(
                level,
                target,
                SkillDamageSource.of(owner, Skills.DARKMATTER_CREATION.get()),
                damage,
                penetration
        ))
                : hurtWithPenetration(
                level,
                target,
                SkillDamageSource.of(owner, Skills.DARKMATTER_CREATION.get()),
                damage,
                penetration
        );
        if (hurt) {
            if (beta > 0.0f) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                    20 + Math.round(10 * beta), 0, false, false));
            recentHeadTarget = target.getUUID();
            recentHeadAttackTick = tickCount;
            if (head.equals(DarkmatterCreatureRegistries.HEAD_JAW.toString()) && alpha > 0.0f) {
                var away = target.position().subtract(position());
                if (away.lengthSqr() > 1.0e-6) {
                    var push = away.normalize().scale(0.12 + 0.08 * alpha);
                    target.push(push.x, Math.min(0.35, push.y + 0.08), push.z);
                }
            }
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + .25, getZ(), 4, .1, .1, .1, .01);
        }
        return hurt;
    }

    private static boolean hurtWithPenetration(
            ServerLevel level,
            LivingEntity target,
            DamageSource source,
            float damage,
            float penetration
    ) {
        var armor = target.getAttribute(Attributes.ARMOR);
        if (armor == null || penetration <= 0.0f) {
            return DarkmatterTargeting.hurt(level, target, source, damage);
        }
        var previous = armor.getModifier(PENETRATION_ID);
        if (previous != null) armor.removeModifier(PENETRATION_ID);
        armor.addTransientModifier(new AttributeModifier(
                PENETRATION_ID,
                -Math.clamp(penetration, 0.0f, 0.5f),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        try {
            return DarkmatterTargeting.hurt(level, target, source, damage);
        } finally {
            armor.removeModifier(PENETRATION_ID);
            if (previous != null) armor.addTransientModifier(previous);
        }
    }

    private float alphaPower(int points) {
        return Math.clamp(points, 0, abilityLevel * 50) / 50.0f;
    }

    private float betaPower(int points) {
        return (abilityLevel * 50 - Math.clamp(points, 0, abilityLevel * 50)) / 50.0f;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (DarkmatterTargeting.isDarkmatterDamage(source)) return false;
        var reduction = gammaCatalyzed() ? Math.min(0.75f, 0.03f * averageGammaPower) : 0.0f;
        if (torso.equals(DarkmatterCreatureRegistries.TORSO_FLY.toString())) {
            reduction = Math.min(0.80f, reduction + Math.min(0.20f,
                    0.04f * alphaPower(torsoAlpha)));
        }
        return super.hurtServer(level, source, amount * (1.0f - reduction));
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
    public boolean isPushable() {
        return !modules.contains(DarkmatterCreatureRegistries.MODULE_FORMATION.toString());
    }

    @Override
    public void remove(RemovalReason reason) {
        // Chunk unload and cross-dimension transfer preserve the authoritative summon record.
        // Only destructive removal releases this construct's fixed CP occupation.
        if (!level().isClientSide() && reason.shouldDestroy()) {
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
        blueprintVersion = input.getIntOr("academy_blueprint_version", DarkmatterCreatureBlueprint.VERSION);
        blueprintSlot = Math.clamp(input.getIntOr("academy_blueprint_slot", 0), 0, 3);
        investment = Math.max(5, input.getIntOr("academy_investment", 5));
        abilityLevel = Math.clamp(input.getIntOr("academy_ability_level", 1), 1, 5);
        proficiencyMilestone = Math.clamp(input.getIntOr("academy_milestone", 0), 0, 3);
        head = input.getString("academy_head").orElse(head);
        torso = input.getString("academy_torso").orElse(torso);
        limbs = input.getString("academy_limbs").orElse(limbs);
        additional = input.getString("academy_additional").orElse(additional);
        headAlpha = input.getIntOr("academy_head_alpha", abilityLevel * 25);
        torsoAlpha = input.getIntOr("academy_torso_alpha", abilityLevel * 25);
        limbsAlpha = input.getIntOr("academy_limbs_alpha", abilityLevel * 25);
        additionalAlpha = input.getIntOr("academy_additional_alpha", abilityLevel * 25);
        averageGammaPower = input.getIntOr("academy_gamma_power_milli", 0) / 1000.0f;
        outputAdjustmentBypassed = input.getIntOr(
                "academy_output_adjustment_bypassed", 0) != 0;
        entityData.set(GAMMA_CATALYZED, input.getIntOr("academy_gamma_catalyzed", 0) != 0);
        modules = parseModules(input.getString("academy_modules").orElse(""));
        cargo = NonNullList.withSize(MAX_CARGO_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty("academy_cargo"), cargo);
        syncModels();
        configureNavigation();
        applyDegradedName();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        getOwnerUUID().ifPresent(uuid -> output.putString("academy_owner", uuid.toString()));
        output.putInt("academy_blueprint_version", blueprintVersion);
        output.putInt("academy_blueprint_slot", blueprintSlot);
        output.putInt("academy_investment", investment);
        output.putInt("academy_ability_level", abilityLevel);
        output.putInt("academy_milestone", proficiencyMilestone);
        output.putString("academy_head", head);
        output.putString("academy_torso", torso);
        output.putString("academy_limbs", limbs);
        output.putString("academy_additional", additional);
        output.putInt("academy_head_alpha", headAlpha);
        output.putInt("academy_torso_alpha", torsoAlpha);
        output.putInt("academy_limbs_alpha", limbsAlpha);
        output.putInt("academy_additional_alpha", additionalAlpha);
        output.putInt("academy_gamma_catalyzed", gammaCatalyzed() ? 1 : 0);
        output.putInt("academy_gamma_power_milli", Math.round(averageGammaPower * 1000));
        output.putInt(
                "academy_output_adjustment_bypassed", outputAdjustmentBypassed ? 1 : 0);
        output.putString("academy_modules", String.join(";", modules));
        ContainerHelper.saveAllItems(output.child("academy_cargo"), cargo, false);
    }

    private static List<String> parseModules(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(";")).filter(value -> !value.isBlank())
                .limit(32).toList();
    }
}
