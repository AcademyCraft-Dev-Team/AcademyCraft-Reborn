package org.academy.internal.common.ability.darkmatter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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
import org.academy.api.common.ability.darkmatter.DarkmatterShape;
import org.academy.api.common.damage.SkillDamageSource;
import org.academy.internal.common.ability.Skills;
import org.academy.internal.common.ability.darkmatter.skills.lv1.DarkmatterShaping;
import org.academy.internal.common.world.entity.projectile.DarkmatterFeatherProjectile;
import org.academy.internal.common.world.item.DarkmatterItemUtil;
import org.academy.internal.common.world.item.Items;
import org.academy.api.server.ability.AbilitySystemServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime hook set for shaped-item modifiers. All item data is read from the embedded profile. */
public final class DarkmatterModifierRuntime {
    private static final Identifier GRAVITY_ID = AcademyCraft.academy("shaping_antigravity");
    private static final Identifier SAFE_FALL_ID = AcademyCraft.academy("shaping_safe_fall");
    private static final Identifier BLOCK_REACH_ID = AcademyCraft.academy("shaping_block_reach");
    private static final Identifier ENTITY_REACH_ID = AcademyCraft.academy("shaping_entity_reach");
    private static final Identifier PROJECTILE_PENETRATION_ID =
            AcademyCraft.academy("shaping_projectile_penetration");
    private static final TagKey<Block> TCONSTRUCT_CROPS = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("tconstruct", "harvestable/crops"));
    private static final TagKey<Block> TCONSTRUCT_INTERACT = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("tconstruct", "harvestable/interact"));
    private static final TagKey<Block> TCONSTRUCT_STACKABLE = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("tconstruct", "harvestable/stackable"));
    private static final TagKey<net.minecraft.world.item.Item> TCONSTRUCT_SEEDS = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath("tconstruct", "seeds"));
    private static final Map<UUID, Long> TELEPORT_SUPPRESSED_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> MAGNETIC_UNTIL = new ConcurrentHashMap<>();

    private DarkmatterModifierRuntime() { }

    public static void applyMelee(ServerPlayer attacker, LivingEntity target, ItemStack weapon) {
        if (!usable(weapon) || target == attacker || attacker.isAlliedTo(target)) return;
        var profile = DarkmatterItemUtil.shapingProfile(weapon);
        applyTargetEffects(attacker, target, weapon, profile.betaPower(), false);
        if (profile.modifierLevel(DarkmatterModifiers.MAGNETIC) > 0) {
            MAGNETIC_UNTIL.put(attacker.getUUID(), attacker.level().getGameTime() + 30L);
        }
    }

    private static void applyTargetEffects(ServerPlayer attacker, LivingEntity target,
                                           ItemStack weapon, float betaPower,
                                           boolean projectile) {
        var profile = DarkmatterItemUtil.shapingProfile(weapon);
        var suppression = profile.modifierLevel(DarkmatterModifiers.TELEPORT_SUPPRESSION);
        if (suppression > 0) {
            TELEPORT_SUPPRESSED_UNTIL.put(target.getUUID(),
                    target.level().getGameTime() + suppression * 100L);
        }

        var extraDamage = creatureBonus(target, weapon);
        if (extraDamage > 0.0f && target.level() instanceof ServerLevel level) {
            target.invulnerableTime = 0;
            target.hurtServer(level, SkillDamageSource.of(attacker,
                    Skills.DARKMATTER_SHAPING.get()), extraDamage);
        }

        var freezing = profile.modifierLevel(DarkmatterModifiers.FREEZING);
        if (freezing > 0) {
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), 80 + freezing * 60));
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOWNESS, 40 + freezing * 40,
                    Math.max(0, freezing - 1)));
        }
        var burning = profile.modifierLevel(DarkmatterModifiers.BURNING);
        if (burning > 0) target.setRemainingFireTicks(Math.max(
                target.getRemainingFireTicks(), burning * 80));

        var law = profile.modifierLevel(DarkmatterModifiers.LAW_EROSION);
        if (law > 0) DarkmatterLawMark.apply(attacker, target,
                Math.max(betaPower, law * 0.5f), 60 + law * 40);

        var pull = profile.modifierLevel(DarkmatterModifiers.PULL);
        var knockback = profile.modifierLevel(DarkmatterModifiers.KNOCKBACK);
        if (pull > 0) moveTarget(target,
                attacker.position().subtract(target.position()), 0.38 * pull * 2.0);
        else if (knockback > 0) moveTarget(target,
                target.position().subtract(attacker.position()), 0.38 * knockback);

        var teleportProtection = profile.modifierLevel(DarkmatterModifiers.TELEPORT_PROTECTION);
        if (projectile && teleportProtection > 0) {
            randomTeleport(target, 16 + 8 * (teleportProtection - 1));
        }

        var explosion = profile.modifierLevel(DarkmatterModifiers.EXPLOSIVE);
        if (explosion > 0) target.level().explode(attacker, target.getX(), target.getY(),
                target.getZ(), 1.0f + 0.65f * explosion, Level.ExplosionInteraction.NONE);

        var echo = profile.modifierLevel(DarkmatterModifiers.ECHO);
        if (echo > 0 && target.level() instanceof ServerLevel level) {
            var source = SkillDamageSource.of(attacker, Skills.DARKMATTER_SHAPING.get());
            var damage = 0.75f * echo + 0.25f * profile.alphaPower();
            for (var nearby : level.getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(3.0), candidate -> candidate != attacker
                            && candidate != target && candidate.isAlive()
                            && !attacker.isAlliedTo(candidate))) {
                nearby.invulnerableTime = 0;
                nearby.hurtServer(level, source, damage);
            }
        }

        var lightning = profile.modifierLevel(DarkmatterModifiers.LIGHTNING);
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

        var feathers = profile.modifierLevel(DarkmatterModifiers.FEATHER_PURSUIT);
        if (feathers > 0 && target.level() instanceof ServerLevel level) {
            for (var index = 0; index < feathers; index++) {
                var feather = org.academy.internal.common.world.entity.EntityTypes
                        .DARKMATTER_FEATHER_PROJECTILE.get().create(
                                level, EntitySpawnReason.TRIGGERED);
                if (feather == null) continue;
                var angle = Math.PI * 2.0 * index / Math.max(1, feathers);
                feather.configure(attacker, target,
                        new Vec3(Math.cos(angle), 0.1, Math.sin(angle)),
                        0.5f + 0.35f * profile.alphaPower(), 0.0f);
                level.addFreshEntity(feather);
            }
        }
    }

    private static float creatureBonus(LivingEntity target, ItemStack weapon) {
        var profile = DarkmatterItemUtil.shapingProfile(weapon);
        var level = 0;
        if (target.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) {
            level = profile.modifierLevel(DarkmatterModifiers.HOLY);
        } else if (target.getType().builtInRegistryHolder().is(EntityTypeTags.ARTHROPOD)) {
            level = profile.modifierLevel(DarkmatterModifiers.DISMEMBER);
        } else if (target instanceof Player || target instanceof Villager
                || target instanceof AbstractIllager) {
            level = profile.modifierLevel(DarkmatterModifiers.SLAUGHTER);
        } else if (target.getType().builtInRegistryHolder().is(EntityTypeTags.AQUATIC)) {
            level = profile.modifierLevel(DarkmatterModifiers.DRYING);
        } else if (target.fireImmune()) {
            level = profile.modifierLevel(DarkmatterModifiers.EXTINGUISH);
        }
        return level <= 0 ? 0.0f : level * (1.5f + 0.25f * profile.alphaPower());
    }

    private static void moveTarget(LivingEntity target, Vec3 direction, double strength) {
        if (direction.lengthSqr() < 1.0e-8) return;
        var movement = direction.normalize().scale(strength);
        target.setDeltaMovement(target.getDeltaMovement().add(
                movement.x, Math.min(0.35, 0.08 + strength * 0.15), movement.z));
        target.hurtMarked = true;
    }

    private static boolean usable(ItemStack stack) {
        return DarkmatterItemUtil.isShapedItem(stack) && DarkmatterItemUtil.isOperational(stack);
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
        private Events() { }

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
                    && target != owner && !owner.isAlliedTo(target)) {
                applyProjectilePhase(owner, target, source);
                applyTargetEffects(owner, target, source,
                        DarkmatterItemUtil.shapingProfile(source).betaPower(), true);
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
            shearable.shear((ServerLevel) player.level(), SoundSource.PLAYERS, held);
            DarkmatterItemUtil.damageIntegrity(held, 1.0f / 12_000.0f);
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
            var changed = harvest > 0 && harvest(player, held, event.getPos(), harvest,
                    event.getHitVec());
            if (!changed && till > 0) {
                var radius = Math.max(0, till - 1);
                for (var x = -radius; x <= radius; x++) for (var z = -radius; z <= radius; z++) {
                    var pos = event.getPos().offset(x, 0, z);
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP,
                            pos, false);
                    var result = net.minecraft.world.item.Items.NETHERITE_HOE.useOn(
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
            if (!DarkmatterItemUtil.damageIntegrity(held, 15.0f / 12_000.0f)) return;
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
        var profile = DarkmatterItemUtil.shapingProfile(source);
        var damage = DarkmatterShaping.Server.phaseDamageBonus(profile.alphaPower())
                * AbilitySystemServer.getSystem(owner)
                .getPlayerDamageMultiplier(owner.getUUID());
        if (!(damage > 0.0f)) return;
        var penetration = DarkmatterShaping.Server.penetration(
                shape, profile.betaPower());
        var armor = target.getAttribute(Attributes.ARMOR);
        if (armor == null || penetration <= 0.0f) {
            target.hurtServer((ServerLevel) owner.level(),
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
            target.hurtServer((ServerLevel) owner.level(),
                    SkillDamageSource.of(owner, Skills.DARKMATTER_SHAPING.get()), damage);
        } finally {
            armor.removeModifier(PROJECTILE_PENETRATION_ID);
            if (existing != null) armor.addTransientModifier(existing);
        }
    }

    private static boolean harvest(ServerPlayer player, ItemStack held, BlockPos center,
                                   int level, BlockHitResult originalHit) {
        var changed = false;
        var radius = level;
        var world = (ServerLevel) player.level();
        for (var x = -radius; x <= radius; x++) for (var z = -radius; z <= radius; z++) {
            var pos = center.offset(x, 0, z);
            var state = world.getBlockState(pos);
            if (state.is(TCONSTRUCT_INTERACT)) {
                changed |= state.useWithoutItem(world, player,
                        new BlockHitResult(Vec3.atCenterOf(pos), originalHit.getDirection(),
                                pos, false)).consumesAction();
                continue;
            }
            if (state.is(TCONSTRUCT_STACKABLE)) {
                var bottom = pos;
                while (world.getBlockState(bottom.below()).is(TCONSTRUCT_STACKABLE)) {
                    bottom = bottom.below();
                }
                var second = bottom.above();
                if (world.getBlockState(second).is(TCONSTRUCT_STACKABLE)) {
                    changed |= player.gameMode.destroyBlock(second);
                }
                continue;
            }
            var crop = state.getBlock() instanceof CropBlock cropBlock && cropBlock.isMaxAge(state);
            if (!(crop || state.is(TCONSTRUCT_CROPS) || state.is(BlockTags.CROPS))) continue;
            if (!player.gameMode.destroyBlock(pos)) continue;
            changed = true;
            if (consumeNearbySeed(world, pos)) {
                world.setBlock(pos, crop
                        ? ((CropBlock) state.getBlock()).getStateForAge(0)
                        : state.getBlock().defaultBlockState(), 3);
            }
        }
        if (changed) DarkmatterItemUtil.damageIntegrity(held, 1.0f / 12_000.0f);
        return changed;
    }

    private static boolean consumeNearbySeed(ServerLevel level, BlockPos pos) {
        for (var item : level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1.5), entity ->
                        entity.isAlive() && entity.getItem().is(TCONSTRUCT_SEEDS))) {
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
                                         net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
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
        for (var item : ((ServerLevel) player.level()).getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(range), ItemEntity::isAlive)) {
            var delta = player.getEyePosition().subtract(item.position());
            if (delta.lengthSqr() < 0.04) continue;
            item.setDeltaMovement(item.getDeltaMovement().scale(0.35)
                    .add(delta.normalize().scale(0.32)));
            item.hurtMarked = true;
        }
    }
}
