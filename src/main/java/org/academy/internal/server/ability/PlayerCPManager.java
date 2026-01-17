package org.academy.internal.server.ability;

import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
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

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public class PlayerCPManager implements AbilitySubsystem {
    private static final Logger LOGGER = AcademyCraft.getLogger();
    private static final AbilityLevel[] CACHED_LEVELS = AbilityLevel.values();
    private static final int PERSONAL_REALITY_OVERLOAD_TICKS = 100;
    private static final int OVERLOAD_TICKS = 600;

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
        syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.CP_DATA);
    }

    @Override
    public void tick(ServerPlayer player) {
        var playerData = playerDataManager.getData(player.getUUID());
        if (playerData == null) return;

        var cpData = playerData.getCpData();
        var occupations = playerData.getCpOccupations();

        boolean dirty = false;

        dirty |= switch (cpData.getStatus()) {
            case NORMAL -> tickNormal(cpData);
            case PERSONAL_REALITY_OVERLOAD -> tickWarning(cpData);
            case OVERLOAD -> tickOverload(cpData, occupations);
        };

        if (cpData.getStatus() != CPData.Status.OVERLOAD) {
            dirty |= processOccupations(player, cpData, occupations);
        }

        dirty |= tickSpRegen(player, cpData);

        if (dirty || cpData.isDirty()) {
            playerData.markDirty();
            syncManager.schedulePlayerSync(player.getUUID(), SyncTypes.CP_DATA);
        }
    }

    @Override
    public void processSync(ServerPlayer serverPlayer) {
        Player player = playerDataManager.getData(serverPlayer.getUUID());
        if (player == null) return;
        var cpData = player.getCpData();
        MisakaNetworkServer.sendPacket(serverPlayer, new SyncCPDataPacket(cpData));
        cpData.clean();
    }

    private boolean tickNormal(CPData cpData) {
        if (cpData.getAvailableCP() < 0) {
            cpData.setStatus(CPData.Status.PERSONAL_REALITY_OVERLOAD);
            cpData.setStateTimer(PERSONAL_REALITY_OVERLOAD_TICKS);
            return true;
        }
        return false;
    }

    private boolean tickWarning(CPData cpData) {
        cpData.setStateTimer(cpData.getStateTimer() - 1);
        if (cpData.getStateTimer() <= 0) {
            cpData.setStatus(CPData.Status.OVERLOAD);
            cpData.setStateTimer(OVERLOAD_TICKS);
            return true;
        }
        return false;
    }

    private boolean tickOverload(CPData cpData, List<CPData.CPOccupationData> occupations) {
        cpData.setStateTimer(cpData.getStateTimer() - 1);
        if (cpData.getStateTimer() <= 0) {
            cpData.setStatus(CPData.Status.NORMAL);
            cpData.setStateTimer(0);

            occupations.clear(); // 清空占用队列
            cpData.setAvailableCP(cpData.getMaxCP()); // 恢复 CP
            return true;
        }
        return false;
    }

    private boolean tickSpRegen(ServerPlayer player, CPData cpData) {
        if (cpData.getCurrSP() >= cpData.getMaxSP()) return false;
        if (player.getFoodData().getSaturationLevel() <= 0) return false;

        int timer = cpData.getSpRegenTimer() + 1;
        if (timer >= 20) {
            cpData.setSpRegenTimer(0);
            cpData.addSP(1);
            return true;
        } else {
            cpData.setSpRegenTimer(timer);
            return false;
        }
    }

    private boolean processOccupations(ServerPlayer player, CPData cpData, List<CPData.CPOccupationData> occupations) {
        boolean dirty = false;
        var it = occupations.iterator();
        while (it.hasNext()) {
            var occupation = it.next();
            // 正常状态下的cp迭代
            if (cpData.getStatus() != CPData.Status.OVERLOAD) {
                occupation.setIterationTicks(Math.max(occupation.getIterationTicks() - 1, 0));
            }

            if (!occupation.isFree()) continue;
            //sp消耗 =（cp迭代量*系数X）* 50% * sp消耗减少率
            var spReductionRate = AbilitySystemServer.getSPReductionRate(player);
            var spCost = (int) (occupation.getAmount() * DAMAGE_MULTIPLIER * 0.5f * spReductionRate);
            if (cpData.getCurrSP() < spCost) continue;
            cpData.addSP(-spCost);

            // 归还迭代完成的CP占用
            cpData.setAvailableCP(cpData.getAvailableCP() + occupation.getAmount());
            it.remove();
            dirty = true;
        }
        return dirty;
    }

    public boolean requestCPOccupation(UUID uuid, float amount, int iterationTicks, boolean isPassive) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) return false;

        var cpData = playerData.getCpData();
        var occupations = playerData.getCpOccupations();

        if (cpData.getStatus() == CPData.Status.OVERLOAD) return false;
        if (!isPassive && cpData.getAvailableCP() < amount) return false;

        occupations.add(new CPData.CPOccupationData(amount, iterationTicks));
        cpData.setAvailableCP(cpData.getAvailableCP() - amount);
        return true;
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
                        addCurrSP(player.getUUID(), spRecovery);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()) {
            var dayTime = player.level().getDayTime() % 24000;
            var isMorning = dayTime >= 0 && dayTime < 2000;
            if (isMorning) {
                modify(player.getUUID(), data -> data.setCurrSP(data.getMaxSP()));
            }
        }
    }

    @SubscribeEvent
    public void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        var display = event.getAdvancement().value().display().orElse(null);
        if (display != null && display.getType() == AdvancementType.CHALLENGE) {
            var uuid = event.getEntity().getUUID();
            setMaxCP(uuid, getMaxCP(uuid) + 5f);
        }
    }

    private void modify(UUID uuid, Consumer<CPData> action) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData != null) {
            var cpData = playerData.getCpData();
            action.accept(cpData);
            if (cpData.isDirty()) {
                playerData.markDirty();
            }
        }
    }

    private <T> T query(UUID uuid, Function<CPData, T> mapper, T defaultValue) {
        var playerData = playerDataManager.getData(uuid);
        if (playerData == null) {
            return defaultValue;
        }
        return mapper.apply(playerData.getCpData());
    }

    public int getLevel(UUID uuid) {
        return query(uuid, CPData::getLevel, AbilityLevel.LEVEL0).getLevelCode();
    }

    public void setLevel(UUID uuid, int levelCode) {
        modify(uuid, cpData -> cpData.setLevel(AbilityLevel.fromLevelCode(levelCode)));
    }

    public float getLevelBasicCP(int levelCode) {
        return AbilityLevel.values()[levelCode].getBasicCP();
    }

    public void setMaxCP(UUID uuid, float newMaxCP) {
        modify(uuid, cpData -> {
            var maxCP = cpData.getMaxCP();
            if (Float.compare(maxCP, newMaxCP) == 0) return;

            float diff = newMaxCP - maxCP;
            float newAvailableCP = cpData.getAvailableCP() + diff;

            cpData.setAvailableCP(newAvailableCP);
            cpData.setMaxCP(newMaxCP);
            checkAndUpgradeLevel(uuid, cpData);
        });
    }

    private void checkAndUpgradeLevel(UUID uuid, CPData cpData) {
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

    public float getMaxCP(UUID uuid) {
        return query(uuid, CPData::getMaxCP, 0f);
    }

    public float getAvailableCP(UUID uuid) {
        return query(uuid, CPData::getAvailableCP, 0f);
    }

    public void setAvailableCP(UUID uuid, float availableCP) {
        var safeCP = Float.isFinite(availableCP) ? availableCP : 0f;
        modify(uuid, cpData -> {
            if (Float.compare(cpData.getAvailableCP(), safeCP) != 0) {
                cpData.setAvailableCP(safeCP);
            }
        });
    }

    public float getOccupiedCP(UUID uuid) {
        return getMaxCP(uuid) - getAvailableCP(uuid);
    }

    public CPData.Status getStatus(UUID uuid) {
        return query(uuid, CPData::getStatus, CPData.Status.NORMAL);
    }

    public void setStatus(UUID uuid, CPData.Status status) {
        modify(uuid, cpData -> cpData.setStatus(status));
    }

    public int getStateTimer(UUID uuid) {
        return query(uuid, CPData::getStateTimer, 0);
    }

    public void setStateTimer(UUID uuid, int stateTimer) {
        modify(uuid, cpData -> cpData.setStateTimer(stateTimer));
    }

    public int getCurrSP(UUID uuid) {
        return query(uuid, CPData::getCurrSP, 0);
    }

    public void setCurrSP(UUID uuid, int currSP) {
        modify(uuid, cpData -> cpData.setCurrSP(currSP));
    }

    public void addCurrSP(UUID uuid, int addSP) {
        modify(uuid, cpData -> cpData.addSP(addSP));
    }

    public int getMaxSP(UUID uuid) {
        return query(uuid, CPData::getMaxSP, 0);
    }

    public void setMaxSP(UUID uuid, int maxSP) {
        modify(uuid, cpData -> cpData.setMaxSP(maxSP));
    }
}
