package org.academy.api.common.data;

import org.academy.api.common.ability.AbilityLevel;

public class AbilityData {
    public static final int FIXED_MAX_SP = 1_000;

    // CP
    private float maxCP = 100;
    private float availableCP = 100;
    private AbilityLevel level = AbilityLevel.LEVEL0;
    private Status status = Status.NORMAL;
    private int stateTimer = 0;

    // SP
    private int currSP = FIXED_MAX_SP;
    private int maxSP = FIXED_MAX_SP;
    private int spRegenTimer = 0;
    private float spRecoveryCpRemainder = 0.0f;

    // MP (Matter Point)
    private float currMP = 100;
    private float maxMP = 100;

    // Ability Exp
    private float abilityExp = 0;

    private transient boolean isDirty = false;

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
        if (stateTimer > 0) {
            stateTimer--;
        }
    }

    public boolean tickSpRegenTimer() {
        var threshold = 20;

        spRegenTimer++;
        if (spRegenTimer >= threshold) {
            spRegenTimer = 0;
            addSP(1);
            return true;
        }
        return false;
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

    public void setStatus(Status status) {
        this.status = status;
        markDirty();
    }

    public int getStateTimer() {
        return stateTimer;
    }

    public void setStateTimer(int stateTimer) {
        this.stateTimer = stateTimer;
        markDirty();
    }

    public int getCurrSP() {
        normalizeSpLimit();
        return currSP;
    }

    public void setCurrSP(int currSP) {
        normalizeSpLimit();
        this.currSP = Math.clamp(currSP, 0, FIXED_MAX_SP);
        markDirty();
    }

    public void addSP(int amount) {
        normalizeSpLimit();
        currSP = Math.clamp(currSP + amount, 0, FIXED_MAX_SP);
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
        } else if (spRecoveryCpRemainder >= 10.0f) {
            spRecoveryCpRemainder %= 10.0f;
        }
        return spRecoveryCpRemainder;
    }

    public void setSpRecoveryCpRemainder(float remainder) {
        spRecoveryCpRemainder = Float.isFinite(remainder)
                ? Math.clamp(remainder, 0.0f, Math.nextDown(10.0f))
                : 0.0f;
        markDirty();
    }

    private void normalizeSpLimit() {
        var normalized = Math.clamp(currSP, 0, FIXED_MAX_SP);
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

    public float getCurrMP() {
        return currMP;
    }

    public void setCurrMP(float currMP) {
        this.currMP = Math.clamp(maxMP, 0, currMP);
        markDirty();
    }

    public void addMP(float amount) {
        currMP = Math.clamp(maxMP, 0, currMP + amount);
        markDirty();
    }

    public float getMaxMP() {
        return maxMP;
    }

    public void setMaxMP(float maxMP) {
        this.maxMP = maxMP;
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
            data.currSP = Math.clamp(currSP, 0, FIXED_MAX_SP);
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
            return data;
        }
    }

    public static class CpOccupationData {
        private float amount;
        private final String skillId;
        private final boolean isPermanent;
        private int iterationTicks;

        public CpOccupationData(float amount, int iterationTicks, String skillId, boolean isPermanent) {
            this.amount = amount;
            this.iterationTicks = iterationTicks;
            this.skillId = skillId;
            this.isPermanent = isPermanent;
        }

        public boolean isFree() {
            return iterationTicks <= 0;
        }

        public float getAmount() {
            return amount;
        }

        public void setAmount(float amount) {
            this.amount = Math.max(0.0f, amount);
        }

        public int getIterationTicks() {
            return iterationTicks;
        }

        public void setIterationTicks(int iterationTicks) {
            this.iterationTicks = iterationTicks;
        }

        public String getSkillId() {
            return skillId;
        }

        public boolean isPermanent() {
            return isPermanent;
        }
    }
}
