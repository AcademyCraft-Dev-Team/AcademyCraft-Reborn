package org.academy.api.common.data;

import org.academy.api.common.ability.AbilityLevel;

import java.util.concurrent.atomic.AtomicInteger;

public class CPData {
    // CP
    private float maxCP = 100;
    private float availableCP = 100;
    private AbilityLevel level = AbilityLevel.LEVEL0;
    private Status status = Status.NORMAL;
    private int stateTimer = 0;

    // SP
    private final AtomicInteger currSP = new AtomicInteger(2000);
    private volatile int maxSP = 2000;

    private boolean isDirty = false;

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
        currSP.set(source.currSP.get());
        maxSP = source.maxSP;
        isDirty = source.isDirty;
    }

    public CPData() {
    }

    public void markDirty() {
        isDirty = true;
    }

    public void clean() {
        isDirty = false;
    }

    public boolean isDirty() {
        return isDirty;
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
        this.availableCP = availableCP;
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
        return currSP.get();
    }

    public void setCurrSP(int currSP) {
        this.currSP.set(Math.max(0, Math.min(maxSP, currSP)));
        markDirty();
    }

    public void addSP(int amount) {
        currSP.updateAndGet(c -> Math.max(0, Math.min(maxSP, c + amount)));
        markDirty();
    }

    public int getMaxSP() {
        return maxSP;
    }

    public void setMaxSP(int maxSP) {
        this.maxSP = maxSP;
        markDirty();
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
            cpData.currSP.set(currSP);
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
