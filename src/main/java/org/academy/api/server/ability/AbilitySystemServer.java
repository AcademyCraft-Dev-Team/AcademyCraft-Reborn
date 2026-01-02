package org.academy.api.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.api.common.ability.AcquireCategoryPacket;
import org.academy.api.common.ability.LearnSkillPacket;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.pakcet.SyncAbilityCategoryPacket;
import org.academy.api.common.ability.pakcet.SyncSkillDataPacket;
import org.academy.api.common.data.CPData;
import org.academy.api.common.registries.Registries;
import org.academy.api.common.util.MathUtil;
import org.academy.api.common.util.UncheckedUtil;
import org.academy.api.common.wireless.WirelessUser;
import org.academy.api.server.vanilla.MinecraftServerContext;
import org.academy.internal.common.ability.AbilityCategories;
import org.academy.internal.common.attachment.AttachmentTypes;
import org.academy.internal.common.skilldata.CommonSkillData;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.common.world.level.block.entity.AbilityDeveloperBlockEntity;
import org.academy.internal.server.ability.PlayerCPManager;
import org.academy.internal.server.ability.PlayerDataManager;
import org.academy.internal.server.ability.SyncManager;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.world.level.storage.Player;
import org.jetbrains.annotations.NotNull;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.future.annotation.HandleFuture;
import org.slf4j.Logger;

import java.util.*;

public final class AbilitySystemServer {
    private static final Logger LOGGER = AcademyCraft.getLogger();

    private final PlayerDataManager playerDataManager;
    private final PlayerCPManager playerCPManager;
    private final SyncManager syncManager;

    public AbilitySystemServer(MinecraftServerContext context, PlayerDataManager dataManager, AbilityConfig abilityConfig) {
        playerDataManager = dataManager;
        syncManager = new SyncManager(context);
        playerCPManager = new PlayerCPManager(playerDataManager, abilityConfig, syncManager);
        NeoForge.EVENT_BUS.register(playerCPManager);

        syncManager.register(playerCPManager);
        this.syncManager.register(new SyncManager.AbilitySubsystem() {
            @Override
            public void onPlayerLogin(@NotNull ServerPlayer player) {
                playerDataManager.onPlayerLogin(player);
                var uuid = player.getUUID();
                syncManager.schedulePlayerSync(uuid, SyncTypes.ABILITY_CATEGORY);
                syncManager.schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
            }

            @Override
            public void processSync(@NotNull ServerPlayer player, @NotNull Identifier type) {
                var uuid = player.getUUID();
                if (SyncTypes.ABILITY_CATEGORY.equals(type)) {
                    var packet = new SyncAbilityCategoryPacket(getPlayerAbilityCategory(uuid));
                    MisakaNetworkServer.sendPacket(player, packet);
                } else if (SyncTypes.SKILL_DATA.equals(type)) {
                    var skills = getPlayerData(uuid).getSkillData();
                    var packet = new SyncSkillDataPacket(skills);
                    MisakaNetworkServer.sendPacket(player, packet);
                }
            }
        });

        for (var category : Registries.ABILITY_CATEGORIES) {
            category.initServer(context);
        }

        for (var skill : Registries.SKILLS) {
            skill.initServer(context);
        }

        MisakaNetworkServer.FUTURE_MANAGER.registerFutureHandler(AbilitySystemServer.class);
    }

    public Player getPlayerData(UUID uuid) {
        return playerDataManager.getData(uuid);
    }

    @HandleFuture
    public static AcquireCategoryPacket.Response handleAcquireCategory(AcquireCategoryPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var userPos = BlockPos.of(payload.getUserPos());
        if (player.position().distanceToSqr(Vec3.atCenterOf(userPos)) > 64.0) {
            return new AcquireCategoryPacket.Response(Collections.singletonList("Error: You are too far away."));
        }

        var level = player.level();
        var be = level.getBlockEntity(userPos);
        if (be instanceof AbilityDeveloperBlockEntity blockEntity) {
            var outputList = new ArrayList<String>();
            var energyStored = blockEntity.getEnergyStored();
            if (energyStored > 10_000) {
                blockEntity.setEnergyStored(energyStored - 10_000);
                var weightedRandom = new MathUtil.WeightedRandom<AbilityCategory>();
                for (var category : Registries.ABILITY_CATEGORIES) {
                    if (category != AbilityCategories.LEVEL0.get()) {
                        weightedRandom.addItem(category, category.getProbability());
                    }
                }

                var serverContext = (MinecraftServerContext) level.getServer();
                var instance = serverContext.getAcademyCraftServer().getAbilitySystemServer();

                var abilityCategory = weightedRandom.getRandomItem();
                if (abilityCategory != null) {
                    instance.setPlayerAbilityCategory(player.getUUID(), abilityCategory);
                    outputList.add("Learning complete. Type 'exit' to shut down, then reopen the screen to proceed.");
                } else {
                    outputList.add("Error: No ability category could be selected.");
                    LOGGER.error("WeightedRandom returned null for ability category selection.");
                }
            } else {
                outputList.add("Insufficient energy to develop ability.");
            }
            return new AcquireCategoryPacket.Response(outputList);
        }
        return new AcquireCategoryPacket.Response(Collections.singletonList("Error: Block is not an Ability Developer."));
    }

