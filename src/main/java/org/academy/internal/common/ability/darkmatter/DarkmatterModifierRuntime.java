package org.academy.internal.common.ability.darkmatter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.darkmatter.DarkmatterModifiers;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterShaping;
import org.academy.internal.common.world.item.DarkmatterItemUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime hook set for shaped-item modifiers. All item data is read from the embedded profile.
 */
public final class DarkmatterModifierRuntime {
    private static final Identifier GRAVITY_ID = AcademyCraft.academy("shaping_antigravity");
    private static final Identifier SAFE_FALL_ID = AcademyCraft.academy("shaping_safe_fall");
    private static final Identifier BLOCK_REACH_ID = AcademyCraft.academy("shaping_block_reach");
    private static final Identifier ENTITY_REACH_ID = AcademyCraft.academy("shaping_entity_reach");
    private static final Identifier PROJECTILE_PENETRATION_ID =
            AcademyCraft.academy("shaping_projectile_penetration");
    private static final TagKey<Block> ADDITIONAL_HARVESTABLE_CROPS = TagKey.create(
            Registries.BLOCK, AcademyCraft.academy("darkmatter_harvestable/crops"));
    private static final TagKey<Block> INTERACT_HARVESTABLES = TagKey.create(
            Registries.BLOCK, AcademyCraft.academy("darkmatter_harvestable/interact"));
    private static final TagKey<Block> STACKABLE_HARVESTABLES = TagKey.create(
            Registries.BLOCK, AcademyCraft.academy("darkmatter_harvestable/stackable"));
    private static final TagKey<Item> HARVEST_REPLANT_ITEMS = TagKey.create(
            Registries.ITEM, AcademyCraft.academy("darkmatter_harvestable/replant_items"));
    private static final Map<UUID, Long> TELEPORT_SUPPRESSED_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MAGNETIC_UNTIL = new ConcurrentHashMap<>();

    private DarkmatterModifierRuntime() {
    }

