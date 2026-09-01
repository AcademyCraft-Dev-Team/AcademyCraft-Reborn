package org.academy.internal.common.skilldata;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.academy.AcademyCraft;

import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Objects;
import java.util.function.Consumer;

public abstract class SkillData {
    public static final float MIN_PROFICIENCY = 0.0f;
    public static final float MAX_PROFICIENCY = 3000.0f;
    private static final StackWalker STATE_STACK_WALKER = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE
    );
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
    private transient Class<?> activationOwner;
    private transient Consumer<Boolean> activationStateListener;

    public SkillData() {
        proficiency = 0;
    }

    public SkillData(float exp) {
        setProficiency(exp);
    }

    public SkillData(float exp, int maxExp) {
        setProficiency(exp);
    }

    public static int getProficiencyTier(float proficiency) {
        if (!Float.isFinite(proficiency) || proficiency < 0.0f) return 1;
        if (proficiency >= 3000.0f) return 4;
        if (proficiency >= 2000.0f) return 3;
        if (proficiency >= 1000.0f) return 2;
        return 1;
    }

    public static int getReachedProficiencyThresholds(float proficiency) {
        return Math.max(0, getProficiencyTier(proficiency) - 1);
    }

    /**
     * @deprecated Use {@link #isMaxProficiency()}.
     */
    @Deprecated
    public boolean isMaxExp() {
        return isMaxProficiency();
    }

    /**
     * @deprecated The proficiency cap is always {@value #MAX_PROFICIENCY}.
     */
    @Deprecated
    public int getMaxExp() {
        return (int) MAX_PROFICIENCY;
    }

    /**
     * @deprecated The proficiency cap is fixed and cannot be changed.
     */
    @Deprecated
    public void setMaxExp(int maxExp) {
    }

    /**
     * @deprecated Use {@link #getProficiency()}.
     */
    @Deprecated
    public float getExp() {
        return getProficiency();
    }

    /**
     * @deprecated Use {@link #setProficiency(float)}.
     */
    @Deprecated
    public void setExp(float exp) {
        setProficiency(exp);
    }

    public float getProficiency() {
        return proficiency;
    }

    public void setProficiency(float proficiency) {
        this.proficiency = Float.isFinite(proficiency)
                ? Mth.clamp(proficiency, MIN_PROFICIENCY, MAX_PROFICIENCY)
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

    public final void setEnabled(boolean enabled) {
        if (activationOwner != null
                && !isStateMutationCallerAllowed("setEnabled", activationOwner)) return;
        setEnabledAndNotify(enabled);
    }

    public final void bindActivationProtection(
            Class<?> owner,
            Consumer<Boolean> activationStateListener
    ) {
        if (!isStateMutationCallerAllowed("bindActivationProtection", AcademyCraft.class)) return;
        activationOwner = Objects.requireNonNull(owner, "owner");
        this.activationStateListener = Objects.requireNonNull(
                activationStateListener,
                "activationStateListener"
        );
    }

    public final boolean isActivationProtectedFor(Class<?> owner) {
        return activationOwner == owner && activationStateListener != null;
    }

    public final void applyPersistedEnabled(boolean enabled) {
        if (!isStateMutationCallerAllowed("applyPersistedEnabled", AcademyCraft.class)) return;
        this.enabled = enabled;
    }

    public final void toggleEnabled() {
        if (activationOwner != null
                && !isStateMutationCallerAllowed("toggleEnabled", activationOwner)) return;
        setEnabledAndNotify(!enabled);
    }

    private void setEnabledAndNotify(boolean enabled) {
        if (activationOwner != null
                && !isStateMutationCallerAllowed("setEnabledAndNotify", activationOwner)) return;
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (activationStateListener != null) activationStateListener.accept(enabled);
    }

    private static boolean isStateMutationCallerAllowed(String entryMethod, Class<?> owner) {
        var caller = STATE_STACK_WALKER.walk(frames -> frames
                        .dropWhile(frame -> frame.getDeclaringClass() != SkillData.class
                                || !frame.getMethodName().equals(entryMethod))
                        .skip(1)
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .findFirst()
                        .orElse(null));
        return sameStateCodeSource(caller, AcademyCraft.class)
                || sameStateCodeSource(caller, owner);
    }

    private static boolean sameStateCodeSource(Class<?> left, Class<?> right) {
        if (left == null || right == null) return false;
        var leftDomain = stateProtectionDomain(left);
        var rightDomain = stateProtectionDomain(right);
        if (leftDomain != null && leftDomain == rightDomain) return true;
        var leftLocation = stateCodeSourceLocation(leftDomain);
        var rightLocation = stateCodeSourceLocation(rightDomain);
        return leftLocation != null && leftLocation.equals(rightLocation);
    }

    private static ProtectionDomain stateProtectionDomain(Class<?> type) {
        try {
            return type.getProtectionDomain();
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private static URL stateCodeSourceLocation(ProtectionDomain domain) {
        return domain == null || domain.getCodeSource() == null
                ? null : domain.getCodeSource().getLocation();
    }

    /**
     * @deprecated Runtime effect levels are derived from proficiency and the owning skill.
     */
    @Deprecated
    public int getLevel() {
        return level;
    }

    /**
     * @deprecated Runtime effect levels are derived from proficiency and the owning skill.
     */
    @Deprecated
    public void setLevel(int level) {
        this.level = level;
    }

    public abstract Identifier getType();
}
