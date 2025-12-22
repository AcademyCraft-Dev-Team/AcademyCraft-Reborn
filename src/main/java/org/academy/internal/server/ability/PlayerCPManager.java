package org.academy.internal.server.ability;

import com.mojang.logging.LogUtils;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.data.CPData;
import org.academy.internal.server.world.level.storage.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCPManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static PlayerDataManager playerDataManager;
    private static final Map<UUID, CPContext> playerCpContextMap = new ConcurrentHashMap<>();
    private static final AbilityLevel[] CACHED_LEVELS = AbilityLevel.values();

    private static final int PERSONAL_REALITY_OVERLOAD_TICKS = 100;
    private static final int OVERLOAD_TICKS = 600;
    private static final float CONFIG_Z = 0.0f;
    private static final float CONFIG_Y = 0.0f;
    private static final float CONFIG_X = 0.0f;


    public static void init(PlayerDataManager manager) {
        playerDataManager = manager;
    }

    public static void loadFromData(UUID uuid, Player savedData) {
        var cpData = new CPData.Builder()
                .maxCP(savedData.getMaxCP())
                .availableCP(savedData.getAvailableCP())
                .level(AbilityLevel.values()[savedData.getLevel()])
                .status(savedData.getStatus())
                .stateTimer(savedData.getCPOverloadTimer())
                .build();
        var currData = new CPContext(cpData);
        currData.occupationList.addAll(savedData.getCPOccupations());
        playerCpContextMap.put(uuid, currData);
    }

    public static void flushToData(UUID uuid) {
        var context = playerCpContextMap.get(uuid);
        if (context == null) return;

        if (playerDataManager != null) {
            Player data = playerDataManager.getData(uuid);
            if (data != null) {
                context.exportTo(data);
                data.markDirty();
            }
        }
    }

    public static void flushAllToData() {
        for (Map.Entry<UUID, CPContext> entry : playerCpContextMap.entrySet()) {
            UUID uuid = entry.getKey();
            flushToData(uuid);
        }
    }

    public static boolean tick(UUID uuid) {
        var cpContext = playerCpContextMap.get(uuid);
        if (cpContext == null) return false;
        return cpContext.tick();
    }

    public static boolean requestCPOccupation(UUID uuid, float amount, int iterationTicks, boolean isPassive) {
        var cpContext = playerCpContextMap.get(uuid);
        if (cpContext == null) return false;
        var cpData = cpContext.getCpData();

        if (cpData.getStatus() == CPData.Status.OVERLOAD) return false;
        if (!isPassive && cpData.getAvailableCP() < amount) return false;

        cpContext.addOccupation(new CPData.CPOccupationData(amount, iterationTicks));
        return true;
    }

    public static class CPContext {
        private final List<CPData.CPOccupationData> occupationList = new ArrayList<>();
        private CPData cpData;

        public synchronized void exportTo(Player data) {
            data.setLevel(cpData.getLevel().ordinal());
            data.setAvailableCP(cpData.getAvailableCP());
            data.setMaxCP(cpData.getMaxCP());
            data.setCPOccupations(new ArrayList<>(this.occupationList));
            data.setStatus(cpData.getStatus());
            data.setCPOverloadTimer(cpData.getStateTimer());
        }

        public CPContext(CPData cpData) {
            this.cpData = cpData;
        }

        public synchronized boolean tick() {
            boolean dirty = false;

            boolean stateChanged = switch (cpData.getStatus()) {
                case NORMAL -> tickNormal();
                case PERSONAL_REALITY_OVERLOAD -> tickWarning();
                case OVERLOAD -> tickOverload();
            };
            dirty |= stateChanged;

            boolean iterationChanged = false;
            if (cpData.getStatus() != CPData.Status.OVERLOAD) {
                iterationChanged = processOccupations();
            }
            dirty |= iterationChanged;

            return dirty;
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

        private boolean processOccupations() {
            boolean dirty = false;
            var it = occupationList.iterator();
            while (it.hasNext()) {
                var occupation = it.next();

                // 非过载状态下的CP迭代
                if (cpData.getStatus() == CPData.Status.NORMAL) {
                    occupation.setIterationTicks(occupation.getIterationTicks() - 1);
                }

                // 归还迭代完成的CP
                if (occupation.isFree()) {
                    cpData.setAvailableCP(Math.min(cpData.getMaxCP(), cpData.getAvailableCP() + occupation.getAmount()));
                    it.remove();
                    dirty = true;
                }
            }
            return dirty;
        }

        public synchronized void updateMaxCP(float newMaxCP) {
            if (Float.compare(cpData.getMaxCP(), newMaxCP) != 0) {
                float newAvailableCP = Math.min(newMaxCP, cpData.getAvailableCP() + (newMaxCP - cpData.getMaxCP()));
                cpData.setAvailableCP(newAvailableCP);
                cpData.setMaxCP(newMaxCP);
                checkAndUpgradeLevel();
            }
        }

        private void checkAndUpgradeLevel() {
            float currentMaxCP = cpData.getMaxCP();
            float effectiveCP = currentMaxCP - CONFIG_Z;

            AbilityLevel newLevel = AbilityLevel.LEVEL0;
            for (int i = CACHED_LEVELS.length - 1; i >= 0; i--) {
                AbilityLevel lvl = CACHED_LEVELS[i];
                if (effectiveCP >= lvl.getBasicCP()) {
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
            this.occupationList.add(data);
            cpData.setAvailableCP(cpData.getAvailableCP() - data.getAmount());
        }
    }

    public static void clear() {
        playerCpContextMap.clear();
    }

    public static int getLevel(UUID uuid) {
        return getCPData(uuid).getLevel().getLevelCode();
    }

    public static void setLevel(UUID uuid, int levelCode) {
        getCPData(uuid).setLevel(AbilityLevel.fromLevelCode(levelCode));
    }

    public static float getBasicCP(int levelCode) {
        return AbilityLevel.values()[levelCode].getBasicCP();
    }

    public static float getMaxCP(UUID uuid) {
        return getCPData(uuid).getMaxCP();
    }

    public static void setMaxCP(UUID uuid, float maxCP) {
        var context = playerCpContextMap.get(uuid);
        if (context != null) {
            context.updateMaxCP(maxCP);
        }
    }

    public static float getAvailableCP(UUID uuid) {
        return getCPData(uuid).getAvailableCP();
    }

    public static void setAvailableCP(UUID uuid, float availableCP) {
        var clampedCP = Math.min(PlayerCPManager.getMaxCP(uuid), availableCP);
        if (Float.isNaN(clampedCP) || Float.isInfinite(clampedCP)) {
            clampedCP = 0;
        }
        if (Float.compare(getCPData(uuid).getAvailableCP(), clampedCP) != 0) {
            getCPData(uuid).setAvailableCP(clampedCP);
        }
    }

    public static float getOccupiedCP(UUID uuid) {
        return getCPData(uuid).getMaxCP() - getCPData(uuid).getAvailableCP();
    }

    public static CPData.Status getStatus(UUID uuid) {
        return getCPData(uuid).getStatus();
    }

    public static void setStatus(UUID uuid, CPData.Status status) {
        getCPData(uuid).setStatus(status);
    }

    public static int getStateTimer(UUID uuid) {
        return getCPData(uuid).getStateTimer();
    }

    public static void setStateTimer(UUID uuid, int stateTimer) {
        getCPData(uuid).setStateTimer(stateTimer);
    }

    public static int getCurrSP(UUID uuid) {
        return getCPData(uuid).getCurrSP();
    }

    public static void setCurrSP(UUID uuid, int currSP) {
        getCPData(uuid).setCurrSP(currSP);
    }

    public static int getMaxSP(UUID uuid) {
        return getCPData(uuid).getMaxSP();
    }

    public static void setMaxSP(UUID uuid, int maxSP) {
        getCPData(uuid).setMaxSP(maxSP);
    }


    public static CPData getCPData(UUID uuid) {
        return playerCpContextMap.get(uuid).getCpData();
    }
}
