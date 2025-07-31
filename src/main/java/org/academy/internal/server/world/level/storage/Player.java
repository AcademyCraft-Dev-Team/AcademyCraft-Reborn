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

    // 1. 添加 transient volatile 的 isDirty 标志
    // transient: 防止Gson序列化这个字段
    // volatile: 确保多线程间的可见性，为未来的异步保存做准备
    private transient volatile boolean isDirty = false;

    // 2. 添加管理 isDirty 状态的公共方法
    public void markDirty() {
        this.isDirty = true;
    }

    public void clean() {
        this.isDirty = false;
    }

    public boolean isDirty() {
        return isDirty;
    }

    // 3. 修改所有的 setter 方法，在数据真正改变时调用 markDirty()
    public String getAbilityCategory() {
        return abilityCategory;
    }

    public void setAbilityCategory(String abilityCategory) {
        // 使用 Objects.equals 处理 null 的情况
        if (!Objects.equals(this.abilityCategory, abilityCategory)) {
            this.abilityCategory = abilityCategory;
            markDirty();
        }
    }

    public HashSet<String> getSkills() {
        // 注意：直接修改返回的集合不会触发 markDirty。这是一个更深层次的问题，
        // 暂时我们假设通过 addPlayerSkill 等方法来修改，后续步骤再优化。
        // 为了安全，任何可能修改集合的操作都应该调用 markDirty()。
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
        // 确保CP值不会超过上限
        float clampedPower = Math.min(this.maxComputingPower, computingPower);
        if (Float.isNaN(clampedPower) || Float.isInfinite(clampedPower)) {
            clampedPower = 0;
        }
        // 使用 Float.compare 来精确比较浮点数
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
            // 当最大值改变时，当前值也可能需要调整
            setComputingPower(this.computingPower);
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
        // 如果SkillData内部有修改，也需要一种机制来通知Player对象 markDirty()
        // 这将在后续步骤中处理。
    }
}