package org.academy.internal.server.world.level.storage;

import com.google.gson.annotations.SerializedName;
import org.academy.api.common.attribute.AbilityFactor;
import org.academy.internal.common.attribute.PropsMath;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Persistent, server-authoritative P.R.O.P.S state.
 */
public final class PropsData {
    public static final int CURRENT_VERSION = 1;
    private static final int VALID_LOCK_MASK = (1 << AbilityFactor.values().length) - 1;

    @SerializedName("version")
    private int version;
    @SerializedName("values")
    private double[] values = new double[AbilityFactor.values().length];
    @SerializedName("lockedMask")
    private int lockedMask;
    @SerializedName("visitedStructures")
    private Set<String> visitedStructures = new HashSet<>();
    @SerializedName("milestoneMask")
    private int milestoneMask;
    @SerializedName("started")
    private boolean started;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public double get(AbilityFactor factor) {
        ensureContainers();
        return values[factor.ordinal()];
    }

    public boolean isStarted() {
        return started;
    }

    public boolean start() {
        if (started) return false;
        started = true;
        return true;
    }

    public void set(AbilityFactor factor, double value) {
        ensureContainers();
        values[factor.ordinal()] = PropsMath.finiteNonNegative(value);
        enforceTotalLimit();
    }

    public void initialize(double[] initialValues) {
        values = new double[AbilityFactor.values().length];
        if (initialValues != null) {
            for (var i = 0; i < Math.min(initialValues.length, values.length); i++) {
                values[i] = PropsMath.finiteNonNegative(initialValues[i]);
            }
        }
        enforceTotalLimit();
        version = CURRENT_VERSION;
    }

    public double total() {
        ensureContainers();
        return Arrays.stream(values).sum();
    }

    public boolean isLocked(AbilityFactor factor) {
        return (lockedMask & factor.bit()) != 0;
    }

    public boolean setLocked(AbilityFactor factor, boolean locked) {
        var oldMask = lockedMask;
        lockedMask = locked ? lockedMask | factor.bit() : lockedMask & ~factor.bit();
        lockedMask &= VALID_LOCK_MASK;
        return oldMask != lockedMask;
    }

    public int getLockedMask() {
        return lockedMask & VALID_LOCK_MASK;
    }

    public boolean visitStructure(String key) {
        ensureContainers();
        return visitedStructures.add(key);
    }

    public boolean markMilestone(int bit) {
        if ((milestoneMask & bit) != 0) return false;
        milestoneMask |= bit;
        return true;
    }

    public boolean repair() {
        var beforeVersion = version;
        var beforeMask = lockedMask;
        var beforeMilestones = milestoneMask;
        var beforeValues = values == null ? null : values.clone();
        var missingStructures = visitedStructures == null;

        ensureContainers();
        for (var i = 0; i < values.length; i++) {
            values[i] = PropsMath.finiteNonNegative(values[i]);
        }
        enforceTotalLimit();
        lockedMask &= VALID_LOCK_MASK;
        milestoneMask = Math.max(0, milestoneMask);
        if (version > CURRENT_VERSION) version = CURRENT_VERSION;

        return beforeVersion != version
                || beforeMask != lockedMask
                || beforeMilestones != milestoneMask
                || missingStructures
                || beforeValues == null
                || !Arrays.equals(beforeValues, values);
    }

    public double[] copyValues() {
        ensureContainers();
        return values.clone();
    }

    private void ensureContainers() {
        if (values == null || values.length != AbilityFactor.values().length) {
            var repaired = new double[AbilityFactor.values().length];
            if (values != null) System.arraycopy(values, 0, repaired, 0, Math.min(values.length, repaired.length));
            values = repaired;
        }
        if (visitedStructures == null) visitedStructures = new HashSet<>();
    }

    private void enforceTotalLimit() {
        var total = Arrays.stream(values).sum();
        if (total <= PropsMath.MAX_TOTAL || total <= 0.0) return;
        var scale = PropsMath.MAX_TOTAL / total;
        for (var i = 0; i < values.length; i++) values[i] *= scale;
    }
}
