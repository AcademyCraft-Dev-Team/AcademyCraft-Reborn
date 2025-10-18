package org.academy.internal.server.world.level.storage;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

public final class Player {
    @SerializedName("skills")
    private final HashSet<String> skills = new HashSet<>();
    @SerializedName("skillData")
    private final Map<String, SkillData> skillData = new HashMap<>();
    @SerializedName("abilityCategory")
    private String abilityCategory;
    @SerializedName("level")
    private int level;
    @SerializedName("computingPower")
    private float computingPower = 0f;
    @SerializedName("maxComputingPower")
    private float maxComputingPower = 100f;
    @SerializedName("computingPowerRecoverySpeed")
    private float computingPowerRecoverySpeed = 1f;

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

    public HashSet<String> getSkills() {
        return skills;
    }

    public Map<String, SkillData> getSkillData() {
        return skillData;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        if (this.level != level) {
            this.level = level;
            markDirty();
        }
    }

    public float getComputingPower() {
        return computingPower;
    }

    public void setComputingPower(float computingPower) {
        var clampedPower = Math.min(maxComputingPower, computingPower);
        if (Float.isNaN(clampedPower) || Float.isInfinite(clampedPower)) {
            clampedPower = 0;
        }
        if (Float.compare(this.computingPower, clampedPower) != 0) {
            this.computingPower = clampedPower;
            markDirty();
        }
    }

    public float getMaxComputingPower() {
        return maxComputingPower;
    }

    public void setMaxComputingPower(float maxComputingPower) {
        if (Float.compare(this.maxComputingPower, maxComputingPower) != 0) {
            this.maxComputingPower = maxComputingPower;
            setComputingPower(computingPower);
            markDirty();
        }
    }

    public float getComputingPowerRecoverySpeed() {
        return computingPowerRecoverySpeed;
    }

    public void setComputingPowerRecoverySpeed(float computingPowerRecoverySpeed) {
        if (Float.compare(this.computingPowerRecoverySpeed, computingPowerRecoverySpeed) != 0) {
            this.computingPowerRecoverySpeed = computingPowerRecoverySpeed;
            markDirty();
        }
    }

    public static abstract class SkillData {
        @SerializedName("exp")
        public float exp;
    }
}