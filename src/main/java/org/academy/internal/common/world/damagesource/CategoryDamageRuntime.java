package org.academy.internal.common.world.damagesource;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.event.SkillExecutionPreEvent;
import org.academy.api.common.damage.DamageComposition;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.mentalout.control.MentalControlMobAccess;
import org.academy.mixin.common.CooldownInstanceAccess;
import org.academy.mixin.common.ItemCooldownsAccess;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;

/** Transient category hit state; expiry follows physical server ticks, never a slowed entity clock. */
@EventBusSubscriber(modid = AcademyCraft.MOD_ID)
public final class CategoryDamageRuntime {
    public static final float DISCHARGE_DAMAGE = 2.0f;
    public static final int PARALYSIS_TICKS = 10;
    public static final int CHARGE_TIMEOUT_TICKS = 100;
    public static final int RADIATION_TICKS = 200;
    private static final Identifier PARALYSIS_SPEED = AcademyCraft.academy("electrical_paralysis");
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final Map<UUID, ChargeState> CHARGES = new HashMap<>();
    private static final Map<UUID, WearState> WEAR = new HashMap<>();
    private static final Map<DamageContainer, Float> BEFORE =
            new WeakHashMap<>();
    private static final Set<DamageContainer> COMPLETED =
            Collections.newSetFromMap(new WeakHashMap<>());

    public static void track(LivingEntity target, DamageContainer hit) {
        BEFORE.put(hit, target.getHealth());
    }

    private CategoryDamageRuntime() {}

    public static long now(LivingEntity target) {
        return target.level().getServer().getTickCount();
    }

    public static int chargePoints(String skill) {
        return switch (skill) {
            case "lightning_nova", "thunder_lance" -> 2;
            case "railgun", "thunderclap", "ball_lightning" -> 3;
            default -> 1; // Arc, contact, storm pulses, magnetism and iron-sand impacts.
        };
    }

    public static int equipmentWear(float fixedDamage) {
        return fixedDamage > 0.0f && Float.isFinite(fixedDamage)
                ? Math.min(20, (int) Math.ceil(4.0f + fixedDamage * 0.5f)) : 0;
    }

