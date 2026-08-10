package org.academy.internal.server.ability;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.TamableAnimal;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.attribute.AbilityFactor;
import org.academy.api.common.attribute.PlayerAttributes;
import org.academy.internal.common.attribute.PropsMath;
import org.academy.internal.common.attribute.PropsPackets;
import org.academy.internal.server.world.level.storage.Player;
import org.academy.internal.server.world.level.storage.PropsData;
import org.misaka.MisakaNetworkServer;

import java.util.*;
import net.minecraft.util.Mth;

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
    private final Map<UUID, CureAttribution> curingPlayers = new HashMap<>();
    private final Set<UUID> dirtySync = new HashSet<>();
    private final Map<UUID, Long> lastSyncTick = new HashMap<>();

    public PropsManager(PlayerDataManager playerDataManager, SyncManager syncManager) {
        this.playerDataManager = playerDataManager;
        this.syncManager = syncManager;
        MisakaNetworkServer.NETWORK_MANAGER.register(PropsPackets.Server.class);
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

    private static int positiveDelta(int current, int previous) {
        return current >= previous ? current - previous : 0;
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

        snapshot.sprintRemainder += positiveDelta(sprint, snapshot.sprintStat);
        snapshot.swimRemainder += positiveDelta(swim, snapshot.swimStat);
        var sprintBlocks = snapshot.sprintRemainder / 100;
        var swimBlocks = snapshot.swimRemainder / 100;
        snapshot.sprintRemainder %= 100;
        snapshot.swimRemainder %= 100;
        snapshot.sprintStat = sprint;
        snapshot.swimStat = swim;

        if (sprintBlocks > 0) award(player, AbilityFactor.DEXTERITY, sprintBlocks * 0.05, false);
        if (swimBlocks > 0) award(player, AbilityFactor.DEXTERITY, swimBlocks * 0.15, false);

        if (positiveDelta(jumps, snapshot.jumpStat) > 0
                && player.level().getGameTime() - snapshot.lastRewardedJumpTick >= 4) {
            award(player, AbilityFactor.DEXTERITY, 0.5, false);
            snapshot.lastRewardedJumpTick = player.level().getGameTime();
        }
        snapshot.jumpStat = jumps;

        var foodLevel = player.getFoodData().getFoodLevel();
        if (foodLevel > snapshot.foodLevel) {
            award(player, AbilityFactor.ENDURANCE, (foodLevel - snapshot.foodLevel) * 0.5, false);
        }
        snapshot.foodLevel = foodLevel;
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

    @SubscribeEvent
    public void onDamage(LivingDamageEvent.Post event) {
        var wholeDamage = Mth.floor(Math.max(0.0, event.getHealthDamage()));
        if (wholeDamage <= 0.0) return;

        if (event.getEntity() instanceof ServerPlayer victim
                && !event.getSource().is(DamageTypes.GENERIC_KILL)) {
            award(victim, AbilityFactor.ENDURANCE, wholeDamage * 0.5, false);
        }

        var attacker = resolvePlayer(event.getSource());
        if (attacker == null || attacker == event.getEntity()) return;
        award(attacker, AbilityFactor.MUSCLE_STRENGTH, wholeDamage * 0.2, false);
    }

    @SubscribeEvent
    public void onExperiencePickup(PlayerXpEvent.PickupXp event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        award(player, AbilityFactor.PERCEPTION, event.getOrb().getValue() * 0.1, false);
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
        private int foodLevel;
        private long lastRewardedJumpTick = Long.MIN_VALUE / 2;

        private static ActivitySnapshot capture(ServerPlayer player) {
            var snapshot = new ActivitySnapshot();
            snapshot.sprintStat = stat(player, Stats.SPRINT_ONE_CM);
            snapshot.swimStat = stat(player, Stats.SWIM_ONE_CM);
            snapshot.jumpStat = stat(player, Stats.JUMP);
            snapshot.foodLevel = player.getFoodData().getFoodLevel();
            return snapshot;
        }
    }

    private record CureAttribution(UUID playerId, long expiresAt) {
    }

    private record Milestone(int bit, double reward) {
    }
}