    @HandleFuture
    public static LearnSkillPacket.Response handleLearnSkill(LearnSkillPacket payload) {
        var player = payload.getPacketListener().getPlayer();
        var userPos = BlockPos.of(payload.getUserPos());
        if (player.position().distanceToSqr(Vec3.atCenterOf(userPos)) > 64.0) {
            return new LearnSkillPacket.Response(false);
        }

        var level = player.level();
        var skillKey = payload.getSkillName();
        var be = level.getBlockEntity(userPos);
        if (be instanceof WirelessUser user) {
            var skillReference = Registries.SKILLS.get(Identifier.parse(skillKey));
            if (skillReference.isPresent()) {
                var skill = skillReference.get().value();
                var energy = skill.getEnergyCostToLearn();
                var depLearned = true;
                var serverContext = (MinecraftServerContext) level.getServer();
                var instance = serverContext.getAcademyCraftServer().getAbilitySystemServer();

                for (var dep : skill.getDependencies()) {
                    if (!instance.getPlayerData(player.getUUID()).isSkillLearned(dep.getKeyString())) {
                        depLearned = false;
                        break;
                    }
                }
                var learned = instance.getPlayerData(player.getUUID()).isSkillLearned(skillKey);
                var canLearn = user.getEnergyStored() >= energy && depLearned && !learned;
                if (canLearn) {
                    user.extractEnergy(energy, false);
                    instance.addPlayerSkill(player.getUUID(), skillKey);
                }
                return new LearnSkillPacket.Response(canLearn);
            }
        }
        return new LearnSkillPacket.Response(false);
    }

    public AbilityCategory getPlayerAbilityCategory(UUID uuid) {
        var abilityCategory = getPlayerData(uuid).getAbilityCategory();
        AbilityCategory category;
        if (abilityCategory == null) {
            category = AbilityCategories.LEVEL0.get();
        } else {
            var categoryKey = Identifier.parse(abilityCategory);
            category = Registries.ABILITY_CATEGORIES.get(categoryKey).orElseThrow().value();
        }

        return category;
    }

    public void setPlayerAbilityCategory(UUID uuid, AbilityCategory abilityCategory) {
        var categoryKey = Registries.ABILITY_CATEGORIES.getKey(abilityCategory);
        if (categoryKey != null) {
            getPlayerData(uuid).setAbilityCategory(categoryKey.toString());
            schedulePlayerSync(uuid, SyncTypes.ABILITY_CATEGORY);
        }
    }

    public void addPlayerSkill(UUID uuid, String skillKey) {
        var playerData = getPlayerData(uuid);
        if (playerData.getSkillData().putIfAbsent(skillKey, new CommonSkillData(0)) == null) {
            var skill = Registries.SKILLS.get(Identifier.parse(skillKey));
            playerData.markDirty();
            schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
        }
    }

    public void addPlayerSkillData(UUID uuid, String skillKey, SkillData skillData) {
        var playerData = getPlayerData(uuid);
        var oldValue = playerData.getSkillData().put(skillKey, skillData);
        if (!Objects.equals(oldValue, skillData)) {
            playerData.markDirty();
        }
    }

    public void removePlayerSkill(UUID uuid, String skillKey) {
        var playerData = getPlayerData(uuid);
        if (playerData.getSkillData().remove(skillKey) != null) {
            playerData.markDirty();
            schedulePlayerSync(uuid, SyncTypes.SKILL_DATA);
        }
    }

    /**
     * 请求 CP 占用
     *
     * @param isPassive 是否被动占用（被动占用能使玩家进入个人现实过载状态）
     * @return 是否成功
     */
    public boolean requestCPOccupation(UUID uuid, float amount, int iterationTicks, boolean isPassive) {
        return playerCPManager.requestCPOccupation(uuid, amount, iterationTicks, isPassive);
    }

    public void schedulePlayerSync(final UUID uuid, final Identifier syncType) {
        syncManager.schedulePlayerSync(uuid, syncType);
    }

