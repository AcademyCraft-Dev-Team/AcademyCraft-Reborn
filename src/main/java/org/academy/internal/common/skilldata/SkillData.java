package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

public abstract class SkillData {
    @SerializedName("exp")
    public float exp;

    @SerializedName("maxExp")
    public int maxExp = 200;

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

    public abstract Identifier getType();
}