    public static int electricalCharge(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel)) return 0;
        var state = CHARGES.get(target.getUUID());
        return state != null && state.target.get() == target && now(target) < state.chargeExpires ? state.points : 0;
    }

    public static boolean isParalyzed(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel)) return false;
        var state = CHARGES.get(target.getUUID());
        return state != null && state.target.get() == target && now(target) < state.paralyzedUntil;
    }

    public static float outgoingDamage(DamageSource source, float damage) {
        return source.getEntity() instanceof LivingEntity attacker && isParalyzed(attacker)
                ? damage * 0.8f : damage;
    }

    public static int addCharge(LivingEntity target, int points) {
        return addCharge(target, points, null);
    }

    public static int addCharge(LivingEntity target, int points,
                                @Nullable DamageSource cause) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive() || points <= 0
                || target instanceof Player player && DamageTypes.isImmunePlayer(player)) return 0;
        var state = CHARGES.computeIfAbsent(target.getUUID(), _ -> new ChargeState(target));
        var now = now(target);
        if (now >= state.chargeExpires) state.points = 0;
        var sum = (long) state.points + points;
        var discharges = (int) Math.min(Integer.MAX_VALUE, sum / 5L);
        state.points = (int) (sum % 5L);
        state.chargeExpires = now + CHARGE_TIMEOUT_TICKS;
        if (discharges > 0) {
            state.paralyzedUntil = now + PARALYSIS_TICKS;
            interrupt(target);
            syncParalysis(target, state);
            discharge(target, cause, discharges);
        }
        return discharges;
    }

    private static void discharge(LivingEntity target,
                                  @Nullable DamageSource cause, int count) {
        var level = (ServerLevel) target.level();
        DamageSource source;
        if (cause instanceof SkillDamageSource skillSource && cause.getEntity() instanceof ServerPlayer owner) {
            source = SkillDamageSource.of(owner, skillSource.getSkill(), DamageTypes.ELECTRO_DAMAGE)
                    .withElectricalChargePoints(0).withoutHostilityMark();
        } else {
            source = new DamageSource(level.registryAccess()
                    .lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(DamageTypes.ELECTRO_DAMAGE),
                    cause == null ? null : cause.getDirectEntity(),
                    cause == null ? null : cause.getEntity());
        }
        // Zero charge on the secondary source prevents its completion callback from recharging.
        // Batch simultaneous thresholds without an unbounded loop for public API callers.
        SkillDamageUtil.applyDirect(level, target, source, DISCHARGE_DAMAGE * count);
    }

    @SuppressWarnings("unchecked")
    private static void interrupt(LivingEntity target) {
        target.stopUsingItem();
        target.swinging = false;
        if (target instanceof ServerPlayer player) {
            AbilitySystemServer.getSystem(player).interruptActiveSkills(player);
        }
        if (target instanceof Mob mob) {
            if (mob instanceof MentalControlMobAccess access) access.academy$stopAutonomousGoals();
            ((net.minecraft.world.entity.ai.Brain<LivingEntity>) mob.getBrain())
                    .stopAll((ServerLevel) mob.level(), mob);
            mob.getNavigation().stop();
        }
    }

    private static void syncParalysis(LivingEntity target, ChargeState state) {
        var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(PARALYSIS_SPEED)) {
            speed.addTransientModifier(new AttributeModifier(PARALYSIS_SPEED, -0.8,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (target instanceof ServerPlayer player) {
            // Cool down every carried group, not just the selected hand. Record only our extensions
            // so expiry cannot erase a longer cooldown subsequently installed by another mechanic.
            for (var index = 0; index < player.getInventory().getContainerSize(); index++) {
                var stack = player.getInventory().getItem(index);
                if (stack.isEmpty()) continue;
                var cooldowns = player.getCooldowns();
                var access = (ItemCooldownsAccess) cooldowns;
                var group = cooldowns.getCooldownGroup(stack);
                var current = access.academy$cooldowns().get(group);
                var end = current == null ? access.academy$tickCount()
                        : ((CooldownInstanceAccess) current).academy$endTime();
                var remaining = (int) Math.max(0L, state.paralyzedUntil - now(target));
                if (end - access.academy$tickCount() >= remaining) continue;
                cooldowns.addCooldown(group, remaining);
                state.cooldownEnds.put(group, access.academy$tickCount() + remaining);
            }
        }
    }

    private static void clearParalysis(LivingEntity target, ChargeState state) {
        var speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(PARALYSIS_SPEED);
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
        state.paralyzedUntil = 0;
    }

    public static void applyRadiation(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive()) return;
        for (var effect : List.of(MobEffects.NAUSEA, MobEffects.SLOWNESS,
                MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS)) {
            target.addEffect(new MobEffectInstance(effect, RADIATION_TICKS, 0));
        }
    }

    public static void damageEquipment(LivingEntity target, int amount, boolean includeHands) {
        if (!(target.level() instanceof ServerLevel) || !target.isAlive() || amount <= 0) return;
        var now = now(target);
        var state = WEAR.computeIfAbsent(target.getUUID(), _ -> new WearState());
        if (now >= state.expires) {
            state.used.clear();
            state.expires = now + 10L;
        }
        for (var slot : ARMOR) wearSlot(target, slot, amount, state);
        if (includeHands) {
            wearSlot(target, EquipmentSlot.MAINHAND, amount, state);
            wearSlot(target, EquipmentSlot.OFFHAND, amount, state);
        }
    }

    private static void wearSlot(LivingEntity target, EquipmentSlot slot, int amount, WearState state) {
        var stack = target.getItemBySlot(slot);
        if (stack.isEmpty() || !stack.isDamageableItem()) return;
        // Heavy cutting retains its 80-durability burst; ordinary sustained hits get a 20 budget.
        var cap = amount > 20 ? 80 : 20;
        var used = state.used.getOrDefault(slot, 0);
        var accepted = Math.min(amount, Math.max(0, cap - used));
        if (accepted <= 0) return;
        state.used.put(slot, used + accepted);
        stack.hurtAndBreak(accepted, target, slot);
    }

    public static void completed(LivingEntity target,
                                 DamageContainer hit) {
        if (!COMPLETED.add(hit)) return;
        var before = BEFORE.remove(hit);
        var healthDamage = before == null ? hit.getNewDamage() : Math.max(0.0f, before - target.getHealth());
        if (!(healthDamage > 0.0f) || !(hit.getSource() instanceof SkillDamageSource source)) return;
        if (source instanceof ReflectedSkillDamageSource reflected && !reflected.shouldTriggerSkillCallbacks()) return;
        var category = source.getSkill().getCategory();
        if (category == AbilityCategories.ELECTROMASTER.get()) {
            addCharge(target, source.electricalChargePoints() >= 0 ? source.electricalChargePoints()
                    : chargePoints(source.getSkill().getKey().getPath()), source);
        } else if (category == AbilityCategories.MELTDOWNER.get()) {
            applyRadiation(target);
        } else if (category == AbilityCategories.AEROMANIP.get()) {
            var fixed = Math.max(0.0f, hit.getOriginalDamage()
                    - DamageComposition.maximumHealthPart(target, source));
            damageEquipment(target, equipmentWear(fixed), source.is(DamageTypes.LAMINAR_CUT));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOrdinaryDamagePre(LivingDamageEvent.Pre event) {
        // Academy pre is dispatched internally; this adapter handles everyone else's damage.
        event.setNewDamage(outgoingDamage(event.getSource(), event.getNewDamage()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSkillUse(SkillExecutionPreEvent event) {
        var player = event.player();
        var time = (org.academy.internal.server.time.TemporalRuntime)
                org.academy.api.server.time.TemporalApi.get(player);
        if (isParalyzed(player) || !event.continuous() && !time.isPlayerActionTick(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        var source = event.getSource();
        if (!(source instanceof SkillDamageSource skillSource)
                || skillSource.getSkill().getCategory() != AbilityCategories.TELEPORT.get()) return;
        var target = event.getEntity();
        if (!(target.level() instanceof ServerLevel) || target.getRandom().nextFloat() >= 0.25f) return;
        var head = headFor(target);
        if (head.isEmpty() || event.getDrops().stream().anyMatch(drop -> drop.getItem().is(head.getItem()))) return;
        event.getDrops().add(new ItemEntity(target.level(), target.getX(), target.getY(), target.getZ(), head));
    }

    /** Supported vanilla heads; no fabricated generic head for unsupported entity types. */
    public static ItemStack headFor(LivingEntity target) {
        if (target instanceof Player player) {
            var head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
            return head;
        }
        var type = target.getType();
        if (type == EntityTypes.SKELETON) return new ItemStack(Items.SKELETON_SKULL);
        if (type == EntityTypes.WITHER_SKELETON) return new ItemStack(Items.WITHER_SKELETON_SKULL);
        if (type == EntityTypes.ZOMBIE) return new ItemStack(Items.ZOMBIE_HEAD);
        if (type == EntityTypes.CREEPER) return new ItemStack(Items.CREEPER_HEAD);
        if (type == EntityTypes.PIGLIN) return new ItemStack(Items.PIGLIN_HEAD);
        if (type == EntityTypes.ENDER_DRAGON) return new ItemStack(Items.DRAGON_HEAD);
        return ItemStack.EMPTY;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(ServerTickEvent.Pre event) {
        var server = event.getServer();
        for (var entry : List.copyOf(CHARGES.entrySet())) {
            var state = entry.getValue();
            var target = state.target.get();
            if (target == null) { CHARGES.remove(entry.getKey()); continue; }
            if (target.level().getServer() != server) continue;
            if (!target.isAlive() || target.isRemoved()) {
                clearParalysis(target, state);
                CHARGES.remove(entry.getKey());
                continue;
            }
            if (state.paralyzedUntil > 0 && now(target) >= state.paralyzedUntil) clearParalysis(target, state);
            else if (state.paralyzedUntil > 0) syncParalysis(target, state);
            if (now(target) >= state.chargeExpires && state.paralyzedUntil == 0) CHARGES.remove(entry.getKey());
        }
        WEAR.values().removeIf(state -> server.getTickCount() >= state.expires);
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(event.getLevel() instanceof ServerLevel)) return;
        var state = CHARGES.remove(target.getUUID());
        if (state != null) clearParalysis(target, state);
        WEAR.remove(target.getUUID());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        CHARGES.values().forEach(state -> {
            var target = state.target.get();
            if (target != null) clearParalysis(target, state);
        });
        CHARGES.clear();
        WEAR.clear();
        BEFORE.clear();
        COMPLETED.clear();
    }

    private static final class ChargeState {
        final WeakReference<LivingEntity> target;
        final Map<Identifier, Integer> cooldownEnds = new HashMap<>();
        int points;
        long chargeExpires;
        long paralyzedUntil;
        ChargeState(LivingEntity target) { this.target = new WeakReference<>(target); }
    }

    private static final class WearState {
        final Map<EquipmentSlot, Integer> used = new EnumMap<>(EquipmentSlot.class);
        long expires;
    }
}
