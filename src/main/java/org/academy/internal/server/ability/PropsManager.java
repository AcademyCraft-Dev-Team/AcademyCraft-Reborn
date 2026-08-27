package org.academy.internal.server.ability;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.attribute.AbilityFactor;
import org.academy.api.common.attribute.PlayerAttributes;
import org.academy.internal.common.attribute.PlayerAttributeRuntime;
import org.academy.internal.common.attribute.PropsMath;
import org.academy.internal.common.attribute.PropsPackets;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.PropsData;
import org.misaka.MisakaNetworkServer;

import java.util.*;

/**
 * Server-authoritative acquisition and synchronization for P.R.O.P.S factors.
 */
public final class PropsManager implements AbilitySubsystem {
    public static final TagKey<Item> REDSTONE_COMPONENTS = TagKey.create(
            Registries.ITEM, AcademyCraft.academy("props/neural_redstone_components")
    );
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(
            Registries.ENTITY_TYPE, AcademyCraft.academy("props/bosses")
    );

    private static final int ELDER_GUARDIAN_MILESTONE = 1;
    private static final int WITHER_MILESTONE = 1 << 1;
    private static final int ENDER_DRAGON_MILESTONE = 1 << 2;
    private static final int SYNC_INTERVAL_TICKS = 10;
    private static final int STRUCTURE_INTERVAL_TICKS = 20;

    private final PlayerDataManager playerDataManager;
    private final SyncManager syncManager;
    private final Map<UUID, ActivitySnapshot> activity = new HashMap<>();
    private final Map<UUID, ArrayDeque<Float>> healthBeforeDamage = new HashMap<>();
    private final Map<UUID, Integer> foodBeforeConsumption = new HashMap<>();
    private final Map<UUID, CureAttribution> curingPlayers = new HashMap<>();
    private final Set<UUID> dirtySync = new HashSet<>();
    private final Map<UUID, Long> lastSyncTick = new HashMap<>();