    public void addTask(Runnable runnable) {
        syncManager.addTask(runnable);
    }

    public void halt() {
        syncManager.halt();
    }

    public void onPlayerLogin(ServerPlayer player) {
        if (playerDataManager != null) {
            playerDataManager.onPlayerLogin(player);
        }
        syncManager.onPlayerLogin(player);
    }

    public void onPlayerLogout(ServerPlayer player) {
        syncManager.onPlayerLogout(player);
    }

    @EventBusSubscriber
    public static final class ServerLifecycleHooks {
        @SubscribeEvent
        public static void tickMinecraftServerThread(ServerTickEvent.Pre event) {
            var server = event.getServer();
            var context = (MinecraftServerContext) server;
            var instance = context.getAcademyCraftServer().getAbilitySystemServer();
            instance.getSyncManager().tick();
        }
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }

    public static void registerContext(ServerContext serverContext) {
        NeoForge.EVENT_BUS.register(serverContext);
        MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(serverContext);
    }

    public static void unregisterContext(ServerContext serverContext) {
        NeoForge.EVENT_BUS.unregister(serverContext);
        MisakaNetworkServer.NETWORK_MANAGER.unregisterPacketListener(serverContext);
    }

    public void flushToData() {
        playerCPManager.flushAllToData();
    }

    public void onServerStopping() {
        playerCPManager.flushAllToData();
        playerCPManager.clear();
        NeoForge.EVENT_BUS.unregister(playerCPManager);
    }

    public static AbilitySystemServer getSystem(Entity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            return ((MinecraftServerContext) serverLevel.getServer())
                    .getAcademyCraftServer()
                    .getAbilitySystemServer();
        }
        throw new IllegalStateException("Entity is not in a ServerLevel");
    }

    public <T extends SkillData> T getPlayerSkillData(UUID uuid, String skillKey) {
        return UncheckedUtil.uncheckedCast(getPlayerData(uuid).getSkillData().get(skillKey));
    }

    public float getPlayerSkillExp(UUID uuid, String skillKey) {
        var skillData = getPlayerData(uuid).getSkillData().get(skillKey);
        if (skillData == null) {
            return 0;
        } else {
            return skillData.exp;
        }
    }

    public float getPlayerOccupiedCP(UUID uuid) {
        return playerCPManager.getOccupiedCP(uuid);
    }

    public int getPlayerLevel(UUID uuid) {
        return playerCPManager.getLevel(uuid);
    }

    public void setPlayerLevel(UUID uuid, int level) {
        playerCPManager.setLevel(uuid, level);
    }

    public float getPlayerAvailableCP(UUID uuid) {
        return playerCPManager.getAvailableCP(uuid);
    }

    public void setPlayerAvailableCP(UUID uuid, float availableCP) {
        playerCPManager.setAvailableCP(uuid, availableCP);
        schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    public float getPlayerMaxCP(UUID uuid) {
        return playerCPManager.getMaxCP(uuid);
    }

    public void setPlayerMaxCP(UUID uuid, float maxCP) {
        playerCPManager.setMaxCP(uuid, maxCP);
    }

    public CPData.Status getPlayerStatus(UUID uuid) {
        return playerCPManager.getStatus(uuid);
    }

    public void setPlayerStatus(UUID uuid, CPData.Status status) {
        playerCPManager.setStatus(uuid, status);
        schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    public int getPlayerStateTimer(UUID uuid) {
        return playerCPManager.getStateTimer(uuid);
    }

    public void setPlayerStateTimer(UUID uuid, int stateTimer) {
        playerCPManager.setStateTimer(uuid, stateTimer);
    }

    public int getPlayerCurrSP(UUID uuid) {
        return playerCPManager.getCurrSP(uuid);
    }

    public void setPlayerCurrSP(UUID uuid, int currSP) {
        playerCPManager.setCurrSP(uuid, currSP);
    }

    public int getPlayerMaxSP(UUID uuid) {
        return playerCPManager.getMaxSP(uuid);
    }

    public void setPlayerMaxSP(UUID uuid, int maxSP) {
        playerCPManager.setMaxSP(uuid, maxSP);
    }

    public static float getSPReductionRate(LivingEntity entity) {
        return entity.getData(AttachmentTypes.SP_REDUCTION_RATE);
    }

    public static void setSPReductionRate(LivingEntity entity, float rate) {
        var clamped = Mth.clamp(rate, 0.0f, 1.0f);
        if (Float.compare(entity.getData(AttachmentTypes.SP_REDUCTION_RATE), clamped) != 0) {
            entity.setData(AttachmentTypes.SP_REDUCTION_RATE, clamped);
        }
    }
}