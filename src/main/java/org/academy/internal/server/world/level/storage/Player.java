package org.academy.internal.server.world.level.storage;

import com.google.gson.annotations.SerializedName;
import org.academy.api.common.data.CPData;
import org.academy.internal.common.skilldata.SkillData;

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

    public void setCpData(CPData cpData) {
        if (!Objects.equals(this.cpData, cpData)) {
            this.cpData = cpData;
            markDirty();
        }
    }

    public List<CPData.CPOccupationData> getCpOccupations() {
        return cpOccupations;
    }

    public void setCpOccupations(List<CPData.CPOccupationData> cpOccupations) {
        this.cpOccupations = cpOccupations;
    }
}