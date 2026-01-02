package org.academy.internal.server.ability;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.ability.SyncTypes;
import org.academy.api.common.ability.pakcet.SyncCPDataPacket;
import org.academy.api.common.data.CPData;
import org.academy.api.server.ability.AbilitySystemServer;
import org.academy.internal.server.config.AbilityConfig;
import org.academy.internal.server.world.level.storage.Player;
import org.misaka.MisakaNetworkServer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCPManager implements SyncManager.AbilitySubsystem {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final AbilityLevel[] CACHED_LEVELS = AbilityLevel.values();
    private static final int PERSONAL_REALITY_OVERLOAD_TICKS = 100;
    private static final int OVERLOAD_TICKS = 600;

    private final Map<UUID, CPContext> contexts = new ConcurrentHashMap<>();
    private final PlayerDataManager playerDataManager;
    private final SyncManager syncManager;

    private final float CP_RATING_OFFSET;//等级评定修正值(Z)
    private final float DAMAGE_MULTIPLIER;//伤害修正值(X)

    public PlayerCPManager(PlayerDataManager manager, AbilityConfig config, SyncManager syncManager) {
        playerDataManager = manager;
        this.syncManager = syncManager;

        CP_RATING_OFFSET = config.cpRatingOffset;
        DAMAGE_MULTIPLIER = config.damageMultiplier;
    }

    @Override
    public void onPlayerLogin(ServerPlayer player) {
        var uuid = player.getUUID();
        var savedData = playerDataManager.getData(uuid);
        if (savedData != null) {
            loadFromData(uuid, savedData);
        }
        syncManager.schedulePlayerSync(uuid, SyncTypes.CP_DATA);
    }

    @Override
    public void tick(ServerPlayer player) {
        var cpContext = contexts.get(player.getUUID());
        if (cpContext == null) {
            LOGGER.error("Player {} has no CPContext", player.getUUID());
            return;
        }
        if (cpContext.tick(player)) {
            syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.CP_DATA);
        }
    }

    @Override
    public void onPlayerLogout(ServerPlayer player) {
        var uuid = player.getUUID();
        var context = contexts.remove(uuid);
        if (context != null) {
            var data = playerDataManager.getData(uuid);
            if (data != null) {
                context.exportTo(data);
                data.markDirty();
            }
        }
    }

    @Override
    public void processSync(ServerPlayer player, Identifier type) {
        if (SyncTypes.CP_DATA.equals(type)) {
            getCPDataOptional(player.getUUID()).ifPresentOrElse(
                    data -> MisakaNetworkServer.sendPacket(player, new SyncCPDataPacket(data)),
                    () -> LOGGER.warn("CPData is null for player {}", player.getUUID())
            );
        }
    }

    public void loadFromData(UUID uuid, Player savedData) {
        var cpData = savedData.getCpData();
        var currCPContext = new CPContext(cpData);
        currCPContext.occupationList.addAll(savedData.getCpOccupations());
        contexts.put(uuid, currCPContext);
    }

    public void flushToData(UUID uuid) {
        var context = contexts.get(uuid);
        if (context == null) return;

        var data = playerDataManager.getData(uuid);
        if (data != null) {
            context.exportTo(data);
            data.markDirty();
        }
    }

    public void flushAllToData() {
        for (var entry : contexts.entrySet()) {
            var uuid = entry.getKey();
            flushToData(uuid);
        }
    }

    public boolean requestCPOccupation(UUID uuid, float amount, int iterationTicks, boolean isPassive) {
        var cpContext = contexts.get(uuid);
        if (cpContext == null) return false;
        var cpData = cpContext.getCpData();

        if (cpData.getStatus() == CPData.Status.OVERLOAD) return false;
        if (!isPassive && cpData.getAvailableCP() < amount) return false;

        cpContext.addOccupation(new CPData.CPOccupationData(amount, iterationTicks));
        return true;
    }

    private class CPContext {
        private final List<CPData.CPOccupationData> occupationList = new ArrayList<>();
        private final CPData cpData;
        boolean dirty = false;
        private int spRegenTimer = 0;

        public synchronized void exportTo(Player data) {
            data.setCpData(new CPData(cpData));
            data.setCpOccupations(new ArrayList<>(occupationList));
        }

        public CPContext(CPData cpData) {
            this.cpData = cpData;
        }

        public synchronized boolean tick(ServerPlayer player) {
            dirty = false;
            var stateChanged = switch (cpData.getStatus()) {
                case NORMAL -> tickNormal();
                case PERSONAL_REALITY_OVERLOAD -> tickWarning();
                case OVERLOAD -> tickOverload();
            };
            dirty |= stateChanged;

            var iterationChanged = false;
            if (cpData.getStatus() != CPData.Status.OVERLOAD) {
                iterationChanged = processOccupations(player);
            }
            dirty |= iterationChanged;

            var spRegen = spRegen(player);
            dirty |= spRegen;

            dirty |= cpData.isDirty();
            cpData.clean();
            return dirty;
        }

        private boolean spRegen(ServerPlayer player) {
            if (cpData.getCurrSP() >= cpData.getMaxSP()) return false;
            if (player.getFoodData().getSaturationLevel() <= 0) return false;
            if (++spRegenTimer >= 20) {
                spRegenTimer = 0;
                cpData.setCurrSP(cpData.getCurrSP() + 1);
                return true;
            }
            return false;
        }

        private boolean tickNormal() {
            if (cpData.getAvailableCP() < 0) {
                cpData.setStatus(CPData.Status.PERSONAL_REALITY_OVERLOAD);
                cpData.setStateTimer(PERSONAL_REALITY_OVERLOAD_TICKS);
                return true;
            }
            return false;
        }

        private boolean tickWarning() {
            cpData.setStateTimer(cpData.getStateTimer() - 1);
            if (cpData.getStateTimer() <= 0) {
                cpData.setStatus(CPData.Status.OVERLOAD);
                cpData.setStateTimer(OVERLOAD_TICKS);
                return true;
            }
            return false;
        }

        private boolean tickOverload() {
            cpData.setStateTimer(cpData.getStateTimer() - 1);
            if (cpData.getStateTimer() <= 0) {
                cpData.setStatus(CPData.Status.NORMAL);
                cpData.setStateTimer(0);

                occupationList.clear();
                cpData.setAvailableCP(cpData.getMaxCP());
                return true;
            }
            return false;
        }

        private boolean processOccupations(ServerPlayer player) {
            var dirty = false;
            var it = occupationList.iterator();
            while (it.hasNext()) {
                var occupation = it.next();

                // 非过载状态下的CP迭代
                if (cpData.getStatus() == CPData.Status.NORMAL) {
                    occupation.setIterationTicks(Math.max(occupation.getIterationTicks() - 1, 0));
                }

                // 迭代完成的CP归还
                if (!occupation.isFree()) continue;
                //sp消耗 =（cp迭代量*系数X）* 50% * sp消耗减少率
                var spReductionRate = AbilitySystemServer.getSPReductionRate(player);
                var spCost = (int) (occupation.getAmount() * DAMAGE_MULTIPLIER * 0.5f * spReductionRate);
                if (cpData.getCurrSP() < spCost) continue;

                cpData.setCurrSP(Math.max(0, cpData.getCurrSP() - spCost));
                cpData.setAvailableCP(Math.min(cpData.getMaxCP(), cpData.getAvailableCP() + occupation.getAmount()));
                it.remove();
                dirty = true;
            }
            return dirty;
        }

        public synchronized void updateMaxCP(float newMaxCP) {
            var maxCP = cpData.getMaxCP();
            if (Float.compare(maxCP, newMaxCP) == 0) return;

            var availableCP = cpData.getAvailableCP();
            float newAvailableCP;
            if (Math.abs(availableCP - maxCP) < 1.0E-5F) {
                newAvailableCP = newMaxCP;
            } else {
                newAvailableCP = Math.min(newMaxCP, cpData.getAvailableCP() + (newMaxCP - cpData.getMaxCP()));
            }

            cpData.setAvailableCP(newAvailableCP);
            cpData.setMaxCP(newMaxCP);
            checkAndUpgradeLevel();
        }

        private void checkAndUpgradeLevel() {
            var currentMaxCP = cpData.getMaxCP();
            var newLevel = AbilityLevel.LEVEL0;

            for (var i = CACHED_LEVELS.length - 1; i >= 0; i--) {
                var lvl = CACHED_LEVELS[i];
                if (currentMaxCP >= lvl.getBasicCP() - CP_RATING_OFFSET) {
                    newLevel = lvl;
                    break;
                }
            }

            if (newLevel != cpData.getLevel()) {
                LOGGER.info("Player Level Changed: {} -> {} (MaxCP: {})", cpData.getLevel(), newLevel, currentMaxCP);
                cpData.setLevel(newLevel);
            }

        }

        public CPData getCpData() {
            return cpData;
        }

        public synchronized void addOccupation(CPData.CPOccupationData data) {
            occupationList.add(data);
            cpData.setAvailableCP(cpData.getAvailableCP() - data.getAmount());
        }

        public void addSP(int amount) {
            cpData.setCurrSP(Math.min(cpData.getMaxSP(), cpData.getCurrSP() + amount));
            dirty = true;
        }

        public void fullRestoreSP() {
            if (cpData.getCurrSP() != cpData.getMaxSP()) {
                cpData.setCurrSP(cpData.getMaxSP());
                dirty = true;
            }
        }
    }

    private void restoreSP(UUID uuid, int amount) {
        var context = contexts.get(uuid);
        if (context != null) {
            context.addSP(amount);
        }
    }

    private void fullRestoreSP(UUID uuid) {
        var context = contexts.get(uuid);
        if (context != null) {
            context.fullRestoreSP();
        }
    }

    @SubscribeEvent
    public void onPlayerEat(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()) {
            var itemStack = event.getItem();
            if (itemStack.has(DataComponents.FOOD)) {
                var food = itemStack.get(DataComponents.FOOD);
                if (food != null) {
                    var saturationGained = food.nutrition() * food.saturation() * 2.0f;
                    if (saturationGained > 0) {
                        var spRecovery = (int) (saturationGained * 5);
                        restoreSP(player.getUUID(), spRecovery);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()) {
            if (event.updateLevel()) {
                fullRestoreSP(player.getUUID());
            }
        }
    }

    public void clear() {
        contexts.clear();
    }

    public int getLevel(UUID uuid) {
        return getCPDataOptional(uuid).map(CPData::getLevel).map(AbilityLevel::getLevelCode).orElse(0);
    }

    public void setLevel(UUID uuid, int levelCode) {
        getCPDataOptional(uuid).ifPresent(data -> data.setLevel(AbilityLevel.fromLevelCode(levelCode)));
    }

    public float getBasicCP(int levelCode) {
        return AbilityLevel.values()[levelCode].getBasicCP();
    }

    public float getMaxCP(UUID uuid) {
        return getCPDataOptional(uuid).map(CPData::getMaxCP).orElse(0f);
    }

    public void setMaxCP(UUID uuid, float maxCP) {
        var context = contexts.get(uuid);
        if (context != null) {
            context.updateMaxCP(maxCP);
        }
    }

    public float getAvailableCP(UUID uuid) {
        return getCPDataOptional(uuid).map(CPData::getAvailableCP).orElse(0f);
    }

    public void setAvailableCP(UUID uuid, float availableCP) {
        var safeCP = Float.isFinite(availableCP) ? availableCP : 0f;

        getCPDataOptional(uuid).ifPresent(data -> {
            var clamped = Math.min(data.getMaxCP(), safeCP);
            if (Float.compare(data.getAvailableCP(), clamped) != 0) {
                data.setAvailableCP(clamped);
            }
        });
    }

    public float getOccupiedCP(UUID uuid) {
        return getMaxCP(uuid) - getAvailableCP(uuid);
    }

    public CPData.Status getStatus(UUID uuid) {
        return getCPDataOptional(uuid).map(CPData::getStatus).orElse(CPData.Status.NORMAL);
    }

    public void setStatus(UUID uuid, CPData.Status status) {
        getCPDataOptional(uuid).ifPresent(data -> data.setStatus(status));
    }

    public int getStateTimer(UUID uuid) {
        return getCPDataOptional(uuid).map(CPData::getStateTimer).orElse(0);
    }

    public void setStateTimer(UUID uuid, int stateTimer) {
        getCPDataOptional(uuid).ifPresent(data -> data.setStateTimer(stateTimer));
    }

    public int getCurrSP(UUID uuid) {
        return getCPDataOptional(uuid).map(CPData::getCurrSP).orElse(0);
    }

    public void setCurrSP(UUID uuid, int currSP) {
        getCPDataOptional(uuid).ifPresent(data -> data.setCurrSP(currSP));
    }

    public int getMaxSP(UUID uuid) {
        return getCPDataOptional(uuid).map(CPData::getMaxSP).orElse(0);
    }

    public void setMaxSP(UUID uuid, int maxSP) {
        getCPDataOptional(uuid).ifPresent(data -> data.setMaxSP(maxSP));
    }

    public Optional<CPData> getCPDataOptional(UUID uuid) {
        return Optional.ofNullable(contexts.get(uuid))
                .map(CPContext::getCpData);
    }
}
