package org.academy.internal.common.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.enchanting.EnchantedBlockLootEvent;
import net.neoforged.neoforge.event.enchanting.EnchantedEntityLootEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.darkmatter.DarkmatterModifiers;
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.api.server.team.TeamRelations;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.DarkmatterAbsorption;
import org.academy.internal.common.ability.darkmatter.DarkmatterEnchantments;
import org.academy.internal.common.ability.darkmatter.DarkmatterModifierRuntime;
import org.academy.internal.common.ability.darkmatter.DarkmatterPhase;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterShaping;
import org.academy.internal.common.ability.darkmatter.skills.lv5.DarkmatterSixWings;
import org.academy.internal.common.attribute.BlockLootPlayerContext;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.academy.internal.common.world.item.Items;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class DarkmatterEquipmentEvents {
    private static final Identifier ENCHANTMENT_PENETRATION_ID =
            AcademyCraft.academy("darkmatter_enchantment_penetration");
    private static final Set<LivingIncomingDamageEvent> CONVERTED_DAMAGE_EVENTS =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
    private static final Map<AttackPair, Long> NEXT_CORROSION_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_GAMMA_ARMOR_TICK = new ConcurrentHashMap<>();

    private record AttackPair(UUID defender, UUID attacker) {
    }

    private DarkmatterEquipmentEvents() {
    }

    static float damageMultiplier(int protectedPieces) {
        return Math.max(0, 1.0f - Mth.clamp(protectedPieces, 0, 4) * 0.1f);
    }

    static float matterConversionTarget(float rawDamage, int abilityLevel) {
        if (Float.isNaN(rawDamage) || rawDamage <= 0.0f || abilityLevel <= 0
                || rawDamage < abilityLevel) return 0.0f;
        return rawDamage * Math.clamp(abilityLevel, 0, 5) * 0.10f;
    }

    static float damageAfterMatterConversion(float rawDamage, int abilityLevel, float availableMatter) {
        return planMatterConversion(rawDamage, abilityLevel, availableMatter).remainingDamage();
    }

    static MatterConversionPlan planMatterConversion(float rawDamage, int abilityLevel,
                                                     float availableMatter) {
        if (Float.isNaN(rawDamage) || rawDamage <= 0.0f) {
            return new MatterConversionPlan(0.0f,
                    Float.isNaN(rawDamage) ? 0.0f : Math.max(0.0f, rawDamage));
        }
        var available = Float.isNaN(availableMatter) ? 0.0f : Math.max(0.0f, availableMatter);
        var converted = Math.min(matterConversionTarget(rawDamage, abilityLevel), available);
        var remaining = Float.isInfinite(rawDamage)
                ? rawDamage : Math.max(0.0f, rawDamage - converted);
        return new MatterConversionPlan(converted, remaining);
    }

    record MatterConversionPlan(float consumedMatter, float remainingDamage) {
    }

    static int integrityLifetimeTicks(int shapingMilestone) {
        return Math.clamp(shapingMilestone, 0, 3) >= 3 ? 18_000
                : Math.clamp(shapingMilestone, 0, 3) >= 2 ? 14_400 : 12_000;
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onAttackEntity(AttackEntityEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)
                    || !(event.getTarget() instanceof LivingEntity target)
                    || target == player || TeamRelations.areAllied(player, target)) return;
            var held = player.getMainHandItem();
            var nativeEffects = DarkmatterItemUtil.hasNativeItemEffects(held);
            var coating = DarkmatterItemUtil.hasCoating(held);
            if (!(DarkmatterItemUtil.hasEnchantment(held, DarkmatterEnchantments.DARKMATTER)
                    || nativeEffects || coating) || !DarkmatterItemUtil.isOperational(held)) return;
            var phase = DarkmatterPhase.weights(player);
            var alpha = nativeEffects || coating
                    ? DarkmatterItemUtil.effectAlphaPower(held) : phase.alpha();
            var beta = nativeEffects || coating
                    ? DarkmatterItemUtil.effectBetaPower(held) : phase.beta();
            var shape = DarkmatterItemUtil.shape(held);
            var damage = (nativeEffects
                    ? DarkmatterShaping.Server.directDamage(shape, alpha)
                    : coating ? DarkmatterShaping.Server.phaseDamageBonus(alpha)
                    : 6.0f + alpha * 0.8f)
                    * AbilitySystemServer.getSystem(player)
                    .getPlayerDamageMultiplier(player.getUUID());
            hurtWithPenetration(level, target,
                    SkillDamageSource.of(player, Skills.DARKMATTER_SHAPING.get()),
                    damage, DarkmatterShaping.Server.penetration(shape, beta));
            if (nativeEffects && shape != DarkmatterShape.MACE) event.setCanceled(true);
            if (nativeEffects || coating) DarkmatterModifierRuntime.applyMelee(player, target, held);
            if (held.is(Items.DARKMATTER_SPEAR.get())
                    && phase.gamma() > 0.0f) {
                var source = SkillDamageSource.of(player, Skills.DARKMATTER_SHAPING.get());
                var shapingMilestone = Skills.DARKMATTER_SHAPING.get()
                        .getEffectiveProficiencyMilestone(player);
                var sixMilestone = Skills.DARKMATTER_SIX_WINGS.get()
                        .getEffectiveProficiencyMilestone(player);
                var shapingGamma = DarkmatterShaping.Server
                        .gammaShapingMultiplier(shapingMilestone);
                var count = 1 + (int) Math.floor(phase.gamma() * shapingGamma);
                var pursuitDamage = (1.0f + 0.5f * phase.gamma() * shapingGamma)
                        * DarkmatterSixWings.Server.gammaMagnitudeMultiplier(sixMilestone);
                var pursuitRange = 8.0
                        * DarkmatterSixWings.Server.areaMultiplier(sixMilestone);
                var processed = 0;
                for (var nearby : level.getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(pursuitRange), candidate ->
                                candidate != target && candidate != player
                                        && candidate.isAlive() && !TeamRelations.areAllied(player, candidate))) {
                    if (processed++ >= count) break;
                    hurtWithPenetration(level, nearby, source,
                            pursuitDamage, phase.beta() * 0.10f);
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onMatterDamageConversion(LivingIncomingDamageEvent event) {
            if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                    || Float.isNaN(event.getAmount()) || event.getAmount() <= 0.0f
                    || !CONVERTED_DAMAGE_EVENTS.add(event)) return;
            var system = AbilitySystemServer.getSystem(player);
            if (system.getPlayerAbilityCategory(player.getUUID())
                    != AbilityCategories.DARKMATTER.get()) return;
            var level = system.getPlayerLevel(player.getUUID());
            var rawDamage = event.getAmount();
            var manager = system.getDarkmatterResourceManager();
            var target = matterConversionTarget(rawDamage, level);
            var requested = Float.isInfinite(target)
                    ? manager.getView(player).totalMatter() : target;
            if (!(requested > 0.0f)) return;
            var absorbed = manager.consumeUpTo(
                    player, requested, Skills.DARKMATTER_GENERATION.get(),
                    Skills.DARKMATTER_GENERATION.get().getIterationTicks(player));
            if (absorbed > 0.0f) {
                event.setAmount(Float.isInfinite(rawDamage)
                        ? rawDamage : Math.max(0.0f, rawDamage - absorbed));
            }
        }

        @SubscribeEvent
        public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var held = player.getMainHandItem();
            if (DarkmatterItemUtil.hasNativeItemEffects(held)) return;
            var alpha = 0.0f;
            if (DarkmatterItemUtil.hasEnchantment(held, DarkmatterEnchantments.DARKMATTER)) {
                alpha += DarkmatterPhase.snapshot(player).alphaPower();
            }
            if (DarkmatterItemUtil.hasCoating(held)) {
                alpha += DarkmatterItemUtil.coatingProfile(held).alphaPower();
            }
            var bonus = DarkmatterShaping.Server.miningSpeedBonus(alpha);
            if (bonus > 0.0f) event.setNewSpeed(event.getNewSpeed() + bonus);
        }

        @SubscribeEvent
        public static void onEnchantedBlockLoot(EnchantedBlockLootEvent event) {
            if (!event.getEnchantment().is(Enchantments.FORTUNE)) return;
            var player = BlockLootPlayerContext.current();
            if (player == null) return;
            var held = player.getMainHandItem();
            if (DarkmatterItemUtil.hasNativeItemEffects(held)) return;
            var beta = 0.0f;
            if (DarkmatterItemUtil.hasEnchantment(held, DarkmatterEnchantments.DARKMATTER)) {
                beta += DarkmatterPhase.snapshot(player).betaPower();
            }
            if (DarkmatterItemUtil.hasCoating(held)) {
                beta += DarkmatterItemUtil.coatingProfile(held).betaPower();
            }
            var extra = DarkmatterShaping.Server.toolFortune(beta)
                    + DarkmatterItemUtil.modifierLevel(held,
                    DarkmatterModifiers.LUCKY);
            if (extra > 0) event.setEnchantmentLevel(event.getEnchantmentLevel() + extra);
        }

        @SubscribeEvent
        public static void onEnchantedEntityLoot(EnchantedEntityLootEvent event) {
            if (!event.getEnchantment().is(Enchantments.LOOTING)
                    || !(event.getDamageSource().getEntity() instanceof ServerPlayer player)) return;
            var held = player.getMainHandItem();
            // Native equipment already exposes the aggregate level through its synchronized
            // vanilla enchantment, so only ordinary coated targets need an event-side bonus.
            if (DarkmatterItemUtil.hasNativeItemEffects(held)
                    || !DarkmatterItemUtil.hasCoating(held)) return;
            var extra = DarkmatterItemUtil.modifierLevel(
                    held, DarkmatterModifiers.LUCKY);
            if (extra > 0) event.setEnchantmentLevel(event.getEnchantmentLevel() + extra);
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
            var protectedPieces = 0;
            var shapedAlpha = 0.0f;
            var shapedBeta = 0.0f;
            var phase = DarkmatterPhase.weights(player);
            for (var slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                var armor = player.getItemBySlot(slot);
                if ((DarkmatterItemUtil.hasFamilyEnchantment(armor)
                        || DarkmatterItemUtil.isNativeEquipment(armor)
                        || DarkmatterItemUtil.hasCoating(armor))
                        && DarkmatterItemUtil.isOperational(armor)) {
                    protectedPieces++;
                    if (DarkmatterItemUtil.hasShapingEffects(armor)) {
                        shapedAlpha += DarkmatterItemUtil.effectAlphaPower(armor);
                        shapedBeta += DarkmatterItemUtil.effectBetaPower(armor);
                    } else {
                        shapedAlpha += phase.alpha();
                        shapedBeta += phase.beta();
                    }
                }
            }
            var armorAlpha = shapedAlpha / Math.max(1, protectedPieces);
            var armorBeta = shapedBeta / Math.max(1, protectedPieces);
            var fullSetReduction = DarkmatterShaping.Server.armorReduction(armorAlpha);
            event.setAmount(event.getAmount()
                    * (1.0f - fullSetReduction * protectedPieces / 4.0f));
            if (protectedPieces > 0 && armorBeta > 0.0f
                    && event.getSource().getEntity() instanceof LivingEntity attacker) {
                var now = player.level().getGameTime();
                var pair = new AttackPair(player.getUUID(), attacker.getUUID());
                if (now >= NEXT_CORROSION_TICK.getOrDefault(pair, 0L)) {
                    NEXT_CORROSION_TICK.put(pair, now + 20L);
                    attacker.addEffect(new MobEffectInstance(
                            MobEffects.WEAKNESS,
                            DarkmatterShaping.Server.armorWeaknessTicks(armorBeta), 0));
                }
            }
        }

        @SubscribeEvent
        public static void onDamageApplied(LivingDamageEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !(event.getHealthDamage() > 0.0f)) return;
            var phase = DarkmatterPhase.snapshot(player);
            if (!(phase.activeGammaPower() > 0.0f) || !hasOperationalNativeArmorSet(player)) return;
            var now = player.level().getGameTime();
            if (now < NEXT_GAMMA_ARMOR_TICK.getOrDefault(player.getUUID(), 0L)) return;
            NEXT_GAMMA_ARMOR_TICK.put(player.getUUID(), now + 100L);
            var shapingMilestone = Skills.DARKMATTER_SHAPING.get()
                    .getEffectiveProficiencyMilestone(player);
            var cap = (1.0f + phase.activeGammaPower())
                    * DarkmatterShaping.Server.gammaShapingMultiplier(shapingMilestone);
            DarkmatterAbsorption.grantAtLeast(player, cap);
        }

        @SubscribeEvent
        public static void onEffectApplicable(MobEffectEvent.Applicable event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !DarkmatterSixWings.Server.isActive(player)
                    || event.getEffectInstance().getEffect().value().isBeneficial()) return;
            var reduction = Math.min(0.40f, DarkmatterPhase.beta(player) * 0.08f);
            if (!(reduction > 0.0f)) return;
            event.getEffectInstance().mapDuration(duration ->
                    Math.max(1, Math.round(duration * (1.0f - reduction))));
        }

        @SubscribeEvent
        public static void onAnvilUpdate(AnvilUpdateEvent event) {
            if (event.getLeft().isEmpty() || !DarkmatterItemUtil.isDarkmatter(event.getRight())) return;
            var result = DarkmatterItemUtil.createAnvilUpgradeResult(
                    event.getPlayer().registryAccess(), event.getLeft(), event.getName());
            if (result.isEmpty()) return;
            event.setOutput(result);
            event.setMaterialCost(1);
            event.setXpCost(3);
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var shaping = Skills.DARKMATTER_SHAPING.get();
            var milestone = shaping.isEnabled(player)
                    ? shaping.getEffectiveProficiencyMilestone(player) : 0;
            var lifetime = integrityLifetimeTicks(milestone);
            var changed = false;
            for (var stack : player.getInventory().getNonEquipmentItems()) {
                changed |= tickNativeStack(player, stack, lifetime);
            }
            for (var slot : new EquipmentSlot[]{
                    EquipmentSlot.OFFHAND,
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                changed |= tickNativeStack(player, player.getItemBySlot(slot), lifetime);
            }
            if (changed) {
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
            }
            if (player.tickCount % 5 == 0) {
                DarkmatterShaping.Server.refreshHeldNativeProfile(player);
                attractGammaToolDrops(player, milestone);
            }
        }

        private static void attractGammaToolDrops(ServerPlayer player, int shapingMilestone) {
            var held = player.getMainHandItem();
            if (!held.is(Items.DARKMATTER_TOOL.get())
                    || !DarkmatterItemUtil.isOperational(held)) return;
            var phase = DarkmatterPhase.snapshot(player);
            if (!(phase.activeGammaPower() > 0.0f)) return;
            var sixMilestone = Skills.DARKMATTER_SIX_WINGS.get()
                    .getEffectiveProficiencyMilestone(player);
            var gamma = phase.activeGammaPower()
                    * DarkmatterShaping.Server.gammaShapingMultiplier(shapingMilestone);
            var range = (2.0 + gamma) * DarkmatterSixWings.Server.areaMultiplier(sixMilestone);
            for (var item : player.level().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(range), candidate -> candidate.isAlive())) {
                var delta = player.getEyePosition().subtract(item.position());
                if (delta.lengthSqr() < 0.04) continue;
                item.setDeltaMovement(item.getDeltaMovement().scale(0.55)
                        .add(delta.normalize().scale(0.18)));
            }
        }

        private static boolean hasOperationalNativeArmorSet(ServerPlayer player) {
            for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                var stack = player.getItemBySlot(slot);
                if (!DarkmatterItemUtil.isNativeEquipment(stack)
                        || !DarkmatterItemUtil.isOperational(stack)) return false;
            }
            return true;
        }

        private static boolean tickNativeStack(ServerPlayer player,
                                               ItemStack stack,
                                               int lifetimeTicks) {
            if (!DarkmatterItemUtil.isNativeEquipment(stack)) return false;
            var changed = DarkmatterItemUtil.absorbVanillaDurabilityDamage(stack);
            changed |= DarkmatterItemUtil.decayIntegrity(stack, lifetimeTicks);
            changed |= DarkmatterItemUtil.setEnchantmentLevel(
                    player.registryAccess(), stack, Enchantments.MENDING, 0);
            return changed;
        }

        private static boolean hurtWithPenetration(
                ServerLevel level,
                LivingEntity target,
                DamageSource source,
                float damage,
                float penetration
        ) {
            var armor = target.getAttribute(Attributes.ARMOR);
            if (armor == null || penetration <= 0.0f) return target.hurtServer(level, source, damage);
            var existing = armor.getModifier(ENCHANTMENT_PENETRATION_ID);
            if (existing != null) armor.removeModifier(ENCHANTMENT_PENETRATION_ID);
            armor.addTransientModifier(new AttributeModifier(
                    ENCHANTMENT_PENETRATION_ID,
                    -Math.clamp(penetration, 0.0f, 0.50f),
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            try {
                return target.hurtServer(level, source, damage);
            } finally {
                armor.removeModifier(ENCHANTMENT_PENETRATION_ID);
                if (existing != null) armor.addTransientModifier(existing);
            }
        }
    }
}