    public PropsManager(PlayerDataManager playerDataManager, SyncManager syncManager) {
        this.playerDataManager = playerDataManager;
        this.syncManager = syncManager;
        MisakaNetworkServer.NETWORK_MANAGER.register(PropsPackets.Server.class);
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        var storedPlayer = playerDataManager.getData(player.getUUID());
        if (storedPlayer == null) return;
        var data = storedPlayer.getPropsData();
        if (data.getVersion() < PropsData.CURRENT_VERSION) {
            var initial = new double[AbilityFactor.values().length];
            for (var factor : AbilityFactor.values()) {
                var instance = player.getAttribute(attribute(factor));
                initial[factor.ordinal()] = instance == null ? 0.0 : instance.getBaseValue();
            }
            data.initialize(initial);
            storedPlayer.markDirty();
        } else if (data.repair()) {
            storedPlayer.markDirty();
        }

        mirrorAttributes(player, storedPlayer);
        activity.put(player.getUUID(), ActivitySnapshot.capture(player));
        lastSyncTick.put(player.getUUID(), player.level().getGameTime());
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.PROPS_DATA);
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        activity.remove(player.getUUID());
        healthBeforeDamage.remove(player.getUUID());
        foodBeforeConsumption.remove(player.getUUID());
        dirtySync.remove(player.getUUID());
        lastSyncTick.remove(player.getUUID());
    }

    @Override
    public void tick(ServerPlayer player) {
        var storedPlayer = playerDataManager.getData(player.getUUID());
        if (storedPlayer != null) mirrorAttributes(player, storedPlayer);
        var snapshot = activity.computeIfAbsent(player.getUUID(), _ -> ActivitySnapshot.capture(player));
        tickActivity(player, snapshot);

        var gameTime = player.level().getGameTime();
        if (gameTime % STRUCTURE_INTERVAL_TICKS == 0) checkStructures(player);
        if (gameTime % 1_200 == 0) curingPlayers.entrySet().removeIf(entry -> entry.getValue().expiresAt < gameTime);

        var lastSync = lastSyncTick.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (dirtySync.contains(player.getUUID()) && gameTime - lastSync >= SYNC_INTERVAL_TICKS) {
            syncNow(player);
        }
    }

    @Override
    public void processSync(ServerPlayer player) {
        var storedPlayer = playerDataManager.getData(player.getUUID());
        if (storedPlayer == null) return;
        var data = storedPlayer.getPropsData();
        MisakaNetworkServer.send(player, new PropsPackets.SyncPacket(
                data.copyValues(), data.getLockedMask(), data.isStarted()
        ));
    }

    public void start(ServerPlayer player) {
        var storedPlayer = playerDataManager.getData(player.getUUID());
        if (storedPlayer == null || !storedPlayer.getPropsData().start()) return;
        storedPlayer.markDirty();
        syncNow(player);
    }

    public double award(ServerPlayer player, AbilityFactor factor, double rawAmount, boolean bypassCoefficient) {
        var storedPlayer = playerDataManager.getData(player.getUUID());
        if (storedPlayer == null) return 0.0;
        var data = storedPlayer.getPropsData();
        if (!data.isStarted()) return 0.0;
        if (data.isLocked(factor)) return 0.0;
        var gained = PropsMath.awardedAmount(data.total(), rawAmount, bypassCoefficient);
        if (gained <= 0.0) return 0.0;
        data.set(factor, data.get(factor) + gained);
        storedPlayer.markDirty();
        dirtySync.add(player.getUUID());
        return gained;
    }

    public void setLocked(ServerPlayer player, AbilityFactor factor, boolean locked) {
        var storedPlayer = playerDataManager.getData(player.getUUID());
        if (storedPlayer == null) return;
        if (!storedPlayer.getPropsData().isStarted()) return;
        if (!storedPlayer.getPropsData().setLocked(factor, locked)) return;
        storedPlayer.markDirty();
        syncNow(player);
    }

    public boolean reset(ServerPlayer player) {
        var storedPlayer = playerDataManager.getData(player.getUUID());
        if (storedPlayer == null) return false;

        var uuid = player.getUUID();
        storedPlayer.getPropsData().reset();
        storedPlayer.markDirty();

        activity.put(uuid, ActivitySnapshot.capture(player));
        healthBeforeDamage.remove(uuid);
        foodBeforeConsumption.remove(uuid);
        curingPlayers.entrySet().removeIf(entry -> entry.getValue().playerId.equals(uuid));
        dirtySync.remove(uuid);

        syncNow(player);
        PlayerAttributeRuntime.syncPlayer(player);
        return true;
    }

    private void syncNow(ServerPlayer player) {
        var storedPlayer = playerDataManager.getData(player.getUUID());
        if (storedPlayer == null) return;
        mirrorAttributes(player, storedPlayer);
        dirtySync.remove(player.getUUID());
        lastSyncTick.put(player.getUUID(), player.level().getGameTime());
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.PROPS_DATA);
    }

    private void tickActivity(ServerPlayer player, ActivitySnapshot snapshot) {
        var sprint = stat(player, Stats.SPRINT_ONE_CM);
        var swim = stat(player, Stats.SWIM_ONE_CM);
        var jumps = stat(player, Stats.JUMP);

        var sprintProgress = PropsAcquisition.distanceProgress(
                snapshot.sprintRemainder, sprint, snapshot.sprintStat
        );
        var swimProgress = PropsAcquisition.distanceProgress(
                snapshot.swimRemainder, swim, snapshot.swimStat
        );
        snapshot.sprintRemainder = sprintProgress.remainingCentimeters();
        snapshot.swimRemainder = swimProgress.remainingCentimeters();
        snapshot.sprintStat = sprint;
        snapshot.swimStat = swim;

        if (sprintProgress.blocks() > 0) {
            award(player, AbilityFactor.DEXTERITY, sprintProgress.blocks(), false);
        }
        if (swimProgress.blocks() > 0) {
            award(player, AbilityFactor.DEXTERITY, swimProgress.blocks(), false);
        }

        var completedJumps = PropsAcquisition.statIncrease(jumps, snapshot.jumpStat);
        if (completedJumps > 0) award(player, AbilityFactor.DEXTERITY, completedJumps, false);
        snapshot.jumpStat = jumps;
    }

    private void checkStructures(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var pos = player.blockPosition();
        var structureManager = level.structureManager();
        var structures = structureManager.getAllStructuresAt(pos);
        if (structures.isEmpty()) return;
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var storedPlayer = playerDataManager.getData(player.getUUID());
        if (storedPlayer == null) return;
        var data = storedPlayer.getPropsData();
        if (!data.isStarted()) return;
        var changed = false;

        for (var structure : structures.keySet()) {
            var start = structureManager.getStructureAt(pos, structure);
            if (!start.isValid()) continue;
            var id = registry.getKey(structure);
            if (id == null) continue;
            var chunk = start.getChunkPos();
            var key = level.dimension().identifier() + "|" + id + "|" + chunk.x() + "," + chunk.z();
            if (!data.visitStructure(key)) continue;
            changed = true;
            award(player, AbilityFactor.NEURAL_ACTIVITY, 50.0, true);
        }

        if (changed) {
            storedPlayer.markDirty();
            syncNow(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        healthBeforeDamage.computeIfAbsent(player.getUUID(), _ -> new ArrayDeque<>())
                .push(player.getHealth());
    }

    @SubscribeEvent
    public void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            var healthBefore = popHealthBeforeDamage(victim);
            var healthLost = PropsAcquisition.healthLost(
                    healthBefore == null ? victim.getMaxHealth() : healthBefore,
                    event.getHealthDamage()
            );
            if (healthLost > 0.0) {
                award(victim, AbilityFactor.ENDURANCE, healthLost, false);
            }
        }

        if (!(event.getEntity() instanceof Mob)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (event.getSource().getDirectEntity() != attacker
                || !event.getSource().is(DamageTypes.PLAYER_ATTACK)) return;
        var meleeDamage = PropsAcquisition.meleeDamage(event.getHealthDamage());
        if (meleeDamage > 0.0) {
            award(attacker, AbilityFactor.MUSCLE_STRENGTH, meleeDamage, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExperienceChange(PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        var experience = PropsAcquisition.experienceGained(event.getAmount());
        if (experience > 0) award(player, AbilityFactor.PERCEPTION, experience, false);
    }

    @SubscribeEvent
    public void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        rememberFoodLevel(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        rememberFoodLevel(event);
    }

    @SubscribeEvent
    public void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var foodBefore = foodBeforeConsumption.remove(player.getUUID());
        if (foodBefore == null || !event.getItem().has(DataComponents.FOOD)) return;
        var restored = PropsAcquisition.foodRestored(
                foodBefore, player.getFoodData().getFoodLevel()
        );
        if (restored > 0) award(player, AbilityFactor.ENDURANCE, restored, false);
    }

    @SubscribeEvent
    public void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            foodBeforeConsumption.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onPotionTaken(PlayerBrewedPotionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            award(player, AbilityFactor.NEURAL_ACTIVITY, 5.0, false);
        }
    }

    @SubscribeEvent
    public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var result = event.getCrafting();
        if (result.is(REDSTONE_COMPONENTS)) {
            award(player, AbilityFactor.NEURAL_ACTIVITY, result.getCount() * 5.0, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onUseMap(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        if (event.getItemStack().is(Items.MAP)) {
            award(player, AbilityFactor.NEURAL_ACTIVITY, 5.0, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onStartCure(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        if (!(event.getTarget() instanceof ZombieVillager zombieVillager)) return;
        if (zombieVillager.isConverting()
                || !event.getItemStack().is(Items.GOLDEN_APPLE)
                || !zombieVillager.hasEffect(MobEffects.WEAKNESS)) return;
        curingPlayers.put(zombieVillager.getUUID(), new CureAttribution(
                player.getUUID(), player.level().getGameTime() + 12_000
        ));
    }

    @SubscribeEvent
    public void onConversion(LivingConversionEvent.Post event) {
        if (!(event.getEntity() instanceof ZombieVillager)) return;
        var attribution = curingPlayers.remove(event.getEntity().getUUID());
        if (attribution == null || !(event.getEntity().level() instanceof ServerLevel level)) return;
        var player = level.getServer().getPlayerList().getPlayer(attribution.playerId);
        if (player != null) award(player, AbilityFactor.NEURAL_ACTIVITY, 20.0, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBossDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !event.getEntity().getType().builtInRegistryHolder().is(BOSSES)) return;
        var player = resolvePlayer(event.getSource());
        if (player == null) return;
        award(player, AbilityFactor.NEURAL_ACTIVITY, 50.0, false);

        var storedPlayer = playerDataManager.getData(player.getUUID());
        if (storedPlayer == null) return;
        var data = storedPlayer.getPropsData();
        if (!data.isStarted()) return;
        var special = specialMilestone(event.getEntity().getType());
        if (special != null && data.markMilestone(special.bit)) {
            storedPlayer.markDirty();
            award(player, AbilityFactor.NEURAL_ACTIVITY, special.reward, true);
        }
        syncNow(player);
    }

    private static Milestone specialMilestone(EntityType<?> type) {
        if (type == EntityTypes.ELDER_GUARDIAN) return new Milestone(ELDER_GUARDIAN_MILESTONE, 100.0);
        if (type == EntityTypes.WITHER) return new Milestone(WITHER_MILESTONE, 500.0);
        if (type == EntityTypes.ENDER_DRAGON) return new Milestone(ENDER_DRAGON_MILESTONE, 300.0);
        return null;
    }

    private static ServerPlayer resolvePlayer(DamageSource source) {
        if (source == null) return null;
        var player = resolvePlayer(source.getEntity());
        return player != null ? player : resolvePlayer(source.getDirectEntity());
    }

    private static ServerPlayer resolvePlayer(Entity entity) {
        if (entity instanceof ServerPlayer player) return player;
        if (entity instanceof Projectile projectile) return resolvePlayer(projectile.getOwner());
        if (entity instanceof TamableAnimal tamable) return resolvePlayer(tamable.getOwner());
        return null;
    }

    private static int stat(ServerPlayer player, Identifier id) {
        return player.getStats().getValue(Stats.CUSTOM.get(id));
    }

    private void rememberFoodLevel(LivingEntityUseItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getItem().has(DataComponents.FOOD)) return;
        foodBeforeConsumption.put(player.getUUID(), player.getFoodData().getFoodLevel());
    }

    private Float popHealthBeforeDamage(ServerPlayer player) {
        var stack = healthBeforeDamage.get(player.getUUID());
        if (stack == null || stack.isEmpty()) return null;
        var health = stack.pop();
        if (stack.isEmpty()) healthBeforeDamage.remove(player.getUUID());
        return health;
    }

    private static Holder<Attribute> attribute(AbilityFactor factor) {
        return switch (factor) {
            case MUSCLE_STRENGTH -> PlayerAttributes.MUSCLE_STRENGTH;
            case ENDURANCE -> PlayerAttributes.ENDURANCE;
            case DEXTERITY -> PlayerAttributes.DEXTERITY;
            case PERCEPTION -> PlayerAttributes.PERCEPTION;
            case NEURAL_ACTIVITY -> PlayerAttributes.NEURAL_ACTIVITY;
        };
    }

    private void mirrorAttributes(
            ServerPlayer player,
            Player storedPlayer
    ) {
        var data = storedPlayer.getPropsData();
        var category = playerDataManager.getPlayerAbilityCategory(player.getUUID());
        var bonuses = CommonSkillBonuses.calculate(
                storedPlayer.getSkillDataMap(),
                storedPlayer.getCpData().getLevel().getLevelCode(),
                category.supportsCommonSkills()
        );
        for (var factor : AbilityFactor.values()) {
            var instance = player.getAttribute(attribute(factor));
            var value = data.isStarted() ? data.get(factor) : 0.0;
            value += switch (factor) {
                case MUSCLE_STRENGTH -> bonuses.muscleBonus();
                case ENDURANCE -> bonuses.enduranceBonus();
                case DEXTERITY -> bonuses.dexterityBonus();
                case PERCEPTION, NEURAL_ACTIVITY -> 0.0;
            };
            if (instance != null && Double.compare(instance.getBaseValue(), value) != 0) {
                instance.setBaseValue(value);
            }
        }
    }

    private static final class ActivitySnapshot {
        private int sprintStat;
        private int swimStat;
        private int jumpStat;
        private int sprintRemainder;
        private int swimRemainder;

        private static ActivitySnapshot capture(ServerPlayer player) {
            var snapshot = new ActivitySnapshot();
            snapshot.sprintStat = stat(player, Stats.SPRINT_ONE_CM);
            snapshot.swimStat = stat(player, Stats.SWIM_ONE_CM);
            snapshot.jumpStat = stat(player, Stats.JUMP);
            return snapshot;
        }
    }

    private record CureAttribution(UUID playerId, long expiresAt) {
    }

    private record Milestone(int bit, double reward) {
    }
}
