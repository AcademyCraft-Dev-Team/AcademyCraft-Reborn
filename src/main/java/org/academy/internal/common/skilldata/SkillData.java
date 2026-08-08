package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

public abstract class SkillData {
    public static final float MIN_PROFICIENCY = 0.0f;
    public static final float MAX_PROFICIENCY = 3000.0f;

    @SerializedName("proficiency")
    private float proficiency;

    /* Legacy progression fields. They are read by SkillDataSerializer and removed on write. */
    @SerializedName("exp")
    private float exp;

    @SerializedName("maxExp")
    private int maxExp = 1000;

    @SerializedName("level")
    private int level = 0;

    @SerializedName("enabled")
    private boolean enabled = true;

    private transient boolean legacyProgress;

    public SkillData() {
        proficiency = 0;
    }

    public SkillData(float exp) {
        setProficiency(exp);
    }

    public SkillData(float exp, int maxExp) {
        setProficiency(exp);
    }

    /** @deprecated Use {@link #isMaxProficiency()}. */
    @Deprecated
    public boolean isMaxExp() {
        return isMaxProficiency();
    }

    /** @deprecated The proficiency cap is always {@value #MAX_PROFICIENCY}. */
    @Deprecated
    public int getMaxExp() {
        return (int) MAX_PROFICIENCY;
    }

    /** @deprecated The proficiency cap is fixed and cannot be changed. */
    @Deprecated
    public void setMaxExp(int maxExp) {
    }

    /** @deprecated Use {@link #getProficiency()}. */
    @Deprecated
    public float getExp() {
        return getProficiency();
    }

    /** @deprecated Use {@link #setProficiency(float)}. */
    @Deprecated
    public void setExp(float exp) {
        setProficiency(exp);
    }

    public float getProficiency() {
        return proficiency;
    }

    public void setProficiency(float proficiency) {
        this.proficiency = Float.isFinite(proficiency)
                ? Math.clamp(proficiency, MIN_PROFICIENCY, MAX_PROFICIENCY)
                : MIN_PROFICIENCY;
        legacyProgress = false;
    }

    public boolean isMaxProficiency() {
        return proficiency >= MAX_PROFICIENCY;
    }

    public boolean hasLegacyProgress() {
        return legacyProgress;
    }

    public void markLegacyProgress(float exp, int maxExp, int level) {
        this.exp = Float.isFinite(exp) ? exp : 0.0f;
        this.maxExp = maxExp > 0 ? maxExp : 1000;
        this.level = level;
        legacyProgress = true;
    }

    public void migrateLegacyProgress(int maxSkillLevel) {
        if (!legacyProgress) return;
        var safeMaxLevel = Math.max(0, maxSkillLevel);
        var safeMaxExp = maxExp > 0 ? maxExp : 1000;
        var legacyFraction = exp / safeMaxExp;
        var legacyTotal = level + legacyFraction;
        setProficiency(legacyTotal / (safeMaxLevel + 1.0f) * MAX_PROFICIENCY);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void toggleEnabled() {
        enabled = !enabled;
    }

    /** @deprecated Runtime effect levels are derived from proficiency and the owning skill. */
    @Deprecated
    public int getLevel() {
        return level;
    }

    /** @deprecated Runtime effect levels are derived from proficiency and the owning skill. */
    @Deprecated
    public void setLevel(int level) {
        this.level = level;
    }

    public abstract Identifier getType();
}
