package org.academy.internal.server.world.level.storage;

import com.google.gson.annotations.SerializedName;
import org.academy.api.common.ability.AbilityLevel;
import org.academy.api.common.data.CPData;
import org.academy.internal.common.skilldata.SkillData;
import org.academy.internal.server.ability.PlayerCPManager;

import java.util.*;

public final class Player {
    @SerializedName("skillData")
    private final Map<String, SkillData> skillDataMap = new HashMap<>();
    @SerializedName("abilityCategory")
    private String abilityCategory;

    // CP
    @SerializedName("cpOccupations")
    private List<CPData.CPOccupationData> cpOccupations = new ArrayList<>();
    @SerializedName("cpData")
    private CPData cpData = new CPData();

    private transient volatile boolean isDirty = false;

    public void markDirty() {
        isDirty = true;
    }

    public void clean() {
        isDirty = false;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public String getAbilityCategory() {
        return abilityCategory;
    }

    public void setAbilityCategory(String abilityCategory) {
        if (!Objects.equals(this.abilityCategory, abilityCategory)) {
            this.abilityCategory = abilityCategory;
            markDirty();
        }
    }

    public Map<String, SkillData> getSkillData() {
        return skillDataMap;
    }

    public boolean isSkillLearned(String skillId) {
        return skillDataMap.containsKey(skillId);
    }

    public CPData getCpData() {
        return cpData;
    }

    public int getLevel() {
        return cpData.getLevel().getLevelCode();
    }

    public void setLevel(int level) {
        if (cpData.getLevel().getLevelCode() != level) {
            cpData.setLevel(AbilityLevel.fromLevelCode(level));
            markDirty();
        }
    }

    public float getAvailableCP() {
        return cpData.getAvailableCP();
    }

    public void setAvailableCP(float availableCP) {
        var clampedCP = Math.min(PlayerCPManager.getBasicCP(getLevel()), availableCP);
        if (Float.isNaN(clampedCP) || Float.isInfinite(clampedCP)) {
            clampedCP = 0;
        }
        if (Float.compare(cpData.getAvailableCP(), clampedCP) != 0) {
            cpData.setAvailableCP(clampedCP);
            markDirty();
        }
    }

    public float getMaxCP() {
        return cpData.getMaxCP();
    }

    public void setMaxCP(float newMaxCP) {
        if (Float.compare(cpData.getMaxCP(), newMaxCP) != 0) {
            setAvailableCP(newMaxCP);
            cpData.setMaxCP(newMaxCP);
            markDirty();
        }
    }

    public List<CPData.CPOccupationData> getCPOccupations() {
        return cpOccupations;
    }

    public void setCPOccupations(List<CPData.CPOccupationData> cpOccupations) {
        this.cpOccupations = cpOccupations;
        markDirty();
    }

    public int getCPOverloadTimer() {
        return cpData.getStateTimer();
    }

    public void setCPOverloadTimer(int cpOverloadTimer) {
        cpData.setStateTimer(cpOverloadTimer);
        markDirty();
    }

    public CPData.Status getStatus() {
        return cpData.getStatus();
    }

    public void setStatus(CPData.Status status) {
        cpData.setStatus(status);
        markDirty();
    }
}