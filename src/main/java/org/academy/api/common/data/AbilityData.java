package org.academy.api.common.data;

import net.minecraft.util.Mth;
import org.academy.AcademyCraft;
import org.academy.api.common.ability.AbilityLevel;

import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Objects;

public class AbilityData {
    public static final int FIXED_MAX_SP = 1_000;
    private static final StackWalker STATE_STACK_WALKER = StackWalker.getInstance(
            StackWalker.Option.RETAIN_CLASS_REFERENCE
    );

    private float maxCP = 100;
    private float availableCP = 100;
    private AbilityLevel level = AbilityLevel.LEVEL0;
    private Status status = Status.NORMAL;
    private int stateTimer = 0;

    private int currSP = FIXED_MAX_SP;
    private int maxSP = FIXED_MAX_SP;
    private int spRegenTimer = 0;
    private int foodSpRecoveryTicks = 0;
    private float spRecoveryCpRemainder = 0.0f;

    private float currMP;
    private float maxMP;

    private float abilityExp = 0;

    private transient boolean isDirty = false;
    private transient Class<?> statusOwner;

    public AbilityData() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public void markDirty() {
        isDirty = true;
    }

    public void clearDirty() {
        isDirty = false;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void tickStateTimer() {
        if (statusOwner != null
                && !isStateMutationCallerAllowed("tickStateTimer", statusOwner)) return;
        if (stateTimer > 0) {
            stateTimer--;
        }
    }

    public boolean tickFoodSpRecovery() {
        if (foodSpRecoveryTicks <= 0) return false;

        foodSpRecoveryTicks--;
        spRegenTimer++;
        var dirty = false;
        if (spRegenTimer >= 20) {
            spRegenTimer = 0;
            addSP(1);
            dirty = true;
        }
        if (foodSpRecoveryTicks <= 0) {
            foodSpRecoveryTicks = 0;
            spRegenTimer = 0;
            markDirty();
            dirty = true;
        }
        return dirty;
    }

    public float getMaxCP() {
        return maxCP;
    }

    public void setMaxCP(float maxCP) {
        this.maxCP = maxCP;
        markDirty();
    }

    public float getAvailableCP() {
        return availableCP;
    }

    public void setAvailableCP(float availableCP) {
        this.availableCP = Math.min(availableCP, maxCP);
        markDirty();
    }

    public void setAvailableCP(float availableCP, float effectiveMaxCP) {
        this.availableCP = Math.min(availableCP, effectiveMaxCP);
        markDirty();
    }

    public AbilityData copyWithMaxCP(float effectiveMaxCP) {
        var copy = new AbilityData();
        copy.maxCP = effectiveMaxCP;
        copy.availableCP = Math.min(availableCP, effectiveMaxCP);
        copy.level = level;
        copy.status = status;
        copy.stateTimer = stateTimer;
        copy.currSP = getCurrSP();
        copy.maxSP = FIXED_MAX_SP;
        copy.spRegenTimer = spRegenTimer;
        copy.spRecoveryCpRemainder = getSpRecoveryCpRemainder();
        copy.currMP = currMP;
        copy.maxMP = maxMP;
        copy.abilityExp = abilityExp;
        return copy;
    }

    public AbilityLevel getLevel() {
        return level;
    }

    public void setLevel(AbilityLevel level) {
        this.level = level;
        markDirty();
    }

    public Status getStatus() {
        return status;
    }

    public final void setStatus(Status status) {
        if (statusOwner != null
                && !isStateMutationCallerAllowed("setStatus", statusOwner)) return;
        this.status = Objects.requireNonNull(status, "status");
        markDirty();
    }

    public int getStateTimer() {
        return stateTimer;
    }

    public final void setStateTimer(int stateTimer) {
        if (statusOwner != null
                && !isStateMutationCallerAllowed("setStateTimer", statusOwner)) return;
        this.stateTimer = stateTimer;
        markDirty();
    }

    public final void bindStatusProtection(Class<?> owner) {
        if (!isStateMutationCallerAllowed("bindStatusProtection", AcademyCraft.class)) return;
        statusOwner = Objects.requireNonNull(owner, "owner");
    }

    private static boolean isStateMutationCallerAllowed(String entryMethod, Class<?> owner) {
        var caller = STATE_STACK_WALKER.walk(frames -> frames
                        .dropWhile(frame -> frame.getDeclaringClass() != AbilityData.class
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

    public int getCurrSP() {
        normalizeSpLimit();
        return currSP;
    }

    public void setCurrSP(int currSP) {
        normalizeSpLimit();
        this.currSP = Mth.clamp(currSP, 0, FIXED_MAX_SP);
        markDirty();
    }

    public void addSP(int amount) {
        normalizeSpLimit();
        currSP = Mth.clamp(currSP + amount, 0, FIXED_MAX_SP);
        markDirty();
    }

    public int getMaxSP() {
        normalizeSpLimit();
        return FIXED_MAX_SP;
    }

    public void setMaxSP(int maxSP) {
        normalizeSpLimit();
        markDirty();
    }

    public float getSpRecoveryCpRemainder() {
        if (!Float.isFinite(spRecoveryCpRemainder) || spRecoveryCpRemainder < 0.0f) {
            spRecoveryCpRemainder = 0.0f;
        }
        return spRecoveryCpRemainder;
    }

    public void setSpRecoveryCpRemainder(float remainder) {
        spRecoveryCpRemainder = Float.isFinite(remainder)
                ? Math.max(0.0f, remainder)
                : 0.0f;
        markDirty();
    }

    private void normalizeSpLimit() {
        var normalized = Mth.clamp(currSP, 0, FIXED_MAX_SP);
        if (maxSP == FIXED_MAX_SP && currSP == normalized) return;
        maxSP = FIXED_MAX_SP;
        currSP = normalized;
        markDirty();
    }

    public int getSpRegenTimer() {
        return spRegenTimer;
    }

    public void setSpRegenTimer(int setSpRegenTimer) {
        spRegenTimer = setSpRegenTimer;
    }

    public int getFoodSpRecoveryTicks() {
        foodSpRecoveryTicks = Math.max(0, foodSpRecoveryTicks);
        return foodSpRecoveryTicks;
    }

    public void addFoodSpRecoveryTicks(int durationTicks) {
        if (durationTicks <= 0) return;
        if (foodSpRecoveryTicks <= 0) spRegenTimer = 0;
        foodSpRecoveryTicks = (int) Math.min(
                Integer.MAX_VALUE,
                (long) Math.max(0, foodSpRecoveryTicks) + durationTicks
        );
        markDirty();
    }

    public float getCurrMP() {
        normalizeMpLimit();
        return currMP;
    }

    public void setCurrMP(float currMP) {
        this.currMP = Float.isFinite(currMP)
                ? Math.max(0.0f, currMP)
                : 0.0f;
        markDirty();
    }

    public void addMP(float amount) {
        var safeAmount = Float.isFinite(amount) ? amount : 0.0f;
        var next = getCurrMP() + safeAmount;
        currMP = Float.isFinite(next)
                ? Math.max(0.0f, next)
                : safeAmount > 0.0f ? Float.MAX_VALUE : 0.0f;
        markDirty();
    }

    public float getMaxMP() {
        normalizeMpLimit();
        return maxMP;
    }

    public void setMaxMP(float maxMP) {
        this.maxMP = Float.isFinite(maxMP) ? Math.max(0.0f, maxMP) : 0.0f;
        markDirty();
    }

    private float normalizedMaxMp() {
        return Float.isFinite(maxMP) ? Math.max(0.0f, maxMP) : 0.0f;
    }

    private void normalizeMpLimit() {
        var normalizedMax = normalizedMaxMp();
        var normalizedCurrent = Float.isFinite(currMP)
                ? Math.max(0.0f, currMP)
                : 0.0f;
        if (Float.compare(maxMP, normalizedMax) == 0
                && Float.compare(currMP, normalizedCurrent) == 0) return;
        maxMP = normalizedMax;
        currMP = normalizedCurrent;
        markDirty();
    }

    public float getAbilityExp() {
        return abilityExp;
    }

    public void setAbilityExp(float abilityExp) {
        this.abilityExp = abilityExp;
        markDirty();
    }

    public void addAbilityExp(float amount) {
        abilityExp += amount;
        markDirty();
    }

    public enum Status {
        NORMAL,
        PERSONAL_REALITY_OVERLOAD,
        OVERLOAD
    }

    public static class Builder {
        private final AbilityData data;

        public Builder() {
            data = new AbilityData();
        }

        public Builder maxCP(float maxCP) {
            data.maxCP = maxCP;
            return this;
        }

        public Builder availableCP(float availableCP) {
            data.availableCP = availableCP;
            return this;
        }

        public Builder level(AbilityLevel level) {
            data.level = level;
            return this;
        }

        public Builder status(Status status) {
            data.status = status;
            return this;
        }

        public Builder stateTimer(int stateTimer) {
            data.stateTimer = stateTimer;
            return this;
        }

        public Builder currSP(int currSP) {
            data.currSP = Mth.clamp(currSP, 0, FIXED_MAX_SP);
            return this;
        }

        public Builder maxSP(int maxSP) {
            data.maxSP = FIXED_MAX_SP;
            return this;
        }

        public Builder currMP(float currMP) {
            data.currMP = currMP;
            return this;
        }

        public Builder maxMP(float maxMP) {
            data.maxMP = maxMP;
            return this;
        }

        public Builder abilityExp(float abilityExp) {
            data.abilityExp = abilityExp;
            return this;
        }

        public AbilityData build() {
            data.normalizeMpLimit();
            data.clearDirty();
            return data;
        }
    }

    public static class CpOccupationData {
        private final String skillId;
        private final boolean isPermanent;
        private float amount;
        private int iterationTicks;
        private final String stackGroup;

        public CpOccupationData(float amount, int iterationTicks, String skillId, boolean isPermanent) {
            this(amount, iterationTicks, skillId, isPermanent, skillId);
        }

        public CpOccupationData(float amount, int iterationTicks, String skillId, boolean isPermanent,
                                String stackGroup) {
            this.amount = amount;
            this.iterationTicks = iterationTicks;
            this.skillId = skillId;
            this.isPermanent = isPermanent;
            this.stackGroup = stackGroup;
        }

        public boolean isFree() {
            return iterationTicks <= 0;
        }

        public float getAmount() {
            return amount;
        }

        public void setAmount(float amount) {
            if (!isOccupationMutationCallerAllowed("setAmount")) return;
            this.amount = Math.max(0.0f, amount);
        }

        public int getIterationTicks() {
            return iterationTicks;
        }

        public void setIterationTicks(int iterationTicks) {
            if (!isOccupationMutationCallerAllowed("setIterationTicks")) return;
            this.iterationTicks = iterationTicks;
        }

        public String getSkillId() {
            return skillId;
        }

        public String getStackGroup() {
            return stackGroup == null || stackGroup.isBlank() ? skillId : stackGroup;
        }

        public boolean isPermanent() {
            return isPermanent;
        }

        private static boolean isOccupationMutationCallerAllowed(String entryMethod) {
            var caller = STATE_STACK_WALKER.walk(frames -> frames
                            .dropWhile(frame -> frame.getDeclaringClass() != CpOccupationData.class
                                    || !frame.getMethodName().equals(entryMethod))
                            .skip(1)
                            .map(StackWalker.StackFrame::getDeclaringClass)
                            .findFirst()
                            .orElse(null));
            return AbilityData.sameStateCodeSource(caller, AcademyCraft.class);
        }
    }
}