    public static void applyMelee(ServerPlayer attacker, LivingEntity target, ItemStack weapon) {
        if (!usable(weapon) || !DarkmatterTargeting.isAttackableBy(attacker, target)) return;
        applyTargetEffects(attacker, target, weapon,
                DarkmatterItemUtil.effectBetaPower(weapon), false);
        if (DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.MAGNETIC) > 0) {
            MAGNETIC_UNTIL.put(attacker.getUUID(), attacker.level().getGameTime() + 30L);
        }
    }

    private static void applyTargetEffects(ServerPlayer attacker, LivingEntity target,
                                           ItemStack weapon, float betaPower,
                                           boolean projectile) {
        var suppression = DarkmatterItemUtil.modifierLevel(
                weapon, DarkmatterModifiers.TELEPORT_SUPPRESSION);
        if (suppression > 0) {
            TELEPORT_SUPPRESSED_UNTIL.put(target.getUUID(),
                    target.level().getGameTime() + suppression * 100L);
        }

        var extraDamage = creatureBonus(target, weapon);
        if (extraDamage > 0.0f && target.level() instanceof ServerLevel level) {
            target.invulnerableTime = 0;
            DarkmatterTargeting.hurt(level, target, SkillDamageSource.of(attacker,
                    Skills.DARKMATTER_SHAPING.get()), extraDamage);
        }

        var freezing = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.FREEZING);
        if (freezing > 0) {
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), 80 + freezing * 60));
            target.addEffect(new MobEffectInstance(
                    MobEffects.SLOWNESS, 40 + freezing * 40,
                    Math.max(0, freezing - 1)));
        }
        var burning = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.BURNING);
        if (burning > 0) target.setRemainingFireTicks(Math.max(
                target.getRemainingFireTicks(), burning * 80));

        var law = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.LAW_EROSION);
        if (law > 0) DarkmatterLawMark.apply(attacker, target,
                Math.max(betaPower, law * 0.5f), 60 + law * 40);

        var pull = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.PULL);
        var knockback = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.KNOCKBACK);
        if (pull > 0) moveTarget(target,
                attacker.position().subtract(target.position()), 0.38 * pull * 2.0);
        else if (knockback > 0) moveTarget(target,
                target.position().subtract(attacker.position()), 0.38 * knockback);

        var teleportProtection = DarkmatterItemUtil.modifierLevel(
                weapon, DarkmatterModifiers.TELEPORT_PROTECTION);
        if (projectile && teleportProtection > 0) {
            randomTeleport(target, 16 + 8 * (teleportProtection - 1));
        }

        var explosion = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.EXPLOSIVE);
        if (explosion > 0) target.level().explode(attacker, target.getX(), target.getY(),
                target.getZ(), 1.0f + 0.65f * explosion, Level.ExplosionInteraction.NONE);

        var echo = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.ECHO);
        if (echo > 0 && target.level() instanceof ServerLevel level) {
            var source = SkillDamageSource.of(attacker, Skills.DARKMATTER_SHAPING.get());
            var damage = 0.75f * echo
                    + 0.25f * DarkmatterItemUtil.effectAlphaPower(weapon);
            for (var nearby : level.getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(3.0), candidate -> candidate != attacker
                            && candidate != target && candidate.isAlive()
                            && DarkmatterTargeting.isAttackableBy(attacker, candidate))) {
                nearby.invulnerableTime = 0;
                DarkmatterTargeting.hurt(level, nearby, source, damage);
            }
        }

        var lightning = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.LIGHTNING);
        if (lightning > 0 && target.level() instanceof ServerLevel level
                && level.getRandom().nextFloat() < 0.10f * lightning) {
            var bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(
                    level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(target.position());
                bolt.setCause(attacker);
                level.addFreshEntity(bolt);
            }
        }

        var feathers = DarkmatterItemUtil.modifierLevel(
                weapon, DarkmatterModifiers.FEATHER_PURSUIT);
        if (feathers > 0 && target.level() instanceof ServerLevel level) {
            for (var index = 0; index < feathers; index++) {
                var feather = org.academy.internal.common.world.entity.EntityTypes
                        .DARKMATTER_FEATHER_PROJECTILE.get().create(
                                level, EntitySpawnReason.TRIGGERED);
                if (feather == null) continue;
                var angle = Math.PI * 2.0 * index / Math.max(1, feathers);
                feather.configure(attacker, target,
                        new Vec3(Math.cos(angle), 0.1, Math.sin(angle)),
                        0.5f + 0.35f * DarkmatterItemUtil.effectAlphaPower(weapon), 0.0f);
                level.addFreshEntity(feather);
            }
        }
    }

    private static float creatureBonus(LivingEntity target, ItemStack weapon) {
        var level = 0;
        if (target.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) {
            level = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.HOLY);
        } else if (target.getType().builtInRegistryHolder().is(EntityTypeTags.ARTHROPOD)) {
            level = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.DISMEMBER);
        } else if (target instanceof Player || target instanceof Villager
                || target instanceof AbstractIllager) {
            level = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.SLAUGHTER);
        } else if (target.getType().builtInRegistryHolder().is(EntityTypeTags.AQUATIC)) {
            level = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.DRYING);
        } else if (target.fireImmune()) {
            level = DarkmatterItemUtil.modifierLevel(weapon, DarkmatterModifiers.EXTINGUISH);
        }
        return level <= 0 ? 0.0f : level
                * (1.5f + 0.25f * DarkmatterItemUtil.effectAlphaPower(weapon));
    }

    private static void moveTarget(LivingEntity target, Vec3 direction, double strength) {
        if (direction.lengthSqr() < 1.0e-8) return;
        var movement = direction.normalize().scale(strength);
        target.setDeltaMovement(target.getDeltaMovement().add(
                movement.x, Math.min(0.35, 0.08 + strength * 0.15), movement.z));
        target.hurtMarked = true;
    }

    private static boolean usable(ItemStack stack) {
        return DarkmatterItemUtil.hasShapingEffects(stack)
                && DarkmatterItemUtil.isOperational(stack);
    }

    private static ItemStack projectileSource(Projectile projectile) {
        if (projectile instanceof AbstractArrow arrow) {
            var weapon = arrow.getWeaponItem();
            if (weapon != null && usable(weapon)) return weapon;
            var ammunition = arrow.getPickupItemStackOrigin();
            if (usable(ammunition)) return ammunition;
        }
        return ItemStack.EMPTY;
    }

    private static boolean randomTeleport(LivingEntity entity, int maximumRange) {
        for (var attempt = 0; attempt < 16; attempt++) {
            var x = entity.getX() + (entity.getRandom().nextDouble() - 0.5) * maximumRange * 2.0;
            var y = entity.getY() + entity.getRandom().nextIntBetweenInclusive(-8, 8);
            var z = entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * maximumRange * 2.0;
            if (entity.randomTeleport(x, y, z, true)) return true;
        }
        return false;
    }

    @EventBusSubscriber(modid = AcademyCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onTeleport(EntityTeleportEvent event) {
            var expiry = TELEPORT_SUPPRESSED_UNTIL.get(event.getEntity().getUUID());
            if (expiry == null) return;
            if (event.getEntity().level().getGameTime() < expiry) event.setCanceled(true);
            else TELEPORT_SUPPRESSED_UNTIL.remove(event.getEntity().getUUID(), expiry);
        }

        @SubscribeEvent
        public static void onProjectileImpact(ProjectileImpactEvent event) {
            var projectile = event.getProjectile();
            if (!(projectile.getOwner() instanceof ServerPlayer owner)) return;
            var source = projectileSource(projectile);
            if (source.isEmpty()) return;
            var hit = event.getRayTraceResult();
            if (hit instanceof EntityHitResult entityHit
                    && entityHit.getEntity() instanceof LivingEntity target
                    && DarkmatterTargeting.isAttackableBy(owner, target)) {
                applyProjectilePhase(owner, target, source);
                applyTargetEffects(owner, target, source,
                        DarkmatterItemUtil.effectBetaPower(source), true);
                if (DarkmatterItemUtil.modifierLevel(source, DarkmatterModifiers.MAGNETIC) > 0) {
                    MAGNETIC_UNTIL.put(owner.getUUID(), owner.level().getGameTime() + 30L);
                }
            } else if (hit instanceof BlockHitResult blockHit) {
                var explosion = DarkmatterItemUtil.modifierLevel(source,
                        DarkmatterModifiers.EXPLOSIVE);
                if (explosion > 0) owner.level().explode(owner,
                        blockHit.getLocation().x, blockHit.getLocation().y,
                        blockHit.getLocation().z, 1.0f + 0.65f * explosion,
                        Level.ExplosionInteraction.NONE);
            }
        }

        @SubscribeEvent
        public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !(event.getTarget() instanceof Shearable shearable)) return;
            var held = player.getItemInHand(event.getHand());
            if (DarkmatterItemUtil.modifierLevel(held, DarkmatterModifiers.SHEAR) <= 0
                    || !DarkmatterItemUtil.isOperational(held)
                    || !shearable.readyForShearing()) return;
            shearable.shear(player.level(), SoundSource.PLAYERS, held);
            damageForUse(player, held, event.getHand(), 1);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            var held = player.getItemInHand(event.getHand());
            if (!usable(held)) return;
            var harvest = DarkmatterItemUtil.modifierLevel(held, DarkmatterModifiers.HARVEST);
            var till = DarkmatterItemUtil.modifierLevel(held, DarkmatterModifiers.TILL);
            var changed = harvest > 0 && harvest(player, held, event.getHand(),
                    event.getPos(), harvest, event.getHitVec());
            if (!changed && till > 0) {
                var radius = Math.max(0, till - 1);
                for (var x = -radius; x <= radius; x++)
                    for (var z = -radius; z <= radius; z++) {
                        var pos = event.getPos().offset(x, 0, z);
                        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP,
                                pos, false);
                        var result = Items.NETHERITE_HOE.useOn(
                                new UseOnContext(player, event.getHand(), hit));
                        changed |= result.consumesAction();
                    }
            }
            if (changed) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }

        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (!(event.getEntity() instanceof ServerPlayer player) || !player.isShiftKeyDown()) return;
            var held = player.getItemInHand(event.getHand());
            if (!usable(held)
                    || DarkmatterItemUtil.modifierLevel(held, DarkmatterModifiers.EDIBLE) <= 0
                    || !player.getFoodData().needsFood()) return;
            if (!damageForUse(player, held, event.getHand(), 15)) return;
            player.getFoodData().eat(1, 0.1f);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }

        @SubscribeEvent
        public static void onBreak(BreakBlockEvent event) {
            if (!(event.getPlayer() instanceof ServerPlayer player)) return;
            var held = player.getMainHandItem();
            if (DarkmatterItemUtil.modifierLevel(held, DarkmatterModifiers.MAGNETIC) > 0) {
                MAGNETIC_UNTIL.put(player.getUUID(), player.level().getGameTime() + 30L);
            }
        }

        @SubscribeEvent
        public static void onIncomingDamage(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
            var protection = armorModifier(player, DarkmatterModifiers.TELEPORT_PROTECTION);
            if (protection > 0 && player.getRandom().nextFloat() < 0.25f) {
                randomTeleport(attacker, 16 + 8 * (protection - 1));
            }
            var guard = armorModifier(player, DarkmatterModifiers.STRUCTURAL_GUARD);
            if (guard > 0) event.setAmount(event.getAmount()
                    * Math.max(0.70f, 1.0f - 0.05f * guard));
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            updateAttributes(player);
            var expiry = MAGNETIC_UNTIL.get(player.getUUID());
            if (expiry != null) {
                if (player.level().getGameTime() <= expiry) attractItems(player);
                else MAGNETIC_UNTIL.remove(player.getUUID(), expiry);
            }
        }
    }

    private static void applyProjectilePhase(
            ServerPlayer owner, LivingEntity target, ItemStack source
    ) {
        var shape = DarkmatterItemUtil.shape(source);
        if (!shape.isRanged()) return;
        var damage = DarkmatterShaping.Server.phaseDamageBonus(
                DarkmatterItemUtil.effectAlphaPower(source))
                * AbilitySystemServer.getSystem(owner)
                .getPlayerDamageMultiplier(owner.getUUID());
        if (!(damage > 0.0f)) return;
        var penetration = DarkmatterShaping.Server.penetration(
                shape, DarkmatterItemUtil.effectBetaPower(source));
        var armor = target.getAttribute(Attributes.ARMOR);
        if (armor == null || penetration <= 0.0f) {
            DarkmatterTargeting.hurt(owner.level(), target,
                    SkillDamageSource.of(owner, Skills.DARKMATTER_SHAPING.get()), damage);
            return;
        }
        var existing = armor.getModifier(PROJECTILE_PENETRATION_ID);
        if (existing != null) armor.removeModifier(PROJECTILE_PENETRATION_ID);
        armor.addTransientModifier(new AttributeModifier(
                PROJECTILE_PENETRATION_ID,
                -Math.clamp(penetration, 0.0f, 0.50f),
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        try {
            DarkmatterTargeting.hurt(owner.level(), target,
                    SkillDamageSource.of(owner, Skills.DARKMATTER_SHAPING.get()), damage);
        } finally {
            armor.removeModifier(PROJECTILE_PENETRATION_ID);
            if (existing != null) armor.addTransientModifier(existing);
        }
    }

    private static boolean harvest(ServerPlayer player, ItemStack held,
                                   InteractionHand hand, BlockPos center,
                                   int level, BlockHitResult originalHit) {
        var changed = false;
        var radius = level;
        var world = player.level();
        for (var x = -radius; x <= radius; x++)
            for (var z = -radius; z <= radius; z++) {
                var pos = center.offset(x, 0, z);
                var state = world.getBlockState(pos);
                if (state.is(INTERACT_HARVESTABLES)) {
                    changed |= state.useWithoutItem(world, player,
                            new BlockHitResult(Vec3.atCenterOf(pos), originalHit.getDirection(),
                                    pos, false)).consumesAction();
                    continue;
                }
                if (state.is(STACKABLE_HARVESTABLES)) {
                    var bottom = pos;
                    while (world.getBlockState(bottom.below()).is(STACKABLE_HARVESTABLES)) {
                        bottom = bottom.below();
                    }
                    var second = bottom.above();
                    if (world.getBlockState(second).is(STACKABLE_HARVESTABLES)) {
                        changed |= player.gameMode.destroyBlock(second);
                    }
                    continue;
                }
                var crop = state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(state);
                if (!(crop || state.is(ADDITIONAL_HARVESTABLE_CROPS)
                        || state.is(BlockTags.CROPS))) continue;
                if (!player.gameMode.destroyBlock(pos)) continue;
                changed = true;
                if (consumeNearbySeed(world, pos)) {
                    world.setBlock(pos, crop
                            ? ((CropBlock) state.getBlock()).getStateForAge(0)
                            : state.getBlock().defaultBlockState(), 3);
                }
            }
        if (changed) damageForUse(player, held, hand, 1);
        return changed;
    }

    private static boolean damageForUse(ServerPlayer player, ItemStack stack,
                                        InteractionHand hand, int amount) {
        if (DarkmatterItemUtil.isNativeEquipment(stack)) {
            return DarkmatterItemUtil.damageIntegrity(stack, amount / 12_000.0f);
        }
        // Coatings also support normally indestructible targets. Such targets keep their
        // vanilla durability semantics while still receiving the configured active effect.
        if (!stack.isDamageableItem()) return true;
        stack.hurtAndBreak(amount, player, hand == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        return true;
    }

    private static boolean consumeNearbySeed(ServerLevel level, BlockPos pos) {
        for (var item : level.getEntitiesOfClass(ItemEntity.class,
                new AABB(pos).inflate(1.5), entity ->
                        entity.isAlive() && entity.getItem().is(HARVEST_REPLANT_ITEMS))) {
            item.getItem().shrink(1);
            if (item.getItem().isEmpty()) item.discard();
            return true;
        }
        return false;
    }

    private static int armorModifier(ServerPlayer player, String id) {
        var result = 0;
        for (var slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var stack = player.getItemBySlot(slot);
            if (usable(stack)) result = Math.max(result, DarkmatterItemUtil.modifierLevel(stack, id));
        }
        return result;
    }

    private static int equippedModifier(ServerPlayer player, String id) {
        var result = Math.max(DarkmatterItemUtil.modifierLevel(player.getMainHandItem(), id),
                DarkmatterItemUtil.modifierLevel(player.getOffhandItem(), id));
        return Math.max(result, armorModifier(player, id));
    }

    private static void updateAttributes(ServerPlayer player) {
        var antigravity = equippedModifier(player, DarkmatterModifiers.ANTIGRAVITY);
        replaceAttribute(player, Attributes.GRAVITY, GRAVITY_ID,
                -0.20 * antigravity, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        replaceAttribute(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID,
                antigravity, AttributeModifier.Operation.ADD_VALUE);
        var reach = equippedModifier(player, DarkmatterModifiers.REACH);
        replaceAttribute(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID,
                reach, AttributeModifier.Operation.ADD_VALUE);
        replaceAttribute(player, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID,
                reach, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void replaceAttribute(ServerPlayer player,
                                         Holder<Attribute> attribute,
                                         Identifier id, double amount,
                                         AttributeModifier.Operation operation) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (Math.abs(amount) > 1.0e-8) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static void attractItems(ServerPlayer player) {
        var range = 5.0;
        for (var item : player.level().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(range), ItemEntity::isAlive)) {
            var delta = player.getEyePosition().subtract(item.position());
            if (delta.lengthSqr() < 0.04) continue;
            item.setDeltaMovement(item.getDeltaMovement().scale(0.35)
                    .add(delta.normalize().scale(0.32)));
            item.hurtMarked = true;
        }
    }
}
