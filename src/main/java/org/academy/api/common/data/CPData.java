package org.academy.api.common.data;

import org.academy.api.common.ability.AbilityLevel;

public class CPData {
    // CP
    private float maxCP = 100;
    private float availableCP = 100;
    private AbilityLevel level = AbilityLevel.LEVEL0;
    private Status status = Status.NORMAL;
    private int stateTimer = 0;// 状态定时器，大于零时每tick自减

    // SP
    private int currSP = 2000;
    private int maxSP = 2000;
    private int spRegenTimer = 0;// 每tick自增，到20时，SP增加1

    private transient boolean isDirty = false;

    public enum Status {
        NORMAL,
        PERSONAL_REALITY_OVERLOAD,
        OVERLOAD
    }

    public CPData() {
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
        return currSP;
    }

    public void setCurrSP(int currSP) {
        this.currSP = Math.max(0, Math.min(maxSP, currSP));
        markDirty();
    }

    public void addSP(int amount) {
        currSP = Math.max(0, Math.min(maxSP, currSP + amount));
        markDirty();
    }

    public int getMaxSP() {
        return maxSP;
    }

    public void setMaxSP(int maxSP) {
        this.maxSP = maxSP;
        markDirty();
    }

    public int getSpRegenTimer() {
        return spRegenTimer;
    }

    public void setSpRegenTimer(int setSpRegenTimer) {
        this.spRegenTimer = setSpRegenTimer;
    }

    public static class Builder {
        private final CPData cpData;

        public Builder() {
            cpData = new CPData();
        }

        public Builder maxCP(float maxCP) {
            cpData.maxCP = maxCP;
            return this;
        }

        public Builder availableCP(float availableCP) {
            cpData.availableCP = availableCP;
            return this;
        }

        public Builder level(AbilityLevel level) {
            cpData.level = level;
            return this;
        }

        public Builder status(Status status) {
            cpData.status = status;
            return this;
        }

        public Builder stateTimer(int stateTimer) {
            cpData.stateTimer = stateTimer;
            return this;
        }

        public Builder currSP(int currSP) {
            cpData.currSP = currSP;
            return this;
        }

        public Builder maxSP(int maxSP) {
            cpData.maxSP = maxSP;
            return this;
        }

        public CPData build() {
            return cpData;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class CpOccupationData {
        private final float amount;
        private int iterationTicks;
        private final String skillId;
        private final boolean isPermanent;

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
