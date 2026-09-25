package org.academy.internal.common.ability.electromaster;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.academy.api.client.resources.R;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.common.damage.TrueHealthDamageSource;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityDevelopmentProfiles;
import org.academy.internal.common.ability.mentalout.control.MentalControlMobAccess;
import org.academy.internal.common.entitycontrol.EntityControlApi;
import org.academy.internal.common.world.damagesource.CTAEntityActuallyHurt;
import org.academy.internal.common.world.damagesource.DamageTypes;
import org.academy.internal.common.world.damagesource.SkillDamageUtil;
import org.academy.internal.server.ability.AbilitySystemServer;
import org.academy.internal.server.entity.SurvivalDefense;
import org.academy.mixin.common.CooldownInstanceAccess;
import org.academy.mixin.common.ItemCooldownsAccess;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Electromaster extends AbilityCategory {
    public static final int PARALYSIS_TICKS = 10;
    public static final int INTERRUPTION_MIN_TICKS = 10;
    public static final int INTERRUPTION_MAX_TICKS = 20;
    public static final int CHARGE_TIMEOUT_TICKS = 100;

    private static final Identifier PARALYSIS_SPEED = Identifier.fromNamespaceAndPath("academy", "electrical_paralysis");
    private final Map<UUID, ChargeState> charges = new HashMap<>();

    public static long now(LivingEntity target) {
        return target.level().getServer().getTickCount();
    }

    public static int chargePoints(String skill) {
        return switch (skill) {
            case "lightning_nova", "thunder_lance" -> 2;
            case "railgun", "thunderclap", "ball_lightning" -> 3;
            default -> 1;
        };
    }

    public int electricalCharge(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel)) return 0;
        var state = charges.get(target.getUUID());
        return state != null && state.target.get() == target && now(target) < state.chargeExpires ? state.points : 0;
    }

    public boolean isParalyzed(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel)) return false;
        var state = charges.get(target.getUUID());
        return state != null && state.target.get() == target && now(target) < state.paralyzedUntil;
    }

    public int electricalInterruptionTicks(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel)) return 0;
        var state = charges.get(target.getUUID());
        return state != null && state.target.get() == target
                ? (int) Math.max(0L, state.interruptedUntil - now(target)) : 0;
    }

    public boolean blocksMobAttack(LivingEntity attacker) {
        return attacker instanceof Mob && electricalInterruptionTicks(attacker) > 0;
    }

    public boolean blocksOutgoingDamage(DamageSource source) {
        var owner = source.getEntity();
        if (owner == null && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            owner = projectile.getOwner();
        }
        if (owner == null) owner = source.getDirectEntity();
        return owner instanceof LivingEntity attacker && blocksMobAttack(attacker);
    }

    public float outgoingDamage(DamageSource source, float damage) {
        if (blocksOutgoingDamage(source)) return 0.0f;
        return source.getEntity() instanceof LivingEntity attacker && isParalyzed(attacker)
                ? damage * 0.8f : damage;
    }

    public void onDamageCompleted(LivingEntity target, DamageContainer hit, float healthDamage) {
        if (!(hit.getSource() instanceof SkillDamageSource source) || !(healthDamage > 0.0f)) return;
        addCharge(target, source.electricalChargePoints() >= 0
                ? source.electricalChargePoints()
                : chargePoints(source.getSkill().getKey().getPath()), source);
    }

    public int addCharge(LivingEntity target, int points) {
        return addCharge(target, points, null);
    }

    public int addCharge(LivingEntity target, int points, @Nullable DamageSource cause) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive() || points <= 0
                || target instanceof Player player && DamageTypes.isImmunePlayer(player)) return 0;
        var state = charges.computeIfAbsent(target.getUUID(), _ -> new ChargeState(target));
        var now = now(target);
        if (now >= state.chargeExpires) state.points = 0;
        var sum = (long) state.points + points;
        var discharges = (int) Math.min(Integer.MAX_VALUE, sum / 5L);
        state.points = (int) (sum % 5L);
        state.chargeExpires = now + CHARGE_TIMEOUT_TICKS;
        if (discharges > 0) {
            var duration = INTERRUPTION_MIN_TICKS + target.getRandom().nextInt(
                    INTERRUPTION_MAX_TICKS - INTERRUPTION_MIN_TICKS + 1);
            if (!discharge(target, cause, discharges)) return 0;
            if (!target.isAlive()) return discharges;
            state.paralyzedUntil = now + PARALYSIS_TICKS;
            state.interruptedUntil = Math.max(state.interruptedUntil, now + duration);
            interrupt(target);
            syncParalysis(target);
            syncItemCooldowns(target, state);
        }
        return discharges;
    }

    private static boolean discharge(LivingEntity target, @Nullable DamageSource cause, int count) {
        var level = (ServerLevel) target.level();
        DamageSource source;
        if (cause instanceof SkillDamageSource skillSource && cause.getEntity() instanceof ServerPlayer owner) {
            source = SkillDamageSource.of(owner, skillSource.getSkill(), DamageTypes.ELECTRO_DAMAGE)
                    .withElectricalChargePoints(0).withoutHostilityMark();
        } else {
            source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.ELECTRO_DAMAGE),
                    cause == null ? null : cause.getDirectEntity(), cause == null ? null : cause.getEntity());
        }
        var before = CTAEntityActuallyHurt.readTrueHealth(target);
        if (target.isInvulnerableTo(level, source)
                || SurvivalDefense.applyHealthReadGuard(target, 0.0f) >= before
                || EntityControlApi.clampHealthWrite(target, 0.0f) >= before) return false;
        var settings = ((MinecraftServerContext) level.getServer()).getAcademyCraftServer()
                .getAbilityConfig().electromaster;
        var damage = settings.paralysisDamage(target.getMaxHealth());
        if (damage <= 0.0f) return true;
        var total = (float) Math.min(Float.MAX_VALUE, (double) damage * count);
        var dischargeSource = TrueHealthDamageSource.of(source);
        var maximumHealthPart = damage > settings.paralysisDamage(0.0f) ? total : 0.0f;
        var accepted = DamageComposition.withMaximumHealthPart(target, dischargeSource, maximumHealthPart,
                () -> SkillDamageUtil.applyVerifiedTrueHealth(target, dischargeSource, total));
        return accepted && CTAEntityActuallyHurt.readTrueHealth(target) < before;
    }

    @SuppressWarnings("unchecked")
    private static void interrupt(LivingEntity target) {
        target.stopUsingItem();
        if (target instanceof ServerPlayer player) AbilitySystemServer.getSystem(player).interruptActiveSkills(player);
        if (target instanceof Mob mob) {
            if (mob instanceof MentalControlMobAccess access) access.academy$stopAutonomousGoals();
            ((Brain<LivingEntity>) mob.getBrain()).stopAll((ServerLevel) mob.level(), mob);
            mob.getNavigation().stop();
        }
    }

    private static void syncParalysis(LivingEntity target) {
        var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(PARALYSIS_SPEED)) {
            speed.addTransientModifier(new AttributeModifier(PARALYSIS_SPEED, -0.8,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void syncItemCooldowns(LivingEntity target, ChargeState state) {
        if (target instanceof ServerPlayer player) {
            for (var index = 0; index < player.getInventory().getContainerSize(); index++) {
                var stack = player.getInventory().getItem(index);
                if (stack.isEmpty()) continue;
                var cooldowns = player.getCooldowns();
                var access = (ItemCooldownsAccess) cooldowns;
                var group = cooldowns.getCooldownGroup(stack);
                var current = access.academy$cooldowns().get(group);
                var end = current == null ? access.academy$tickCount()
                        : ((CooldownInstanceAccess) current).academy$endTime();
                var remaining = (int) Math.max(0L, state.interruptedUntil - now(target));
                if (end - access.academy$tickCount() >= remaining) continue;
                cooldowns.addCooldown(group, remaining);
                state.cooldownEnds.put(group, access.academy$tickCount() + remaining);
            }
        }
    }

    private static void clearParalysis(LivingEntity target, ChargeState state) {
        var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(PARALYSIS_SPEED);
        state.paralyzedUntil = 0;
    }

    private static void clearInterruption(LivingEntity target, ChargeState state) {
        if (target instanceof ServerPlayer player) {
            var access = (ItemCooldownsAccess) player.getCooldowns();
            state.cooldownEnds.forEach((group, ownEnd) -> {
                var current = access.academy$cooldowns().get(group);
                if (current != null && ((CooldownInstanceAccess) current).academy$endTime() == ownEnd) {
                    player.getCooldowns().removeCooldown(group);
                }
            });
        }
        state.cooldownEnds.clear();
        state.interruptedUntil = 0;
    }

    public void tick(net.minecraft.server.MinecraftServer server) {
        for (var entry : java.util.List.copyOf(charges.entrySet())) {
            var state = entry.getValue();
            var target = state.target.get();
            if (target == null) {
                charges.remove(entry.getKey());
                continue;
            }
            if (target.level().getServer() != server) continue;
            if (!target.isAlive() || target.isRemoved()) {
                clearParalysis(target, state);
                clearInterruption(target, state);
                charges.remove(entry.getKey());
                continue;
            }
            if (state.paralyzedUntil > 0 && now(target) >= state.paralyzedUntil) clearParalysis(target, state);
            else if (state.paralyzedUntil > 0) syncParalysis(target);
            if (state.interruptedUntil > 0 && now(target) >= state.interruptedUntil) clearInterruption(target, state);
            else if (state.interruptedUntil > 0) {
                interrupt(target);
                target.setJumping(false);
                target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
                syncItemCooldowns(target, state);
            }
            if (now(target) >= state.chargeExpires && state.paralyzedUntil == 0 && state.interruptedUntil == 0)
                charges.remove(entry.getKey());
        }
    }

    public void leave(LivingEntity target) {
        var state = charges.remove(target.getUUID());
        if (state != null) {
            clearParalysis(target, state);
            clearInterruption(target, state);
        }
    }

    public void stop() {
        charges.values().forEach(state -> {
            var target = state.target.get();
            if (target != null) {
                clearParalysis(target, state);
                clearInterruption(target, state);
            }
        });
        charges.clear();
    }

    private static final class ChargeState {
        final WeakReference<LivingEntity> target;
        final Map<Identifier, Integer> cooldownEnds = new HashMap<>();
        int points;
        long chargeExpires;
        long paralyzedUntil;
        long interruptedUntil;

        ChargeState(LivingEntity target) {
            this.target = new WeakReference<>(target);
        }
    }

    public Electromaster() {
        super(0.1F, AbilityDevelopmentProfiles.ELECTROMASTER);
    }

    @Override
    public Optional<ResourceKey<DamageType>> getDefaultDamageType() {
        return Optional.of(DamageTypes.ELECTRO_DAMAGE);
    }

    @Override
    public Identifier getDeveloperIcon() {
        return R.textures.ability.electromaster.icon;
    }

    @Override
    public String getDisplayName() {
        return "Electromaster";
    }
}
