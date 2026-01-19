package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

public abstract class SkillData {
    @SerializedName("exp")
    private float exp;

    @SerializedName("maxExp")
    private int maxExp = 1000;

    @SerializedName("level")
    private int level = 0;

    @SerializedName("enabled")
    private boolean enabled = true;

    public SkillData() {
        exp = 0;
    }

    public SkillData(float exp) {
        this.exp = exp;
    }

    public SkillData(float exp, int maxExp) {
        this.exp = exp;
        this.maxExp = maxExp;
    }

    public boolean isMaxExp() {
        return exp >= maxExp;
    }

    public int getMaxExp() {
        return maxExp;
    }

    public void setMaxExp(int maxExp) {
        this.maxExp = maxExp;
    }

    public float getExp() {
        return exp;
    }

    public void setExp(float exp) {
        this.exp = exp;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggleEnabled() {
        enabled = !enabled;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public abstract Identifier getType();
}