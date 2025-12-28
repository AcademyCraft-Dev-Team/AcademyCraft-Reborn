package org.academy.api.common.data;

import org.academy.api.common.ability.AbilityLevel;

public class CPData {
    // CP
    private float maxCP = 100;
    private float availableCP = 100;
    private AbilityLevel level = AbilityLevel.LEVEL0;
    private Status status = Status.NORMAL;
    private int stateTimer = 0;

    // SP
    private int currSP = 0;
    private int maxSP = 1000;

    public enum Status {
        NORMAL,
        PERSONAL_REALITY_OVERLOAD,
        OVERLOAD
    }

    public CPData(CPData source) {
        maxCP = source.maxCP;
        availableCP = source.availableCP;
        status = source.status;
        stateTimer = source.stateTimer;
        level = source.level;
        currSP = source.currSP;
        maxSP = source.maxSP;
    }

    public CPData() {
    }

    public float getMaxCP() {
        return maxCP;
    }

    public float getAvailableCP() {
        return availableCP;
    }

    public AbilityLevel getLevel() {
        return level;
    }

    public Status getStatus() {
        return status;
    }

    public int getStateTimer() {
        return stateTimer;
    }

    public void setMaxCP(float maxCP) {
        this.maxCP = maxCP;
    }

    public void setAvailableCP(float availableCP) {
        this.availableCP = availableCP;
    }

    public void setLevel(AbilityLevel level) {
        this.level = level;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setStateTimer(int stateTimer) {
        this.stateTimer = stateTimer;
    }

    public int getCurrSP() {
        return currSP;
    }

    public int getMaxSP() {
        return maxSP;
    }

    public void setCurrSP(int currSP) {
        this.currSP = currSP;
    }

    public void setMaxSP(int maxSP) {
        this.maxSP = maxSP;
    }

    public static class Builder {
        private CPData cpData;

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

    public static class CPOccupationData {
        float amount;
        int iterationTicks;

        public CPOccupationData(float amount, int iterationTicks) {
            this.amount = amount;
            this.iterationTicks = iterationTicks;
        }

        public boolean isFree() {
            return iterationTicks <= 0;
        }

        public float getAmount() {
            return amount;
        }

        public void setAmount(float amount) {
            this.amount = amount;
        }

        public int getIterationTicks() {
            return iterationTicks;
        }

        public void setIterationTicks(int iterationTicks) {
            this.iterationTicks = iterationTicks;
        }
    }
}
